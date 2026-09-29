# Elbows Up — Fork Setup Design

- **Date:** 2026-09-29
- **Status:** design approved; implementation not started
- **Scope:** fork setup only — workspace, rename, patched Commons, first successful build.
  Spam filtering is a separate, later project with its own design.

## 1. Purpose

Create our own redistributable fork of Fossify Phone ("Elbows Up") that:

- installs alongside the official Fossify Phone and other forks,
- builds against our own patched Fossify Commons, so the anti-tamper nags and
  `org.fossify.*` assumptions do not misfire on our app id,
- stays close to upstream, so regular `git merge upstream/main` remains easy.

## 2. Success criteria

1. `/Users/chrisc/workspace/elbowsup/` contains `phone/` and `commons/`, each cloned from our
   GitHub forks with `upstream` remotes configured.
2. `./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1` succeeds in `commons/`.
3. `./gradlew assembleFossDebug` succeeds in `phone/` and resolves Commons `6.1.6-elbowsup1`
   from mavenLocal.
4. The produced debug APK reports package `com.keejii.elbowsup.debug` and label "Elbows Up_debug"
   (debug builds override the label), and the fake-version dialog string is absent from it.
5. `FORK.md` in each repo documents the fork changes and the upstream maintenance routine.

## 3. Identity decisions

| Item | Value |
|---|---|
| Application id | `com.keejii.elbowsup` (debug builds get `.debug`) |
| Code namespace | `org.fossify.phone` (unchanged, to keep upstream merges small) |
| Launcher name | "Elbows Up" in every locale; debug builds "Elbows Up_debug" |
| About title | "Elbows Up" |
| Versions | upstream `VERSION_NAME=1.11.1`, `VERSION_CODE=22` for now |
| Attribution | Fossify notices, license and upstream links stay intact |

## 4. Workspace and repositories

```
/Users/chrisc/workspace/elbowsup/
├── phone/     # our fork of FossifyOrg/Phone, branch main
├── commons/   # our fork of FossifyOrg/Commons, branch main (based on tag 6.1.6)
└── docs/      # this spec and the implementation plan; later committed into phone/docs/
```

- GitHub: `ccormier/elbowsup-phone` and `ccormier/elbowsup-commons`, created as public forks with
  `gh repo fork --fork-name`.
- Remotes in each clone: `origin` = our fork, `upstream` = the Fossify repo.
- GitHub Actions disabled on both forks — the inherited workflows call Fossify org workflows and
  would only fail and send mail.

## 5. Commons fork: patch list (6.1.6 → 6.1.6-elbowsup1)

All changes are deletions or small condition changes; no new functionality is added.

| File | Change |
|---|---|
| `extensions/Activity.kt` | Delete `showModdedAppWarning()`. Reduce `checkAppSideloading()` to set `appSideloadingStatus = SIDELOADING_FALSE` and return false. In `launchCallIntent`, target `packageName` + `org.fossify.phone.activities.DialerActivity` instead of the hard-coded `org.fossify.phone[.debug]`. |
| `compose/extensions/ActivityExtensions.kt` | Delete `FAKE_VERSION_APP_LABEL` and `fakeVersionCheck()`. |
| `compose/extensions/ComposeActivityExtensions.kt` | Delete the `FakeVersionCheck()` composable. |
| `compose/theme/AppTheme.kt` | Remove the `OnContentDisplayed()` call and function. |
| `activities/BaseSimpleActivity.kt` | Remove the `onCreate` "modded app" block; remove the `startCustomizationActivity()` guard. |
| `activities/CustomizationActivity.kt` | Remove the `pickPrimaryColor()` guard. |
| `helpers/MyContactsContentProvider.kt` | Drop the caller-package allowlist in `getSimpleContacts()` and `getContacts()`. |
| `activities/ManageBlockedNumbersActivity.kt` | Recognise `com.keejii.elbowsup` in `isDialer` and `maybeSetDefaultCallerIdApp()`. |
| `extensions/Context.kt` | `isDefaultDialer()` becomes a real check for our package: role manager on Q+, `defaultDialerPackage == packageName` below. |

Deliberately left alone: `isAProApp()`, `getCanAppBeUpgraded()`, Thank-You integration, the
global-config provider, and the gallery/file-manager special cases.

Publishing: `./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1` writes
`~/.m2/repository/org/fossify/commons/6.1.6-elbowsup1/`. The patched AAR exists only on the build
machine; any other machine or CI must publish it first.

## 6. App fork changes

| File | Change |
|---|---|
| `gradle.properties` | `APP_ID=com.keejii.elbowsup`; add `APP_NAMESPACE=org.fossify.phone`. |
| `app/build.gradle.kts` | `namespace = project.property("APP_NAMESPACE")`. |
| `gradle/libs.versions.toml` | `commons = "6.1.6-elbowsup1"`. |
| `app/src/main/res/values/strings.xml` and the 57 locale files that define it | `app_launcher_name` = "Elbows Up". |
| `app/src/debug/res/values/strings.xml` | `app_launcher_name` = "Elbows Up_debug". |
| `app/src/main/res/values/donottranslate.xml` | `app_name` = "Elbows Up"; `package_name` = `com.keejii.elbowsup`. |
| `FORK.md` (new) | Fork overview, Commons patch list, build steps, upstream routine. |

No Kotlin changes: the outgoing-call fix lives in the Commons patch, so the app source stays
identical to upstream apart from resources and build configuration.

## 7. Build and verification

Environment already installed on this machine: JDK 21 at
`/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, Android SDK at
`/opt/homebrew/share/android-commandlinetools` (platforms 35–37, build-tools, licences accepted).

Steps:

1. Write `local.properties` in both repos: `sdk.dir=/opt/homebrew/share/android-commandlinetools`.
2. Export `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
3. In `commons/`: `./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup1`.
4. In `phone/`: `./gradlew assembleFossDebug`.

Checks:

- APK exists at `phone/app/build/outputs/apk/foss/debug/app-foss-debug.apk`.
- `aapt2 dump badging` reports `package: name='com.keejii.elbowsup.debug'` and
  `application-label:'Elbows Up_debug'`.
- Dependency resolution shows `6.1.6-elbowsup1` for `fossDebugRuntimeClasspath`.
- The string "fake version" does not appear in the APK's dex files.
- Optional: if a device or emulator is attached, install and launch it.

## 8. Upstream maintenance

- App repo: `git fetch upstream && git merge upstream/main` regularly. Our diff is small, so
  conflicts stay small. Resolve, rebuild, push.
- Commons repo: when the app's upstream bumps the Commons version, fetch upstream tags, rebase our
  patch onto the new tag, republish as `<newversion>-elbowsup1`, and bump the pin. The `FORK.md`
  in the Commons repo lists every patch so it can be re-applied.
- Never commit build outputs, `local.properties`, or keystores.

## 9. Risks and notes

- The patched Commons is machine-local (mavenLocal); CI or another machine must publish it first.
- The launcher-name sweep touches 58 translation files once; future upstream locales need the same
  one-line sweep (rare).
- If upstream changes the shape of `launchCallIntent`, `isDefaultDialer`, or the anti-tamper code,
  re-apply the patch at the next Commons bump.
- Upstream version numbers are kept for now; releases will need unique version codes (later).
- The fork stays GPLv3; Fossify attribution and license files remain.

## 10. Out of scope

- Spam filtering (next project: its own design, spec, and plan).
- Release signing/keystore, store or F-Droid publishing, fastlane metadata.
- App icon and other branding.
- Build counter / fork version scheme.
