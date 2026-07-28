#!/usr/bin/env python3
"""Prompt templates aligned with NoFUD FoodAnalysisService and research ablations."""

from __future__ import annotations

from clarify import clarify_answer_block

FULL_JSON_SCHEMA = (
    '{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,'
    '"serving_size_grams":0.0,"emoji":"<single specific food emoji>",'
    '"sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,'
    '"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,'
    '"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,'
    '"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,'
    '"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,'
    '"folate":0.0,"omega_3":0.0,"unit_options":[]}'
)

COMPACT_JSON_SCHEMA = (
    '{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,'
    '"serving_size_grams":0.0}'
)

UNIT_RULES = """
The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is obvious.
unit_options is required when the text names an obvious non-gram serving unit, and optional otherwise. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. Do not copy any sample number; use the quantity stated or clearly implied by the meal. Use [] only when no non-gram unit is apparent. Do not include g/grams in unit_options.
""".strip()

IMAGE_UNIT_RULES = """
unit_options is required for obvious non-gram units visible in the food — almost every solid or liquid food has one; treat [] as a last resort only for loose, uncountable food (e.g. plain scrambled eggs, mixed stir-fry) where no natural unit exists. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. For a whole or mostly-whole divisible food like cake, pie, or pizza, count the visible pieces/slices and derive grams_per_unit from serving_size_grams / quantity. If N slices are visible, return quantity N. Use quantity 1 only when a single piece/slice is actually the analyzed portion. If you are uncertain, still give your single best-guess unit and quantity rather than returning []; a plausible guess is always more useful than none. Do not include g/grams in unit_options.
""".strip()

FEWSHOT_UNITS = """
Examples of unit_options (adapt quantities to the actual meal):
- "2 slices pepperoni pizza and a 330ml Coke" → unit_options for pizza slices and ml for the drink
- "1 cup cooked oatmeal with 1 medium banana and 1 tbsp honey" → cup, piece, tbsp as appropriate
- "grilled chicken breast 150g with 200g white rice and 150g steamed broccoli" → [] (grams stated)
""".strip()

# Short portion/quantity grounding for research ablation `compact_portion` only.
# Derived from JFB hard/easy failure modes (restaurant overestimate, hidden oil/dip
# denseness, ignoring stated tbsp/scoop). Flash-Lite JFB A/B did not beat plain
# compact — do not default these into production prompts. See
# docs/FOOD_ACCURACY_BENCHMARK_STATUS.md § Failure modes & portion reasoning.
IMAGE_PORTION_RULES = """
Portion rules:
- Estimate only food clearly visible or named by the user. Do not invent typical sides, drinks, bread, or garnishes that are not visible.
- Size from what you see (plate fill, piece count, height). Prefer the visible amount over a generic restaurant "full plate" prior when they disagree.
- Include calories from oils, dressings, dips, sauces, nut butters, cheese, and fried coatings even when the layer looks thin.
- For whole cakes/pies/pizzas estimate the whole visible item; for a single slice estimate only that slice.
- If the user states amounts (g, tbsp, slices, brand/product), those override visual guesses.
""".strip()

# Research ablation `compact_scale_ref` only. Extends the reference-object hint
# already shipped in FoodAnalysisService.analyzeAuto with an explicit standard
# plate/bowl fallback for when no object is visible. See
# docs/UNCERTAINTY_DRIVEN_ENTRY.md § New candidates (2026-07-28 brainstorm).
SCALE_REFERENCE_RULES = """
Scale rules:
- If a utensil, hand, coin, or common object of known size is visible, use it to judge the food's real-world scale.
- Otherwise, if a plate or bowl is visible, assume a standard dinner plate is about 26cm across and a standard bowl is about 15cm across, and scale the food's portion against that.
- Prefer this visual scale reasoning over a generic restaurant "full plate" assumption when they disagree.
""".strip()

TEXT_QUANTITY_RULES = """
Quantity rules:
- Honor explicit amounts (g, ml, tbsp, tsp, scoop, slice, piece, cup). Do not inflate "2 tbsp hummus" or "1 scoop whey" into a large bowl/shake.
- For branded or packaged product names, prefer that product's labeled nutrition when known.
- If amount is unspecified, use one typical labeled serving (not a large restaurant portion) and set serving_size_grams accordingly.
""".strip()

NUTRIENT_UNITS = (
    "Calories are integers. Protein/carbs/fat are decimal gram values when needed. "
    "serving_size_grams is the estimated total weight in grams. "
    "Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; "
    "cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; "
    "vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms."
)


def production_text_prompt(description: str, *, portion_aware: bool = False) -> str:
    # Production shipped the lean wording (lean_units2 A/B win, 2026-07-24);
    # this builder mirrors FoodAnalysisService.analyzeText.
    prompt = lean_text_prompt(description, unit_rule="v2")
    if portion_aware:
        prompt += f"\n{TEXT_QUANTITY_RULES}"
    return prompt


def user_description(sample) -> str | None:
    """User-typed note for image+text entry. Only sample.text counts (not meal_name metadata)."""
    if sample.text and sample.text.strip():
        return sample.text.strip()
    return None


def append_user_context(prompt: str, description: str | None) -> str:
    if description:
        prompt += (
            f"\n\nAdditional context from the user about this meal: {description}\n"
            "Use this context to improve accuracy of identification, portion size, and nutrition estimates."
        )
    return prompt


def production_image_prompt(
    *, description: str | None = None, portion_aware: bool = False
) -> str:
    # Production shipped the lean wording (lean_units2 A/B win, 2026-07-24);
    # this builder mirrors FoodAnalysisService.analyzeFood.
    prompt = lean_image_prompt(unit_rule="v2")
    if portion_aware:
        prompt += f"\n{IMAGE_PORTION_RULES}"
    return append_user_context(prompt, description)


def compact_text_prompt(description: str, *, portion_aware: bool = False) -> str:
    portion = f"\n{TEXT_QUANTITY_RULES}" if portion_aware else ""
    return f"""
Estimate macronutrients for: {description}
Respond ONLY with JSON:
{COMPACT_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.{portion}
""".strip()


def compact_image_prompt(
    *, description: str | None = None, portion_aware: bool = False
) -> str:
    portion = f"\n{IMAGE_PORTION_RULES}" if portion_aware else ""
    prompt = f"""
Analyze this food image. Estimate macronutrients for the visible serving.
Respond ONLY with JSON:
{COMPACT_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.{portion}
""".strip()
    return append_user_context(prompt, description)


# Research: full production schema with compact-style wording. Isolates schema
# size from rule verbosity (production loses to compact on macros; see
# docs/FOOD_ACCURACY_BENCHMARK_STATUS.md). `lean_units` adds a single-sentence
# unit_options rule; `lean_units2` carries the option object shape inline
# (grams_per_unit is required by the app parser; options without it are dropped).
LEAN_NUTRIENT_UNITS = (
    "Calories are integers; other nutrients are numbers "
    "(grams for protein/carbs/fat/sugars/fiber/fats/omega-3; "
    "mg for cholesterol, sodium, potassium, calcium, iron, magnesium, zinc, vitamin C, vitamin E; "
    "mcg for vitamins A, D, B12, K and folate). "
    "serving_size_grams is the estimated total weight in grams."
)

LEAN_UNIT_RULE = (
    "Fill unit_options with the natural serving unit (slice, piece, cup, ml, tbsp, can) "
    "and the quantity covering the whole analyzed amount when one is obvious; "
    "use [] only when no non-gram unit fits."
)

LEAN_UNIT_RULE2 = (
    'unit_options entries look like {"unit":"slice","quantity":2,"grams_per_unit":180}: '
    "the natural non-gram unit (slice, piece, cup, ml, tbsp, can) with quantity covering "
    "the whole analyzed amount and its weight per unit. Use [] only when no non-gram unit "
    "fits; never use g/grams as a unit."
)


def _lean_unit_line(unit_rule: bool | str) -> str:
    if unit_rule == "v2":
        return f"\n{LEAN_UNIT_RULE2}"
    if unit_rule:
        return f"\n{LEAN_UNIT_RULE}"
    return ""


def lean_text_prompt(description: str, *, unit_rule: bool | str = False) -> str:
    units = _lean_unit_line(unit_rule)
    return f"""
Estimate the nutritional content for: {description}
Respond ONLY with JSON:
{FULL_JSON_SCHEMA}
{LEAN_NUTRIENT_UNITS}{units}
For "emoji" pick the single most specific food emoji for this dish. Use null for any nutrient you cannot estimate.
""".strip()


def lean_image_prompt(*, description: str | None = None, unit_rule: bool | str = False) -> str:
    units = _lean_unit_line(unit_rule)
    prompt = f"""
Analyze this food image. Estimate the nutritional content of the visible food.
Respond ONLY with JSON:
{FULL_JSON_SCHEMA}
{LEAN_NUTRIENT_UNITS}{units}
For "emoji" pick the single most specific food emoji for this dish. Use null for any nutrient you cannot estimate.
""".strip()
    return append_user_context(prompt, description)


def fewshot_text_prompt(description: str) -> str:
    return production_text_prompt(description) + "\n\n" + FEWSHOT_UNITS


def fewshot_image_prompt(*, description: str | None = None) -> str:
    return production_image_prompt(description=description) + "\n\n" + FEWSHOT_UNITS


def _build_image_prompt(sample, builder):
    return builder(description=user_description(sample))


def compact_scale_ref_image_prompt(*, description: str | None = None) -> str:
    prompt = f"""
Analyze this food image. Estimate macronutrients for the visible serving.
Respond ONLY with JSON:
{COMPACT_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.
{SCALE_REFERENCE_RULES}
""".strip()
    return append_user_context(prompt, description)


# Simulated-clarification research variants: compact baseline plus oracle
# answers injected as if the user tapped a clarification chip. Items without
# the oracle extras fall back to plain compact — compare on covered ids only
# (build_clarify_manifests.py emits the id lists). See
# docs/UNCERTAINTY_DRIVEN_ENTRY.md.
def _compact_base_prompt(sample) -> str:
    if sample.modality == "text":
        return compact_text_prompt(sample.text or "")
    return compact_image_prompt(description=user_description(sample))


def compact_clarify_prompt(sample, *, portion: bool = False, fat: bool = False) -> str:
    return _compact_base_prompt(sample) + clarify_answer_block(
        sample, portion=portion, fat=fat
    )


COMPACT_ASK_JSON_SCHEMA = (
    '{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,'
    '"serving_size_grams":0.0,"clarify_request":"portion|added_fat|none"}'
)

ASK_RULE = (
    'Set clarify_request to "portion" if knowing the true portion weight would most '
    'improve your estimate, "added_fat" if knowing about unseen oil, butter, or '
    'dressing would, or "none" if neither answer would change your estimate much.'
)


def compact_ask_prompt(sample) -> str:
    intro = (
        f"Estimate macronutrients for: {sample.text}"
        if sample.modality == "text"
        else "Analyze this food image. Estimate macronutrients for the visible serving."
    )
    prompt = f"""
{intro}
Respond ONLY with JSON:
{COMPACT_ASK_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.
{ASK_RULE}
""".strip()
    if sample.modality == "image":
        prompt = append_user_context(prompt, user_description(sample))
    return prompt


PROMPT_BUILDERS = {
    "production_text": lambda sample: production_text_prompt(sample.text or ""),
    "production_image": lambda sample: _build_image_prompt(sample, production_image_prompt),
    "compact": lambda sample: (
        compact_text_prompt(sample.text or "")
        if sample.modality == "text"
        else _build_image_prompt(sample, compact_image_prompt)
    ),
    # Research-only: compact + portion/quantity rules. Flash-Lite JFB A/B did not beat compact
    # (WMAPE 37.2% vs 35.9%); kept for future experiments, not production default.
    "compact_portion": lambda sample: (
        compact_text_prompt(sample.text or "", portion_aware=True)
        if sample.modality == "text"
        else compact_image_prompt(
            description=user_description(sample), portion_aware=True
        )
    ),
    # Pre-2026-07-24 production image wording (verbose rules); kept for baselines.
    "legacy_production_image": lambda sample: append_user_context(
        f"""
Analyze this food image. Identify the food and estimate its nutritional content.
Respond ONLY with JSON:
{FULL_JSON_SCHEMA}
{NUTRIENT_UNITS}
{IMAGE_UNIT_RULES}
Give your best estimate for the visible food amount shown in the image. Use null for any nutrient you cannot estimate.
""".strip(),
        user_description(sample),
    ),
    "lean_full": lambda sample: (
        lean_text_prompt(sample.text or "")
        if sample.modality == "text"
        else lean_image_prompt(description=user_description(sample))
    ),
    "lean_units": lambda sample: (
        lean_text_prompt(sample.text or "", unit_rule=True)
        if sample.modality == "text"
        else lean_image_prompt(description=user_description(sample), unit_rule=True)
    ),
    "lean_units2": lambda sample: (
        lean_text_prompt(sample.text or "", unit_rule="v2")
        if sample.modality == "text"
        else lean_image_prompt(description=user_description(sample), unit_rule="v2")
    ),
    # Research-only: compact + reference-object/plate-scale rules (image only;
    # falls back to plain compact for text samples, where scale-anchoring doesn't
    # apply). See docs/UNCERTAINTY_DRIVEN_ENTRY.md § New candidates (2026-07-28).
    "compact_scale_ref": lambda sample: (
        compact_text_prompt(sample.text or "")
        if sample.modality == "text"
        else _build_image_prompt(sample, compact_scale_ref_image_prompt)
    ),
    # Simulated clarification (oracle chip answers); research-only.
    "compact_clarify_portion": lambda sample: compact_clarify_prompt(sample, portion=True),
    "compact_clarify_fat": lambda sample: compact_clarify_prompt(sample, fat=True),
    "compact_clarify_both": lambda sample: compact_clarify_prompt(
        sample, portion=True, fat=True
    ),
    # Stage 1 of the two-stage ask-then-answer eval (run_clarify_eval.py).
    "compact_clarify_ask": compact_ask_prompt,
    "fewshot_units": lambda sample: (
        fewshot_text_prompt(sample.text or "")
        if sample.modality == "text"
        else _build_image_prompt(sample, fewshot_image_prompt)
    ),
}


def build_prompt(sample, prompt_name: str) -> str:
    try:
        builder = PROMPT_BUILDERS[prompt_name]
    except KeyError as exc:
        available = ", ".join(sorted(PROMPT_BUILDERS))
        raise ValueError(f"Unknown prompt {prompt_name!r}. Choose from: {available}") from exc
    return builder(sample)


def list_prompts() -> list[str]:
    return sorted(PROMPT_BUILDERS)
