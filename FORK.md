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

## Upstream touch points

Every edit to a file that exists upstream, marked `// ELBOWSUP` in the source. Keep this list
current; it is what to check first when a merge conflicts.

| File | Change |
|---|---|
| `app/build.gradle.kts` | `testImplementation` JUnit |
| `app/src/main/AndroidManifest.xml` | one block between `<!-- ELBOWSUP begin -->` and `<!-- ELBOWSUP end -->`: the call blocker screen and the pause notification's Resume receiver |
| `app/src/main/res/menu/menu.xml` | a "Call blocker" overflow item |
| `app/src/main/kotlin/org/fossify/phone/activities/MainActivity.kt` | opens the call blocker screen from that menu item (1 line and an import) |
| `app/src/main/kotlin/org/fossify/phone/services/SimpleCallScreeningService.kt` | first statement of `onScreenCall` asks the blocker for a verdict and returns if it answered; otherwise Fossify's screening runs unchanged (1 line and an import) |
| `app/src/main/kotlin/org/fossify/phone/helpers/CallContactHelper.kt` | with no contact match, show the carrier caller name instead of the number (1 line and an import) |
| `app/src/main/kotlin/org/fossify/phone/services/CallService.kt` | `onCallAdded` asks the blocker whether it dealt with the call and returns if so (import plus 1 line) |

Design and plan: `docs/superpowers/specs/2026-09-29-elbowsup-blocker-integration-design.md`.
