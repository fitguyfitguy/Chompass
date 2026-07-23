#!/usr/bin/env bash
# Inspect and prune Codeberg release assets (APK attachments).
#
# Codeberg applies a combined quota for releases, packages, LFS, and attachments
# (default 1.5 GiB per user/org). Policy: keep only the latest release, and ship
# the universal APK (+ SHA256SUMS) — not per-ABI splits.
#
# Usage:
#   ./scripts/manage_release_assets.sh list
#   ./scripts/manage_release_assets.sh keep-latest -y
#   ./scripts/manage_release_assets.sh keep-latest --keep v1.14.10 --dry-run
#   ./scripts/manage_release_assets.sh prune-abi-splits v1.14.10 -y
#   ./scripts/manage_release_assets.sh prune-play-assets -y
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CODEBERG_REPO="${CODEBERG_REPO:-fitguy/nofud}"
LOGIN="${CODEBERG_LOGIN:-codeberg}"

run_tea() {
  if command -v tea >/dev/null 2>&1; then
    tea "$@"
  else
    nix shell nixpkgs#tea -c tea "$@"
  fi
}

ensure_login() {
  if ! run_tea logins list 2>/dev/null | rg -q "$LOGIN"; then
    cat >&2 <<EOF
No Codeberg login "$LOGIN" configured.

  export CODEBERG_TOKEN='...'
  nix shell nixpkgs#tea -c tea logins add -n $LOGIN -u https://codeberg.org -t "\$CODEBERG_TOKEN"
EOF
    exit 1
  fi
}

is_abi_split() {
  local name="$1"
  [[ "$name" == *-arm64-v8a.apk || "$name" == *-armeabi-v7a.apk || "$name" == *-x86_64.apk ]]
}

is_play_asset() {
  local name="$1"
  [[ "$name" == NoFUD-play-* ]]
}

list_release_tags() {
  run_tea releases list \
    --login "$LOGIN" \
    --repo "$CODEBERG_REPO" \
    -o simple 2>/dev/null \
    | sed -n 's/^[[:space:]]*//;s/[[:space:]].*//;s/^v//;s/^/v/p' \
    | sort -V
}

list_assets_for_tag() {
  local tag="$1"
  run_tea releases assets list \
    --login "$LOGIN" \
    --repo "$CODEBERG_REPO" \
    "$tag" \
    -o simple 2>/dev/null
}

parse_size_mb() {
  local size="$1"
  if [[ "$size" =~ ^([0-9]+)[[:space:]]*MB$ ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "$size" =~ ^([0-9]+)[[:space:]]*KB$ ]]; then
    echo "0"
  elif [[ "$size" =~ ^([0-9]+)[[:space:]]*B$ ]]; then
    echo "0"
  else
    echo "0"
  fi
}

cmd_list() {
  ensure_login
  local total_mb=0
  echo "Release assets on $CODEBERG_REPO"
  echo
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    local assets
    assets="$(list_assets_for_tag "$tag" || true)"
    [[ -z "$assets" ]] && continue
    echo "==> $tag"
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local name="${line%% *}"
      local size="${line#* }"
      local mb
      mb="$(parse_size_mb "$size")"
      total_mb=$((total_mb + mb))
      local marker=""
      if is_play_asset "$name"; then
        marker=" [play — disabled flavor]"
      elif is_abi_split "$name"; then
        marker=" [abi split]"
      fi
      printf '  %-40s %8s%s\n' "$name" "$size" "$marker"
    done <<<"$assets"
    echo
  done < <(list_release_tags)
  echo "Estimated attachment total: ~${total_mb} MB"
  echo
  cat <<EOF
Quota notes:
  - Codeberg default: 1.5 GiB for releases + packages + LFS + attachments (per user/org).
  - Check usage: https://codeberg.org/user/settings (or org settings).
  - Request more: https://codeberg.org/Codeberg-e.V./requests
  - Prune old ABI splits (keep universal APKs + SHA256SUMS):
      ./scripts/manage_release_assets.sh prune-abi-splits --before v1.6.0 -y
  - Keep only the latest release (delete older release pages + assets):
      ./scripts/manage_release_assets.sh keep-latest -y
  - Remove disabled play-flavor APKs (keep fdroid/universal + SHA256SUMS):
      ./scripts/manage_release_assets.sh prune-play-assets --dry-run
EOF
}

cmd_prune_abi_splits() {
  ensure_login
  local dry_run=0
  local assume_yes=0
  local before=""
  local -a tags=()

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        dry_run=1
        shift
        ;;
      -y|--yes)
        assume_yes=1
        shift
        ;;
      --before)
        before="${2:?--before requires a tag like v1.6.0}"
        shift 2
        ;;
      -h|--help)
        cat <<'EOF'
Usage:
  manage_release_assets.sh prune-abi-splits [options] [tag ...]

Options:
  --before <tag>   Prune ABI splits on all releases older than <tag> (e.g. v1.6.0)
  --dry-run        Print deletions without applying them
  -y, --yes        Skip confirmation prompt

Keeps universal APKs (*.apk without -arm64-v8a / -armeabi-v7a / -x86_64) and SHA256SUMS.
EOF
        exit 0
        ;;
      v*)
        tags+=("$1")
        shift
        ;;
      *)
        echo "Unknown argument: $1" >&2
        exit 1
        ;;
    esac
  done

  if [[ -n "$before" ]]; then
    while IFS= read -r tag; do
      [[ -z "$tag" ]] && continue
      if [[ "$(printf '%s\n%s\n' "$tag" "$before" | sort -V | head -1)" == "$tag" && "$tag" != "$before" ]]; then
        tags+=("$tag")
      fi
    done < <(list_release_tags)
  fi

  if [[ ${#tags[@]} -eq 0 ]]; then
    echo "No release tags selected. Pass tags (v1.3.0 ...) or --before vX.Y.Z." >&2
    exit 1
  fi

  local -a to_delete=()
  for tag in "${tags[@]}"; do
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local name="${line%% *}"
      if is_abi_split "$name"; then
        to_delete+=("$tag|$name")
      fi
    done < <(list_assets_for_tag "$tag" || true)
  done

  if [[ ${#to_delete[@]} -eq 0 ]]; then
    echo "No ABI split assets to prune."
    exit 0
  fi

  echo "ABI split assets to delete (${#to_delete[@]}):"
  for entry in "${to_delete[@]}"; do
    IFS='|' read -r tag name <<<"$entry"
    echo "  $tag  $name"
  done

  if [[ "$dry_run" -eq 1 ]]; then
    echo
    echo "Dry run — no assets deleted."
    exit 0
  fi

  if [[ "$assume_yes" -ne 1 ]]; then
    echo
    read -r -p "Delete these assets? [y/N] " confirm
    if [[ "$confirm" != [yY] ]]; then
      echo "Aborted."
      exit 1
    fi
  fi

  declare -A by_tag=()
  for entry in "${to_delete[@]}"; do
    IFS='|' read -r tag name <<<"$entry"
    by_tag["$tag"]+="$name "
  done

  for tag in "${!by_tag[@]}"; do
    # shellcheck disable=SC2206
    local names=( ${by_tag[$tag]} )
    run_tea releases assets delete \
      --login "$LOGIN" \
      --repo "$CODEBERG_REPO" \
      -y \
      "$tag" \
      "${names[@]}"
    echo "Deleted ${#names[@]} asset(s) from $tag"
  done
}

cmd_keep_latest() {
  ensure_login
  local dry_run=0
  local assume_yes=0
  local keep=""
  local delete_tags=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        dry_run=1
        shift
        ;;
      -y|--yes)
        assume_yes=1
        shift
        ;;
      --keep)
        keep="${2:?--keep requires a tag like v1.14.10}"
        shift 2
        ;;
      --delete-tags)
        delete_tags=1
        shift
        ;;
      -h|--help)
        cat <<'EOF'
Usage:
  manage_release_assets.sh keep-latest [options]

Options:
  --keep <tag>     Release tag to keep (default: highest semver tag with a release)
  --delete-tags    Also delete git tags for removed releases
  --dry-run        Print deletions without applying them
  -y, --yes        Skip confirmation prompt

Deletes every Codeberg release except the kept one (attachments + release page).
Quota policy: only the latest full release is retained.
EOF
        exit 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        exit 1
        ;;
    esac
  done

  local -a all_tags=()
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    all_tags+=("$tag")
  done < <(list_release_tags)

  if [[ ${#all_tags[@]} -eq 0 ]]; then
    echo "No releases found on $CODEBERG_REPO."
    exit 0
  fi

  if [[ -z "$keep" ]]; then
    keep="${all_tags[-1]}"
  fi
  if [[ "$keep" != v* ]]; then
    keep="v${keep}"
  fi

  local -a to_delete=()
  local found_keep=0
  for tag in "${all_tags[@]}"; do
    if [[ "$tag" == "$keep" ]]; then
      found_keep=1
      continue
    fi
    to_delete+=("$tag")
  done

  if [[ "$found_keep" -ne 1 ]]; then
    echo "Keep target $keep is not an existing release." >&2
    echo "Available: ${all_tags[*]}" >&2
    exit 1
  fi

  if [[ ${#to_delete[@]} -eq 0 ]]; then
    echo "Already only one release ($keep). Nothing to delete."
    exit 0
  fi

  echo "Keeping: $keep"
  echo "Deleting ${#to_delete[@]} older release(s):"
  for tag in "${to_delete[@]}"; do
    echo "  $tag"
  done
  if [[ "$delete_tags" -eq 1 ]]; then
    echo "(also deleting git tags for those releases)"
  fi

  if [[ "$dry_run" -eq 1 ]]; then
    echo
    echo "Dry run — no releases deleted."
    exit 0
  fi

  if [[ "$assume_yes" -ne 1 ]]; then
    echo
    read -r -p "Delete these releases? [y/N] " confirm
    if [[ "$confirm" != [yY] ]]; then
      echo "Aborted."
      exit 1
    fi
  fi

  local -a delete_args=(--login "$LOGIN" --repo "$CODEBERG_REPO" -y)
  if [[ "$delete_tags" -eq 1 ]]; then
    delete_args+=(--delete-tag)
  fi
  # tea accepts multiple tags per invocation
  run_tea releases delete "${delete_args[@]}" "${to_delete[@]}"
  echo "Deleted ${#to_delete[@]} release(s). Kept $keep."
}

cmd_prune_play_assets() {
  ensure_login
  local dry_run=0
  local assume_yes=0
  local -a tags=()

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        dry_run=1
        shift
        ;;
      -y|--yes)
        assume_yes=1
        shift
        ;;
      -h|--help)
        cat <<'EOF'
Usage:
  manage_release_assets.sh prune-play-assets [options] [tag ...]

Options:
  --dry-run   Print deletions without applying them
  -y, --yes   Skip confirmation prompt

Deletes NoFUD-play-*.apk attachments from Codeberg releases. Keeps fdroid APKs,
legacy NoFUD-<version>.apk files, and SHA256SUMS. The play product flavor is
disabled — see docs/DISTRIBUTION.md.

With no tags, prunes play assets on every release that has them.
EOF
        exit 0
        ;;
      v*)
        tags+=("$1")
        shift
        ;;
      *)
        echo "Unknown argument: $1" >&2
        exit 1
        ;;
    esac
  done

  if [[ ${#tags[@]} -eq 0 ]]; then
    while IFS= read -r tag; do
      [[ -z "$tag" ]] && continue
      tags+=("$tag")
    done < <(list_release_tags)
  fi

  local -a to_delete=()
  for tag in "${tags[@]}"; do
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local name="${line%% *}"
      if is_play_asset "$name"; then
        to_delete+=("$tag|$name")
      fi
    done < <(list_assets_for_tag "$tag" || true)
  done

  if [[ ${#to_delete[@]} -eq 0 ]]; then
    echo "No play-flavor assets to prune."
    exit 0
  fi

  echo "Play-flavor assets to delete (${#to_delete[@]}):"
  for entry in "${to_delete[@]}"; do
    IFS='|' read -r tag name <<<"$entry"
    echo "  $tag  $name"
  done

  if [[ "$dry_run" -eq 1 ]]; then
    echo
    echo "Dry run — no assets deleted."
    exit 0
  fi

  if [[ "$assume_yes" -ne 1 ]]; then
    echo
    read -r -p "Delete these assets? [y/N] " confirm
    if [[ "$confirm" != [yY] ]]; then
      echo "Aborted."
      exit 1
    fi
  fi

  declare -A by_tag=()
  for entry in "${to_delete[@]}"; do
    IFS='|' read -r tag name <<<"$entry"
    by_tag["$tag"]+="$name "
  done

  for tag in "${!by_tag[@]}"; do
    # shellcheck disable=SC2206
    local names=( ${by_tag[$tag]} )
    run_tea releases assets delete \
      --login "$LOGIN" \
      --repo "$CODEBERG_REPO" \
      -y \
      "$tag" \
      "${names[@]}"
    echo "Deleted ${#names[@]} play asset(s) from $tag"
  done
}

usage() {
  cat <<'EOF'
Usage: manage_release_assets.sh <command> [options]

Commands:
  list                         List release attachments and estimated total size
  keep-latest [options]        Delete all releases except the latest (or --keep)
  prune-abi-splits [options]   Delete per-ABI APK splits; keep universal APKs + SHA256SUMS
  prune-play-assets [options]  Delete NoFUD-play-*.apk attachments (disabled flavor)

Environment:
  CODEBERG_REPO   Default: fitguy/nofud
  CODEBERG_LOGIN  Default: codeberg
EOF
}

main() {
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    list|ls)
      cmd_list
      ;;
    keep-latest|keep)
      cmd_keep_latest "$@"
      ;;
    prune-abi-splits|prune)
      cmd_prune_abi_splits "$@"
      ;;
    prune-play-assets|prune-play)
      cmd_prune_play_assets "$@"
      ;;
    -h|--help|"")
      usage
      ;;
    *)
      echo "Unknown command: $cmd" >&2
      usage >&2
      exit 1
      ;;
  esac
}

main "$@"
