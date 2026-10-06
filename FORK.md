# Elbows Up — fork notes

Elbows Up is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone).

- **Application id:** `com.keejii.elbowsup` (debug: `com.keejii.elbowsup.debug`)
- **Code namespace:** `org.fossify.phone` (unchanged from upstream, to keep merges small)
- **Upstream:** https://github.com/FossifyOrg/Phone (remote `upstream`)

## Building

Requires JDK 17+ (21 used here) and an Android SDK with platform 36.
`local.properties` must point at your SDK (`sdk.dir=...`).

The patched Commons is fetched from JitPack as `com.github.ccormier:elbowsup-commons`, so a plain
build works:

```bash
./gradlew assembleFossDebug
```

To test uncommitted Commons changes locally, publish the patched Commons to the local Maven repo
(`cd ../commons && ./gradlew :commons:publishToMavenLocal -PVERSION=<version>`) and temporarily
point `fossify-commons` in `gradle/libs.versions.toml` at `org.fossify:commons` for that build.

## What this fork changes

- `APP_ID` / `APP_NAMESPACE` split in `gradle.properties` + `app/build.gradle.kts`
- Launcher name "Elbows Up" in all locales (debug: "Elbows Up_debug")
- `commons = "6.1.6-elbowsup2"` in `gradle/libs.versions.toml` (patched Commons — see `../commons/FORK.md`)
- Launcher icon: the maple leaf from the Canadian flag (public domain, Wikimedia Commons) with the phone
  and no-entry badge; one background drawable per icon colour variant
- Call blocker and caller-name work: new code lives under `app/src/main/kotlin/com/keejii/elbowsup/`;
  the only edits to upstream files are listed in **Upstream touch points** below.

## Versioning

Elbows Up always tracks the upstream version it is based on. The version name keeps that upstream
version and adds a fork suffix, and the version code is derived so it always rises, even when we ship
more than one fork release for the same upstream version:

```
VERSION_NAME = <upstream version>-elbowsup<N>
VERSION_CODE = <upstream version code> * 100 + N
```

Upstream 1.11.1 (code 22) gives `1.11.1-elbowsup1` with code `2201`. A follow-up fix without an
upstream bump is `1.11.1-elbowsup2`, code `2202`. After upstream 1.12.0 (code 23) the next fork
release is `1.12.0-elbowsup1`, code `2301`.

- The base version never changes: it always equals the upstream version this branch is based on.
- A version code is never reused or lowered; F-Droid and Android both reject updates that do.
- Release tags are `v<versionName>`, for example `v1.11.1-elbowsup1`. Tags without the `-elbowsup`
  suffix belong to upstream and are ignored for releases.
- The patched Commons must be published and verified on JitPack before the app release is tagged;
  see **Publishing the patched Commons**.

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
| `gradle.properties` | Take upstream's `VERSION_NAME` and `VERSION_CODE`, then re-apply the fork suffix and derived code from **Versioning**; keep `APP_ID=com.keejii.elbowsup` and `APP_NAMESPACE=org.fossify.phone`. |
| `gradle/libs.versions.toml` | Take upstream's other changes, then pin Commons again as described above. |
| a few `values-*/strings.xml` (3 of 57 in the rehearsal) | Take upstream's file, then re-run the launcher-name pass below. |

Launcher name, after taking upstream's strings (the default language lives in `values/strings.xml`):

```bash
perl -pi -e 's|(<string name="app_launcher_name">)[^<]*(</string>)|${1}Elbows Up${2}|' \
  app/src/main/res/values*/strings.xml
```

Hooks use fully qualified names and no `import`, because upstream edits its import blocks constantly
and our earlier imports were the only hook lines that conflicted. Keep it that way for new hooks.

### Publishing the patched Commons

F-Droid and CI builds resolve the patched Commons from JitPack, built from
`https://github.com/ccormier/elbowsup-commons`, because it is not on Maven Central:

1. Tag the commons fork with the version the app pins, for example `6.1.6-elbowsup2`, and push it.
2. Trigger the JitPack build by requesting any artifact URL for that tag, and wait until
   `https://jitpack.io/api/builds/com.github.ccormier/elbowsup-commons/<tag>` reports `"status": "ok"`.
3. Confirm `https://jitpack.io/com/github/ccormier/elbowsup-commons/<tag>/elbowsup-commons-<tag>.pom`
   returns 200. If the tag is not built yet, F-Droid and CI builds fail on the missing dependency.

The app depends on the artifact under JitPack's `com.github.ccormier` coordinates. JitPack's
`org.fossify:commons` alias serves the fork for commit hashes but not for tags, so do not pin it.

Do this before tagging the app release.

### Releases

Pushing a tag `v<versionName>` (for example `v1.11.1-elbowsup1`) runs
`.github/workflows/release.yml`, which builds `assembleFossRelease`, signs it with the repository's
signing secrets and publishes a GitHub release. Configure these repository secrets first:
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD` and `SIGNING_STORE_BASE64`
(the keystore file, base64-encoded). Without them the workflow skips the release with a warning, so
pushing the tag is always safe. The F-Droid recipe lists `Binaries` and `AllowedAPKSigningKeys` (the
release certificate's SHA-256) so F-Droid can verify its own rebuild against the published APK.

### Before a release

The project has three flavors (`core`, `foss`, `gplay`), each with a debug and a release build.
`./gradlew assembleDebug assembleRelease` builds all six; everything of ours lives in `main`, and the
only debug-only piece is the `DebugSeedReceiver` in `src/debug`, which must not appear in a release.

Check, for every release APK: the storage classes in `storage/` are `@Keep` (R8 otherwise renames the
saved field names), the merged manifest has `BlockerActivity` and the pause receiver but no
`DebugSeedReceiver`, no `IN_CALL_SERVICE_RINGING` and no `INTERNET`, and the strings and layouts of the
blocker screen are present after resource shrinking. Each of these has caught or ruled out a real problem.

R8 inlines our hook targets into their callers, so `BlockerCalls`, `BlockerScreening` and
`BlockedRecents` show as removed in `mapping.txt` while their code is still in the APK. Do not conclude the
blocker is missing from a class name; look for strings and calls that only our code has (for example the
default rule names, or `TelecomManager.silenceRinger`), or better, run the release build on a device.

To run a release build on a device: `zipalign -p 4`, sign it with `apksigner` and the debug keystore (this
needs `JAVA_HOME`), install it (its app id has no `.debug`, so it sits beside the debug app), grant it the
contacts permission, give it the phone and call screening roles with `cmd role add-role-holder`, and open
its Call blocker screen once to finish setup. Put the roles back and uninstall it afterwards.
