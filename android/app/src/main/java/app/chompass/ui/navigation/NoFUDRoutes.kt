package app.chompass.ui.navigation

object ChompassRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val COACH = "coach"
    const val SETTINGS = "settings"
    const val SETTINGS_PERSONAL = "settings/personal"
    const val SETTINGS_GOALS = "settings/goals"
    const val SETTINGS_APP = "settings/app"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_DATA = "settings/data"
    const val OPTIONAL_NUTRIENT_GOALS = "settings/optional-nutrient-goals"
    const val HOME_DISPLAY = "settings/home-display"
    const val CALCULATION_METHODS = "settings/calculation-methods"
    const val BODY_MEASUREMENTS = "settings/body-measurements"

    val bottomTabs = listOf(HOME, PROGRESS, COACH, SETTINGS)
}
