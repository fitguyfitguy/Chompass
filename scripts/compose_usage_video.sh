#!/usr/bin/env bash
set -euo pipefail

# Composes the portrait on-device clips (scripts/capture_usage_video.sh) into the
# 16:9 marketing usage video used on the homepage hero. Produces:
#   website/static/video/chompass-usage.mp4     (1920x1080, x264, cinematic)
#   website/static/video/chompass-poster.jpg    (poster frame for <video poster>)
#
# Cinematics per segment: dynamic Ken Burns (pull-back openings, drift, and a
# final punch that ends zoomed on the calorie gauge), a phone bezel mockup with
# soft glow, drifting teal accent light, and lower-third callouts (assets from
# scripts/generate_video_overlays.py). Crossfades between segments.
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
  [diary]=callout-diary
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

# pick_clip <prefix> -> newest raw clip whose name starts with prefix.
pick_clip() {
  local prefix="$1" clip
  for clip in $(printf '%s\n' "${CLIPS[@]}" | sort); do
    if [[ "$(basename "$clip")" == "${prefix}-"* ]]; then last="$clip"; fi
  done
  if [ -n "${last:-}" ]; then echo "$last"; return 0; fi
  echo "" >&2 && echo "  WARNING: no clip for segment '$prefix'" >&2
  return 1
}

# smoothstep ease of progress p (0..1)
ease_expr() { echo "($1)*($1)*(3-2*($1))"; }

# General Ken Burns window via zoompan (the only per-frame evaluator here).
# Zoom z0->z1 over t0..t1, then z1->z2 over t2..t3 (stage 2 skipped when
# t3 <= t2); the viewport pans toward (tx,ty) in step with zoom progress, so a
# late stage-2 punch ends the segment zoomed on the target.
# zoom_expr <z0> <z1> <z2> <tx> <ty> <t0> <t1> [<t2> <t3>]
zoom_expr() {
  local z0=$1 z1=$2 z2=$3 tx=$4 ty=$5 t0=$6 t1=$7 t2=${8:-0} t3=${9:-0} f=$FPS
  local p1="clip((on-${t0}*${f})/((${t1}-${t0})*${f}),0,1)"
  local s1; s1=$(ease_expr "${p1}")
  local s2="0"
  if [ "$(awk -v a="$t3" -v b="$t2" 'BEGIN{print (a>b)?1:0}')" = "1" ]; then
    local p2="clip((on-${t2}*${f})/((${t3}-${t2})*${f}),0,1)"
    s2=$(ease_expr "${p2}")
  fi
  local z="(${z0}+(${z1}-${z0})*${s1}+(${z2}-${z1})*${s2})"
  local frac="if(gt(abs(${z2}-${z0}),0.001),(${z}-${z0})/(${z2}-${z0}),${s1})"
  echo "zoompan=z='${z}':x='iw/2-(iw/zoom/2)+(${tx}-iw/2)*${frac}':y='ih/2-(ih/zoom/2)+(${ty}-ih/2)*${frac}':d=1:s=${WIDTH}x${HEIGHT}"
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
    ai)      PUSH=$(zoom_expr 1.28 1.00 1.00 960 540 0 3.5) ;;
    barcode) PUSH=$(zoom_expr 1.00 1.20 1.20 960 540 0.5 9) ;;
    trend)   PUSH=$(zoom_expr 1.00 1.20 1.20 1020 540 1 10) ;;
    diary)   PUSH=$(zoom_expr 1.00 1.12 1.90 960 444 0.5 4.5 5.5 11.8) ;;
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
