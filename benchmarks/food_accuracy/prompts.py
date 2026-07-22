#!/usr/bin/env python3
"""Prompt templates aligned with NoFUD FoodAnalysisService and research ablations."""

from __future__ import annotations

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

NUTRIENT_UNITS = (
    "Calories are integers. Protein/carbs/fat are decimal gram values when needed. "
    "serving_size_grams is the estimated total weight in grams. "
    "Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; "
    "cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; "
    "vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms."
)


def production_text_prompt(description: str) -> str:
    return f"""
Estimate the nutritional content for: {description}
Parse any quantities, brands, and multiple items from the text. If a brand is mentioned, use that brand's known nutritional data. If multiple items are described, sum up the total nutrition.
Respond ONLY with JSON:
{FULL_JSON_SCHEMA}
{NUTRIENT_UNITS}
{UNIT_RULES}
For "emoji" pick the single most specific food emoji that depicts this dish. Use null for any nutrient you cannot estimate.
""".strip()


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


def production_image_prompt(*, description: str | None = None) -> str:
    prompt = f"""
Analyze this food image. Identify the food and estimate its nutritional content.
Respond ONLY with JSON:
{FULL_JSON_SCHEMA}
{NUTRIENT_UNITS}
{IMAGE_UNIT_RULES}
Give your best estimate for the visible food amount shown in the image. Use null for any nutrient you cannot estimate.
""".strip()
    return append_user_context(prompt, description)


def compact_text_prompt(description: str) -> str:
    return f"""
Estimate macronutrients for: {description}
Respond ONLY with JSON:
{COMPACT_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.
""".strip()


def compact_image_prompt(*, description: str | None = None) -> str:
    prompt = f"""
Analyze this food image. Estimate macronutrients for the visible serving.
Respond ONLY with JSON:
{COMPACT_JSON_SCHEMA}
Calories are integers. Protein/carbs/fat are grams. serving_size_grams is total weight in grams.
""".strip()
    return append_user_context(prompt, description)


def fewshot_text_prompt(description: str) -> str:
    return production_text_prompt(description) + "\n\n" + FEWSHOT_UNITS


def fewshot_image_prompt(*, description: str | None = None) -> str:
    return production_image_prompt(description=description) + "\n\n" + FEWSHOT_UNITS


def _build_image_prompt(sample, builder):
    return builder(description=user_description(sample))


PROMPT_BUILDERS = {
    "production_text": lambda sample: production_text_prompt(sample.text or ""),
    "production_image": lambda sample: _build_image_prompt(sample, production_image_prompt),
    "compact": lambda sample: (
        compact_text_prompt(sample.text or "")
        if sample.modality == "text"
        else _build_image_prompt(sample, compact_image_prompt)
    ),
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
