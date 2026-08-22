#!/usr/bin/env bash
# Predownload on-device LLM models (Gemma 4 E2B/E4B-it .litertlm) into a local
# cache and push them into the test phone's app-private storage, so the
# 2.6–3.7 GB file is downloaded once and reused across installs/reflashes
# instead of being re-fetched every test cycle.
#
# Usage:
#   ./scripts/push_ondevice_model.sh                  # e2b: download (if needed) + push
#   ./scripts/push_ondevice_model.sh e4b              # Gemma 4 E4B-it (~3.7 GB)
#   ./scripts/push_ondevice_model.sh --download-only  # fetch + verify into cache only
#   ./scripts/push_ondevice_model.sh --push-only      # push a cached model (e.g. after reflash)
#   PACKAGE=app.chompass.debug2 ./scripts/push_ondevice_model.sh
#
# Env:
#   ADB_BIN    adb binary (auto-detects Windows adb.exe from WSL if unset)
#   PACKAGE    target package (default: app.chompass.debug; must be debuggable for run-as)
#   CACHE_DIR  cache location (default: android/build/ondevice-models — gitignored)
#   HF_TOKEN   Hugging Face token (unused for these repos: both are public/gated:false)
#
# Model metadata below mirrors ModelCatalog.kt — keep in sync when the catalog
# changes (filename, sha256, downloadUrl).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
CACHE_DIR="${CACHE_DIR:-${ROOT}/android/build/ondevice-models}"

MODEL="e2b"
DO_DOWNLOAD=1
DO_PUSH=1
while [ $# -gt 0 ]; do
  case "$1" in
    --download-only) DO_PUSH=0 ;;
    --push-only) DO_DOWNLOAD=0 ;;
    -h|--help)
      sed -n '2,22p' "$0"
      exit 0
      ;;
    e2b|e4b) MODEL="$1" ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

# --- catalog (mirror of android/.../services/ondevice/ModelCatalog.kt) --------
case "${MODEL}" in
  e2b)
    DISPLAY="Gemma 4 E2B-it"
    FILENAME="gemma-4-E2B-it.litertlm"
    SIZE_GB="2.6"
    SHA256="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    ;;
  e4b)
    DISPLAY="Gemma 4 E4B-it"
    FILENAME="gemma-4-E4B-it.litertlm"
    SIZE_GB="3.7"
    SHA256="0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    URL="https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    ;;
esac

verify_sha() {
  local file="$1"
  [ -f "$file" ] || return 1
  echo "${SHA256}  ${file}" | sha256sum -c --status - 2>/dev/null
}

download_model() {
  local file="${CACHE_DIR}/${FILENAME}"
  if verify_sha "$file"; then
    echo "  cached and verified: ${file} ($(du -h "$file" | cut -f1))"
    return 0
  fi
  mkdir -p "${CACHE_DIR}"
  echo "  downloading ${DISPLAY} (${SIZE_GB} GB, sha256-verified)..."
  local curl_args=( -L --fail --retry 3 -C - --output "$file" )
  if [ -n "${HF_TOKEN:-}" ]; then
    curl_args+=( -H "Authorization: Bearer ${HF_TOKEN}" )
  fi
  if ! curl "${curl_args[@]}" "$URL"; then
    echo "  download interrupted; retrying from scratch" >&2
    rm -f "$file"
    curl "${curl_args[@]}" "$URL"
  fi
  if ! verify_sha "$file"; then
    rm -f "$file"
    echo "  ERROR: SHA-256 mismatch for ${FILENAME} — deleted; re-run to retry" >&2
    return 1
  fi
  echo "  verified sha256: ${file} ($(du -h "$file" | cut -f1))"
}

push_model() {
  local file="${CACHE_DIR}/${FILENAME}"
  if ! verify_sha "$file"; then
    echo "Cached file missing or corrupt: ${file}" >&2
    echo "Run without --push-only first (or delete the cache entry)." >&2
    return 1
  fi
  echo "Pushing ${DISPLAY} (${SIZE_GB} GB) into ${PACKAGE} files/models/ ..."
  "${ADB_BIN}" push "$file" "/data/local/tmp/${FILENAME}"
  "${ADB_BIN}" shell run-as "${PACKAGE}" mkdir -p files/models
  "${ADB_BIN}" shell run-as "${PACKAGE}" cp "/data/local/tmp/${FILENAME}" "files/models/"
  "${ADB_BIN}" shell rm -f "/data/local/tmp/${FILENAME}"
  local ondev
  ondev="$("${ADB_BIN}" shell run-as "${PACKAGE}" sha256sum "files/models/${FILENAME}" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
  if [ "${ondev,,}" = "${SHA256}" ]; then
    echo "  verified on device (sha256 match)"
  else
    echo "  WARNING: could not verify sha256 on device (got: '${ondev}')" >&2
    echo "    check manually: ${ADB_BIN} shell run-as ${PACKAGE} ls -la files/models/" >&2
  fi
  echo "Done. Smoke test:"
  echo "  ${ADB_BIN} shell am start -n ${PACKAGE}/app.chompass.MainActivity --ez run_ondevice_llm_test true"
}

echo "Using adb: ${ADB_BIN}"
echo "Model: ${DISPLAY} (${FILENAME}, ${SIZE_GB} GB)"

if [ "${DO_DOWNLOAD}" -eq 1 ]; then
  download_model
fi

if [ "${DO_PUSH}" -eq 1 ]; then
  "${ADB_BIN}" get-state >/dev/null
  push_model
fi
