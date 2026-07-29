#!/usr/bin/env python3
"""Download ACETADA (Purdue) and build L0/L1/L2 image manifests.

ACETADA is CC BY-NC 4.0 — research / non-commercial eval only. Do not use
results in commercial accuracy claims.

The public zip is ~4.9 GB. This script:

1. Pulls ``ACETADA-HF-dataset.csv`` via HTTP range from the ZIP64 archive
   (no full download required for metadata).
2. Optionally extracts only the before-meal JPEGs needed for ``--limit N``.

Ground truth macros are **served (before-meal)** amounts: dietitian per-item
macros in the CSV are for consumed portions, so each item is rescaled by
``before_portion_g / consumed_g`` to match the before-meal photo.

L1 user text = ``meal_type`` (Breakfast / Lunch / Dinner).
L2 user text = food-item names without quantities.
"""

from __future__ import annotations

import argparse
import csv
import ssl
import sys
import urllib.error
import urllib.request
import zlib
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from image_text_variants import write_image_text_variants
from schema import DATA_DIR, Sample, write_manifest

ACETADA_PAGE = "https://skynet.ecn.purdue.edu/~coburn6/ACETADA/"
ACETADA_ZIP_URL = f"{ACETADA_PAGE}dataset/ACETADA-release.zip"
CSV_MEMBER = "ACETADA-release/ACETADA-HF-dataset.csv"
# From the published ZIP central directory (stable across re-fetches of this release).
CSV_LOCAL_OFFSET = 78
CSV_COMPRESSED_SIZE = 158133
CSV_NAME_LEN = 38
CSV_EXTRA_LEN = 32


def _ssl_context() -> ssl.SSLContext:
    # Purdue skynet often fails verification on WSL CA bundles; prefer verified
    # but fall back so the public ZIP remains reachable.
    try:
        ctx = ssl.create_default_context()
        req = urllib.request.Request(ACETADA_ZIP_URL, method="HEAD")
        urllib.request.urlopen(req, context=ctx, timeout=15).close()
        return ctx
    except Exception:
        print("WARN: using unverified SSL for skynet.ecn.purdue.edu", file=sys.stderr)
        return ssl._create_unverified_context()


def http_range(url: str, start: int, end: int, ctx: ssl.SSLContext) -> bytes:
    """Inclusive byte range GET."""
    req = urllib.request.Request(url, headers={"Range": f"bytes={start}-{end}"})
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        return resp.read()


def download_csv(dest: Path, ctx: ssl.SSLContext) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        print(f"CSV already present: {dest}")
        return dest
    # Local file header + name + extra + deflated payload.
    header_len = 30 + CSV_NAME_LEN + CSV_EXTRA_LEN
    blob = http_range(
        ACETADA_ZIP_URL,
        CSV_LOCAL_OFFSET,
        CSV_LOCAL_OFFSET + header_len + CSV_COMPRESSED_SIZE - 1,
        ctx,
    )
    if blob[:4] != b"PK\x03\x04":
        raise RuntimeError(f"Unexpected ZIP local header at offset {CSV_LOCAL_OFFSET}")
    nlen = int.from_bytes(blob[26:28], "little")
    elen = int.from_bytes(blob[28:30], "little")
    method = int.from_bytes(blob[8:10], "little")
    data_start = 30 + nlen + elen
    comp = blob[data_start : data_start + CSV_COMPRESSED_SIZE]
    if len(comp) < CSV_COMPRESSED_SIZE:
        raise RuntimeError("Truncated CSV member download")
    if method == 8:
        raw = zlib.decompress(comp, -15)
    elif method == 0:
        raw = comp
    else:
        raise RuntimeError(f"Unsupported ZIP method {method} for ACETADA CSV")
    dest.write_bytes(raw)
    print(f"Wrote {dest} ({len(raw)} bytes)")
    return dest


def _f(row: dict[str, str], key: str) -> float:
    val = (row.get(key) or "").strip()
    if not val:
        return 0.0
    return float(val)


def parse_food_items(row: dict[str, str]) -> list[dict[str, float | str]]:
    count = int(float(row.get("food_item_count") or 0))
    items: list[dict[str, float | str]] = []
    for i in range(1, max(count, 15) + 1):
        name = (row.get(f"food_item_{i}_name") or "").strip()
        if not name:
            continue
        before = _f(row, f"food_item_{i}_before_portion_g")
        consumed = _f(row, f"food_item_{i}_consumed_g")
        energy = _f(row, f"food_item_{i}_energy_kcal")
        protein = _f(row, f"food_item_{i}_protein_g")
        carbs = _f(row, f"food_item_{i}_carbs_g")
        fat = _f(row, f"food_item_{i}_fat_g")
        # CSV macros are for consumed mass; rescale to the before-meal plate.
        if consumed > 0:
            scale = before / consumed
        elif before > 0 and energy == 0 and protein == 0 and carbs == 0 and fat == 0:
            scale = 0.0
        else:
            scale = 1.0
        items.append(
            {
                "name": name,
                "before_portion_g": before,
                "consumed_g": consumed,
                "calories": energy * scale,
                "protein_g": protein * scale,
                "carbs_g": carbs * scale,
                "fat_g": fat * scale,
            }
        )
    return items


def load_rows(csv_path: Path) -> list[dict[str, str]]:
    with csv_path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def find_member_offsets(ctx: ssl.SSLContext) -> dict[str, tuple[int, int, int]]:
    """Map ZIP member path -> (local_header_offset, compressed_size, method).

    Uses the ZIP64 end-of-central-directory (archive is >4 GiB).
    """
    # Fetch last 64 KiB for ZIP64 EOCD + locator.
    # Content-Length from HEAD would be ideal; use known size with fallback probe.
    try:
        req = urllib.request.Request(ACETADA_ZIP_URL, method="HEAD")
        with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
            size = int(resp.headers["Content-Length"])
    except Exception:
        size = 4941836247
    tail = http_range(ACETADA_ZIP_URL, max(0, size - 65536), size - 1, ctx)
    loc = tail.rfind(b"PK\x06\x07")
    if loc < 0:
        raise RuntimeError("ZIP64 locator not found")
    eocd64_off = int.from_bytes(tail[loc + 8 : loc + 16], "little")
    # Re-fetch a window covering the ZIP64 EOCD if needed.
    if eocd64_off < size - len(tail):
        eocd_blob = http_range(ACETADA_ZIP_URL, eocd64_off, eocd64_off + 128, ctx)
    else:
        eocd_blob = tail[eocd64_off - (size - len(tail)) :]
    if eocd_blob[:4] != b"PK\x06\x06":
        raise RuntimeError("ZIP64 EOCD signature mismatch")
    cd_size = int.from_bytes(eocd_blob[40:48], "little")
    cd_offset = int.from_bytes(eocd_blob[48:56], "little")
    cd = http_range(ACETADA_ZIP_URL, cd_offset, cd_offset + cd_size - 1, ctx)

    members: dict[str, tuple[int, int, int]] = {}
    pos = 0
    while pos + 46 <= len(cd):
        if cd[pos : pos + 4] != b"PK\x01\x02":
            break
        method = int.from_bytes(cd[pos + 10 : pos + 12], "little")
        comp = int.from_bytes(cd[pos + 20 : pos + 24], "little")
        uncomp = int.from_bytes(cd[pos + 24 : pos + 28], "little")
        nlen = int.from_bytes(cd[pos + 28 : pos + 30], "little")
        elen = int.from_bytes(cd[pos + 30 : pos + 32], "little")
        clen = int.from_bytes(cd[pos + 32 : pos + 34], "little")
        local_off = int.from_bytes(cd[pos + 42 : pos + 46], "little")
        name = cd[pos + 46 : pos + 46 + nlen].decode("utf-8", "replace")
        extra = cd[pos + 46 + nlen : pos + 46 + nlen + elen]
        real_comp, real_off = comp, local_off
        if 0xFFFFFFFF in (comp, uncomp, local_off) and extra:
            epos = 0
            while epos + 4 <= len(extra):
                eid = int.from_bytes(extra[epos : epos + 2], "little")
                esz = int.from_bytes(extra[epos + 2 : epos + 4], "little")
                edata = extra[epos + 4 : epos + 4 + esz]
                if eid == 1:
                    o = 0
                    if uncomp == 0xFFFFFFFF:
                        o += 8
                    if comp == 0xFFFFFFFF:
                        real_comp = int.from_bytes(edata[o : o + 8], "little")
                        o += 8
                    if local_off == 0xFFFFFFFF:
                        real_off = int.from_bytes(edata[o : o + 8], "little")
                epos += 4 + esz
        members[name] = (real_off, real_comp, method)
        pos += 46 + nlen + elen + clen
    return members


def extract_member(
    member_path: str,
    dest: Path,
    members: dict[str, tuple[int, int, int]],
    ctx: ssl.SSLContext,
) -> Path | None:
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    key = member_path if member_path.startswith("ACETADA-release/") else f"ACETADA-release/{member_path}"
    info = members.get(key)
    if info is None:
        # Try without leading folder variants.
        for cand in members:
            if cand.endswith(member_path) or cand.endswith("/" + member_path.lstrip("/")):
                info = members[cand]
                key = cand
                break
    if info is None:
        print(f"WARN: missing zip member for {member_path}", file=sys.stderr)
        return None
    local_off, comp_size, method = info
    # Read local header to find payload start.
    header = http_range(ACETADA_ZIP_URL, local_off, local_off + 30 + 1024 - 1, ctx)
    if header[:4] != b"PK\x03\x04":
        print(f"WARN: bad local header for {key}", file=sys.stderr)
        return None
    nlen = int.from_bytes(header[26:28], "little")
    elen = int.from_bytes(header[28:30], "little")
    data_start = local_off + 30 + nlen + elen
    payload = http_range(ACETADA_ZIP_URL, data_start, data_start + comp_size - 1, ctx)
    if method == 8:
        raw = zlib.decompress(payload, -15)
    elif method == 0:
        raw = payload
    else:
        print(f"WARN: unsupported method {method} for {key}", file=sys.stderr)
        return None
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(raw)
    return dest


def build_manifest(
    rows: list[dict[str, str]],
    data_dir: Path,
    out_path: Path,
    *,
    limit: int | None,
    metadata_only: bool,
    members: dict[str, tuple[int, int, int]] | None,
    ctx: ssl.SSLContext | None,
) -> list[Sample]:
    repo_root = _HERE.parents[1]
    samples: list[Sample] = []
    for idx, row in enumerate(rows):
        if limit is not None and len(samples) >= limit:
            break
        items = parse_food_items(row)
        if not items:
            continue
        calories = sum(float(i["calories"]) for i in items)
        protein = sum(float(i["protein_g"]) for i in items)
        carbs = sum(float(i["carbs_g"]) for i in items)
        fat = sum(float(i["fat_g"]) for i in items)
        mass = sum(float(i["before_portion_g"]) for i in items)
        if calories <= 0 and mass <= 0:
            continue

        before_rel = (row.get("before_filepath") or "").strip()
        if not before_rel:
            continue
        meal_type = (row.get("meal_type") or "").strip() or None
        participant = (row.get("participant_id") or "").strip()
        sample_id = f"acetada-{participant}-{Path(before_rel).stem}"

        image_path: str | None = None
        if not metadata_only:
            assert members is not None and ctx is not None
            local = data_dir / before_rel
            extracted = extract_member(before_rel, local, members, ctx)
            if extracted is None:
                continue
            image_path = str(extracted.relative_to(repo_root))

        samples.append(
            Sample(
                id=sample_id,
                modality="image",
                source="acetada",
                image_path=image_path,
                meal_name=meal_type,
                calories=calories,
                protein_g=protein,
                carbs_g=carbs,
                fat_g=fat,
                mass_g=mass,
                notes=(
                    "ACETADA before-meal photo; macros rescaled to served "
                    "(before) mass from dietitian consumed labels; CC BY-NC"
                ),
                extra={
                    "ingredients": [{"name": str(i["name"])} for i in items],
                    "ingredients_served": items,
                    "meal_type": meal_type,
                    "participant_id": participant,
                    "total_kcal_consumed_csv": _f(row, "total_kcal"),
                    "total_portion_g_csv": _f(row, "total_portion_g"),
                    "license": "CC BY-NC 4.0",
                },
            )
        )

    write_manifest(out_path, samples)
    if samples:
        write_image_text_variants(samples, out_path.parent, prefix="acetada")
    return samples


def main() -> None:
    parser = argparse.ArgumentParser(description="Download ACETADA and write L0/L1/L2 manifests")
    parser.add_argument("--limit", type=int, default=50, help="Max before-meal images (default 50)")
    parser.add_argument(
        "--metadata-only",
        action="store_true",
        help="Fetch CSV only; write manifest without downloading images",
    )
    parser.add_argument(
        "--out",
        default=str(DATA_DIR / "manifests" / "acetada.jsonl"),
        help="L0 output manifest path",
    )
    parser.add_argument(
        "--data-dir",
        default=str(DATA_DIR / "acetada"),
        help="Cache directory for CSV + selected JPEGs",
    )
    args = parser.parse_args()

    ctx = _ssl_context()
    data_dir = Path(args.data_dir)
    csv_path = download_csv(data_dir / "ACETADA-HF-dataset.csv", ctx)
    rows = load_rows(csv_path)
    print(f"Loaded {len(rows)} ACETADA meals from CSV")

    members = None
    if not args.metadata_only:
        print("Indexing ZIP central directory for selective image extract...")
        members = find_member_offsets(ctx)
        print(f"Indexed {len(members)} zip members")

    samples = build_manifest(
        rows,
        data_dir,
        Path(args.out),
        limit=args.limit,
        metadata_only=args.metadata_only,
        members=members,
        ctx=None if args.metadata_only else ctx,
    )
    print(f"Wrote {len(samples)} samples to {args.out}")
    print("License: CC BY-NC 4.0 — research only; cite Coburn et al. BHI 2025.")


if __name__ == "__main__":
    main()
