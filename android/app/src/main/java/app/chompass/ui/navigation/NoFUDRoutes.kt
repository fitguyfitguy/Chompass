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
    const val CUSTOMIZE_PROGRESS = "settings/customize-progress"
    const val CALCULATION_METHODS = "settings/calculation-methods"
    const val BODY_MEASUREMENTS = "settings/body-measurements"
    const val SETTINGS_FOOD = "settings/food"
    const val SETTINGS_WATER = "settings/water?from={from}"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications?from={from}"
    const val SETTINGS_SYNC = "settings/sync?from={from}"

    /** Cross-link route builders; `from` drives the sub-screen's back label. */
    fun waterRoute(from: String) = "settings/water?from=$from"
    fun notificationsRoute(from: String) = "settings/notifications?from=$from"
    fun syncRoute(from: String) = "settings/sync?from=$from"

    val bottomTabs = listOf(HOME, PROGRESS, COACH, SETTINGS)

    /**
     * Destinations reachable via `chompass://go/<dest>` (notification taps, deep
     * links). Only exactly these plain routes: arg-routed sub-screens
     * (`settings/water?from=…`) cannot be built from a path-only `go` link, and an
     * arbitrary string passed to `nav.navigate()` throws — so unknown destinations
     * are ignored instead of navigated (any app can fire a VIEW intent).
     */
    fun isGoDestination(dest: String): Boolean = dest in GO_DESTINATIONS

    private val GO_DESTINATIONS = setOf(
        HOME,
        PROGRESS,
        COACH,
        SETTINGS,
        SETTINGS_PERSONAL,
        SETTINGS_GOALS,
        SETTINGS_APP,
        SETTINGS_AI,
        SETTINGS_DATA,
        OPTIONAL_NUTRIENT_GOALS,
        HOME_DISPLAY,
        CUSTOMIZE_PROGRESS,
        CALCULATION_METHODS,
        BODY_MEASUREMENTS,
        SETTINGS_FOOD,
    )
}
