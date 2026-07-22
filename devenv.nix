{ pkgs, ... }:

{
  android = {
    enable = true;
    platforms.version = [ "36" "36.1" ];
    buildTools.version = [ "36.0.0" ];
    emulator.enable = false;
    systemImages.enable = false;
    ndk.enable = false;
    googleTVAddOns.enable = false;
  };

  packages = [ pkgs.android-tools pkgs.hugo ];

  enterShell = ''
    mkdir -p android
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
    # Avoid hanging adb commands when Windows also runs adb on the shared WSL localhost.
    export ANDROID_ADB_SERVER_PORT=5038
  '';

  scripts.build-debug.exec = "cd android && ./gradlew :app:assembleDebug";
  scripts.build-release.exec = "cd android && ./gradlew :app:assembleRelease";
  scripts.site-serve.exec = "hugo server -D -s website --baseURL http://localhost:1313/NoFUD/";
  scripts.site-build.exec = "hugo --minify -s website";

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
    description = "Run pre-release checks, build release APKs, package, and write SHA256SUMS";
  };

  tasks."release:check-metadata" = {
    exec = "./scripts/check_release_metadata.sh";
    description = "Verify version consistency across build.gradle.kts, CHANGELOG.md, and fdroid metadata";
  };

  tasks."release:screenshots" = {
    exec = "./scripts/export_release_screenshots.sh";
    description = "Render JVM screenshot previews; export to release-screenshots/ and docs/screenshots/";
  };

  tasks."release:assets-list" = {
    exec = "./scripts/manage_release_assets.sh list";
    description = "List Codeberg release attachments and estimated total size";
  };

  tasks."site:serve" = {
    exec = "hugo server -D -s website --baseURL http://localhost:1313/NoFUD/";
    description = "Preview the Codeberg Pages Hugo site locally";
  };

  tasks."site:build" = {
    exec = "hugo --minify -s website";
    description = "Build the static Codeberg Pages site into website/public";
  };
}
