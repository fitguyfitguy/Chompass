# Sourced helper (not executable): resolve $ADB_BIN for perf scripts.
#
# The USB device is reachable from the Windows adb server, not WSL adb. If
# ADB_BIN isn't set, auto-detect a Windows adb.exe in the usual spots and fall
# back to plain `adb`. A Windows adb.exe must NOT inherit ANDROID_ADB_SERVER_PORT
# (devenv sets 5038 for WSL adb) or it won't find the device on the 5037 server.
if [ -z "${ADB_BIN:-}" ]; then
  for cand in \
    /mnt/c/Users/*/Downloads/platform-tools*/platform-tools/adb.exe \
    /mnt/c/Users/*/AppData/Local/Android/Sdk/platform-tools/adb.exe \
    /mnt/c/Android/platform-tools/adb.exe \
    /mnt/c/platform-tools/adb.exe; do
    if [ -f "$cand" ]; then ADB_BIN="$cand"; break; fi
  done
fi
ADB_BIN="${ADB_BIN:-adb}"
case "$ADB_BIN" in
  *.exe) unset ANDROID_ADB_SERVER_PORT ;;
esac
