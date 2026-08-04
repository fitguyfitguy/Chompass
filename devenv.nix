{ pkgs, ... }:

{
  devenv.warnOnNewVersion = false;

  android = {
    enable = true;
    platforms.version = [
      "36"
      "36.1"
    ];
    buildTools.version = [ "36.0.0" ];
    emulator.enable = false;
    systemImages.enable = false;
    ndk.enable = false;
    googleTVAddOns.enable = false;
  };

  # ktlint is the CLI, not the Gradle plugin: the plugin hooks the standalone
  # Kotlin Android plugin, which AGP 9 replaced with built-in Kotlin support, so
  # it never sees app/src. Running the CLI also keeps the linter out of the
  # F-Droid release build graph.
  # uv: parity validators and asset scripts (never bare python/pip).
  packages = [
    pkgs.android-tools
    pkgs.hugo
    pkgs.nodejs
    pkgs.typescript
    pkgs.ktlint
    pkgs.uv
  ];

  enterShell = ''
    mkdir -p android
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
    # Avoid hanging adb commands when Windows also runs adb on the shared WSL localhost.
    export ANDROID_ADB_SERVER_PORT=5038
    # Reject Cursor/AI commit trailers (Co-authored-by: Cursor, Made-with: Cursor, …).
    # Pin Codeberg maintainer identity for this clone (do not use global personal identity).
    if [[ -d .git ]]; then
      chmod +x scripts/git-hooks/* 2>/dev/null || true
      git config core.hooksPath scripts/git-hooks
      git config user.name fitguy
      git config user.email fit.guy@mailfence.com
    fi
  '';

  scripts.build-debug.exec = "cd android && ./gradlew :app:assembleDebug";
  scripts.install-debug.exec = "./scripts/install_debug.sh";
  scripts.build-release.exec = "cd android && ./gradlew :app:assembleRelease";
  scripts.site-serve.exec = "hugo server -D -s website --baseURL http://localhost:1313/Chompass/";
  scripts.site-build.exec = "hugo --minify -s website";
  scripts.site-deploy.exec = "./scripts/deploy_pages.sh";
  scripts.pwa-test.exec = "cd web && node --test app/src/lib/chompass-core/__tests__/*.test.js app/src/lib/__tests__/*.test.js";
  scripts.pwa-typecheck.exec = "cd web && tsc --checkJs --noEmit -p tsconfig.json";
  scripts.pwa-serve.exec = "node web/serve.mjs";
  # Rule scope lives in android/.editorconfig. `kotlin-lint-fix` autocorrects.
  scripts.kotlin-lint.exec = "cd android && ktlint --relative 'app/src/**/*.kt'";
  scripts.kotlin-lint-fix.exec = "cd android && ktlint --format --relative 'app/src/**/*.kt'";

  tasks."build:debug" = {
    exec = "cd android && ./gradlew :app:assembleDebug";
    description = "Assemble the debug APK";
  };

  tasks."build:release" = {
    exec = "cd android && ./gradlew :app:assembleRelease";
    description = "Assemble the signed release APK (F-Droid / Codeberg distribution)";
  };

  tasks."release:package" = {
    exec = "./scripts/package_release.sh";
    description = "Run pre-release checks (Android tests + parity), build release APKs, package, and write SHA256SUMS";
  };

  tasks."lint:kotlin" = {
    exec = "cd android && ktlint --relative 'app/src/**/*.kt'";
    description = "ktlint over the Android sources (scope: android/.editorconfig)";
  };

  tasks."release:check-parity" = {
    exec = "./scripts/check_parity.sh";
    description = "PWA tests + typecheck + validate testdata/parity fixtures against contracts/ schemas";
  };

  tasks."ci:verify" = {
    exec = ''
      set -euo pipefail
      cd android && ./gradlew :app:testDebugUnitTest
      cd ..
      ./scripts/check_parity.sh
    '';
    description = "PR happy path: Android debug unit tests + release:check-parity";
  };

  tasks."release:check-metadata" = {
    exec = "./scripts/check_release_metadata.sh";
    description = "Verify version consistency across build.gradle.kts, docs/CHANGELOG.md, website/hugo.toml, and docs/fdroid metadata";
  };

  tasks."release:publish" = {
    exec = ''
      set -euo pipefail
      if [[ -z "''${RELEASE_VERSION:-}" ]]; then
        echo "Usage: RELEASE_VERSION=1.14.10 devenv tasks run release:publish" >&2
        echo "Optional flags via PUBLISH_FLAGS, e.g. PUBLISH_FLAGS='--skip-pages'" >&2
        exit 1
      fi
      # shellcheck disable=SC2086
      ./scripts/publish_release.sh "$RELEASE_VERSION" ''${PUBLISH_FLAGS:-}
    '';
    description = "Upload release APKs to Codeberg and redeploy Pages (RELEASE_VERSION=x.y.z)";
  };

  tasks."release:screenshots" = {
    exec = "./scripts/export_release_screenshots.sh";
    description = "Render JVM screenshot previews; export to release-screenshots/ and docs/screenshots/";
  };

  tasks."release:assets-list" = {
    exec = "./scripts/manage_release_assets.sh list";
    description = "List Codeberg release attachments and estimated total size";
  };

  tasks."benchmark:food-accuracy-smoke" = {
    exec = "./scripts/check_food_accuracy_smoke.sh";
    description = "Deterministic food-accuracy smoke (stub eval + grounded metrics + retrieval golden)";
  };

  tasks."site:serve" = {
    exec = "hugo server -D -s website --baseURL http://localhost:1313/Chompass/";
    description = "Preview the Codeberg Pages Hugo site locally";
  };

  tasks."site:build" = {
    exec = "hugo --minify -s website";
    description = "Build the static Codeberg Pages site into website/public";
  };

  tasks."site:deploy" = {
    exec = "./scripts/deploy_pages.sh";
    description = "Build site and force-push the orphan pages branch for Codeberg Pages webhook deploy";
  };
}
