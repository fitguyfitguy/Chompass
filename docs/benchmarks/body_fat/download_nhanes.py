#!/usr/bin/env python3
"""NHANES adult DXA + body measures join (Track A).

Phase 0 (CDC Examination variable lists, CycleBeginYear 1999, 2001, 2003,
2005, 2007, 2009, 2011, 2013, 2015, 2017, 2019, 2021; plus BMX_G/H/J HTML):

    **No cycle publishes neck circumference (`BMXNECK` or equivalent).**
    Hits on "neck" are femoral-neck DXA (DXXNKA / DXXNKBMD), not tape.

    Therefore Navy is **not scorable on NHANES**. Do not claim
    "Navy validated on NHANES." RFM (height, waist, sex) is.

Hip (`BMXHIP`) exists 2011–2018. Waist (`BMXWAIST`) exists with DXA in
1999–2006 and 2011–2018. This downloader joins 2011–2018 whole-body DXA
(`DXDTOPF`) with DEMO + BMX. Adults only (RIDAGEYR >= 18). Incomplete
DXA rows (missing DXDTOPF) are dropped and counted.
"""

from __future__ import annotations

import argparse
import urllib.request
from pathlib import Path

from schema import DATA_DIR, Subject, write_jsonl

# suffix, years
CYCLES = (
    ("G", "2011-2012"),
    ("H", "2013-2014"),
    ("I", "2015-2016"),
    ("J", "2017-2018"),
)

SITE_INVENTORY = {
    "waist": "BMXWAIST",
    "hip": "BMXHIP",
    "neck": None,  # verified absent
    "arm": "BMXARMC",
    "height": "BMXHT",
    "weight": "BMXWT",
}


def _url(cycle: str, suffix: str, table: str) -> str:
    year = cycle.split("-")[0]
    return (
        "https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/"
        f"{year}/DataFiles/{table}_{suffix}.xpt"
    )


def fetch_xpt(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1000:
        return dest
    print(f"GET {url}")
    urllib.request.urlretrieve(url, dest)
    return dest


def read_xpt(path: Path):
    import pandas as pd

    return pd.read_sas(path, format="xport")


def _sex(code) -> str | None:
    if code == 1:
        return "male"
    if code == 2:
        return "female"
    return None


def _eth(code) -> str | None:
    if code is None or (isinstance(code, float) and code != code):
        return None
    return str(int(code))


def join_cycle(suffix: str, years: str, raw: Path) -> tuple[list[Subject], dict]:
    demo_p = fetch_xpt(_url(years, suffix, "DEMO"), raw / f"DEMO_{suffix}.XPT")
    bmx_p = fetch_xpt(_url(years, suffix, "BMX"), raw / f"BMX_{suffix}.XPT")
    dxx_p = fetch_xpt(_url(years, suffix, "DXX"), raw / f"DXX_{suffix}.XPT")
    demo = read_xpt(demo_p)
    bmx = read_xpt(bmx_p)
    dxx = read_xpt(dxx_p)
    stats = {
        "cycle": years,
        "demo_n": int(len(demo)),
        "bmx_n": int(len(bmx)),
        "dxx_n": int(len(dxx)),
        "bmx_columns_neck_like": [c for c in bmx.columns if "NECK" in str(c).upper()],
        "has_BMXHIP": "BMXHIP" in bmx.columns,
        "has_BMXWAIST": "BMXWAIST" in bmx.columns,
        "has_DXDTOPF": "DXDTOPF" in dxx.columns,
    }
    merged = demo.merge(bmx, on="SEQN", how="inner").merge(dxx, on="SEQN", how="inner")
    dropped_age = 0
    dropped_dxa = 0
    dropped_anthro = 0
    subjects: list[Subject] = []
    source = f"nhanes_{years.replace('-', '_')}"
    for _, row in merged.iterrows():
        age = row.get("RIDAGEYR")
        if age is None or age != age or age < 18:
            dropped_age += 1
            continue
        bf = row.get("DXDTOPF")
        if bf is None or bf != bf:
            dropped_dxa += 1
            continue
        ht = row.get("BMXHT")
        wt = row.get("BMXWT")
        waist = row.get("BMXWAIST")
        if ht != ht or wt != wt or waist != waist or not ht or not wt:
            dropped_anthro += 1
            continue
        sex = _sex(row.get("RIAGENDR"))
        if sex is None:
            continue
        seqn = int(row["SEQN"])
        def _opt(val) -> float | None:
            if val is None:
                return None
            try:
                if val != val:
                    return None
            except TypeError:
                return None
            return float(val)

        subjects.append(
            Subject(
                id=f"nhanes-{years}-{seqn}",
                source=source,
                sex=sex,
                age=int(age),
                height_cm=float(ht),
                weight_kg=float(wt),
                waist_cm=float(waist),
                hips_cm=_opt(row.get("BMXHIP")),
                upper_arm_cm=_opt(row.get("BMXARMC")),
                neck_cm=None,
                bf_percent=float(bf),
                bf_method="dxa",
                ethnicity=_eth(row.get("RIDRETH1")),
            )
        )
    stats["kept"] = len(subjects)
    stats["dropped_age"] = dropped_age
    stats["dropped_dxa"] = dropped_dxa
    stats["dropped_anthro"] = dropped_anthro
    return subjects, stats


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=DATA_DIR / "eval_nhanes.jsonl")
    parser.add_argument("--limit-cycles", type=int, default=None)
    args = parser.parse_args()
    raw = DATA_DIR / "nhanes"
    all_subjects: list[Subject] = []
    cycles = CYCLES[: args.limit_cycles] if args.limit_cycles else CYCLES
    for suffix, years in cycles:
        rows, stats = join_cycle(suffix, years, raw)
        print(stats)
        all_subjects.extend(rows)
    write_jsonl(args.out, all_subjects)
    print(f"wrote {len(all_subjects)} rows -> {args.out}")
    print("Navy scorable: 0 (no neck circumference in any NHANES BMX cycle)")


if __name__ == "__main__":
    main()
