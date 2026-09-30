#!/usr/bin/env bash
# Ensure the pinned Maestro CLI (mobile UI automation) is installed for the
# maintainer user. Prints the absolute binary path; safe to re-run.
#
# Install root: ${MAESTRO_HOME:-~/.local/share/chompass/maestro} (cache; wiped
# on version change). The release zip bundles its own JRE — no system Java.
#
# Env:
#   MAESTRO_VERSION  pinned release (tag on GitHub is cli-<version>)

set -euo pipefail

MAESTRO_VERSION="${MAESTRO_VERSION:-2.11.0}"
INSTALL_ROOT="${MAESTRO_HOME:-$HOME/.local/share/chompass/maestro}"

find_bin() {
  find "${1}" -type f -name maestro -perm -u+x 2>/dev/null | head -1 || true
}

BIN="$(find_bin "${INSTALL_ROOT}")"
if [ -n "${BIN}" ] && "${BIN}" --version >/dev/null 2>&1; then
  printf '%s\n' "${BIN}"
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
BASE="https://github.com/mobile-dev-inc/Maestro/releases/download/cli-${MAESTRO_VERSION}"
echo "Downloading Maestro CLI ${MAESTRO_VERSION} ..."
curl -fsSL -o "${TMP}/maestro.zip" "${BASE}/maestro.zip"
curl -fsSL -o "${TMP}/checksums" "${BASE}/checksums_sha256.txt"
( cd "${TMP}" && grep '[[:space:]]maestro\.zip$' checksums | sha256sum -c - >/dev/null )
mkdir -p "${TMP}/x"
if command -v unzip >/dev/null 2>&1; then
  unzip -q "${TMP}/maestro.zip" -d "${TMP}/x"
else
  bsdtar -xf "${TMP}/maestro.zip" -C "${TMP}/x" 2>/dev/null \
    || { echo "Need unzip or bsdtar to extract maestro.zip" >&2; exit 1; }
fi
NEW_BIN="$(find_bin "${TMP}/x")"
[ -n "${NEW_BIN}" ] || { echo "maestro binary not found inside the zip" >&2; exit 1; }
rm -rf "${INSTALL_ROOT}"
mkdir -p "${INSTALL_ROOT}"
cp -a "${TMP}/x/." "${INSTALL_ROOT}/"
BIN="$(find_bin "${INSTALL_ROOT}")"
"${BIN}" --version >/dev/null 2>&1 || { echo "Installed binary failed to run: ${BIN}" >&2; exit 1; }
printf '%s\n' "${BIN}"
