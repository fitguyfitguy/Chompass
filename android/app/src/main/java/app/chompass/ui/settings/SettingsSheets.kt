package app.chompass.ui.settings

import app.chompass.data.OpenRouterReasoningEffort
import app.chompass.data.WeatherRepository
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import app.chompass.ui.components.FudIconBubble
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.models.ActivityLevel
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.DietMode
import app.chompass.models.Gender
import app.chompass.models.HeuristicRuleOverride
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.KetoCarbMode
import app.chompass.models.OptionalNutrient
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.ProteinTargetMode
import app.chompass.models.ServingUnitHeuristicRule
import app.chompass.models.ServingUnitHeuristics
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import app.chompass.models.WeightGoal
import app.chompass.models.WaterGoalCalculator
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.ui.components.DecimalWheelPicker
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.home.FoodLogSortOrder
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.macroAccentColor
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheets(
    sheet: SettingsSheet,
    ui: SettingsUiState,
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
    onInvalidGoalWeight: (String) -> Unit,
    onRebalanceBlocked: () -> Unit
) {
    val state = rememberChompassSheetState()
    val invalidLoseMsg = stringResource(R.string.settings_invalid_goal_lose)
    val invalidGainMsg = stringResource(R.string.settings_invalid_goal_gain)
    val isDark = isDarkTheme()
    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            when (sheet) {
                SettingsSheet.AI_PROVIDER -> ListSheet(
                    title = stringResource(R.string.sheet_ai_provider),
                    items = AIProvider.values().filter { it != AIProvider.ON_DEVICE || ui.onDeviceAvailable },
                    label = { stringResource(it.displayNameRes) },
                    subtitle = { provider ->
                        if (provider == AIProvider.ON_DEVICE) {
                            stringResource(R.string.ai_provider_on_device_subtitle)
                        } else {
                            null
                        }
                    },
                    selected = { it == ui.selectedAI },
                    onSelect = { vm.selectProvider(it); onDismiss() }
                )
                SettingsSheet.AI_MODEL -> ListSheet(
                    title = stringResource(R.string.sheet_model),
                    items = ui.selectedAI.models,
                    label = { it },
                    subtitle = { model ->
                        when (ui.selectedAI.modelTiers[model]) {
                            "paid" -> stringResource(R.string.ai_model_tier_paid)
                            "varies" -> stringResource(R.string.ai_model_tier_varies)
                            else -> null
                        }
                    },
                    selected = { it == ui.selectedModel },
                    onSelect = { vm.selectModel(it); onDismiss() },
                    footer = if (ui.selectedAI.supportsCustomModelName) stringResource(R.string.sheet_model_footer) else null,
                    customField = if (ui.selectedAI.supportsCustomModelName) {
                        { m -> vm.selectModel(m); onDismiss() }
                    } else null
                )
                SettingsSheet.OPENROUTER_REASONING -> ListSheet(
                    title = stringResource(R.string.settings_ai_reasoning_effort),
                    items = OpenRouterReasoningEffort.entries,
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.openRouterReasoningEffort },
                    onSelect = { vm.setOpenRouterReasoningEffort(it); onDismiss() },
                    footer = stringResource(R.string.settings_ai_reasoning_effort_footer),
                )
                SettingsSheet.VISION_MODEL -> ListSheet(
                    title = stringResource(R.string.settings_ai_vision_model),
                    items = listOf("") + ui.selectedAI.models,
                    label = { if (it.isEmpty()) stringResource(R.string.settings_ai_vision_model_unset) else it },
                    subtitle = { model ->
                        if (model.isEmpty()) null
                        else when (ui.selectedAI.modelTiers[model]) {
                            "paid" -> stringResource(R.string.ai_model_tier_paid)
                            "varies" -> stringResource(R.string.ai_model_tier_varies)
                            else -> null
                        }
                    },
                    selected = { it == ui.visionModel },
                    onSelect = { vm.selectVisionModel(it.ifEmpty { null }); onDismiss() },
                    footer = stringResource(R.string.settings_ai_vision_model_footer),
                    customField = if (ui.selectedAI.supportsCustomModelName) {
                        { m -> vm.selectVisionModel(m); onDismiss() }
                    } else null
                )
                SettingsSheet.API_KEY -> ApiKeySheet(
                    title = stringResource(R.string.sheet_api_key_format, stringResource(ui.selectedAI.displayNameRes)),
                    placeholder = stringResource(ui.selectedAI.apiKeyPlaceholderRes),
                    onSave = { vm.setApiKey(it); onDismiss() }
                )
                SettingsSheet.CUSTOM_BASE_URL -> {
                    val existing by vm.container.prefs
                        .customBaseUrl(ui.selectedAI)
                        .collectAsState(initial = "")
                    TextFieldSheet(
                        title = stringResource(R.string.settings_custom_url_title),
                        initial = existing.orEmpty(),
                        placeholder = stringResource(R.string.settings_custom_url_placeholder),
                        onSave = { vm.setCustomBaseUrl(ui.selectedAI, it); onDismiss() }
                    )
                }
                SettingsSheet.ON_DEVICE_MODEL -> OnDeviceModelSheet(
                    container = vm.container,
                    selectedModelId = ui.selectedModel,
                    onUnload = vm::unloadOnDeviceModel,
                    onDelete = vm::deleteOnDeviceModel,
                    onStartDownload = vm::startOnDeviceModelDownload,
                    onCancelDownload = vm::cancelOnDeviceModelDownload,
                    onSetOverWifiOnly = vm::setOnDeviceDownloadOverWifiOnly,
                )
                SettingsSheet.MAX_TOKENS -> {
                    var value by remember(ui.maxResponseTokens) { mutableIntStateOf(ui.maxResponseTokens) }
                    Text(stringResource(R.string.settings_max_tokens), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    NumericWheelPicker(
                        value = value,
                        onValueChange = { value = it },
                        min = 50,
                        max = 8000,
                        step = 50,
                        unit = stringResource(R.string.settings_max_tokens_unit),
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientSaveButton { vm.setMaxResponseTokens(value); onDismiss() }
                    Spacer(Modifier.height(8.dp))
                }
                SettingsSheet.AI_READ_TIMEOUT -> {
                    var value by remember(ui.aiReadTimeoutSeconds) { mutableIntStateOf(ui.aiReadTimeoutSeconds) }
                    Text(stringResource(R.string.settings_ai_read_timeout), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    NumericWheelPicker(
                        value = value,
                        onValueChange = { value = it },
                        min = 1,
                        max = 300,
                        step = 1,
                        unit = stringResource(R.string.settings_ai_read_timeout_unit),
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientSaveButton { vm.setAiReadTimeoutSeconds(value); onDismiss() }
                    Spacer(Modifier.height(8.dp))
                }
                SettingsSheet.SERVING_UNIT_MODE -> ListSheet(
                    title = stringResource(R.string.sheet_serving_unit_mode),
                    items = ServingUnitInferenceMode.entries,
                    label = { stringResource(it.displayNameRes) },
                    subtitle = { stringResource(it.subtitleRes) },
                    selected = { it == ui.servingUnitInferenceMode },
                    onSelect = { vm.setServingUnitInferenceMode(it); onDismiss() }
                )
                SettingsSheet.SERVING_UNIT_HEURISTICS -> ServingUnitHeuristicsSheet(
                    settings = ui.heuristicServingUnitSettings,
                    onToggle = vm::setHeuristicRuleEnabled,
                    onGramsPerUnitChange = vm::setHeuristicRuleGramsPerUnit
                )
                SettingsSheet.SPEECH_PROVIDER -> ListSheet(
                    title = stringResource(R.string.sheet_speech_engine),
                    items = SpeechProvider.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.selectedSpeech },
                    onSelect = { vm.selectSpeech(it); onDismiss() }
                )
                SettingsSheet.SPEECH_LANGUAGE -> ListSheet(
                    title = stringResource(R.string.sheet_speech_language),
                    items = SpeechLanguage.optionsFor(ui.selectedSpeech),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.selectedSpeechLanguage },
                    onSelect = { vm.selectSpeechLanguage(it); onDismiss() },
                    subtitle = {
                        when (it) {
                            SpeechLanguage.PROVIDER_AUTO -> stringResource(R.string.speech_language_provider_auto_subtitle)
                            SpeechLanguage.DEVICE -> stringResource(R.string.speech_language_device_subtitle)
                            else -> null
                        }
                    }
                )
                SettingsSheet.SPEECH_KEY -> ApiKeySheet(
                    title = stringResource(R.string.sheet_speech_api_key_format, stringResource(ui.selectedSpeech.displayNameRes)),
                    placeholder = stringResource(ui.selectedSpeech.apiKeyPlaceholderRes),
                    onSave = {
                        // Route through the VM so SettingsUiState.speechApiKeyMasked
                        // updates and the API Key row reflects the new value
                        // (was bypassing the VM and writing straight to KeyStore,
                        // which left the UI showing "Tap to edit" forever).
                        vm.setSpeechApiKey(it)
                        onDismiss()
                    }
                )
                SettingsSheet.FALLBACK_PROVIDER -> ListSheet(
                    title = stringResource(R.string.sheet_ai_provider),
                    // ON_DEVICE is a valid fallback target when the feature is
                    // available (mirror the primary picker gate); resolution in
                    // currentFallbackConfig additionally requires the model file
                    // to be downloaded, so the list stays honest.
                    items = AIProvider.values().filter { it != AIProvider.ON_DEVICE || ui.onDeviceAvailable },
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.fallbackProvider },
                    onSelect = { vm.selectFallbackProvider(it); onDismiss() }
                )
                SettingsSheet.FALLBACK_MODEL -> {
                    // Same provider as primary → exclude primary's selected model so
                    // fallback can't be a literal duplicate config.
                    val opts = if (ui.fallbackProvider == ui.selectedAI)
                        ui.fallbackProvider.models.filter { it != ui.selectedModel }
                    else ui.fallbackProvider.models
                    ListSheet(
                        title = stringResource(R.string.sheet_model),
                        items = opts,
                        label = { it },
                        selected = { it == ui.fallbackModel },
                        onSelect = { vm.selectFallbackModel(it); onDismiss() },
                        footer = if (ui.fallbackProvider.supportsCustomModelName) stringResource(R.string.sheet_model_footer) else null,
                        customField = if (ui.fallbackProvider.supportsCustomModelName) {
                            { m -> vm.selectFallbackModel(m); onDismiss() }
                        } else null
                    )
                }
                SettingsSheet.FALLBACK_KEY -> ApiKeySheet(
                    title = stringResource(R.string.sheet_api_key_format, stringResource(ui.fallbackProvider.displayNameRes)),
                    placeholder = stringResource(ui.fallbackProvider.apiKeyPlaceholderRes),
                    onSave = { vm.setFallbackApiKey(it); onDismiss() }
                )
                SettingsSheet.FALLBACK_BASE_URL -> {
                    val existing by vm.container.prefs
                        .fallbackCustomBaseUrl(ui.fallbackProvider)
                        .collectAsState(initial = "")
                    TextFieldSheet(
                        title = stringResource(R.string.settings_custom_url_title),
                        initial = existing.orEmpty(),
                        placeholder = stringResource(R.string.settings_custom_url_placeholder),
                        onSave = { vm.setFallbackCustomBaseUrl(ui.fallbackProvider, it); onDismiss() }
                    )
                }
                SettingsSheet.GENDER -> ListSheet(
                    title = stringResource(R.string.sheet_gender),
                    items = Gender.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.profile?.gender },
                    onSelect = { g -> vm.updateProfile { it.copy(gender = g) }; onDismiss() },
                    icon = { genderIcon(it) }
                )
                SettingsSheet.HEIGHT -> {
                    val cm = ui.profile?.heightCm?.toInt() ?: 175
                    HeightSheet(
                        current = cm,
                        useMetric = ui.heightMetric,
                        onUnitChange = { metric -> vm.setHeightUnit(if (metric) "cm" else "ftin") },
                        onSave = { newCm -> vm.updateProfile { it.copy(heightCm = newCm.toDouble()) }; onDismiss() }
                    )
                }
                SettingsSheet.WEIGHT -> {
                    val kg = ui.profile?.weightKg ?: 70.0
                    WeightSheet(
                        titleText = stringResource(R.string.sheet_weight),
                        current = kg,
                        useMetric = ui.weightMetric,
                        onUnitChange = { metric -> vm.setWeightUnit(if (metric) "kg" else "lbs") },
                        onSave = { newKg -> vm.saveCurrentWeight(newKg); onDismiss() }
                    )
                }
                SettingsSheet.BODY_FAT -> BodyFatSheet(
                    current = ui.profile?.bodyFatPercentage,
                    // Clearing the current value also clears the goal so a stale
                    // goal doesn't linger on someone who opted out of the
                    // body-fat track entirely.
                    onSave = { bf ->
                        vm.updateProfile {
                            it.copy(
                                bodyFatPercentage = bf,
                                goalBodyFatPercentage = if (bf == null) null else it.goalBodyFatPercentage
                            )
                        }
                        onDismiss()
                    }
                )
                SettingsSheet.GOAL_BODY_FAT -> GoalBodyFatSheet(
                    currentGoal = ui.profile?.goalBodyFatPercentage,
                    currentBodyFat = ui.profile?.bodyFatPercentage,
                    // Goal body fat doesn't feed BMR/TDEE/macro math, so use
                    // updateProfile (no recompute) — editing the goal must
                    // never silently wipe the user's pinned macros.
                    onSave = { goal -> vm.updateProfile { it.copy(goalBodyFatPercentage = goal) }; onDismiss() }
                )
                SettingsSheet.ACTIVITY -> ListSheet(
                    title = stringResource(R.string.sheet_activity_level),
                    items = ActivityLevel.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    subtitle = { stringResource(it.subtitleRes) },
                    selected = { it == ui.profile?.activityLevel },
                    onSelect = { a -> vm.updateProfile { it.copy(activityLevel = a) }; onDismiss() },
                    icon = { activityIcon(it) }
                )
                SettingsSheet.GOAL -> ListSheet(
                    title = stringResource(R.string.sheet_goal),
                    items = WeightGoal.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.profile?.goal },
                    icon = { goalIcon(it) },
                    onSelect = { g ->
                        // Mirrors iOS ContentView.swift profile.goal onChange:
                        //   - Switching to MAINTAIN clears weeklyChangeKg + goalWeightKg.
                        //   - Switching to LOSE/GAIN seeds weeklyChangeKg if missing and
                        //     clears goalWeightKg if it now contradicts the new direction.
                        // Then recompute calories+macros from the new goal.
                        vm.updateProfile { p ->
                            when (g) {
                                WeightGoal.MAINTAIN ->
                                    p.copy(goal = g, weeklyChangeKg = null, goalWeightKg = null)
                                else -> {
                                    val gw = p.goalWeightKg
                                    val mismatched = gw != null && (
                                        (g == WeightGoal.LOSE && gw >= p.weightKg) ||
                                        (g == WeightGoal.GAIN && gw <= p.weightKg)
                                    )
                                    p.copy(
                                        goal = g,
                                        weeklyChangeKg = p.weeklyChangeKg ?: 0.5,
                                        goalWeightKg = if (mismatched) null else p.goalWeightKg
                                    )
                                }
                            }
                        }
                        onDismiss()
                    }
                )
                SettingsSheet.DIET_MODE -> ListSheet(
                    title = stringResource(R.string.sheet_diet_mode),
                    items = DietMode.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.profile?.dietMode },
                    subtitle = { if (it == DietMode.KETO) stringResource(R.string.diet_mode_beta_note) else null },
                    onSelect = { mode -> vm.setDietMode(mode); onDismiss() },
                    icon = {
                        when (it) {
                            DietMode.STANDARD -> Icons.Outlined.Restaurant
                            DietMode.KETO -> Icons.Outlined.LocalFireDepartment
                        }
                    }
                )
                SettingsSheet.DIET_CARB_MODE -> ListSheet(
                    title = stringResource(R.string.sheet_keto_carb_mode),
                    items = KetoCarbMode.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.profile?.ketoCarbMode },
                    subtitle = {
                        when (it) {
                            KetoCarbMode.ADAPTIVE -> stringResource(R.string.keto_carb_mode_adaptive_subtitle)
                            KetoCarbMode.MANUAL -> stringResource(R.string.keto_carb_mode_manual_subtitle)
                        }
                    },
                    onSelect = { mode -> vm.setKetoCarbMode(mode); onDismiss() },
                    icon = {
                        when (it) {
                            KetoCarbMode.ADAPTIVE -> Icons.Outlined.AutoAwesome
                            KetoCarbMode.MANUAL -> Icons.Outlined.Tune
                        }
                    }
                )
                SettingsSheet.DIET_CARB_TARGET -> NutritionPickerSheet(
                    label = stringResource(R.string.settings_keto_net_carbs),
                    unit = stringResource(R.string.unit_g),
                    currentValue = (ui.profile?.ketoCarbManualTarget ?: ui.profile?.ketoActiveCarbTarget)
                        ?: KetoCarbRecommendationService.MIN_NET_CARBS_G,
                    range = KetoCarbRecommendationService.MIN_NET_CARBS_G..KetoCarbRecommendationService.MAX_NET_CARBS_G,
                    step = 1,
                    onSave = { grams ->
                        vm.setKetoCarbMode(KetoCarbMode.MANUAL)
                        vm.setKetoCarbManualTarget(grams)
                        onDismiss()
                    },
                    onResetToAuto = {
                        vm.setKetoCarbMode(KetoCarbMode.ADAPTIVE)
                        vm.setKetoCarbManualTarget(null)
                        onDismiss()
                    }
                )
                SettingsSheet.GOAL_WEIGHT -> {
                    val kg = ui.profile?.goalWeightKg ?: (ui.profile?.weightKg ?: 70.0)
                    WeightSheet(
                        titleText = stringResource(R.string.sheet_target_weight),
                        current = kg,
                        useMetric = ui.weightMetric,
                        onUnitChange = { metric -> vm.setWeightUnit(if (metric) "kg" else "lbs") },
                        onSave = { newKg ->
                            // Mirrors iOS ContentView.swift case .editGoalWeight: a Lose goal
                            // requires target < current weight; a Gain goal requires target >
                            // current weight. Reject mismatched targets with an alert instead
                            // of silently saving an unreachable goal.
                            val p = ui.profile
                            val current = p?.weightKg
                            val invalid = p != null && current != null && (
                                (p.goal == WeightGoal.LOSE && newKg >= current) ||
                                (p.goal == WeightGoal.GAIN && newKg <= current)
                            )
                            if (invalid) {
                                onInvalidGoalWeight(
                                    if (p!!.goal == WeightGoal.LOSE)
                                        invalidLoseMsg
                                    else
                                        invalidGainMsg
                                )
                            } else {
                                vm.updateProfile { it.copy(goalWeightKg = newKg) }
                                onDismiss()
                            }
                        }
                    )
                }
                SettingsSheet.GOAL_SPEED -> GoalSpeedSheet(
                    current = ui.profile?.weeklyChangeKg ?: 0.5,
                    goal = ui.profile?.goal ?: WeightGoal.MAINTAIN,
                    useMetric = ui.weightMetric,
                    onSave = { kg -> vm.updateProfile { it.copy(weeklyChangeKg = kg) }; onDismiss() }
                )
                SettingsSheet.BIRTHDAY -> BirthdaySheet(
                    current = ui.profile?.birthday ?: Instant.now(),
                    onSave = { newInstant ->
                        vm.updateProfile { it.copy(birthday = newInstant) }
                        onDismiss()
                    }
                )
                SettingsSheet.APPEARANCE -> ListSheet(
                    title = stringResource(R.string.sheet_appearance),
                    items = listOf(
                        "system" to stringResource(R.string.settings_appearance_system),
                        "light" to stringResource(R.string.settings_appearance_light),
                        "dark" to stringResource(R.string.settings_appearance_dark)
                    ),
                    label = { it.second },
                    selected = { it.first == ui.appearanceMode },
                    onSelect = { vm.setAppearanceMode(it.first); onDismiss() },
                    icon = { appearanceIcon(it.first) }
                )
                SettingsSheet.LANGUAGE -> ListSheet(
                    title = stringResource(R.string.sheet_language),
                    items = listOf(
                        "" to stringResource(R.string.settings_language_system),
                        "ar" to "Arabic (العربية)",
                        "az" to "Azerbaijani (Azərbaycan)",
                        "de" to "German (Deutsch)",
                        "es" to "Spanish (Español)",
                        "fr" to "French (Français)",
                        "hi" to "Hindi (हिन्दी)",
                        "it" to "Italian (Italiano)",
                        "ja" to "Japanese (日本語)",
                        "ko" to "Korean (한국어)",
                        "nl" to "Dutch (Nederlands)",
                        "pt-BR" to "Portuguese (Português)",
                        "ro" to "Romanian (Română)",
                        "ru" to "Russian (Русский)",
                        "uk" to "Ukrainian (Українська)",
                        "zh-CN" to "Chinese (简体中文)"
                    ),
                    label = { it.second },
                    selected = { it.first == ui.appLanguage },
                    onSelect = { vm.setAppLanguage(it.first); onDismiss() },
                    icon = { Icons.Outlined.Language }
                )
                SettingsSheet.FOOD_LOG_SORT -> ListSheet(
                    title = stringResource(R.string.settings_food_log_sort),
                    items = FoodLogSortOrder.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.foodLogSortOrder },
                    onSelect = { vm.setFoodLogSortOrder(it); onDismiss() }
                )
                SettingsSheet.WEEK_START -> ListSheet(
                    title = stringResource(R.string.sheet_week_starts),
                    items = listOf(
                        false to stringResource(R.string.settings_week_sunday),
                        true to stringResource(R.string.settings_week_monday)
                    ),
                    label = { it.second },
                    selected = { it.first == ui.weekStartsOnMonday },
                    onSelect = { vm.setWeekStartsOnMonday(it.first); onDismiss() }
                )
                SettingsSheet.PROGRESS_DEFAULT_RANGE -> ListSheet(
                    title = stringResource(R.string.settings_progress_default_range),
                    items = app.chompass.ui.progress.TimeRange.entries.toList(),
                    label = { stringResource(it.labelRes) },
                    selected = { it.storageId == ui.progressDefaultRangeId },
                    onSelect = { vm.setProgressDefaultRangeId(it.storageId); onDismiss() }
                )
                SettingsSheet.MEAL_TIMES -> MealTimesSheet(
                    current = ui.mealSchedule,
                    onSave = {
                        vm.setMealSchedule(it)
                        onDismiss()
                    },
                )
                SettingsSheet.WATER_GOAL -> WaterGoalSheet(
                    current = ui.waterDailyGoalMl,
                    onSave = {
                        vm.setWaterDailyGoalMl(it)
                        onDismiss()
                    },
                )
                SettingsSheet.WATER_QUICK_PRESETS -> WaterQuickPresetsSheet(
                    current = ui.waterQuickPresetsMl,
                    useMetric = ui.weightMetric,
                    onSave = {
                        vm.setWaterQuickPresetsMl(it)
                        onDismiss()
                    },
                )
                SettingsSheet.WATER_DYNAMIC_BASE -> ListSheet(
                    title = stringResource(R.string.settings_water_dynamic_base),
                    items = listOf(
                        WaterGoalCalculator.BASE_SOURCE_WEIGHT to stringResource(R.string.settings_water_dynamic_base_weight),
                        WaterGoalCalculator.BASE_SOURCE_MANUAL to stringResource(R.string.settings_water_dynamic_base_manual),
                    ),
                    label = { it.second },
                    selected = { it.first == ui.waterBaseSource },
                    onSelect = {
                        vm.setWaterBaseSource(it.first)
                        onDismiss()
                    },
                )
                SettingsSheet.WATER_MANUAL_TEMP -> WaterManualTempSheet(
                    current = ui.waterManualTempC,
                    onSave = {
                        vm.setWaterManualTempC(it)
                        onDismiss()
                    },
                )
                SettingsSheet.WEATHER_SOURCE -> ListSheet(
                    title = stringResource(R.string.settings_weather_source),
                    items = listOf(
                        WeatherRepository.SOURCE_MANUAL to stringResource(R.string.settings_weather_source_manual),
                        WeatherRepository.SOURCE_OPEN_METEO to stringResource(R.string.settings_weather_source_meteo),
                    ),
                    label = { it.second },
                    subtitle = { option ->
                        when (option.first) {
                            WeatherRepository.SOURCE_MANUAL -> stringResource(R.string.settings_weather_source_manual_sub)
                            else -> stringResource(R.string.settings_weather_source_meteo_sub)
                        }
                    },
                    selected = { it.first == ui.weatherSource },
                    onSelect = {
                        vm.setWeatherSource(it.first)
                        onDismiss()
                    },
                    footer = stringResource(R.string.settings_weather_source_help),
                )
                SettingsSheet.WEATHER_OM_CITY -> OpenMeteoCitySheet(
                    currentCity = ui.weatherOmCity,
                    currentHighC = ui.weatherOmHighC,
                    updatedAtMillis = ui.weatherOmUpdatedAtMillis,
                    manualHighC = ui.waterManualTempC,
                    onSearch = { vm.searchWeatherCities(it) },
                    onSelect = {
                        vm.selectWeatherCity(it)
                        onDismiss()
                    },
                    onRefresh = {
                        vm.refreshWeatherNow()
                        onDismiss()
                    },
                    onClose = onDismiss,
                )
                SettingsSheet.WATER_REMINDER_PLAN -> WaterReminderPlanSheet(
                    currentStartMinutes = ui.waterAwakeStartMinutes,
                    currentEndMinutes = ui.waterAwakeEndMinutes,
                    currentCupMl = ui.waterCupSizeMl,
                    goalMl = ui.waterDynamicGoalPreview?.netGoalMl ?: ui.waterDailyGoalMl,
                    onSave = { start, end, cup ->
                        vm.setWaterAwakeStartMinutes(start)
                        vm.setWaterAwakeEndMinutes(end)
                        vm.setWaterCupSizeMl(cup)
                        onDismiss()
                    },
                )
                SettingsSheet.CALORIES -> NutritionPickerSheet(
                    label = stringResource(R.string.macro_calories), unit = stringResource(R.string.unit_kcal),
                    currentValue = ui.profile?.effectiveCalories ?: 2000,
                    range = 800..6000, step = 50,
                    onSave = { v ->
                        vm.editCaloriesGoal(v)
                        onDismiss()
                    },
                    onResetToAuto = if (ui.profile?.caloriesLocked == true) {
                        { vm.resetCaloriesLock(); onDismiss() }
                    } else null
                )
                SettingsSheet.PROTEIN -> ProteinGoalSheet(
                    profile = ui.profile,
                    onModeChange = { mode ->
                        vm.setProteinTargetMode(mode)
                    },
                    onSaveGrams = { v ->
                        vm.editMacroGoal(AutoBalanceMacro.PROTEIN, v) { onRebalanceBlocked() }
                        onDismiss()
                    },
                    onResetToAuto = if (ui.profile?.isMacroLocked(AutoBalanceMacro.PROTEIN) == true ||
                        (ui.profile?.proteinTargetMode?.usesRate == true && ui.profile?.proteinGramsPerKg != null)
                    ) {
                        {
                            vm.resetMacroLock(AutoBalanceMacro.PROTEIN)
                            vm.setProteinTargetMode(ProteinTargetMode.GRAMS_PER_DAY)
                            onDismiss()
                        }
                    } else null,
                )
                SettingsSheet.CARBS -> NutritionPickerSheet(
                    label = stringResource(R.string.macro_carbs), unit = stringResource(R.string.unit_g),
                    currentValue = ui.profile?.effectiveCarbs ?: 0,
                    range = 0..800, step = 5,
                    accentColor = AppColors.Carbs,
                    onSave = { v ->
                        vm.editMacroGoal(AutoBalanceMacro.CARBS, v) { onRebalanceBlocked() }
                        onDismiss()
                    },
                    onResetToAuto = if (ui.profile?.isMacroLocked(AutoBalanceMacro.CARBS) == true) {
                        { vm.resetMacroLock(AutoBalanceMacro.CARBS); onDismiss() }
                    } else null
                )
                SettingsSheet.FAT -> NutritionPickerSheet(
                    label = stringResource(R.string.macro_fat), unit = stringResource(R.string.unit_g),
                    currentValue = ui.profile?.effectiveFat ?: 0,
                    range = 10..300, step = 5,
                    accentColor = AppColors.Fat,
                    onSave = { v ->
                        vm.editMacroGoal(AutoBalanceMacro.FAT, v) { onRebalanceBlocked() }
                        onDismiss()
                    },
                    onResetToAuto = if (ui.profile?.isMacroLocked(AutoBalanceMacro.FAT) == true) {
                        { vm.resetMacroLock(AutoBalanceMacro.FAT); onDismiss() }
                    } else null
                )
                SettingsSheet.OPTIONAL_NUTRIENTS -> OptionalNutrientGoalsSheet(
                    goals = ui.optionalNutrientGoals,
                    onChange = vm::setOptionalNutrientGoals,
                    onDismiss = onDismiss
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun ServingUnitHeuristicsSheet(
    settings: HeuristicServingUnitSettings,
    onToggle: (String, Boolean) -> Unit,
    onGramsPerUnitChange: (String, Double?) -> Unit
) {
    Text(
        stringResource(R.string.sheet_serving_unit_heuristics),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.serving_unit_heuristics_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    )
    Spacer(Modifier.height(8.dp))
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(ServingUnitHeuristics.RULES, key = { it.id }) { rule ->
            ServingUnitHeuristicRuleRow(
                rule = rule,
                override = settings.overrides[rule.id],
                onToggle = { onToggle(rule.id, it) },
                onGramsPerUnitChange = { onGramsPerUnitChange(rule.id, it) }
            )
        }
    }
}

@Composable
internal fun ServingUnitHeuristicRuleRow(
    rule: ServingUnitHeuristicRule,
    override: HeuristicRuleOverride?,
    onToggle: (Boolean) -> Unit,
    onGramsPerUnitChange: (Double?) -> Unit
) {
    val enabled = override?.enabled ?: true
    val currentGrams = override?.gramsPerUnit ?: rule.defaultGramsPerUnit
    var grams by remember(rule.id, currentGrams) { mutableStateOf(currentGrams) }
    Column(Modifier.fillMaxWidth()) {
        ToggleRow(label = rule.label, checked = enabled, onChange = onToggle)
        if (enabled) {
            DecimalWheelPicker(
                value = grams,
                onValueChange = { grams = it; onGramsPerUnitChange(it) },
                min = 0.1,
                max = 1000.0,
                step = 0.1,
                unit = stringResource(R.string.unit_g),
            )
            if (override?.gramsPerUnit != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { grams = rule.defaultGramsPerUnit; onGramsPerUnitChange(null) }) {
                        Text(stringResource(R.string.serving_unit_heuristics_reset), fontSize = 12.sp)
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun OptionalNutrientGoalsSheet(
    goals: OptionalNutrientGoals,
    onChange: (OptionalNutrientGoals) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<OptionalNutrient?>(null) }
    val nutrient = editing

    if (nutrient != null) {
        TextButton(onClick = { editing = null }) {
            Text(stringResource(R.string.settings_other_nutrients), color = AppColors.Calorie)
        }
        Spacer(Modifier.height(4.dp))
        NutritionPickerSheet(
            label = stringResource(nutrient.displayNameRes),
            unit = stringResource(nutrient.unitRes),
            currentValue = goals.valueFor(nutrient),
            range = nutrient.goalRange,
            step = nutrient.goalStep,
            accentColor = nutrient.macroAccentColor() ?: AppColors.Calorie,
            onSave = { value ->
                onChange(goals.withValue(nutrient, value))
                editing = null
            }
        )
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_other_nutrient_goals), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done), color = AppColors.Calorie) }
    }
    Text(
        "Separate from calorie, protein, carbs, and fat targets.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    )
    Spacer(Modifier.height(12.dp))
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(OptionalNutrient.values().toList()) { item ->
            OptionalNutrientGoalRow(
                nutrient = item,
                value = goals.valueFor(item),
                onClick = { editing = item }
            )
        }
    }
    TextButton(
        onClick = { onChange(OptionalNutrientGoals.Default) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_reset_defaults), color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted))
    }
}

@Composable
internal fun OptionalNutrientGoalRow(
    nutrient: OptionalNutrient,
    value: Int,
    onClick: () -> Unit
) {
    val accent = nutrient.macroAccentColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudIconBubble(
            Icons.Outlined.DataUsage,
            size = 22.dp,
            iconSize = 15.dp,
            tint = accent ?: MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(nutrient.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(nutrient.unitRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint)
            )
        }
        Text(
            "$value${nutrient.unit}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent?.copy(alpha = 0.85f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
            modifier = Modifier.size(18.dp)
        )
    }
}
