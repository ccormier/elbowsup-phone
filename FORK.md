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
