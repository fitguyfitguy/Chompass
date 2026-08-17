#!/usr/bin/env python3
"""Guard against hardcoded user-facing strings in Kotlin UI code.

Recurring i18n fixes (serving unit label, "Add next ingredient", "Loading
chart...") came from app-generated strings built as Kotlin literals instead of
resources. This check scans the Android sources for string literals passed to
user-facing composable parameters and fails on new prose literals.

Heuristic: a literal is "user-facing prose" when it contains a space AND at
least one lowercase letter AND is not on the allowlist (symbols, animation
labels, format templates, debug-only code). Single-word literals (animation
labels like "typingScale") and pure symbols (bullet, middot) are ignored.

Run from repo root: uv run python scripts/check_hardcoded_strings.py
Exit 0 = clean, 1 = violations found.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android" / "app" / "src" / "main" / "java"

# Parameters that render text to the user. Animation labels and debug tags use
# the same `label =` syntax, so the prose heuristic (space + lowercase) is what
# separates them.
USER_FACING_PARAMS = re.compile(
    r'\b(text|label|title|hint|placeholder|contentDescription|subtitle|message)\s*=\s*"'
)

# Literals that are legitimately not translatable prose.
ALLOWLIST = {
    # Symbols / separators
    "•", "·", "-", "/", "—", "…", "🍽", "····",
    # Units / short labels
    "kcal", "g", "ml", "kg",
}

# Debug-only / non-UI files that may contain prose for tooling.
SKIP_PATHS = (
    "/debug/",
    "/test/",
    "MainActivityDebugExtras",
)

# Object declarations whose bodies are test-only legacy code (production path
# lives elsewhere). The messages inside are user-facing English but unreachable
# from the app; localizing them would be dead work. Keep this list tiny and
# documented.
SKIP_OBJECTS = {
    # Production path is AdaptiveGoalsService (AdaptiveGoalsService.kt); this
    # object is exercised only by WeightAnalysisServiceTest.
    "object AdaptiveGoalService {",
}


def is_prose(literal: str) -> bool:
    """True when the literal looks like user-facing prose (space + lowercase)."""
    if literal in ALLOWLIST:
        return False
    if not literal:
        return False
    if " " not in literal:
        return False
    if not any(c.islower() for c in literal):
        return False
    # Pure template with placeholders only (e.g. "${x} kcal") is data, not prose.
    stripped = re.sub(r"\$\{[^}]*\}", "", literal).strip()
    if not stripped:
        return False
    return True


def main() -> int:
    violations: list[tuple[str, int, str]] = []
    for path in sorted(SRC.rglob("*.kt")):
        rel = str(path.relative_to(ROOT))
        if any(s in rel for s in SKIP_PATHS):
            continue
        in_skipped_object = False
        skip_depth = 0
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if in_skipped_object:
                skip_depth += line.count("{") - line.count("}")
                if skip_depth <= 0:
                    in_skipped_object = False
                continue
            if any(s in line for s in SKIP_OBJECTS):
                in_skipped_object = True
                skip_depth = 1
                continue
            for m in USER_FACING_PARAMS.finditer(line):
                # The param regex consumed the opening quote; capture up to the
                # next unescaped closing quote.
                rest = line[m.end():]
                lit = re.match(r'(?:[^"\\]|\\.)*"', rest)
                if not lit:
                    continue
                value = lit.group(0)[:-1]
                # Templates with nested string literals (e.g. "${x ?: "🍽"}")
                # break naive extraction; they are data templates, not prose.
                if value.count('"') % 2 == 1:
                    continue
                if "${" in value and "}" not in value:
                    continue
                if is_prose(value):
                    violations.append((rel, lineno, value))

    if violations:
        print("Hardcoded user-facing strings (move to res/values/strings.xml):", file=sys.stderr)
        for rel, lineno, value in violations:
            print(f"  {rel}:{lineno}: {value!r}", file=sys.stderr)
        return 1

    print("No hardcoded user-facing strings found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())