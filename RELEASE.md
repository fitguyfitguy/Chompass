# Releasing NoFUD

Maintainer steps for tagging and publishing an Android release on Codeberg.

## One-time setup: signing key

Generate the release keystore (back it up offline — losing it blocks updates):

```bash
cd android
keytool -genkey -v -keystore nofud-release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias nofud
cp keystore.properties.template keystore.properties
# storeFile=../nofud-release.jks, fill storePassword / keyPassword / keyAlias
```

**Signing key SHA-256** (for F-Droid `AllowedAPKSigningKeys`):

```
2694994fcb99d70e2c3978f770384dcf3091a310d9c56a23d4a145f150658dcf
```

## Pre-release checks

```bash
devenv shell -- bash -lc 'cd android && ./gradlew test'
devenv shell -- bash -lc 'uv run --with pillow python scripts/optimize_exercise_images.py --check-only'
devenv tasks run build:release
```

## Build and package

```bash
devenv tasks run build:release
cp android/app/build/outputs/apk/play/release/app-play-release.apk NoFUD-<version>.apk
sha256sum NoFUD-<version>.apk > SHA256SUMS
```

## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `CHANGELOG.md`
3. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "NoFUD 1.0.0 — initial public release"
git push origin v1.0.0
```

4. Create a release at https://codeberg.org/fitguy/nofud/releases
   - Attach `NoFUD-<version>.apk` and `SHA256SUMS`
   - Paste changelog notes
   - Stable download URL pattern: `https://codeberg.org/fitguy/nofud/releases/download/v<version>/NoFUD-<version>.apk`

## F-Droid follow-up

Before submitting to [fdroiddata](https://gitlab.com/fdroid/fdroiddata):

- Build the `fdroid` flavor (`assembleFdroidRelease`) — omits proprietary Play Core libraries
- Add store metadata under `metadata/en-US/`
- Open an MR with `metadata/org.codeberg.fitguy.nofud.yml` using the signing key fingerprint above

See the plan in `.cursor/plans/` or project issues for the full F-Droid checklist.
