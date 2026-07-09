#!/usr/bin/env bash
# Verify version consistency across release metadata files.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="$ROOT/android/app/build.gradle.kts"
CHANGELOG="$ROOT/CHANGELOG.md"
FDROID_YML="$ROOT/fdroid/org.codeberg.fitguy.nofud.yml"

read_gradle_value() {
  local key="$1"
  sed -n "s/.*${key} = \\(.*\\)/\\1/p" "$GRADLE_FILE" | head -1 | tr -d ' "'
}

VERSION_NAME="$(read_gradle_value versionName)"
VERSION_CODE="$(read_gradle_value versionCode)"

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  echo "Could not read versionName/versionCode from $GRADLE_FILE" >&2
  exit 1
fi

if ! grep -q "^## \\[${VERSION_NAME}\\]" "$CHANGELOG"; then
  echo "CHANGELOG.md is missing a section: ## [${VERSION_NAME}]" >&2
  exit 1
fi

FDROID_VERSION="$(sed -n "s/^CurrentVersion: '\\(.*\\)'/\\1/p" "$FDROID_YML" | head -1)"
FDROID_CODE="$(sed -n 's/^CurrentVersionCode: \(.*\)/\1/p' "$FDROID_YML" | head -1)"

if [[ "$FDROID_VERSION" != "$VERSION_NAME" ]]; then
  echo "fdroid metadata CurrentVersion (${FDROID_VERSION:-<missing>}) != build.gradle versionName (${VERSION_NAME})" >&2
  exit 1
fi

if [[ "$FDROID_CODE" != "$VERSION_CODE" ]]; then
  echo "fdroid metadata CurrentVersionCode (${FDROID_CODE:-<missing>}) != build.gradle versionCode (${VERSION_CODE})" >&2
  exit 1
fi

echo "Release metadata is consistent for ${VERSION_NAME} (${VERSION_CODE})."
