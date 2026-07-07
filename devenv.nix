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
    # Avoid hanging adb commands when Windows also runs adb on the shared WSL localhost.
    export ANDROID_ADB_SERVER_PORT=5038
  '';

  scripts.build-debug.exec = "cd android && ./gradlew :app:assemblePlayDebug";
  scripts.build-release.exec = "cd android && ./gradlew :app:assemblePlayRelease";

  tasks."build:debug" = {
    exec = "cd android && ./gradlew :app:assemblePlayDebug";
    description = "Assemble the debug APK";
  };

  tasks."build:release" = {
    exec = "cd android && ./gradlew :app:assemblePlayRelease";
    description = "Assemble the signed play-flavor release APK";
  };

  tasks."build:fdroid-release" = {
    exec = "cd android && ./gradlew :app:assembleFdroidRelease";
    description = "Assemble the F-Droid release APK (no Play Core)";
  };
}
