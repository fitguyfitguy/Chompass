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

## Build and package

One command runs tests, checks bundled exercise images, builds both release flavors in a single Gradle invocation, copies 8 APKs to the repo root, and writes `SHA256SUMS`:

```bash
devenv tasks run release:package
```

Equivalent:

```bash
./scripts/package_release.sh
```

Useful flags:

```bash
./scripts/package_release.sh --check-only      # validate only; no build/package
./scripts/package_release.sh --skip-build        # package existing Gradle outputs
./scripts/package_release.sh --check-metadata    # also verify CHANGELOG + fdroid metadata
```

For a local single-ABI smoke-test release (not for publishing):

```bash
devenv shell bash -lc 'cd android && ./gradlew -PreleaseAbi=arm64-v8a :app:assemblePlayRelease :app:assembleFdroidRelease'
```

## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `CHANGELOG.md` (`## [Unreleased]` → new `## [X.Y.Z] - YYYY-MM-DD` section)
3. Optional: sync `fdroid/org.codeberg.fitguy.nofud.yml` and run `devenv tasks run release:check-metadata`
4. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "NoFUD 1.0.0 - initial public release"
git push origin v1.0.0
```

5. Create a release at https://codeberg.org/fitguy/nofud/releases
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
- Keep `fdroid/org.codeberg.fitguy.nofud.yml` in sync with `versionName` / `versionCode` (`devenv tasks run release:check-metadata`)

See the plan in `.cursor/plans/` or project issues for the full F-Droid checklist.

## Calculation changes

Before shipping formula, constant, or guardrail changes:

1. Update implementation and [`NutritionConstants.kt`](android/app/src/main/java/org/codeberg/fitguy/nofud/models/NutritionConstants.kt) when applicable
2. Update [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (formula register + policy table)
3. Update in-app strings (`settings_calc_*` in `strings.xml`) if user-visible
4. Add or adjust unit tests under `android/app/src/test/`
5. Document user impact in `CHANGELOG.md`
6. Run `devenv shell bash -lc 'cd android && ./gradlew test'`

## APK size baselines (1.4.0)

- `fdroid` release APKs with ML Kit barcode scanning: ~45 MB (universal and per-ABI splits are similar)
- Play and fdroid flavors share the same barcode scanner; only Play Core (in-app review/update) is play-only
