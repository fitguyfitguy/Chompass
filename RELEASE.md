# Releasing NoFUD

Maintainer steps for tagging and publishing an Android release on Codeberg.

## One-time setup: signing key

Generate the release keystore (back it up offline - losing it blocks updates):

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
devenv tasks run build:fdroid-release
```

## Build and package

```bash
devenv tasks run build:release
devenv tasks run build:fdroid-release

cp android/app/build/outputs/apk/play/release/app-play-universal-release.apk NoFUD-play-<version>.apk
cp android/app/build/outputs/apk/play/release/app-play-arm64-v8a-release.apk NoFUD-play-<version>-arm64-v8a.apk
cp android/app/build/outputs/apk/play/release/app-play-armeabi-v7a-release.apk NoFUD-play-<version>-armeabi-v7a.apk
cp android/app/build/outputs/apk/play/release/app-play-x86_64-release.apk NoFUD-play-<version>-x86_64.apk

cp android/app/build/outputs/apk/fdroid/release/app-fdroid-universal-release.apk NoFUD-fdroid-<version>.apk
cp android/app/build/outputs/apk/fdroid/release/app-fdroid-arm64-v8a-release.apk NoFUD-fdroid-<version>-arm64-v8a.apk
cp android/app/build/outputs/apk/fdroid/release/app-fdroid-armeabi-v7a-release.apk NoFUD-fdroid-<version>-armeabi-v7a.apk
cp android/app/build/outputs/apk/fdroid/release/app-fdroid-x86_64-release.apk NoFUD-fdroid-<version>-x86_64.apk

sha256sum \
  NoFUD-play-<version>.apk \
  NoFUD-play-<version>-arm64-v8a.apk \
  NoFUD-play-<version>-armeabi-v7a.apk \
  NoFUD-play-<version>-x86_64.apk \
  NoFUD-fdroid-<version>.apk \
  NoFUD-fdroid-<version>-arm64-v8a.apk \
  NoFUD-fdroid-<version>-armeabi-v7a.apk \
  NoFUD-fdroid-<version>-x86_64.apk > SHA256SUMS
```

## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `CHANGELOG.md`
3. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "NoFUD 1.0.0 - initial public release"
git push origin v1.0.0
```

4. Create a release at https://codeberg.org/fitguy/nofud/releases
   - Attach all Play APK assets (`NoFUD-play-<version>.apk` + ABI APKs) and `SHA256SUMS`
   - Also attach F-Droid APK assets (`NoFUD-fdroid-<version>.apk` + ABI APKs) and `SHA256SUMS`
   - Paste changelog notes
   - Stable download URL pattern (Play flavor): `https://codeberg.org/fitguy/nofud/releases/download/v<version>/NoFUD-play-<version>.apk`
   - Stable download URL pattern (F-Droid flavor): `https://codeberg.org/fitguy/nofud/releases/download/v<version>/NoFUD-fdroid-<version>.apk`

   Or with [tea](https://codeberg.org/tea/tea):

```bash
# One-time: create a token at https://codeberg.org/user/settings/applications
# Scopes: read:user + write:repository (repo access: this repo or all)
export CODEBERG_TOKEN='paste-token-here'
./scripts/publish_release.sh 1.0.0
```

The script auto-runs `nix shell nixpkgs#tea` when `tea` is not on PATH (e.g. inside devenv).

## F-Droid follow-up

Before submitting to [fdroiddata](https://gitlab.com/fdroid/fdroiddata):

- Build the `fdroid` flavor (`assembleFdroidRelease`) - omits proprietary Play Core libraries
- Add store metadata under `metadata/en-US/`
- Open an MR with `metadata/org.codeberg.fitguy.nofud.yml` using the signing key fingerprint above

See the plan in `.cursor/plans/` or project issues for the full F-Droid checklist.

## APK size baselines (1.4.0)

- Pre-optimization `fdroid` release APK (`app-fdroid-release.apk`): ~45 MB
- Post-optimization `fdroid` release APKs:
  - `app-fdroid-universal-release.apk`: ~25 MB
  - `app-fdroid-arm64-v8a-release.apk`: ~25 MB
  - `app-fdroid-armeabi-v7a-release.apk`: ~25 MB
  - `app-fdroid-x86_64-release.apk`: ~25 MB
- Main reduction: move ML Kit barcode scanner implementation to `play` flavor and use a lightweight fallback dialog in `fdroid`.
