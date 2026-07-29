#!/usr/bin/env python3
"""Grounded-entry eval: tool-loop search_* → finalize → scale macros.

Default path mirrors the app cloud GroundedFoodEntryService tool loop
(search_usda / search_history / search_off / lookup_barcode →
finalize_grounding → PortionResolver-aligned scale). Use --legacy-top1
for the older recognize-JSON → lexical USDA top-1 path.

Example:

  uv run python docs/benchmarks/food_accuracy/run_grounded_eval.py \\
    --provider openrouter --model google/gemini-3.5-flash-lite \\
    --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \\
    --usda-db android/app/src/debug/assets/usda/usda_foods.sqlite \\
    --sleep 6 --retries 2 \\
    --out docs/benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text
"""

from __future__ import annotations

import argparse
import csv
import json
import sqlite3
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from env_local import load_env_local
from grounded_metrics import classify_failure, score_trace
from parse import extract_json_text
from portion_resolve import resolve_portion
from providers import aggregate_usage, build_provider, normalize_usage
from query_normalize import normalize_query, normalize_tokens
from schema import RESULTS_DIR, load_manifest
from score import SampleScore, aggregate_scores, score_sample
from parse import ParsedPrediction

REPO = _HERE.parents[1]
DEFAULT_USDA = REPO / "android" / "app" / "src" / "debug" / "assets" / "usda" / "usda_foods.sqlite"

RECOGNITION_SCHEMA = (
    '{"meal_name":"...","emoji":null,"notes":null,'
    '"components":[{"name":"...","brand":null,"preparation":null,'
    '"estimated_grams":null,"portion_hint":null,"barcode":null,'
    '"quantity":null,"unit":null}]}'
)


def recognition_prompt(description: str) -> str:
    return f"""
Identify the food(s) in this meal for a nutrition database lookup.
Do NOT estimate calories, protein, carbs, fat, or micronutrients.
Focus on identity, brands, preparation, and portion hints.
Respond ONLY with JSON:
{RECOGNITION_SCHEMA}
Rules:
- Split distinct foods into separate components (e.g. eggs + toast + butter).
- estimated_grams is the edible amount in grams when reasonably guessable; else null.
- portion_hint is a short phrase like "1 large egg" or "2 slices".
- unit should be a non-gram household unit when clear (slice, cup, tbsp, piece, ml).
- barcode is digits only when a package barcode is known; else null.
- Use null for unknown optional fields. Keep meal_name short and human-readable.

User description: {description}
""".strip()


def parse_recognition(text: str) -> dict:
    payload = json.loads(extract_json_text(text))
    if not isinstance(payload, dict):
        raise ValueError("recognition_root_not_object")
    meal_name = (payload.get("meal_name") or payload.get("name") or "").strip()
    if not meal_name:
        raise ValueError("missing_meal_name")
    raw_components = payload.get("components") or []
    components: list[dict] = []
    if isinstance(raw_components, list):
        for raw in raw_components:
            if not isinstance(raw, dict):
                continue
            name = str(raw.get("name") or "").strip()
            if not name:
                continue
            grams = raw.get("estimated_grams")
            try:
                grams_f = float(grams) if grams is not None else None
            except (TypeError, ValueError):
                grams_f = None
            try:
                qty_f = float(raw["quantity"]) if raw.get("quantity") is not None else None
            except (TypeError, ValueError):
                qty_f = None
            components.append(
                {
                    "name": name,
                    "brand": (str(raw["brand"]).strip() if raw.get("brand") else None),
                    "preparation": (
                        str(raw["preparation"]).strip() if raw.get("preparation") else None
                    ),
                    "estimated_grams": grams_f,
                    "portion_hint": (
                        str(raw["portion_hint"]).strip() if raw.get("portion_hint") else None
                    ),
                    "quantity": qty_f,
                    "unit": (str(raw["unit"]).strip() if raw.get("unit") else None),
                    "barcode": (
                        str(raw["barcode"]).strip() if raw.get("barcode") else None
                    ),
                }
            )
    if not components:
        components = [{"name": meal_name, "estimated_grams": None}]
    return {"meal_name": meal_name, "components": components, "raw": payload}


class UsdaIndex:
    def __init__(self, path: Path):
        if not path.exists():
            raise FileNotFoundError(path)
        self.path = path
        self.conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        self.conn.row_factory = sqlite3.Row
        self.version = self._meta("dataset_version") or "unknown"
        self.food_count = self.conn.execute("SELECT COUNT(*) FROM foods").fetchone()[0]
        self.asset_bytes = path.stat().st_size

    def _meta(self, key: str) -> str | None:
        row = self.conn.execute(
            "SELECT value FROM meta WHERE key = ? LIMIT 1", (key,)
        ).fetchone()
        return None if row is None else row[0]

    def search(self, query: str, limit: int = 6, include_incomplete_energy: bool = False) -> list[dict]:
        tokens = normalize_tokens(query)
        if not tokens:
            return []
        # Prefer FTS when available; fall back to multi-token LIKE.
        rows: list[sqlite3.Row] = []
        try:
            fts_q = " ".join(tokens)
            rows = self.conn.execute(
                """
                SELECT foods.* FROM foods_fts
                JOIN foods ON foods.fdc_id = foods_fts.rowid
                WHERE foods_fts MATCH ?
                LIMIT 80
                """,
                (fts_q,),
            ).fetchall()
        except sqlite3.OperationalError:
            rows = []
        if not rows:
            clauses = []
            params: list[str] = []
            for tok in tokens[:4]:
                like = f"%{tok}%"
                clauses.append("(tokens LIKE ? OR description LIKE ?)")
                params.extend([like, like])
            sql = f"SELECT * FROM foods WHERE {' OR '.join(clauses)} LIMIT 120"
            rows = self.conn.execute(sql, params).fetchall()
        if not rows:
            loose = f"%{normalize_query(query)}%"
            rows = self.conn.execute(
                """
                SELECT * FROM foods
                WHERE description LIKE ? OR tokens LIKE ?
                LIMIT 80
                """,
                (loose, loose),
            ).fetchall()
        scored: list[tuple[float, sqlite3.Row]] = []
        for row in rows:
            if not include_incomplete_energy and row["calories"] is None:
                continue
            score = self._score(tokens, row)
            if score > 0:
                scored.append((score, row))
        scored.sort(key=lambda x: x[0], reverse=True)
        out = []
        for score, row in scored[:limit]:
            out.append(
                {
                    "fdc_id": row["fdc_id"],
                    "source_id": str(row["fdc_id"]),
                    "description": row["description"],
                    "data_type": row["data_type"],
                    "score": score,
                    "calories": row["calories"],
                    "protein": row["protein"],
                    "carbs": row["carbs"],
                    "fat": row["fat"],
                    "serving_grams": row["serving_grams"],
                    "serving_unit": row["serving_unit"] if "serving_unit" in row.keys() else None,
                    "incomplete_energy": row["calories"] is None,
                }
            )
        return out

    def get_by_fdc_id(self, fdc_id: int | str) -> dict | None:
        try:
            fid = int(fdc_id)
        except (TypeError, ValueError):
            return None
        row = self.conn.execute(
            "SELECT * FROM foods WHERE fdc_id = ? LIMIT 1", (fid,)
        ).fetchone()
        if row is None:
            return None
        return {
            "fdc_id": row["fdc_id"],
            "source_id": str(row["fdc_id"]),
            "description": row["description"],
            "data_type": row["data_type"],
            "score": 10.0,
            "calories": row["calories"],
            "protein": row["protein"],
            "carbs": row["carbs"],
            "fat": row["fat"],
            "serving_grams": row["serving_grams"],
            "serving_unit": row["serving_unit"] if "serving_unit" in row.keys() else None,
            "incomplete_energy": row["calories"] is None,
        }

    @staticmethod
    def _score(query_tokens: list[str], row: sqlite3.Row) -> float:
        desc = (row["description"] or "").lower()
        food_tokens = set((row["tokens"] or "").split())
        joined = " ".join(query_tokens)
        score = 0.0
        if desc == joined:
            score += 10.0
        elif desc.startswith(joined):
            score += 6.0
        elif joined in desc:
            score += 3.5
        overlap = sum(1 for t in query_tokens if t in food_tokens)
        score += overlap * 1.5
        score += max(0.0, 2.0 - len(desc) / 80.0)

        data_type = (row["data_type"] or "").lower()
        is_fndds = "fndds" in data_type or "survey" in data_type
        cooked_or_generic = {
            "cooked", "grilled", "steamed", "boiled", "baked", "roasted", "fried",
            "sauteed", "braised", "meal", "plate", "bowl", "lunch", "dinner",
            "breakfast", "snack",
        }
        dry = {"raw", "dry", "dried", "flour", "powder"}
        query_implies_cooked = any(t in cooked_or_generic for t in query_tokens) or all(
            t not in dry for t in query_tokens
        )
        if is_fndds:
            score += 1.0 if query_implies_cooked else 0.35

        score += UsdaIndex._form_adjustment(query_tokens, desc, food_tokens)
        if row["calories"] is None:
            score -= 2.0
        return score

    @staticmethod
    def _form_adjustment(
        query_tokens: list[str], description_lower: str, food_tokens: set[str]
    ) -> float:
        cooked_or_generic = {
            "cooked", "grilled", "steamed", "boiled", "baked", "roasted", "fried",
            "sauteed", "braised", "meal", "plate", "bowl", "lunch", "dinner",
            "breakfast", "snack",
        }
        dry_form = {"flour", "powder", "dry", "dried", "mix"}
        dessert_form = {"pie", "cake", "cookie", "candy"}
        beverage_query = {
            "beer", "wine", "milk", "juice", "soda", "coffee", "tea", "water",
            "shake", "smoothie", "drink", "beverage", "cola",
        }
        beverage_desc = {
            "beer", "wine", "milk", "juice", "soda", "coffee", "tea", "drink",
            "beverage", "cola", "ale", "lager",
        }
        q = set(query_tokens)
        adj = 0.0
        implies_cooked_solid = any(t in cooked_or_generic for t in q) or (
            not (q & beverage_query) and not (q & dry_form)
        )
        desc_dry = any(t in food_tokens or t in description_lower for t in dry_form)
        desc_dessert = any(
            t in food_tokens
            or f" {t}" in description_lower
            or description_lower.startswith(f"{t},")
            or f", {t}" in description_lower
            for t in dessert_form
        )
        query_beverage = any(t in beverage_query for t in q)
        desc_beverage = any(t in food_tokens or t in description_lower for t in beverage_desc)
        if implies_cooked_solid and desc_dry and not (q & dry_form):
            adj -= 2.5
        if not (q & dessert_form) and desc_dessert and implies_cooked_solid:
            adj -= 2.0
        if query_beverage and not desc_beverage:
            adj -= 3.0
        if not query_beverage and any(
            x in description_lower for x in ("beer", "wine", "soda", "cola", "ale", "lager")
        ):
            adj -= 2.0
        if any(t in {"raw", "fresh"} for t in q) and "cooked" in description_lower:
            adj -= 1.0
        if "cooked" in q and ("raw" in description_lower or desc_dry):
            adj -= 1.5
        return adj

    def close(self) -> None:
        self.conn.close()


SYSTEM_TOOL_PROMPT = """
You ground a meal for a calorie tracker using tools only.
Rules:
1. Call search_usda (and search_history / search_off when useful) before picking any source_id.
2. Prefer survey_fndds_food / FNDDS rows for cooked or generic meals; avoid flour, powder, dry, pie, or dessert false friends unless the user text says so.
3. For branded / packaged products, prefer search_off (or lookup_barcode when digits are known); keep USDA for generic/cooked foods.
4. Split multi-item meals into separate components; each needs its own source_id or reject_to_estimate.
5. Never invent calories, protein, carbs, or fat — only choose among tool results.
6. Set grams to the edible amount when reasonably clear; otherwise omit grams and set quantity/unit when known.
7. If no good match exists, set reject_to_estimate=true.
8. When done, you MUST call finalize_grounding with meal_name and components.
9. Only use source_id values returned by tools in this conversation.
""".strip()

TOOL_SCHEMAS = [
    {
        "name": "search_usda",
        "description": "Search the offline USDA Foundation + FNDDS food index.",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string"},
                "limit": {"type": "integer"},
            },
            "required": ["query"],
        },
    },
    {
        "name": "search_history",
        "description": "Search confirmed diary/favorites identity matches (eval stub: empty).",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string"},
                "limit": {"type": "integer"},
            },
            "required": ["query"],
        },
    },
    {
        "name": "search_off",
        "description": "Search Open Food Facts by product/brand name for packaged foods.",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string"},
                "brand": {"type": "string"},
                "limit": {"type": "integer"},
            },
            "required": ["query"],
        },
    },
    {
        "name": "lookup_barcode",
        "description": "Look up a packaged product by barcode digits via Open Food Facts.",
        "parameters": {
            "type": "object",
            "properties": {
                "barcode": {"type": "string"},
            },
            "required": ["barcode"],
        },
    },
    {
        "name": "finalize_grounding",
        "description": "Submit meal_name and components with source_id from search results.",
        "parameters": {
            "type": "object",
            "properties": {
                "meal_name": {"type": "string"},
                "emoji": {"type": "string"},
                "components": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "brand": {"type": "string"},
                            "source_id": {"type": "string"},
                            "source_kind": {"type": "string"},
                            "grams": {"type": "number"},
                            "quantity": {"type": "number"},
                            "unit": {"type": "string"},
                            "portion_hint": {"type": "string"},
                            "reject_to_estimate": {"type": "boolean"},
                        },
                        "required": ["name"],
                    },
                },
            },
            "required": ["meal_name", "components"],
        },
    },
]


def grounding_user_message(description: str) -> str:
    return (
        "Ground this meal. Search databases, then call finalize_grounding.\n\n"
        f"User description: {description}"
    )


def load_off_fixtures(path: Path | None) -> dict[str, list[dict]]:
    if path is None or not path.exists():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"off fixtures must be a JSON object: {path}")
    out: dict[str, list[dict]] = {}
    for key, hits in raw.items():
        if isinstance(hits, list):
            out[str(key)] = [h for h in hits if isinstance(h, dict)]
    return out


def _off_hit_to_candidate(hit: dict) -> dict:
    """Normalize fixture / tool hit to scale_macros-compatible candidate."""
    cal = hit.get("calories_per_100g", hit.get("calories"))
    return {
        "fdc_id": hit.get("source_id"),
        "source_id": str(hit.get("source_id") or ""),
        "description": hit.get("description") or hit.get("name") or "OFF product",
        "score": float(hit.get("score") or 10.0),
        "calories": cal,
        "protein": hit.get("protein_per_100g", hit.get("protein")),
        "carbs": hit.get("carbs_per_100g", hit.get("carbs")),
        "fat": hit.get("fat_per_100g", hit.get("fat")),
        "serving_grams": hit.get("serving_grams"),
        "serving_unit": hit.get("serving_unit"),
        "source_kind": "openFoodFacts",
        "incomplete_energy": cal is None,
    }


def make_tool_executor(usda: UsdaIndex, off_fixtures: dict[str, list[dict]] | None = None):
    fixtures = off_fixtures or {}
    # barcode / source_id → candidate (populated when search_off / lookup returns hits)
    off_by_id: dict[str, dict] = {}
    for hits in fixtures.values():
        for hit in hits:
            cand = _off_hit_to_candidate(hit)
            if cand["source_id"]:
                off_by_id[cand["source_id"]] = cand
    stats = {
        "search_usda_count": 0,
        "search_history_count": 0,
        "search_off_count": 0,
        "off_fixture_hits": 0,
    }

    def _fixture_hits_for_query(query: str, brand: str | None, limit: int) -> list[dict]:
        q = query.strip()
        keys = []
        if brand:
            keys.append(f"{brand} {q}".strip())
            keys.append(brand.strip())
        keys.append(q)
        # Case-insensitive exact key match, then substring
        lower_map = {k.lower(): k for k in fixtures}
        for key in keys:
            if key in fixtures:
                return fixtures[key][:limit]
            lk = key.lower()
            if lk in lower_map:
                return fixtures[lower_map[lk]][:limit]
        for fk, hits in fixtures.items():
            if q.lower() in fk.lower() or fk.lower() in q.lower():
                return hits[:limit]
        return []

    def execute(name: str, args: dict) -> str:
        if name == "search_usda":
            stats["search_usda_count"] += 1
            query = str(args.get("query") or "").strip()
            limit = int(args.get("limit") or 6)
            limit = max(1, min(8, limit))
            hits = usda.search(query, limit=limit)
            payload = {
                "query": query,
                "results": [
                    {
                        "source_kind": "usda",
                        "source_id": h["source_id"],
                        "description": h["description"],
                        "data_type": h.get("data_type"),
                        "score": round(float(h["score"]), 1),
                        "calories_per_100g": h["calories"],
                        "protein_per_100g": h["protein"],
                        "carbs_per_100g": h["carbs"],
                        "fat_per_100g": h["fat"],
                        "serving_grams": h.get("serving_grams"),
                        "serving_unit": h.get("serving_unit"),
                    }
                    for h in hits
                ],
                "hint": (
                    "Prefer survey_fndds_food for cooked/generic meals; "
                    "avoid flour/powder/dry/pie unless the query says so."
                ),
            }
            return json.dumps(payload)
        if name == "search_history":
            stats["search_history_count"] += 1
            query = str(args.get("query") or "").strip()
            return json.dumps(
                {
                    "query": query,
                    "results": [],
                    "hint": "Eval harness has no diary history pool.",
                }
            )
        if name == "search_off":
            stats["search_off_count"] += 1
            query = str(args.get("query") or "").strip()
            brand = args.get("brand")
            brand_s = str(brand).strip() if brand else None
            limit = max(1, min(8, int(args.get("limit") or 6)))
            raw_hits = _fixture_hits_for_query(query, brand_s, limit)
            results = []
            for hit in raw_hits:
                cand = _off_hit_to_candidate(hit)
                if cand["source_id"]:
                    off_by_id[cand["source_id"]] = cand
                results.append(
                    {
                        "source_kind": "openFoodFacts",
                        "source_id": cand["source_id"],
                        "description": cand["description"],
                        "brand": hit.get("brand"),
                        "score": cand["score"],
                        "calories_per_100g": cand["calories"],
                        "protein_per_100g": cand["protein"],
                        "carbs_per_100g": cand["carbs"],
                        "fat_per_100g": cand["fat"],
                        "serving_grams": cand.get("serving_grams"),
                        "serving_unit": cand.get("serving_unit"),
                    }
                )
            if results:
                stats["off_fixture_hits"] += 1
            return json.dumps(
                {
                    "query": query,
                    "brand": brand_s,
                    "results": results,
                    "hint": (
                        "Packaged/branded: pick source_id (barcode) from results. "
                        if results
                        else "No OFF fixture for this query; try search_usda or reject_to_estimate."
                    ),
                }
            )
        if name == "lookup_barcode":
            code = str(args.get("barcode") or "").strip()
            cand = off_by_id.get(code)
            if cand is None:
                for hits in fixtures.values():
                    for hit in hits:
                        if str(hit.get("source_id")) == code:
                            cand = _off_hit_to_candidate(hit)
                            off_by_id[code] = cand
                            break
            if cand is None:
                return json.dumps({"found": False, "barcode": code})
            return json.dumps(
                {
                    "found": True,
                    "barcode": code,
                    "result": {
                        "source_kind": "openFoodFacts",
                        "source_id": cand["source_id"],
                        "description": cand["description"],
                        "calories_per_100g": cand["calories"],
                        "protein_per_100g": cand["protein"],
                        "carbs_per_100g": cand["carbs"],
                        "fat_per_100g": cand["fat"],
                        "serving_grams": cand.get("serving_grams"),
                    },
                }
            )
        if name == "finalize_grounding":
            return json.dumps({"ok": True, "message": "finalized"})
        return json.dumps({"error": f"unknown tool {name}"})

    execute.stats = stats  # type: ignore[attr-defined]
    execute.off_by_id = off_by_id  # type: ignore[attr-defined]
    return execute


def resolve_from_finalize(
    sample,
    *,
    finalize: dict,
    usda: UsdaIndex,
    off_by_id: dict[str, dict] | None = None,
) -> dict:
    components_raw = finalize.get("components") or []
    components_out = []
    totals = {"calories": 0.0, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0, "grams": 0.0}
    sources: list[str] = []
    top_candidates: list[dict] = []
    unresolved_portion = False
    off_index = off_by_id or {}

    for comp in components_raw:
        if not isinstance(comp, dict):
            continue
        name = str(comp.get("name") or "").strip() or "food"
        reject = bool(comp.get("reject_to_estimate") or comp.get("rejectToEstimate"))
        source_id = comp.get("source_id") or comp.get("sourceId")
        source_kind_raw = str(comp.get("source_kind") or comp.get("sourceKind") or "").lower()
        grams_override = comp.get("grams")
        try:
            grams_override_f = float(grams_override) if grams_override is not None else None
        except (TypeError, ValueError):
            grams_override_f = None
        qty = comp.get("quantity")
        try:
            qty_f = float(qty) if qty is not None else None
        except (TypeError, ValueError):
            qty_f = None
        unit = comp.get("unit")
        portion_hint = comp.get("portion_hint") or comp.get("portionHint")
        est = comp.get("estimated_grams") or comp.get("estimatedGrams")
        try:
            est_f = float(est) if est is not None else None
        except (TypeError, ValueError):
            est_f = None

        selected = None
        candidates: list[dict] = []
        selected_kind = "usda"
        if not reject and source_id is not None:
            sid = str(source_id)
            is_off = (
                source_kind_raw in {"openfoodfacts", "open_food_facts", "off"}
                or sid in off_index
            )
            if is_off and sid in off_index:
                selected = off_index[sid]
                selected_kind = "openFoodFacts"
                candidates = [selected]
                top_candidates.extend(candidates[:3])
            else:
                selected = usda.get_by_fdc_id(source_id)
                if selected and selected.get("incomplete_energy"):
                    selected = None
                candidates = usda.search(name, limit=6)
                top_candidates.extend(candidates[:3])
                selected_kind = "usda"

        portion = resolve_portion(
            quantity=qty_f,
            unit=str(unit) if unit else None,
            estimated_grams=est_f,
            portion_hint=str(portion_hint) if portion_hint else None,
            gram_override=grams_override_f,
            candidate_serving_grams=(
                float(selected["serving_grams"])
                if selected and selected.get("serving_grams")
                else None
            ),
            candidate_serving_unit=(
                str(selected["serving_unit"])
                if selected and selected.get("serving_unit")
                else None
            ),
        )
        grams_f = portion["grams"]
        portion_source = portion["source"]
        if grams_f is None or grams_f <= 0:
            unresolved_portion = True
            sources.append("modelEstimate" if selected is None else selected_kind)
            components_out.append(
                {
                    "name": name,
                    "source_kind": "modelEstimate" if selected is None else selected_kind,
                    "source_id": (
                        (selected.get("source_id") or selected.get("fdc_id"))
                        if selected
                        else None
                    ),
                    "matched": selected["description"] if selected else None,
                    "grams": None,
                    "portion_source": "unresolved",
                    "portion_unresolved": True,
                }
            )
            continue

        if selected is None:
            sources.append("modelEstimate")
            components_out.append(
                {
                    "name": name,
                    "source_kind": "modelEstimate",
                    "grams": grams_f,
                    "portion_source": portion_source,
                    "matched": None,
                }
            )
            continue

        macros = scale_macros(selected, grams_f)
        for k in ("calories", "protein_g", "carbs_g", "fat_g", "grams"):
            totals[k] += macros[k]
        sources.append(selected_kind)
        components_out.append(
            {
                "name": name,
                "source_kind": selected_kind,
                "source_id": selected.get("source_id") or selected.get("fdc_id"),
                "matched": selected["description"],
                "match_score": selected.get("score"),
                "grams": grams_f,
                "portion_source": portion_source,
                "macros": macros,
                "candidates": [
                    {
                        "fdc_id": c.get("fdc_id") or c.get("source_id"),
                        "description": c["description"],
                        "score": c.get("score"),
                    }
                    for c in candidates[:3]
                ],
            }
        )

    primary = max(set(sources), key=sources.count) if sources else "modelEstimate"
    grounded_ok = any(
        c.get("source_kind") in {"usda", "openFoodFacts"} and c.get("grams")
        for c in components_out
    )
    if unresolved_portion or not grounded_ok:
        pred = ParsedPrediction(ok=False, error="unresolved_portion")
        parse_blocker = "unresolved_portion" if unresolved_portion else "no_db_match"
    else:
        pred = ParsedPrediction(
            ok=True,
            calories=totals["calories"],
            protein_g=totals["protein_g"],
            carbs_g=totals["carbs_g"],
            fat_g=totals["fat_g"],
            serving_size_grams=totals["grams"],
        )
        parse_blocker = None
    return {
        "components": components_out,
        "primary_source": primary,
        "prediction": pred,
        "top_candidates": top_candidates,
        "totals": totals,
        "meal_name": finalize.get("meal_name") or finalize.get("mealName") or "meal",
        "unresolved_portion": unresolved_portion,
        "parse_blocker": parse_blocker,
    }


def scale_macros(candidate: dict, grams: float) -> dict[str, float]:
    scale = grams / 100.0

    def n(key: str) -> float:
        v = candidate.get(key)
        return float(v) * scale if v is not None else 0.0

    return {
        "calories": round(n("calories")),
        "protein_g": n("protein"),
        "carbs_g": n("carbs"),
        "fat_g": n("fat"),
        "grams": grams,
    }


def identity_hit(gt_text: str, candidate_name: str | None) -> bool:
    if not candidate_name:
        return False
    gt_toks = set(normalize_tokens(gt_text))
    cand_toks = set(normalize_tokens(candidate_name, strip_units=False))
    if not gt_toks or not cand_toks:
        return False
    overlap = len(gt_toks & cand_toks) / len(gt_toks)
    return overlap >= 0.5


def resolve_sample(
    sample,
    *,
    recognition: dict,
    usda: UsdaIndex,
) -> dict:
    components_out = []
    totals = {"calories": 0.0, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0, "grams": 0.0}
    sources: list[str] = []
    top_candidates: list[dict] = []
    unresolved_portion = False

    for comp in recognition["components"]:
        query = " ".join(
            x for x in [comp.get("brand"), comp["name"], comp.get("preparation")] if x
        )
        candidates = usda.search(query, limit=6)
        top_candidates.extend(candidates[:3])
        selected = candidates[0] if candidates else None
        portion = resolve_portion(
            quantity=comp.get("quantity"),
            unit=comp.get("unit"),
            estimated_grams=comp.get("estimated_grams"),
            portion_hint=comp.get("portion_hint"),
            candidate_serving_grams=(
                float(selected["serving_grams"])
                if selected and selected.get("serving_grams")
                else None
            ),
            candidate_serving_unit=(
                str(selected["serving_unit"])
                if selected and selected.get("serving_unit")
                else None
            ),
        )
        grams = portion["grams"]
        portion_source = portion["source"]
        if grams is None or grams <= 0:
            unresolved_portion = True
            sources.append("modelEstimate" if selected is None else "usda")
            components_out.append(
                {
                    "name": comp["name"],
                    "source_kind": "modelEstimate" if selected is None else "usda",
                    "grams": None,
                    "portion_source": "unresolved",
                    "portion_unresolved": True,
                    "matched": selected["description"] if selected else None,
                }
            )
            continue

        if selected is None:
            sources.append("modelEstimate")
            components_out.append(
                {
                    "name": comp["name"],
                    "source_kind": "modelEstimate",
                    "grams": grams,
                    "portion_source": portion_source,
                    "matched": None,
                }
            )
            continue

        macros = scale_macros(selected, grams)
        for k in ("calories", "protein_g", "carbs_g", "fat_g", "grams"):
            totals[k] += macros[k]
        sources.append("usda")
        components_out.append(
            {
                "name": comp["name"],
                "source_kind": "usda",
                "source_id": selected["fdc_id"],
                "matched": selected["description"],
                "match_score": selected["score"],
                "grams": grams,
                "portion_source": portion_source,
                "macros": macros,
                "candidates": [
                    {"fdc_id": c["fdc_id"], "description": c["description"], "score": c["score"]}
                    for c in candidates[:3]
                ],
            }
        )

    primary = max(set(sources), key=sources.count) if sources else "modelEstimate"
    if unresolved_portion or not any(
        c.get("source_kind") == "usda" and c.get("grams") for c in components_out
    ):
        pred = ParsedPrediction(ok=False, error="unresolved_portion")
        parse_blocker = "unresolved_portion"
    else:
        pred = ParsedPrediction(
            ok=True,
            calories=totals["calories"],
            protein_g=totals["protein_g"],
            carbs_g=totals["carbs_g"],
            fat_g=totals["fat_g"],
            serving_size_grams=totals["grams"],
        )
        parse_blocker = None
    return {
        "components": components_out,
        "primary_source": primary,
        "prediction": pred,
        "top_candidates": top_candidates,
        "totals": totals,
        "unresolved_portion": unresolved_portion,
        "parse_blocker": parse_blocker,
    }


def _write_summary_csv(path: Path, summary: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary.keys()))
        writer.writeheader()
        writer.writerow(summary)


def main() -> None:
    load_env_local()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", default="openrouter")
    parser.add_argument("--model", default="google/gemini-3.5-flash-lite")
    parser.add_argument(
        "--manifest",
        type=Path,
        default=_HERE / "manifest" / "eval_text.jsonl",
    )
    parser.add_argument("--usda-db", type=Path, default=DEFAULT_USDA)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--sleep", type=float, default=6.0)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--resume", type=Path, default=None)
    parser.add_argument(
        "--legacy-top1",
        action="store_true",
        help="Use recognize JSON + lexical USDA top-1 instead of the tool loop",
    )
    parser.add_argument(
        "--off-fixtures",
        type=Path,
        default=None,
        help="JSON map of query → OFF search hits for offline search_off (branded readiness)",
    )
    args = parser.parse_args()

    samples = load_manifest(args.manifest)
    if args.limit is not None:
        samples = samples[: args.limit]

    out_dir = args.out or (
        RESULTS_DIR / f"grounded_{args.model.replace('/', '_').replace(':', '_')}"
    )
    out_dir.mkdir(parents=True, exist_ok=True)
    samples_path = out_dir / "samples.jsonl"
    summary_path = out_dir / "summary.csv"
    grounded_summary_path = out_dir / "grounded_summary.json"

    existing: dict[str, dict] = {}
    if args.resume:
        resume_path = args.resume / "samples.jsonl" if args.resume.is_dir() else args.resume
        if resume_path.exists():
            for line in resume_path.read_text(encoding="utf-8").splitlines():
                if not line.strip():
                    continue
                row = json.loads(line)
                if row.get("parse_ok"):
                    existing[str(row["id"])] = row
            print(f"Resuming with {len(existing)} ok samples from {resume_path}")

    provider = build_provider(args.provider, model=args.model)
    usda = UsdaIndex(args.usda_db)
    off_fixtures = load_off_fixtures(args.off_fixtures)
    tool_executor = make_tool_executor(usda, off_fixtures)
    if off_fixtures:
        print(f"OFF fixtures: {len(off_fixtures)} queries from {args.off_fixtures}")
    usda_version = usda.version
    usda_food_count = usda.food_count
    usda_asset_bytes = usda.asset_bytes
    print(
        f"USDA index: {usda_food_count} foods, version={usda_version}, "
        f"bytes={usda_asset_bytes}"
    )

    ordered_ids = [s.id for s in samples]
    records = dict(existing)
    usages: list[dict | None] = []
    todo = [s for s in samples if s.id not in records]
    print(f"Evaluating {len(todo)} / {len(samples)} samples → {out_dir}")

    try:
        for i, sample in enumerate(todo, start=1):
            print(f"[{i}/{len(todo)}] {sample.id} ...", end="", flush=True)
            last_error: Exception | None = None
            response = None
            finalize = None
            tool_stats: dict = {}
            recognition = None
            for attempt in range(args.retries + 1):
                try:
                    if args.legacy_top1:
                        response = provider.complete(
                            prompt=recognition_prompt(sample.text or ""),
                            image_path=None,
                        )
                    else:
                        if not hasattr(provider, "complete_with_tools"):
                            raise RuntimeError(
                                f"Provider {args.provider} does not support tool loops; "
                                "use OpenRouter/OpenAI or --legacy-top1"
                            )
                        response, finalize, tool_stats = provider.complete_with_tools(
                            system=SYSTEM_TOOL_PROMPT,
                            user=grounding_user_message(sample.text or ""),
                            tools=TOOL_SCHEMAS,
                            execute_tool=tool_executor,
                            max_rounds=4,
                        )
                        # Merge harness executor counters (provider may only return rounds).
                        exec_stats = getattr(tool_executor, "stats", {}) or {}
                        tool_stats = {
                            **exec_stats,
                            **(tool_stats or {}),
                        }
                    break
                except Exception as exc:  # noqa: BLE001
                    last_error = exc
                    msg = str(exc)
                    retryable = "429" in msg or "rate-limited" in msg or "502" in msg
                    if attempt < args.retries and retryable:
                        wait = min(60.0, 5.0 * (2**attempt))
                        print(f" retryable ({exc}); sleep {wait:.0f}s ...", end="", flush=True)
                        time.sleep(wait)
                        continue
                    break

            if response is None:
                scored = SampleScore(id=sample.id, parse_ok=False, error=str(last_error))
                record = {
                    "id": sample.id,
                    "parse_ok": False,
                    "error": str(last_error),
                    "score": scored.to_dict(),
                    "mode": "grounded_tool" if not args.legacy_top1 else "grounded",
                }
                records[sample.id] = record
                print(f" fail {last_error}")
            else:
                try:
                    if args.legacy_top1:
                        recognition = parse_recognition(response.text)
                        resolved = resolve_sample(sample, recognition=recognition, usda=usda)
                    else:
                        if finalize is None:
                            raise ValueError("no_finalize_grounding")
                        resolved = resolve_from_finalize(
                            sample,
                            finalize=finalize,
                            usda=usda,
                            off_by_id=getattr(tool_executor, "off_by_id", {}),
                        )
                        recognition = {
                            "meal_name": resolved.get("meal_name"),
                            "components": [
                                {"name": c["name"], "estimated_grams": c.get("grams")}
                                for c in resolved["components"]
                            ],
                        }
                    pred = resolved["prediction"]
                    if resolved.get("parse_blocker") == "unresolved_portion":
                        scored = SampleScore(
                            id=sample.id,
                            parse_ok=False,
                            error="unresolved_portion",
                        )
                        parse_ok = False
                    elif all(
                        c["source_kind"] == "modelEstimate" for c in resolved["components"]
                    ):
                        scored = SampleScore(
                            id=sample.id,
                            parse_ok=False,
                            error="no_db_match",
                        )
                        parse_ok = False
                    else:
                        scored = score_sample(sample.id, sample.ground_truth(), pred)
                        parse_ok = scored.parse_ok

                    matched_name = next(
                        (
                            c.get("matched")
                            for c in resolved["components"]
                            if c.get("matched")
                        ),
                        None,
                    )
                    identity_top1 = identity_hit(sample.text or "", matched_name)
                    topk = None
                    for rank, cand in enumerate(resolved["top_candidates"], start=1):
                        desc = cand.get("description") if isinstance(cand, dict) else None
                        if identity_hit(sample.text or "", desc):
                            topk = rank
                            break

                    gram_error = None
                    if sample.mass_g and pred.serving_size_grams is not None:
                        gram_error = pred.serving_size_grams - float(sample.mass_g)

                    nutrient_wmape = None
                    if scored.parse_ok and scored.abs_error_sum is not None and scored.gt_sum:
                        nutrient_wmape = scored.abs_error_sum / scored.gt_sum

                    silent_zero = bool(
                        parse_ok
                        and pred.calories == 0
                        and (sample.ground_truth().get("calories") or 0) > 0
                    )
                    slice_tag = sample.extra.get("slice") if hasattr(sample, "extra") else None
                    grounded_row = {
                        "id": sample.id,
                        "slice": slice_tag,
                        "identity_top1": identity_top1,
                        "identity_topk": topk,
                        "source_kind": resolved["primary_source"],
                        "gram_error": gram_error,
                        "nutrient_wmape": nutrient_wmape,
                        "user_corrected": False,
                        "latency_ms": response.latency_ms,
                        "asset_bytes": usda_asset_bytes,
                        "tool_rounds": tool_stats.get("rounds"),
                        "search_usda_count": tool_stats.get("search_usda_count"),
                        "search_off_count": tool_stats.get("search_off_count"),
                        "parse_ok": parse_ok,
                        "error": scored.error,
                        "silent_zero": silent_zero,
                        "fallback": resolved["primary_source"] == "modelEstimate",
                    }
                    grounded_row["failure_class"] = classify_failure(grounded_row)

                    record = {
                        "id": sample.id,
                        "modality": sample.modality,
                        "mode": "grounded_tool" if not args.legacy_top1 else "grounded",
                        "parse_ok": parse_ok,
                        "prompt": "tool_loop" if not args.legacy_top1 else "recognize_food",
                        "provider": args.provider,
                        "model": response.model,
                        "latency_ms": response.latency_ms,
                        "usage": normalize_usage(response.usage),
                        "recognition": {
                            "meal_name": recognition["meal_name"] if recognition else None,
                            "components": recognition["components"] if recognition else None,
                        },
                        "finalize": finalize,
                        "tool_stats": tool_stats,
                        "resolution": resolved["components"],
                        "prediction": {
                            "calories": pred.calories,
                            "protein_g": pred.protein_g,
                            "carbs_g": pred.carbs_g,
                            "fat_g": pred.fat_g,
                            "serving_size_grams": pred.serving_size_grams,
                        },
                        "grounded": grounded_row,
                        "score": scored.to_dict(),
                        "raw_text": response.text,
                        "usda_version": usda_version,
                        "ts": datetime.now(timezone.utc).isoformat(),
                    }
                    if not parse_ok and scored.error:
                        record["error"] = scored.error
                    records[sample.id] = record
                    usages.append(response.usage)
                    cal = scored.mape_calories
                    cal_s = f"{cal*100:.2f}%" if cal is not None and cal != float("inf") else "n/a"
                    print(
                        f" ok src={resolved['primary_source']} "
                        f"id1={identity_top1} cal_mape={cal_s} "
                        f"latency={response.latency_ms:.0f}ms"
                        f" rounds={tool_stats.get('rounds', '-')}"
                    )
                except Exception as exc:  # noqa: BLE001
                    scored = SampleScore(id=sample.id, parse_ok=False, error=str(exc))
                    records[sample.id] = {
                        "id": sample.id,
                        "parse_ok": False,
                        "error": str(exc),
                        "mode": "grounded_tool" if not args.legacy_top1 else "grounded",
                        "raw_text": response.text if response else None,
                        "finalize": finalize,
                        "score": scored.to_dict(),
                    }
                    print(f" fail {exc}")

            with samples_path.open("w", encoding="utf-8") as handle:
                for sid in ordered_ids:
                    if sid in records:
                        handle.write(json.dumps(records[sid], ensure_ascii=False) + "\n")

            if i < len(todo) and args.sleep > 0:
                time.sleep(args.sleep)
    finally:
        usda.close()

    all_scores: list[SampleScore] = []
    all_grounded: list[dict] = []
    for sid in ordered_ids:
        row = records.get(sid)
        if not row:
            continue
        sc = row.get("score") or {}
        all_scores.append(
            SampleScore(
                id=sid,
                parse_ok=bool(row.get("parse_ok")),
                mae_calories=sc.get("mae_calories"),
                mae_protein_g=sc.get("mae_protein_g"),
                mae_carbs_g=sc.get("mae_carbs_g"),
                mae_fat_g=sc.get("mae_fat_g"),
                mape_calories=sc.get("mape_calories"),
                mape_protein_g=sc.get("mape_protein_g"),
                mape_carbs_g=sc.get("mape_carbs_g"),
                mape_fat_g=sc.get("mape_fat_g"),
                within_20pct_calories=sc.get("within_20pct_calories"),
                abs_error_sum=sc.get("abs_error_sum"),
                gt_sum=sc.get("gt_sum"),
                error=sc.get("error") or row.get("error"),
            )
        )
        if row.get("grounded"):
            all_grounded.append(row["grounded"])

    summary = aggregate_scores(all_scores).to_dict()
    summary.update(
        {
            "mode": "grounded",
            "provider": args.provider,
            "model": args.model,
            "manifest": str(args.manifest),
            "usda_db": str(args.usda_db),
            "usda_version": usda_version,
            "usda_food_count": usda_food_count,
            "n_samples": len(all_scores),
        }
    )
    usage_agg = aggregate_usage(usages)
    for k, v in usage_agg.items():
        summary[f"usage_{k}"] = v

    grounded_summary = score_trace(all_grounded)
    latencies = [
        records[sid]["latency_ms"]
        for sid in ordered_ids
        if sid in records and records[sid].get("latency_ms") is not None
    ]
    if latencies:
        summary["mean_latency_ms"] = sum(latencies) / len(latencies)
        grounded_summary["mean_latency_ms"] = summary["mean_latency_ms"]
    grounded_summary["macro"] = {
        "parse_ok_rate": summary.get("parse_ok_rate"),
        "wmape": summary.get("wmape"),
        "mae_calories": summary.get("mae_calories"),
        "within_20pct_calories_rate": summary.get("within_20pct_calories_rate"),
    }

    _write_summary_csv(summary_path, summary)
    grounded_summary_path.write_text(
        json.dumps(grounded_summary, indent=2) + "\n", encoding="utf-8"
    )
    print(f"\nWrote {samples_path}")
    print(f"Wrote {summary_path}")
    print(f"Wrote {grounded_summary_path}")
    print(json.dumps(grounded_summary, indent=2))


if __name__ == "__main__":
    main()
