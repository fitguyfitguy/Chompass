#!/usr/bin/env python3
"""List / smoke-test the NoFUD free router pool (excludes content-safety)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from env_local import load_env_local, openrouter_api_key
from openrouter_models import (
    NOFUD_FREE_ROUTER_ID,
    eligible_chat_free_models,
    is_excluded_model,
    list_free_models,
)
from providers import NofudFreeRouterProvider


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect NoFUD free-router model pool")
    parser.add_argument("--vision", action="store_true", help="Show vision-capable pool only")
    parser.add_argument("--show-excluded", action="store_true", help="Also list excluded free models")
    parser.add_argument(
        "--smoke",
        action="store_true",
        help="Send one tiny chat via nofud/free and print routed backend",
    )
    args = parser.parse_args()

    load_env_local()
    api_key = openrouter_api_key()
    if not api_key:
        raise SystemExit("OPENROUTER_TOKEN not set")

    pool = eligible_chat_free_models(api_key=api_key, require_vision=args.vision)
    label = "vision" if args.vision else "text+vision"
    print(f"nofud/free eligible pool ({label}): {len(pool)} models\n")
    for m in pool:
        print(f"  {m.id:52} vision={m.supports_vision}")

    if args.show_excluded:
        all_free = list_free_models(
            api_key=api_key,
            include_router=False,
            exclude_filters=False,
        )
        excluded = [
            m
            for m in all_free
            if is_excluded_model(m.id, name=m.name, description=m.description)
        ]
        print(f"\nExcluded free models: {len(excluded)}")
        for m in excluded:
            print(f"  EXCLUDE  {m.id}")

    if args.smoke:
        print(f"\nSmoke {NOFUD_FREE_ROUTER_ID} ...", flush=True)
        provider = NofudFreeRouterProvider(api_key=api_key, failover=3)
        resp = provider.complete(
            prompt='Reply ONLY with JSON: {"calories":100,"protein":10.0,"carbs":5.0,"fat":2.0}'
        )
        print(f"  routed={resp.routed_model} latency={resp.latency_ms:.0f}ms")
        print(f"  text={resp.text[:200]!r}")


if __name__ == "__main__":
    main()
