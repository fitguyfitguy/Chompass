#!/usr/bin/env python3
"""Load repo-root .env.local into os.environ (without overwriting existing vars)."""

from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_env_local(*, repo_root: Path | None = None) -> bool:
    """Parse `export KEY=value` lines from `.env.local`. Returns True if file existed."""
    path = (repo_root or ROOT) / ".env.local"
    if not path.exists():
        return False

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :]
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key and key not in os.environ:
            os.environ[key] = value
    return True


def openrouter_api_key() -> str:
    return os.environ.get("OPENROUTER_TOKEN") or os.environ.get("OPENROUTER_API_KEY") or ""
