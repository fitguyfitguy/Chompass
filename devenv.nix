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

  packages = [ pkgs.android-tools ];

  enterShell = ''
    mkdir -p android
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
  '';

  scripts.build-debug.exec = "cd android && ./gradlew :app:assembleDebug";
  scripts.build-release.exec = "cd android && ./gradlew :app:assembleRelease";

  tasks."build:debug" = {
    exec = "cd android && ./gradlew :app:assembleDebug";
    description = "Assemble the debug APK";
  };

  tasks."build:release" = {
    exec = "cd android && ./gradlew :app:assembleRelease";
    description = "Assemble the release APK";
  };
}
