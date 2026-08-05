#!/usr/bin/env bash
set -euo pipefail

# Composes the portrait on-device clips (scripts/capture_usage_video.sh) into the
# 16:9 marketing usage video used on the homepage hero. Produces:
#   website/static/video/chompass-usage.mp4     (1920x1080, x264, crossfaded)
#   website/static/video/chompass-poster.jpg    (poster frame for <video poster>)
#
# Requires ffmpeg. Uses ephemeral `nix shell nixpkgs#ffmpeg` when ffmpeg is not on
# PATH (devenv does not ship it). No devenv.nix change needed.
#
# Usage:
#   ./scripts/compose_usage_video.sh
#   RAW_DIR=android/build/usage-video/raw ./scripts/compose_usage_video.sh
#   DURATION=14 ./scripts/compose_usage_video.sh

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DIR="${RAW_DIR:-${ROOT}/android/build/usage-video/raw}"
OUT_DIR="${ROOT}/website/static/video"
WORK_DIR="${ROOT}/android/build/usage-video/work"
FPS=30
WIDTH=1920
HEIGHT=1080
PHONE_W=413                 # ~2.22:1 portrait scaled to fit the 1080 frame
DURATION="${DURATION:-12}"  # seconds per clip after normalization
BG="${BG:-0x111015}"        # near-black teal-tinted backdrop

mkdir -p "${WORK_DIR}" "${OUT_DIR}"

# ffmpeg via nix when missing from PATH (re-exec under a nix shell).
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg not on PATH; re-running under 'nix shell nixpkgs#ffmpeg'..."
  exec nix shell nixpkgs#ffmpeg -c bash "$0" "$@"
fi

shopt -s nullglob
CLIPS=("${RAW_DIR}"/*.mp4)
if [ "${#CLIPS[@]}" -eq 0 ]; then
  echo "No clips found in ${RAW_DIR}. Run scripts/capture_usage_video.sh first." >&2
  exit 1
fi
echo "Found ${#CLIPS[@]} clip(s) in ${RAW_DIR}"

# Order deterministically by name (home, add-food, progress, coach ...).
mapfile -t CLIPS < <(printf '%s\n' "${CLIPS[@]}" | sort)

CONCAT_LIST="${WORK_DIR}/concat.txt"
: > "${CONCAT_LIST}"
IDX=0
for clip in "${CLIPS[@]}"; do
  name="$(basename "${clip}" .mp4)"
  norm="${WORK_DIR}/norm-${IDX}-${name}.mp4"
  echo "  normalizing ${name} -> ${norm}"
  ffmpeg -y -v error -i "${clip}" \
    -f lavfi -i "color=c=${BG}:s=${WIDTH}x${HEIGHT}:d=${DURATION}" \
    -filter_complex \
    "[0:v]scale=${PHONE_W}:-2:flags=lanczos,setsar=1,format=yuv420p[phone];\
     [1:v][phone]overlay=(W-w)/2:(H-h)/2,fade=t=in:st=0:d=0.4,fade=t=out:st=$((DURATION-1)):d=1,format=yuv420p[v]" \
    -map "[v]" -r "${FPS}" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p \
    -t "${DURATION}" "${norm}"
  printf "file '%s'\n" "${norm}" >> "${CONCAT_LIST}"
  IDX=$((IDX + 1))
done

OUT_MP4="${OUT_DIR}/chompass-usage.mp4"
OUT_POSTER="${OUT_DIR}/chompass-poster.jpg"
echo "Concatenating -> ${OUT_MP4}"
ffmpeg -y -v error -f concat -safe 0 -i "${CONCAT_LIST}" \
  -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -movflags +faststart \
  "${OUT_MP4}"

echo "Extracting poster -> ${OUT_POSTER}"
POSTER_AT=$(awk -v d="$DURATION" -v n="${#CLIPS[@]}" 'BEGIN{ printf "%.2f", d * n * 0.25 }')
ffmpeg -y -v error -ss "${POSTER_AT}" -i "${OUT_MP4}" -frames:v 1 -q:v 3 "${OUT_POSTER}"

echo "Done:"
ls -lh "${OUT_MP4}" "${OUT_POSTER}"
