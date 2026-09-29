# Elbows Up Fork Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Elbows Up fork — two GitHub forks, a patched Fossify Commons published to mavenLocal, and a renamed app that builds a debug APK, ready for feature work.

**Architecture:** Two independent git repos side by side (`phone/`, `commons/`) under a plain workspace folder. The app keeps upstream's code namespace `org.fossify.phone` and changes only its application id. Commons carries nine small patches, is published as `6.1.6-elbowsup1`, and the app pins that version through the `mavenLocal()` repository it already lists. Upstream remotes stay configured for regular merges.

**Tech Stack:** Android Gradle Plugin 9.4.1 (app) / 9.0.1 (Commons), Gradle wrappers 9.7.1 (app) / 9.3.1 (Commons), Kotlin 2.4.10 (app) / 2.3.10 (Commons), JDK 21, Android SDK 36, `gh` CLI (authenticated as `ccormier`).

**Spec:** `/Users/chrisc/workspace/elbowsup/docs/superpowers/specs/2026-09-29-elbowsup-fork-setup-design.md`

## Global Constraints

- Application id: `com.keejii.elbowsup` (debug builds: `com.keejii.elbowsup.debug`).
- Code namespace stays `org.fossify.phone` — do not rename Kotlin packages.
- Commons patch version: `6.1.6-elbowsup1`, based on upstream tag `6.1.6`.
- Every Gradle command runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Android SDK is `/opt/homebrew/share/android-commandlinetools`, supplied via `local.properties` in each repo.
- Never commit `local.properties`, build outputs, or keystores.
- Keep Fossify attribution, license files, and upstream version numbers (`1.11.1` / `22`).
- GitHub repos: `ccormier/elbowsup-phone`, `ccormier/elbowsup-commons` (public forks).
- First Gradle runs download distributions and dependencies (Commons ~10 min, app ~25 min); use generous timeouts.

## Review Focus

Behaviors the spec requires that builds alone cannot prove — each gets the closest static check in its owning task, and a manual device check in Task 6:

1. **No anti-tamper nag on any launch** — the "fake version" dialog must never appear and "Customize colors" must keep working past 100 runs. Static check: the string is absent from the published AAR (Task 4) and from the APK (Task 5).
2. **Side-by-side install** — `com.keejii.elbowsup` must install alongside `org.fossify.phone`. Static check: APK badging shows our package id (Task 5).
3. **Outgoing calls when we hold the dialer role** — must target our own `DialerActivity`, never the official app's package. Static check: no hard-coded `"org.fossify.phone` strings remain in Commons except the intentional `ManageBlockedNumbersActivity` checks (Task 3).
4. **Dialer-role behavior** — the app prompts to become the default dialer when it is not, and treats itself as one only when it holds the role. Covered by the `isDefaultDialer()` patch (Task 3); device check in Task 6.
5. **Blocked numbers / private contacts** — the blocked-numbers screen recognises our id as a dialer, and the contacts-provider allowlist no longer hides private contacts. Covered by patches (Task 3); device check in Task 6.

---

### Task 1: Forks and workspace clones

**Files:**
- Create: `/Users/chrisc/workspace/elbowsup/` (plain directory)
- Create: `/Users/chrisc/workspace/elbowsup/phone/` (clone)
- Create: `/Users/chrisc/workspace/elbowsup/commons/` (clone)

**Interfaces:**
- Consumes: nothing.
- Produces: `phone/` on `main` tracking `origin` (our fork) with `upstream` = FossifyOrg/Phone; `commons/` on `main` based on tag `6.1.6` with `upstream` = FossifyOrg/Commons.

- [ ] **Step 1: Create the workspace directory**

```bash
mkdir -p /Users/chrisc/workspace/elbowsup
```

- [ ] **Step 2: Create the two GitHub forks (idempotent)**

```bash
gh repo view ccormier/elbowsup-phone --json name >/dev/null 2>&1 \
  || gh repo fork FossifyOrg/Phone --fork-name elbowsup-phone --clone=false
gh repo view ccormier/elbowsup-commons --json name >/dev/null 2>&1 \
  || gh repo fork FossifyOrg/Commons --fork-name elbowsup-commons --clone=false
```

Expected: both commands exit 0; the forks exist under `ccormier`.

- [ ] **Step 3: Disable GitHub Actions on both forks**

```bash
gh api --method PUT /repos/ccormier/elbowsup-phone/actions/permissions -F enabled=false
gh api --method PUT /repos/ccormier/elbowsup-commons/actions/permissions -F enabled=false
```

Verify:

```bash
gh api /repos/ccormier/elbowsup-phone/actions/permissions | grep -o '"enabled":[a-z]*'
gh api /repos/ccormier/elbowsup-commons/actions/permissions | grep -o '"enabled":[a-z]*'
```

Expected: `"enabled":false` twice.

- [ ] **Step 4: Clone both repos**

```bash
cd /Users/chrisc/workspace/elbowsup
git clone git@github.com:ccormier/elbowsup-phone.git phone
git clone git@github.com:ccormier/elbowsup-commons.git commons
```

- [ ] **Step 5: Add the upstream remotes**

```bash
git -C /Users/chrisc/workspace/elbowsup/phone remote add upstream https://github.com/FossifyOrg/Phone.git
git -C /Users/chrisc/workspace/elbowsup/commons remote add upstream https://github.com/FossifyOrg/Commons.git
```

- [ ] **Step 6: Base the Commons branch on the pinned tag**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
git fetch upstream --tags
git switch -C main 6.1.6
```

Verify:

```bash
git -C /Users/chrisc/workspace/elbowsup/commons tag -l 6.1.6
git -C /Users/chrisc/workspace/elbowsup/commons log -1 --format='%h %s'
```

Expected: `6.1.6` and the tag's commit subject (the Commons release commit). The fork's original `main` will be force-pushed over in Task 4 — that is intended.

- [ ] **Step 7: Verify remotes**

```bash
git -C /Users/chrisc/workspace/elbowsup/phone remote -v
git -C /Users/chrisc/workspace/elbowsup/commons remote -v
```

Expected: `origin` → `ccormier/elbowsup-*` (fetch and push), `upstream` → `FossifyOrg/*` (fetch). No commit in this task — nothing has changed yet.

---

### Task 2: Commons — remove the anti-tamper checks

**Files:**
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/extensions/Activity.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/compose/extensions/ActivityExtensions.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/compose/extensions/ComposeActivityExtensions.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/compose/theme/AppTheme.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/activities/BaseSimpleActivity.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/activities/CustomizationActivity.kt`

**Interfaces:**
- Consumes: the Commons clone from Task 1.
- Produces: `Activity.checkAppSideloading(): Boolean` keeps its signature and always returns `false`; the fake-version dialog API (`fakeVersionCheck`, `FakeVersionCheck`, `FAKE_VERSION_APP_LABEL`, `showModdedAppWarning`) no longer exists.

All paths below are relative to `/Users/chrisc/workspace/elbowsup/commons/`.

- [ ] **Step 1: Reduce `checkAppSideloading()` and delete the modded-app warning in `extensions/Activity.kt`**

Replace this block:

```kotlin
fun BaseSimpleActivity.showModdedAppWarning() {
    val label =
        "You are using a fake version of the app. For your own safety " +
                "download the original one from www.fossify.org. Thanks"
    ConfirmationDialog(
        activity = this,
        message = label,
        positive = R.string.ok,
        negative = 0
    ) {
        launchViewIntent(DEVELOPER_PLAY_STORE_URL)
    }
}

fun Activity.checkAppSideloading(): Boolean {
    val isSideloaded = when (baseConfig.appSideloadingStatus) {
        SIDELOADING_TRUE -> true
        SIDELOADING_FALSE -> false
        else -> isAppSideloaded()
    }

    baseConfig.appSideloadingStatus = if (isSideloaded) SIDELOADING_TRUE else SIDELOADING_FALSE
    if (isSideloaded) {
        showSideloadingDialog()
    }

    return isSideloaded
}
```

with:

```kotlin
fun Activity.checkAppSideloading(): Boolean {
    // elbowsup: anti-tamper check removed; our fork is never treated as sideloaded
    baseConfig.appSideloadingStatus = SIDELOADING_FALSE
    return false
}
```

Then delete the now-unused import line:

```kotlin
import org.fossify.commons.helpers.SIDELOADING_TRUE
```

(`isAppSideloaded()` and `showSideloadingDialog()` stay in the file; `BaseSplashActivity` still references them.)

- [ ] **Step 2: Delete the fake-version check in `compose/extensions/ActivityExtensions.kt`**

Delete this block (keep `DEVELOPER_PLAY_STORE_URL` above it — `upgradeToPro()` still uses it):

```kotlin
const val FAKE_VERSION_APP_LABEL =
    "You are using a fake version of the app. For your own safety download the original one from www.fossify.org. Thanks"

fun Context.fakeVersionCheck(
    showConfirmationDialog: () -> Unit
) {
    if (!packageName.startsWith("org.fossify.", true)) {
        if ((0..50).random() == 10 || baseConfig.appRunCount % 100 == 0) {
            showConfirmationDialog()
        }
    }
}
```

- [ ] **Step 3: Delete the `FakeVersionCheck()` composable in `compose/extensions/ComposeActivityExtensions.kt`**

Delete:

```kotlin
@Composable
fun FakeVersionCheck() {
    val context = LocalContext.current
    val confirmationDialogAlertDialogState = rememberAlertDialogState().apply {
        DialogMember {
            ConfirmationAlertDialog(
                alertDialogState = this,
                message = FAKE_VERSION_APP_LABEL,
                positive = R.string.ok,
                negative = null
            ) {
                context.getActivity().launchViewIntent(DEVELOPER_PLAY_STORE_URL)
            }
        }
    }
    LaunchedEffect(Unit) {
        context.fakeVersionCheck(confirmationDialogAlertDialogState::show)
    }
}
```

Then delete the now-unused import:

```kotlin
import org.fossify.commons.extensions.launchViewIntent
```

(`CheckAppOnSdCard()` and its imports stay.)

- [ ] **Step 4: Remove the call site in `compose/theme/AppTheme.kt`**

Delete the import:

```kotlin
import org.fossify.commons.compose.extensions.FakeVersionCheck
```

Change:

```kotlin
    Theme(theme = currentTheme) {
        content()
        if (!view.isInEditMode) {
            OnContentDisplayed()
        }
    }
```

to:

```kotlin
    Theme(theme = currentTheme) {
        content()
    }
```

And delete the private helper at the end of the file:

```kotlin
@Composable
private fun OnContentDisplayed() {
    FakeVersionCheck()
}
```

- [ ] **Step 5: Remove the launch nag and customization guard in `activities/BaseSimpleActivity.kt`**

Delete the import:

```kotlin
import org.fossify.commons.extensions.showModdedAppWarning
```

Delete this block from `onCreate`:

```kotlin
        if (!packageName.startsWith("org.fossify.", true)) {
            if ((0..50).random() == 10 || baseConfig.appRunCount % 100 == 0) {
                showModdedAppWarning()
            }
        }
```

Delete this guard from `startCustomizationActivity()`:

```kotlin
        if (!packageName.contains("yfissof".reversed(), true)) {
            if (baseConfig.appRunCount > 100) {
                showModdedAppWarning()
                return
            }
        }
```

- [ ] **Step 6: Remove the primary-color guard in `activities/CustomizationActivity.kt`**

Delete this block from `pickPrimaryColor()`:

```kotlin
        if (!packageName.startsWith("org.fossify.", true) && baseConfig.appRunCount > 50) {
            finish()
            return
        }
```

- [ ] **Step 7: Compile**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > local.properties
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :commons:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`. First run downloads Gradle 9.3.1 and dependencies; allow up to 15 minutes.

- [ ] **Step 8: Commit**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
git add -A
git commit -m "Remove Fossify anti-tamper checks (fake-version dialog, sideload gate, customization guards)"
```

Expected: commit created; `local.properties` is not staged (it is gitignored).

---

### Task 3: Commons — fix hard-coded `org.fossify.phone` for renamed forks

**Files:**
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/extensions/Activity.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/extensions/Context.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/helpers/MyContactsContentProvider.kt`
- Modify: `commons/commons/src/main/kotlin/org/fossify/commons/activities/ManageBlockedNumbersActivity.kt`

**Interfaces:**
- Consumes: Task 2's committed tree.
- Produces: `BaseSimpleActivity.launchCallIntent` targets the caller's own `packageName`; `Context.isDefaultDialer()` reflects the real dialer role for any package; the contacts provider has no package allowlist; blocked-numbers UI recognises `com.keejii.elbowsup`.

All paths below are relative to `/Users/chrisc/workspace/elbowsup/commons/`.

- [ ] **Step 1: Fix the call-intent package in `extensions/Activity.kt`**

Replace:

```kotlin
            if (isDefaultDialer()) {
                val packageName = if (baseConfig.appId.contains(".debug", true)) "org.fossify.phone.debug" else "org.fossify.phone"
                val className = "org.fossify.phone.activities.DialerActivity"
                setClassName(packageName, className)
            }
```

with:

```kotlin
            if (isDefaultDialer()) {
                // elbowsup: target the real (renamed) app package, not org.fossify.phone
                val className = "org.fossify.phone.activities.DialerActivity"
                setClassName(packageName, className)
            }
```

- [ ] **Step 2: Make `isDefaultDialer()` honest in `extensions/Context.kt`**

Replace:

```kotlin
fun Context.isDefaultDialer(): Boolean {
    return if (!packageName.startsWith("org.fossify.contacts") && !packageName.startsWith("org.fossify.phone")) {
        true
    } else if ((packageName.startsWith("org.fossify.contacts") || packageName.startsWith("org.fossify.phone")) && isQPlus()) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager!!.isRoleAvailable(RoleManager.ROLE_DIALER) && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        telecomManager.defaultDialerPackage == packageName
    }
}
```

with:

```kotlin
fun Context.isDefaultDialer(): Boolean {
    // elbowsup: always use the real dialer-role check for our renamed package
    return if (isQPlus()) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager!!.isRoleAvailable(RoleManager.ROLE_DIALER) && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        telecomManager.defaultDialerPackage == packageName
    }
}
```

- [ ] **Step 3: Drop the caller allowlist in `helpers/MyContactsContentProvider.kt`**

In `getSimpleContacts()` delete:

```kotlin
            val packageName = context.packageName.removeSuffix(".debug")
            if (packageName != "org.fossify.phone" && packageName != "org.fossify.messages" && packageName != "org.fossify.calendar") {
                return contacts
            }
```

In `getContacts()` delete the identical block:

```kotlin
            val packageName = context.packageName.removeSuffix(".debug")
            if (packageName != "org.fossify.phone" && packageName != "org.fossify.messages" && packageName != "org.fossify.calendar") {
                return contacts
            }
```

- [ ] **Step 4: Recognise our id in `activities/ManageBlockedNumbersActivity.kt`**

Change the `isDialer` expression:

```kotlin
            val isDialer = remember {
                config.appId.startsWith("org.fossify.phone")
            }
```

to:

```kotlin
            val isDialer = remember {
                config.appId.startsWith("org.fossify.phone") || config.appId.startsWith("com.keejii.elbowsup")
            }
```

Change `maybeSetDefaultCallerIdApp()`:

```kotlin
    private fun maybeSetDefaultCallerIdApp() {
        if (isQPlus() && baseConfig.appId.startsWith("org.fossify.phone")) {
            setDefaultCallerIdApp()
        }
    }
```

to:

```kotlin
    private fun maybeSetDefaultCallerIdApp() {
        if (isQPlus() && (baseConfig.appId.startsWith("org.fossify.phone") || baseConfig.appId.startsWith("com.keejii.elbowsup"))) {
            setDefaultCallerIdApp()
        }
    }
```

- [ ] **Step 5: Compile and check for leftovers**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :commons:compileReleaseKotlin
grep -rn '"org\.fossify\.phone' commons/src/main/kotlin
```

Expected: `BUILD SUCCESSFUL`, and the only grep matches are the two intentional `ManageBlockedNumbersActivity.kt` lines (the OR expressions that keep recognising the original id).

- [ ] **Step 6: Commit**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
git add -A
git commit -m "Fix hard-coded org.fossify.phone package for renamed forks"
```

---

### Task 4: Commons — FORK.md, publish `6.1.6-elbowsup1`, push

**Files:**
- Create: `commons/FORK.md`

**Interfaces:**
- Consumes: the committed patch set from Tasks 2–3.
- Produces: `org.fossify:commons:6.1.6-elbowsup1` in `~/.m2/repository`; `main` pushed to `origin`.

- [ ] **Step 1: Write `commons/FORK.md`**

````markdown
# elbowsup-commons — patched Fossify Commons

A fork of [Fossify Commons](https://github.com/FossifyOrg/Commons) used by the
Elbows Up dialer. It removes the anti-tamper checks and fixes hard-coded
`org.fossify.*` assumptions that break renamed forks.

- **Base:** upstream tag `6.1.6`
- **Published as:** `org.fossify:commons:6.1.6-elbowsup1` (via mavenLocal)
- **Upstream:** https://github.com/FossifyOrg/Commons (remote `upstream`)

## Publish

```bash
printf 'sdk.dir=/path/to/android-sdk\n' > local.properties
./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1
```

## Patches (re-apply on every new upstream tag)

1. `extensions/Activity.kt`
   - delete `showModdedAppWarning()`
   - `checkAppSideloading()` sets `appSideloadingStatus = SIDELOADING_FALSE` and returns false
   - `launchCallIntent` uses `setClassName(packageName, "org.fossify.phone.activities.DialerActivity")`
2. `compose/extensions/ActivityExtensions.kt` — delete `FAKE_VERSION_APP_LABEL` and `fakeVersionCheck()`
3. `compose/extensions/ComposeActivityExtensions.kt` — delete `FakeVersionCheck()`
4. `compose/theme/AppTheme.kt` — remove `OnContentDisplayed()` and its call
5. `activities/BaseSimpleActivity.kt` — remove the `onCreate` modded-app block and the `startCustomizationActivity()` guard
6. `activities/CustomizationActivity.kt` — remove the `pickPrimaryColor()` guard
7. `helpers/MyContactsContentProvider.kt` — drop the caller-package allowlist
8. `activities/ManageBlockedNumbersActivity.kt` — recognise `com.keejii.elbowsup`
9. `extensions/Context.kt` — `isDefaultDialer()` uses the real role check

## Rebasing onto a new upstream Commons tag

```bash
git fetch upstream --tags
git switch -C main <new-tag>          # re-apply the patches above
./gradlew :commons:publishToMavenLocal -PVERSION=<new-tag>-elbowsup1
git push --force-with-lease origin main
```

Then bump the `commons` pin in the app's `gradle/libs.versions.toml`.
````

- [ ] **Step 2: Commit FORK.md**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
git add FORK.md
git commit -m "Add FORK.md documenting the patch set and republish routine"
```

- [ ] **Step 3: Publish to mavenLocal**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify the artifact and that the patch is inside it**

```bash
ls ~/.m2/repository/org/fossify/commons/6.1.6-elbowsup1/
rm -rf /tmp/elbowsup-aar && mkdir -p /tmp/elbowsup-aar && cd /tmp/elbowsup-aar
unzip -q ~/.m2/repository/org/fossify/commons/6.1.6-elbowsup1/commons-6.1.6-elbowsup1.aar
unzip -p classes.jar | grep -ac "fake version" || true
```

Expected: the listing shows `commons-6.1.6-elbowsup1.aar` and `.pom`; the grep prints `0` (grep exits non-zero when nothing matches — that is the success case here).

- [ ] **Step 5: Push the Commons branch**

```bash
cd /Users/chrisc/workspace/elbowsup/commons
git push --force-with-lease origin main
```

Expected: `main` on `origin` now points at our patched history (force is required because the fork's original `main` tracked upstream's).

---

### Task 5: App — rename, strings, fork docs, build, push

**Files:**
- Modify: `phone/gradle.properties`
- Modify: `phone/app/build.gradle.kts`
- Modify: `phone/gradle/libs.versions.toml`
- Modify: `phone/app/src/main/res/values/strings.xml` and the 57 locale files that define `app_launcher_name`
- Modify: `phone/app/src/debug/res/values/strings.xml`
- Modify: `phone/app/src/main/res/values/donottranslate.xml`
- Create: `phone/FORK.md`
- Create: `phone/docs/superpowers/specs/2026-09-29-elbowsup-fork-setup-design.md` (copy)
- Create: `phone/docs/superpowers/plans/2026-09-29-elbowsup-fork-setup.md` (copy)

**Interfaces:**
- Consumes: `6.1.6-elbowsup1` published in Task 4.
- Produces: debug APK at `phone/app/build/outputs/apk/foss/debug/app-foss-debug.apk` with package `com.keejii.elbowsup.debug` and label `Elbows Up_debug`; `main` pushed to `origin`.

All paths below are relative to `/Users/chrisc/workspace/elbowsup/phone/`.

- [ ] **Step 1: Point the build at the SDK**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > local.properties
```

(`/local.properties` is already in the repo's `.gitignore`.)

- [ ] **Step 2: Split app id and namespace in `gradle.properties`**

Replace:

```properties
APP_ID=org.fossify.phone
```

with:

```properties
APP_ID=com.keejii.elbowsup
APP_NAMESPACE=org.fossify.phone
```

- [ ] **Step 3: Use the namespace property in `app/build.gradle.kts`**

Replace:

```kotlin
    namespace = project.property("APP_ID").toString()
```

with:

```kotlin
    namespace = project.property("APP_NAMESPACE").toString()
```

- [ ] **Step 4: Pin the patched Commons in `gradle/libs.versions.toml`**

Replace:

```toml
commons = "6.1.6"
```

with:

```toml
commons = "6.1.6-elbowsup1"
```

- [ ] **Step 5: Set the launcher name in every locale**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
grep -rl 'name="app_launcher_name"' app/src/main/res | while IFS= read -r f; do
  sed -i '' 's#<string name="app_launcher_name">[^<]*</string>#<string name="app_launcher_name">Elbows Up</string>#' "$f"
done
```

Verify (expect exactly one unique line and 58 files):

```bash
grep -rh 'name="app_launcher_name"' app/src/main/res | sort -u
grep -rl 'name="app_launcher_name"' app/src/main/res | wc -l
```

- [ ] **Step 6: Set the debug launcher name**

Replace the contents of `app/src/debug/res/values/strings.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_launcher_name">Elbows Up_debug</string>
</resources>
```

- [ ] **Step 7: Update app identity strings in `app/src/main/res/values/donottranslate.xml`**

Replace the whole file with:

```xml
<resources>
    <string name="package_name">com.keejii.elbowsup</string>
    <string name="app_name">Elbows Up</string>
</resources>
```

- [ ] **Step 8: Write `phone/FORK.md`**

````markdown
# Elbows Up — fork notes

Elbows Up is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone).

- **Application id:** `com.keejii.elbowsup` (debug: `com.keejii.elbowsup.debug`)
- **Code namespace:** `org.fossify.phone` (unchanged from upstream, to keep merges small)
- **Upstream:** https://github.com/FossifyOrg/Phone (remote `upstream`)

## Building

Requires JDK 17+ (21 used here) and an Android SDK with platform 36.
`local.properties` must point at your SDK (`sdk.dir=...`).

1. Publish the patched Commons to the local Maven repo first:

   ```bash
   cd ../commons
   ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1
   ```

2. Build the app:

   ```bash
   ./gradlew assembleFossDebug
   ```

## What this fork changes

- `APP_ID` / `APP_NAMESPACE` split in `gradle.properties` + `app/build.gradle.kts`
- Launcher name "Elbows Up" in all locales (debug: "Elbows Up_debug")
- `commons = "6.1.6-elbowsup1"` in `gradle/libs.versions.toml` (patched Commons — see `../commons/FORK.md`)
- No Kotlin changes.

## Keeping up with upstream

```bash
git fetch upstream
git merge upstream/main
```

Resolve conflicts (expected: rare and small), rebuild, and push to `origin`.
If upstream bumps the Commons version, re-apply the Commons patch onto the new
tag, republish as `<newversion>-elbowsup1`, and bump the pin in
`gradle/libs.versions.toml`.
````

- [ ] **Step 9: Copy the spec and plan into the repo**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
mkdir -p docs/superpowers/specs docs/superpowers/plans
cp /Users/chrisc/workspace/elbowsup/docs/superpowers/specs/2026-09-29-elbowsup-fork-setup-design.md docs/superpowers/specs/
cp /Users/chrisc/workspace/elbowsup/docs/superpowers/plans/2026-09-29-elbowsup-fork-setup.md docs/superpowers/plans/
```

- [ ] **Step 10: Commit**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
git add -A
git commit -m "Fork as Elbows Up: app id com.keejii.elbowsup, launcher name, patched Commons pin, fork docs"
```

Expected: commit created; `local.properties` is not staged.

- [ ] **Step 11: Build the debug APK**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleFossDebug
```

Expected: `BUILD SUCCESSFUL`. First run downloads Gradle 9.7.1 and dependencies; allow up to 30 minutes.

- [ ] **Step 12: Verify the APK**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
ls -la app/build/outputs/apk/foss/debug/
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/foss/debug/app-foss-debug.apk | head -3
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:dependencyInsight --dependency org.fossify:commons --configuration fossDebugRuntimeClasspath | grep '6.1.6-elbowsup1'
unzip -p app/build/outputs/apk/foss/debug/app-foss-debug.apk 'classes*.dex' | grep -ac "fake version" || true
```

Expected:
- the APK file exists,
- badging shows `package: name='com.keejii.elbowsup.debug'` and `application-label:'Elbows Up_debug'`,
- dependency insight shows `org.fossify:commons:6.1.6-elbowsup1`,
- the fake-version grep prints `0`.

- [ ] **Step 13: Push the app branch**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
git push origin main
```

Expected: `main` pushed to `origin`.

---

### Task 6: End-to-end verification

**Files:**
- No changes; verification only.

**Interfaces:**
- Consumes: everything from Tasks 1–5.
- Produces: a verified, reproducible build and a short report.

- [ ] **Step 1: Clean rebuild from the committed state**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew clean :app:assembleFossDebug
```

Expected: `BUILD SUCCESSFUL`; the APK is regenerated.

- [ ] **Step 2: Re-run the APK checks**

```bash
cd /Users/chrisc/workspace/elbowsup/phone
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/foss/debug/app-foss-debug.apk | grep -E "^package:|^application-label:"
unzip -p app/build/outputs/apk/foss/debug/app-foss-debug.apk 'classes*.dex' | grep -ac "fake version" || true
```

Expected: `package: name='com.keejii.elbowsup.debug'`, `application-label:'Elbows Up_debug'`, and `0`.

- [ ] **Step 3: Verify both repos are clean and pushed**

```bash
git -C /Users/chrisc/workspace/elbowsup/phone status --short
git -C /Users/chrisc/workspace/elbowsup/commons status --short
git -C /Users/chrisc/workspace/elbowsup/phone log --oneline -1
git -C /Users/chrisc/workspace/elbowsup/commons log --oneline -1
git -C /Users/chrisc/workspace/elbowsup/phone status -sb | head -1
git -C /Users/chrisc/workspace/elbowsup/commons status -sb | head -1
```

Expected: no uncommitted changes, both branches show `...origin/main` with no ahead/behind markers.

- [ ] **Step 4: Device checks when a device is attached (manual)**

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices
```

If a device is listed, install and walk through the Review Focus list:

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Manual checks on the device:
1. Launcher shows "Elbows Up_debug" and it coexists with official Fossify Phone.
2. Launch the app several times — no "fake version" dialog ever appears.
3. Settings → Customize colors → primary color picker opens (guards removed).
4. Open Blocked numbers — the screen treats the app as a dialer.
5. Place a call after granting the dialer role — it must not fail with "No valid app found".
6. When not the default dialer, the app offers to become one.

If no device is attached, report these as not yet verified.

- [ ] **Step 5: Report**

Summarise: fork URLs, commit hashes, the published Commons version, the APK path, and which checks passed. Note the next project (spam filtering) is designed separately.
