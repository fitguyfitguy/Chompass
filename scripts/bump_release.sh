#!/usr/bin/env bash
# One-command release version bump across every metadata file.
#
# The release choreography used to be 5+ hand-edited files per release plus a
# second commit to pin the F-Droid build hash. This script performs the whole
# bump in one step and then runs check_release_metadata.sh to prove the files
# agree.
#
# Usage: ./scripts/bump_release.sh <versionName> <versionCode>
#   e.g.  ./scripts/bump_release.sh 3.17.0 55
#
# What it updates:
#   - android/app/build.gradle.kts        versionName / versionCode
#   - docs/CHANGELOG.md                   [Unreleased] -> [NEW] - <date>, fresh Unreleased header
#   - docs/fdroid/app.chompass.yml        CurrentVersion/Code + new Builds entry (HEAD commit)
#   - website/hugo.toml                   params.version
#   - metadata/app.chompass.yml           CurrentVersion/Code + new Builds entry
#
# The F-Droid build-hash pin (docs/fdroid/app.chompass.yml Builds commit) is
# set to the current HEAD: run this script right before tagging the release
# commit so the pin matches the tag. After tagging, re-run with the tag hash if
# the tag commit differs from HEAD.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="$ROOT/android/app/build.gradle.kts"
CHANGELOG="$ROOT/docs/CHANGELOG.md"
FDROID_YML="$ROOT/docs/fdroid/app.chompass.yml"
HUGO_TOML="$ROOT/website/hugo.toml"
METADATA_YML="$ROOT/metadata/app.chompass.yml"

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <versionName> <versionCode>" >&2
  exit 1
fi
NEW_NAME="$1"
NEW_CODE="$2"

if ! [[ "$NEW_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "versionName must be X.Y.Z, got '$NEW_NAME'" >&2
  exit 1
fi
if ! [[ "$NEW_CODE" =~ ^[0-9]+$ ]]; then
  echo "versionCode must be an integer, got '$NEW_CODE'" >&2
  exit 1
fi

OLD_NAME="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' "$GRADLE_FILE" | head -1)"
OLD_CODE="$(sed -n 's/.*versionCode = \([0-9]*\)/\1/p' "$GRADLE_FILE" | head -1)"
if [[ -z "$OLD_NAME" || -z "$OLD_CODE" ]]; then
  echo "Could not read current version from $GRADLE_FILE" >&2
  exit 1
fi
echo "Bumping $OLD_NAME ($OLD_CODE) -> $NEW_NAME ($NEW_CODE)"

if [[ "$NEW_CODE" -le "$OLD_CODE" ]]; then
  echo "versionCode must increase ($OLD_CODE -> $NEW_CODE)" >&2
  exit 1
fi

# 1. build.gradle.kts
sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sed -i "s/versionName = \"$OLD_NAME\"/versionName = \"$NEW_NAME\"/" "$GRADLE_FILE"
echo "  updated $GRADLE_FILE"

# 2. CHANGELOG.md: [Unreleased] -> [NEW] - date, fresh Unreleased header
TODAY="$(date +%F)"
if grep -q "^## \[$NEW_NAME\]" "$CHANGELOG"; then
  echo "CHANGELOG already has a section for $NEW_NAME; leaving it in place" >&2
else
  # Rename the Unreleased header and insert a fresh one above it.
  sed -i "0,/^## \[Unreleased\]/s//## [$NEW_NAME] - $TODAY/" "$CHANGELOG"
  sed -i "0,/^## \[$NEW_NAME\] - $TODAY/s//## [Unreleased]\n\n## [$NEW_NAME] - $TODAY/" "$CHANGELOG"
  echo "  updated $CHANGELOG"
fi

# 3. docs/fdroid/app.chompass.yml
HEAD_COMMIT="$(git -C "$ROOT" rev-parse HEAD)"
uv run python - "$FDROID_YML" "$NEW_NAME" "$NEW_CODE" "$HEAD_COMMIT" <<'PYEOF'
import sys
path, name, code, commit = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(path).read()
entry = (
    f"  - versionName: {name}\n"
    f"    versionCode: {code}\n"
    f"    commit: {commit}\n"
    "    subdir: android/app\n"
    "    gradle:\n"
    "      - yes\n"
    "    scandelete:\n"
    "      - web\n"
)
s = s.replace("Builds:\n", "Builds:\n" + entry, 1)
s = s.replace("CurrentVersion: ", "CurrentVersion: ", 1)
import re
s = re.sub(r"^CurrentVersion: .*$", f"CurrentVersion: {name}", s, flags=re.M)
s = re.sub(r"^CurrentVersionCode: .*$", f"CurrentVersionCode: {code}", s, flags=re.M)
open(path, "w").write(s)
PYEOF
echo "  updated $FDROID_YML (build pinned to $HEAD_COMMIT)"

# 4. website/hugo.toml
sed -i "s/^  version = '.*'/  version = '$NEW_NAME'/" "$HUGO_TOML"
echo "  updated $HUGO_TOML"

# 5. metadata/app.chompass.yml (F-Droid metadata mirror)
uv run python - "$METADATA_YML" "$NEW_NAME" "$NEW_CODE" "$HEAD_COMMIT" <<'PYEOF'
import sys, re
path, name, code, commit = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(path).read()
entry = (
    f"  - versionName: {name}\n"
    f"    versionCode: {code}\n"
    f"    commit: {commit}\n"
    "    subdir: android/app\n"
    "    gradle:\n"
    "      - yes\n"
    "    scandelete:\n"
    "      - web\n"
    "    gradleprops:\n"
    "      - releaseAbi=arm64-v8a\n"
)
s = s.replace("Builds:\n", "Builds:\n" + entry, 1)
s = re.sub(r"^CurrentVersion: .*$", f"CurrentVersion: {name}", s, flags=re.M)
s = re.sub(r"^CurrentVersionCode: .*$", f"CurrentVersionCode: {code}", s, flags=re.M)
open(path, "w").write(s)
PYEOF
echo "  updated $METADATA_YML"

# 6. Verify everything agrees
"$ROOT/scripts/check_release_metadata.sh"
echo "Release bump complete: $NEW_NAME ($NEW_CODE)"