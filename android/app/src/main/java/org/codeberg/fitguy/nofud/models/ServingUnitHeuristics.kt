package org.codeberg.fitguy.nofud.models

/**
 * One entry in the built-in zero-network serving-unit heuristic table used by
 * [org.codeberg.fitguy.nofud.services.ai.FoodAnalysisService] when
 * [ServingUnitInferenceMode.HEURISTIC] is selected. [id] is the stable key
 * users' [HeuristicRuleOverride]s are keyed by, so ids must never change once
 * shipped (renaming would silently drop a user's customization).
 *
 * [keywords] and [label] are plain English on purpose: they match against the
 * (English) food name the AI analysis returns and describe the rule in the
 * settings UI — this heuristic table is inherently English-keyword-based,
 * unlike the AI-call fallback which works in any language.
 */
data class ServingUnitHeuristicRule(
    val id: String,
    val keywords: List<String>,
    val unit: String,
    val defaultGramsPerUnit: Double,
    val label: String
)

object ServingUnitHeuristics {
    val RULES: List<ServingUnitHeuristicRule> = listOf(
        ServingUnitHeuristicRule("pizza", listOf("pizza"), "slice", 120.0, "Pizza → slice"),
        ServingUnitHeuristicRule("cake", listOf("cake", "pie", "quiche", "tart"), "slice", 90.0, "Cake / pie / tart → slice"),
        ServingUnitHeuristicRule("bread", listOf("bread", "toast"), "slice", 30.0, "Bread / toast → slice"),
        ServingUnitHeuristicRule("cheese", listOf("cheese"), "slice", 20.0, "Cheese → slice"),
        ServingUnitHeuristicRule("cookie", listOf("cookie", "biscuit", "cracker"), "piece", 15.0, "Cookie / biscuit / cracker → piece"),
        ServingUnitHeuristicRule("nugget", listOf("nugget"), "piece", 20.0, "Nugget → piece"),
        ServingUnitHeuristicRule(
            "dumpling",
            listOf("dumpling", "gyoza", "samosa", "springroll", "spring roll"),
            "piece",
            25.0,
            "Dumpling / samosa / spring roll → piece"
        ),
        ServingUnitHeuristicRule("donut", listOf("donut", "doughnut", "muffin"), "piece", 70.0, "Donut / muffin → piece"),
        ServingUnitHeuristicRule("waffle", listOf("waffle", "pancake"), "piece", 40.0, "Waffle / pancake → piece"),
        ServingUnitHeuristicRule("bagel", listOf("bagel"), "piece", 90.0, "Bagel → piece"),
        ServingUnitHeuristicRule("egg", listOf("egg"), "piece", 50.0, "Egg → piece"),
        ServingUnitHeuristicRule("apple", listOf("apple"), "piece", 180.0, "Apple → piece"),
        ServingUnitHeuristicRule("banana", listOf("banana"), "piece", 120.0, "Banana → piece"),
        ServingUnitHeuristicRule("orange", listOf("orange", "mandarin"), "piece", 150.0, "Orange / mandarin → piece"),
        ServingUnitHeuristicRule("burger", listOf("burger", "hamburger", "cheeseburger"), "piece", 200.0, "Burger → piece"),
        ServingUnitHeuristicRule("sandwich", listOf("sandwich", "sandwiches", "wrap"), "piece", 220.0, "Sandwich / wrap → piece"),
        ServingUnitHeuristicRule("taco", listOf("taco"), "piece", 90.0, "Taco → piece"),
        ServingUnitHeuristicRule("burrito", listOf("burrito"), "piece", 280.0, "Burrito → piece"),
        ServingUnitHeuristicRule("hotdog", listOf("hotdog", "hot dog"), "piece", 100.0, "Hot dog → piece"),
        ServingUnitHeuristicRule("icecream", listOf("ice cream"), "scoop", 60.0, "Ice cream → scoop"),
        ServingUnitHeuristicRule(
            "drinksMl",
            listOf(
                "milk", "juice", "smoothie", "soup", "yogurt", "yoghurt", "broth",
                "coffee", "tea", "latte", "cappuccino"
            ),
            "ml",
            1.03,
            "Milk / juice / soup / coffee / tea → ml"
        ),
        ServingUnitHeuristicRule(
            "spoonedTbsp",
            listOf("peanut butter", "honey", "chutney", "ghee", "jam", "syrup", "mayonnaise", "mustard"),
            "tbsp",
            15.0,
            "Peanut butter / honey / jam / sauces → tbsp"
        ),
        ServingUnitHeuristicRule("can", listOf("soda", "cola", "beer"), "can", 330.0, "Soda / cola / beer → can"),
        ServingUnitHeuristicRule("wine", listOf("wine"), "glass", 150.0, "Wine → glass"),
        ServingUnitHeuristicRule(
            "bar",
            listOf("candy bar", "chocolate bar", "protein bar", "granola bar"),
            "bar",
            50.0,
            "Candy / protein / granola bar → bar"
        )
    )

    /**
     * First matching rule for [foodName], using word-boundary matching so short
     * keywords (e.g. "egg") do not fire inside unrelated words ("eggplant").
     */
    fun matchingRule(foodName: String): ServingUnitHeuristicRule? {
        val words = foodName.lowercase(java.util.Locale.US)
            .split(Regex("[^a-z]+"))
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val wordForms = words.toHashSet()
        for (word in words) {
            if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) {
                wordForms.add(word.dropLast(1))
            }
        }
        val normalized = words.joinToString(" ")
        return RULES.firstOrNull { rule ->
            rule.keywords.any { keyword ->
                if (' ' in keyword) normalized.contains(keyword) else keyword in wordForms
            }
        }
    }
}
