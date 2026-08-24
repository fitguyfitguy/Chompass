package app.chompass.ui.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import app.chompass.R
import app.chompass.ui.navigation.ChompassRoutes
import java.text.Normalizer
import java.util.Locale

/**
 * Static, offline index of every settings row for hub search (F1). Each entry
 * maps a setting label + guessable synonyms to the route that owns it. Labels
 * are string resources so they match whatever locale the app is in; keywords
 * are English (search vocabulary, not UI copy). Conditional rows (keto, on-device)
 * are included with their owning screen so search still finds them.
 */
internal data class SettingsIndexEntry(
    @StringRes val groupRes: Int,
    @StringRes val labelRes: Int,
    val keywords: List<String>,
    val route: String,
    val icon: ImageVector,
)

/** Normalize for matching: lowercase + strip diacritics (Größe -> grosse). */
internal fun settingsSearchFold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.getDefault())
        // ß has no NFD decomposition; map it so "grosse" finds "Größe".
        .replace("ß", "ss")

internal fun settingsSearchMatches(
    entry: SettingsIndexEntry,
    label: String,
    query: String,
): Boolean {
    val folded = settingsSearchFold(query)
    if (folded.isBlank()) return false
    return settingsSearchFold(label).contains(folded) ||
        entry.keywords.any { settingsSearchFold(it).contains(folded) }
}

/**
 * Pre-folded search entry: label + keywords normalized once per locale so the
 * results list folds the query only, not every index entry, on each keystroke
 * (F perf pass, device walk 2026-08-24). [settingsSearchMatchesFolded] is the
 * per-keystroke matcher over it.
 */
internal class FoldedSettingsEntry(
    val entry: SettingsIndexEntry,
    val label: String,
    val labelFold: String,
    val keywordFolds: List<String>,
)

internal fun foldSettingsEntry(entry: SettingsIndexEntry, label: String): FoldedSettingsEntry =
    FoldedSettingsEntry(
        entry = entry,
        label = label,
        labelFold = settingsSearchFold(label),
        keywordFolds = entry.keywords.map { settingsSearchFold(it) },
    )

internal fun settingsSearchMatchesFolded(folded: FoldedSettingsEntry, query: String): Boolean {
    val foldedQuery = settingsSearchFold(query)
    if (foldedQuery.isBlank()) return false
    return folded.labelFold.contains(foldedQuery) ||
        folded.keywordFolds.any { it.contains(foldedQuery) }
}

internal val SETTINGS_INDEX: List<SettingsIndexEntry> = listOf(
    // — Personal Info —
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_units,
        listOf("metric", "imperial", "cm", "ft", "kg", "lbs", "measurement system"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Straighten),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_gender,
        listOf("sex"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Person),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_birthday,
        listOf("birthday", "age", "dob", "born"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Person),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_height,
        listOf("height", "tall", "cm", "ft"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Straighten),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_weight,
        listOf("weight", "current", "kg", "lbs"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_body_fat,
        listOf("body fat", "bf", "percent"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Percent),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_use_body_fat_bmr,
        listOf("bmr", "katch", "mcardle", "metabolism"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.Percent),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_goal_body_fat,
        listOf("bf goal"), ChompassRoutes.SETTINGS_PERSONAL, Icons.Outlined.TrackChanges),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.body_measurements_title,
        listOf("waist", "tape", "circumference", "hips", "chest"), ChompassRoutes.BODY_MEASUREMENTS, Icons.Outlined.Straighten),

    // — Goals & Nutrition —
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_weight_goal,
        listOf("lose", "gain", "maintain", "target"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Equalizer),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_diet_mode,
        listOf("diet", "keto", "vegan", "vegetarian", "carnivore"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_keto_carb_mode,
        listOf("net", "total", "keto"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_keto_net_carbs,
        listOf("keto", "grams"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_activity_level,
        listOf("activity", "sedentary", "exercise"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Speed),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_weekly_change,
        listOf("rate", "speed", "per week"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Speed),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_goal_weight,
        listOf("target"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Equalizer),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_adaptive_goals,
        listOf("weekly", "auto", "nudge"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.TrackChanges),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_energy_goals,
        listOf("burn", "health connect", "measured"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_calories,
        listOf("calories", "kcal", "calorie goal"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.macro_protein,
        listOf("macro", "protein"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.macro_carbs,
        listOf("macro", "carbs", "carbohydrates"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.macro_fat,
        listOf("macro", "fat", "lipid"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_other_nutrient_goals,
        listOf("fiber", "sodium", "micronutrients", "vitamins"), ChompassRoutes.OPTIONAL_NUTRIENT_GOALS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_recalculate_goals,
        listOf("recalculate", "recalc", "refresh", "update targets"), ChompassRoutes.SETTINGS_GOALS, Icons.Outlined.Refresh),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_calc_methods,
        listOf("formula", "bmr", "tdee", "mifflin", "katch", "audit"), ChompassRoutes.CALCULATION_METHODS, Icons.Outlined.Calculate),

    // — Food & Entry —
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_default_to_grams,
        listOf("grams", "units"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_food_log_sort,
        listOf("order", "sorting"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_meal_times,
        listOf("meal", "breakfast", "lunch", "dinner", "schedule"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_photo_note_prompt,
        listOf("photo", "ask", "note", "describe"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_portion_clarify,
        listOf("portion", "serving", "grams"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_meal_constituents,
        listOf("ingredients", "components"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_serving_unit_mode,
        listOf("serving", "unit", "detection"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_serving_unit_heuristics,
        listOf("serving", "rules", "detection"), ChompassRoutes.SETTINGS_FOOD, Icons.Outlined.Tune),

    // — Display —
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_home_display,
        listOf("home", "cards", "nutrients", "gauge"), ChompassRoutes.HOME_DISPLAY, Icons.Outlined.Dashboard),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_appearance,
        listOf("dark", "light", "oled", "night", "theme mode"), ChompassRoutes.SETTINGS_APP, Icons.Outlined.Brightness6),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_theme_color,
        listOf("theme", "accent", "colour", "color", "teal", "blue"), ChompassRoutes.SETTINGS_APP, Icons.Outlined.Palette),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_language_title,
        listOf("language", "locale", "translation", "deutsch", "espanol"), ChompassRoutes.SETTINGS_APP, Icons.Outlined.Language),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_fixed_launcher_icon,
        listOf("launcher", "app icon", "teal"), ChompassRoutes.SETTINGS_APP, Icons.Outlined.Star),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_customize_progress,
        listOf("progress", "range", "charts", "plots", "trend", "default"), ChompassRoutes.CUSTOMIZE_PROGRESS, Icons.AutoMirrored.Outlined.ShowChart),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_week_starts,
        listOf("calendar", "monday", "sunday", "week"), ChompassRoutes.CUSTOMIZE_PROGRESS, Icons.Outlined.CalendarToday),

    // — Trackers & Reminders —
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_water_title,
        listOf("hydration", "goal", "drink", "ml", "presets"), ChompassRoutes.waterRoute("search"), Icons.Outlined.WaterDrop),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_nicotine_title,
        listOf("cigarettes", "vape", "pouch", "smoking", "limit"), ChompassRoutes.nicotineRoute("search"), Icons.Outlined.FilterAlt),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_fasting_title,
        listOf("fasting", "intermittent", "timer", "goal", "fast"), ChompassRoutes.fastingRoute("search"), Icons.Outlined.Schedule),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_notifications,
        listOf("reminders", "streak", "summary", "weight reminder", "battery"), ChompassRoutes.notificationsRoute("search"), Icons.Outlined.Notifications),

    // — AI & Speech —
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_features_master,
        listOf("ai", "on", "off", "privacy"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_show_coach_tab,
        listOf("coach", "tab", "hide"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Forum),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_provider,
        listOf("gemini", "openai", "anthropic", "claude", "ollama", "gpt", "local"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_model,
        listOf("model", "version"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_vision_model,
        listOf("vision", "image", "multimodal"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_api_key,
        listOf("api key", "secret", "token", "gemini", "openai"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Key),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_base_url,
        listOf("endpoint", "url", "ollama", "local", "server"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Link),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_allow_insecure_http,
        listOf("http", "cleartext", "local"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Link),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_on_device_model,
        listOf("on-device", "gemma", "download", "local", "offline"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_max_tokens,
        listOf("tokens", "length", "cap"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Numbers),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_read_timeout,
        listOf("timeout", "seconds", "network"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Speed),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_gemini_google_search,
        listOf("grounding", "google", "web"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.Search),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_section_custom_instructions,
        listOf("context", "prompt", "coach", "personality"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_section_fallback,
        listOf("fallback", "backup", "secondary", "retry"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_section_speech,
        listOf("speech", "voice", "dictation", "speech to text", "microphone", "stt"), ChompassRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),

    // — Health & Data —
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_health_connect,
        listOf("sync", "steps", "sleep", "permissions", "google fit", "samsung"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_manage_health_access,
        listOf("permissions", "health connect"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_health_background_sync,
        listOf("background", "automatic"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_section_health, R.string.export_diary_title,
        listOf("export", "json", "backup", "file"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_section_health, R.string.export_body_metrics_title,
        listOf("export", "weight", "body fat", "file"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_section_health, R.string.import_diary_title,
        listOf("import", "restore", "json"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_section_health, R.string.import_body_metrics_title,
        listOf("import", "restore", "weight", "csv"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_sync_section,
        listOf("webdav", "backup", "cloud", "auto sync", "server"), ChompassRoutes.syncRoute("search"), Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_danger_zone, R.string.settings_clear_food_log,
        listOf("delete", "erase", "wipe", "food"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.DeleteForever),
    SettingsIndexEntry(R.string.settings_danger_zone, R.string.settings_delete_all_data,
        listOf("delete", "erase", "wipe", "reset", "factory"), ChompassRoutes.SETTINGS_DATA, Icons.Outlined.DeleteForever),
)
