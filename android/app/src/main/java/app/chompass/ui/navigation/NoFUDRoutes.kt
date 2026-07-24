package app.chompass.ui.navigation

object ChompassRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val COACH = "coach"
    const val SETTINGS = "settings"
    const val OPTIONAL_NUTRIENT_GOALS = "settings/optional-nutrient-goals"
    const val HOME_DISPLAY = "settings/home-display"
    const val CALCULATION_METHODS = "settings/calculation-methods"
    const val BODY_MEASUREMENTS = "settings/body-measurements"

    val bottomTabs = listOf(HOME, PROGRESS, COACH, SETTINGS)
}
