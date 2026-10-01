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
   ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-elbowsup2
   ```

2. Build the app:

   ```bash
   ./gradlew assembleFossDebug
   ```

## What this fork changes

- `APP_ID` / `APP_NAMESPACE` split in `gradle.properties` + `app/build.gradle.kts`
- Launcher name "Elbows Up" in all locales (debug: "Elbows Up_debug")
- `commons = "6.1.6-elbowsup1"` in `gradle/libs.versions.toml` (patched Commons — see `../commons/FORK.md`)
- Call blocker and caller-name work: new code lives under `app/src/main/kotlin/com/keejii/elbowsup/`;
  the only edits to upstream files are listed in **Upstream touch points** below.

## Keeping up with upstream

```bash
git fetch upstream
git merge upstream/main
```

Resolve conflicts (expected: rare and small), rebuild, and push to `origin`.
If upstream bumps the Commons version, re-apply the Commons patch onto the new
tag, republish as `<newversion>-elbowsup1`, and bump the pin in
`gradle/libs.versions.toml`.

### Merge rehearsal (2026-09-29)

Upstream had not moved since the fork point, so a real merge was a no-op. To measure the cost
instead, our edits to upstream files were replayed onto upstream as it was a year earlier and
current upstream (218 commits) was merged in. With the hooks as they are now, only fork-setup files
conflicted, each in one hunk. The blocker's own hooks and the seven other edited files merged clean.

| Conflict | Resolution |
|---|---|
| `gradle.properties` | Take upstream's `VERSION_NAME` and `VERSION_CODE`; keep `APP_ID=com.keejii.elbowsup` and `APP_NAMESPACE=org.fossify.phone`. |
| `gradle/libs.versions.toml` | Take upstream's other changes, then pin Commons again as described above. |
| a few `values-*/strings.xml` (3 of 57 in the rehearsal) | Take upstream's file, then re-run the launcher-name pass below. |

Launcher name, after taking upstream's strings (the default language lives in `values/strings.xml`):

```bash
perl -pi -e 's|(<string name="app_launcher_name">)[^<]*(</string>)|${1}Elbows Up${2}|' \
  app/src/main/res/values*/strings.xml
```

Hooks use fully qualified names and no `import`, because upstream edits its import blocks constantly
and our earlier imports were the only hook lines that conflicted. Keep it that way for new hooks.

### Before a release

Build `assembleFossRelease` and check three things, which have each caught a real problem: the
storage classes in `storage/` are `@Keep` (R8 otherwise renames the saved field names), the merged
manifest has no `DebugSeedReceiver` and no `IN_CALL_SERVICE_RINGING`, and there is no `INTERNET`.

## Upstream touch points

Every edit to a file that exists upstream, marked `// ELBOWSUP` in the source. Keep this list
current; it is what to check first when a merge conflicts.

| File | Change |
|---|---|
| `app/build.gradle.kts` | `testImplementation` JUnit |
| `app/src/main/AndroidManifest.xml` | one block between `<!-- ELBOWSUP begin -->` and `<!-- ELBOWSUP end -->`: the call blocker screen and the pause notification's Resume receiver |
| `app/src/main/res/menu/menu.xml` | a "Call blocker" overflow item |
| `app/src/main/kotlin/org/fossify/phone/activities/MainActivity.kt` | opens the call blocker screen from that menu item (1 line and an import) |
| `app/src/main/kotlin/org/fossify/phone/services/SimpleCallScreeningService.kt` | first statement of `onScreenCall` asks the blocker for a verdict and returns if it answered; otherwise Fossify's screening runs unchanged (1 line, fully qualified, no import) |
| `app/src/main/kotlin/org/fossify/phone/helpers/CallContactHelper.kt` | with no contact match, show the carrier caller name instead of the number (1 line and an import) |
| `app/src/main/kotlin/org/fossify/phone/services/CallService.kt` | `onCallAdded` asks the blocker whether it dealt with the call and returns if so (1 line, fully qualified, no import) |
| `app/src/main/kotlin/org/fossify/phone/adapters/RecentCallsAdapter.kt` | `bind` marks a blocked call in the row's time text (import plus 1 line) |

Design and plan: `docs/superpowers/specs/2026-09-29-elbowsup-blocker-integration-design.md`.
