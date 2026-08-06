#!/usr/bin/env bash
set -euo pipefail

# Composes the portrait on-device clips (scripts/capture_usage_video.sh) into the
# 16:9 marketing usage video used on the homepage hero. Produces:
#   website/static/video/chompass-usage.mp4     (1920x1080, x264, cinematic)
#   website/static/video/chompass-poster.jpg    (poster frame for <video poster>)
#
# Cinematics per segment: slow push-in / zoom-to-action windows, a phone bezel
# mockup with soft glow, drifting teal accent light, and lower-third callouts
# (assets from scripts/generate_video_overlays.py). Crossfades between segments.
#
# Requires ffmpeg; runs under `nix shell nixpkgs#ffmpeg` when missing.
# Overlay assets: uv run --with pillow python scripts/generate_video_overlays.py
#
# Usage:
#   ./scripts/compose_usage_video.sh
#   RAW_DIR=android/build/usage-video/raw ./scripts/compose_usage_video.sh
#   DURATION=14 ./scripts/compose_usage_video.sh

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DIR="${RAW_DIR:-${ROOT}/android/build/usage-video/raw}"
ASSET_DIR="${ROOT}/android/build/usage-video/assets"
OUT_DIR="${ROOT}/website/static/video"
WORK_DIR="${ROOT}/android/build/usage-video/work"
FPS=30
WIDTH=1920
HEIGHT=1080
PHONE_W=413                 # ~2.22:1 portrait scaled to fit the 1080 frame
DURATION="${DURATION:-12}"  # seconds per segment after normalization
FADE=0.6                    # crossfade between segments

# Segment order + callout mapping (by clip name prefix).
SEGMENT_ORDER=(ai barcode trend diary)
declare -A CALLOUT_MAP=(
  [ai]=callout-ai
  [barcode]=callout-barcode
  [trend]=callout-trend
  [diary]=callout-budget
)

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
if [ ! -f "${ASSET_DIR}/phone-frame.png" ] || [ ! -f "${ASSET_DIR}/backdrop.png" ]; then
  echo "Missing overlay assets; run: uv run --with pillow python scripts/generate_video_overlays.py" >&2
  exit 1
fi

# pick_clip <prefix> -> first raw clip whose name starts with prefix (sorted).
pick_clip() {
  local prefix="$1" clip
  for clip in $(printf '%s\n' "${CLIPS[@]}" | sort); do
    if [[ "$(basename "$clip")" == "${prefix}-"* ]]; then echo "$clip"; return 0; fi
  done
  echo "" >&2 && echo "  WARNING: no clip for segment '$prefix'" >&2
  return 1
}

# smoothstep ease of progress p (0..1)
ease_expr() { echo "($1)*($1)*(3-2*($1))"; }

# Ken Burns window via zoompan (the only per-frame evaluator here; crop w/h are
# config-time). Push zoom toward (tx,ty) between t0..t1; optional pull back
# between t2..t3. Zoom factor z1; x/y pan the window center toward the target.
# push_expr <z1> <tx> <ty> <t0> <t1> [<t2> <t3>]
push_expr() {
  local z1=$1 tx=$2 ty=$3 t0=$4 t1=$5 t2=${6:-999999} t3=${7:-999999} f=$FPS
  local p1="clip((on-${t0}*${f})/((${t1}-${t0})*${f}),0,1)"
  local p2="clip((on-${t2}*${f})/((${t3}-${t2})*${f}),0,1)"
  local s1; s1=$(ease_expr "${p1}")
  local s2; s2=$(ease_expr "${p2}")
  local e="(${s1}-${s2})"
  local z="1+(${z1}-1)*${e}"
  echo "zoompan=z='${z}':x='iw/2-(iw/zoom/2)+(${tx}-iw/2)*${e}':y='ih/2-(ih/zoom/2)+(${ty}-ih/2)*${e}':d=1:s=${WIDTH}x${HEIGHT}"
}

echo "Rendering segments..."
CONCAT_LIST="${WORK_DIR}/concat.txt"
: > "${CONCAT_LIST}"
IDX=0
for seg in "${SEGMENT_ORDER[@]}"; do
  clip=$(pick_clip "$seg" || true)
  if [ -z "$clip" ]; then
    echo "  SKIP ${seg} (no clip)"
    IDX=$((IDX + 1))
    continue
  fi
  norm="${WORK_DIR}/norm-${IDX}-${seg}.mp4"
  echo "  [${IDX}] ${seg} <- $(basename "$clip")"

  # Zoom profile per segment.
  case "$seg" in
    ai)      PUSH=$(push_expr 1.30 960 620 2.5 7.0 9.5 11.4) ;;
    barcode) PUSH=$(push_expr 1.16 960 540 1.0 10.0) ;;
    trend)   PUSH=$(push_expr 1.16 960 540 1.0 10.0) ;;
    diary)   PUSH=$(push_expr 1.22 960 420 2.0 6.0 8.5 11.0) ;;
    *)       PUSH="crop=${WIDTH}:${HEIGHT}:0:0" ;;
  esac

  CALLOUT="${CALLOUT_MAP[$seg]:-}"
  CALL_ARGS=()
  CALL_CHAIN=""
  if [ -n "$CALLOUT" ] && [ -f "${ASSET_DIR}/${CALLOUT}.png" ]; then
    CALL_ARGS=(-loop 1 -i "${ASSET_DIR}/${CALLOUT}.png")
    CALL_CHAIN=";[4:v]fade=t=in:st=3.2:d=0.5:alpha=1,fade=t=out:st=$((DURATION - 2)):d=0.9:alpha=1[call];[pushed][call]overlay=64:(main_h-overlay_h)-64[withcall]"
  else
    CALL_CHAIN=";[pushed]null[withcall]"
  fi

  ffmpeg -y -v error -i "${clip}" -loop 1 -i "${ASSET_DIR}/backdrop.png" \
    -loop 1 -i "${ASSET_DIR}/glow.png" -loop 1 -i "${ASSET_DIR}/phone-frame.png" \
    "${CALL_ARGS[@]}" \
    -filter_complex \
    "[0:v]scale=${PHONE_W}:-2:flags=lanczos,setsar=1,format=yuv420p[phone];\
[1:v]scale=${WIDTH}:${HEIGHT},setsar=1,format=yuv420p[bg];\
[2:v]scale=-1:1200,format=rgba[glow];\
[bg][glow]overlay=x=960-450+220*sin(t/4.2):y=540-450+160*cos(t/5.3):format=auto[bgg];\
[bgg][phone]overlay=x=(main_w-overlay_w)/2:y=(main_h-overlay_h)/2[sc];\
[sc][3:v]overlay=x=(main_w-overlay_w)/2:y=(main_h-overlay_h)/2[scf];\
[scf]${PUSH}[pushed]${CALL_CHAIN};\
[withcall]fade=t=in:st=0:d=0.4,format=yuv420p[v]" \
    -map "[v]" -r "${FPS}" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p \
    -t "${DURATION}" "${norm}"
  printf "file '%s'\n" "${norm}" >> "${CONCAT_LIST}"
  IDX=$((IDX + 1))
done

COUNT=$((IDX))
if [ "${COUNT}" -lt 2 ]; then
  echo "Need at least 2 segments to compose; got ${COUNT}." >&2
  exit 1
fi

OUT_MP4="${OUT_DIR}/chompass-usage.mp4"
OUT_POSTER="${OUT_DIR}/chompass-poster.jpg"

echo "Crossfading ${COUNT} segments -> ${OUT_MP4}"
FILTER=""
PREV=""
for i in $(seq 0 $((COUNT - 2))); do
  NEXT="xf$((i + 1))"
  OFF=$(awk -v i="$i" -v d="$DURATION" -v f="$FADE" 'BEGIN{ printf "%.3f", (i + 1) * d - (i + 1) * f }')
  if [ -z "$PREV" ]; then
    FILTER="[0:v][1:v]xfade=transition=fade:duration=${FADE}:offset=${OFF}[${NEXT}]"
  else
    FILTER="${FILTER};[${PREV}][$((i + 1)):v]xfade=transition=fade:duration=${FADE}:offset=${OFF}[${NEXT}]"
  fi
  PREV="$NEXT"
done
FILTER="${FILTER};[${PREV}]format=yuv420p[vout]"

INPUTS=""
for f in "${WORK_DIR}"/norm-*.mp4; do INPUTS="${INPUTS} -i ${f}"; done
# shellcheck disable=SC2086
ffmpeg -y -v error $INPUTS -filter_complex "${FILTER}" \
  -map "[vout]" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -movflags +faststart \
  -r "${FPS}" "${OUT_MP4}"

echo "Extracting poster -> ${OUT_POSTER}"
TOTAL=$(awk -v n="$COUNT" -v d="$DURATION" -v f="$FADE" 'BEGIN{ printf "%.2f", n * d - (n - 1) * f }')
POSTER_AT=$(awk -v total="$TOTAL" 'BEGIN{ printf "%.2f", total * 0.28 }')
ffmpeg -y -v error -ss "${POSTER_AT}" -i "${OUT_MP4}" -frames:v 1 -q:v 3 "${OUT_POSTER}"

echo "Done (${TOTAL}s):"
ls -lh "${OUT_MP4}" "${OUT_POSTER}"
