#!/usr/bin/env python3
"""Print MAE tables from one or more results JSON files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("results", nargs="+", type=Path)
    args = parser.parse_args()
    print(f"{'file':<40} {'method':<36} {'n_pred':>6} {'MAE':>8} {'RMSE':>8} {'bias':>8} {'parse':>6}")
    for path in args.results:
        data = json.loads(path.read_text(encoding="utf-8"))
        for name, table in data.get("tables", {}).items():
            ov = table.get("overall", {})
            mae = ov.get("mae")
            rmse = ov.get("rmse")
            bias = ov.get("bias")
            print(
                f"{path.name:<40} {name:<36} {ov.get('n_pred', 0):6} "
                f"{mae if mae is None else f'{mae:8.3f}'} "
                f"{rmse if rmse is None else f'{rmse:8.3f}'} "
                f"{bias if bias is None else f'{bias:8.3f}'} "
                f"{ov.get('parse_rate', 0):6.3f}"
            )
        kill = data.get("kill") or {}
        if kill:
            print("kill:", json.dumps(kill))


if __name__ == "__main__":
    main()
