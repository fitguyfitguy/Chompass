package org.codeberg.fitguy.nofud.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.AIProvider
import org.codeberg.fitguy.nofud.models.ActivityLevel
import org.codeberg.fitguy.nofud.models.AutoBalanceMacro
import org.codeberg.fitguy.nofud.models.DietMode
import org.codeberg.fitguy.nofud.models.Gender
import org.codeberg.fitguy.nofud.models.HeuristicRuleOverride
import org.codeberg.fitguy.nofud.models.HeuristicServingUnitSettings
import org.codeberg.fitguy.nofud.models.KetoCarbMode
import org.codeberg.fitguy.nofud.models.OptionalNutrient
import org.codeberg.fitguy.nofud.models.OptionalNutrientGoals
import org.codeberg.fitguy.nofud.models.ServingUnitHeuristicRule
import org.codeberg.fitguy.nofud.models.ServingUnitHeuristics
import org.codeberg.fitguy.nofud.models.ServingUnitInferenceMode
import org.codeberg.fitguy.nofud.models.ServingUnitOption
import org.codeberg.fitguy.nofud.models.SpeechLanguage
import org.codeberg.fitguy.nofud.models.SpeechProvider
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.services.KetoCarbRecommendationService
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.home.FoodLogSortOrder
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.macroAccentColor
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
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val invalidLoseMsg = stringResource(R.string.settings_invalid_goal_lose)
    val invalidGainMsg = stringResource(R.string.settings_invalid_goal_gain)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            when (sheet) {
                SettingsSheet.AI_PROVIDER -> ListSheet(
                    title = stringResource(R.string.sheet_ai_provider),
                    items = AIProvider.values().toList(),
                    label = { stringResource(it.displayNameRes) },
                    selected = { it == ui.selectedAI },
                    onSelect = { vm.selectProvider(it); onDismiss() }
                )
                SettingsSheet.AI_MODEL -> ListSheet(
                    title = stringResource(R.string.sheet_model),
                    items = ui.selectedAI.models,
                    label = { it },
                    selected = { it == ui.selectedModel },
                    onSelect = { vm.selectModel(it); onDismiss() },
                    footer = if (ui.selectedAI.supportsCustomModelName) stringResource(R.string.sheet_model_footer) else null,
                    customField = if (ui.selectedAI.supportsCustomModelName) {
                        { m -> vm.selectModel(m); onDismiss() }
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
                SettingsSheet.MAX_TOKENS -> {
                    TextFieldSheet(
                        title = stringResource(R.string.settings_max_tokens),
                        initial = ui.maxResponseTokens.toString(),
                        placeholder = "1024",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onSave = { it.trim().toIntOrNull()?.let(vm::setMaxResponseTokens); onDismiss() }
                    )
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
                    items = AIProvider.values().toList(),
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
                        .customBaseUrl(ui.fallbackProvider)
                        .collectAsState(initial = "")
                    TextFieldSheet(
                        title = stringResource(R.string.settings_custom_url_title),
                        initial = existing.orEmpty(),
                        placeholder = stringResource(R.string.settings_custom_url_placeholder),
                        onSave = { vm.setCustomBaseUrl(ui.fallbackProvider, it); onDismiss() }
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
                SettingsSheet.PROTEIN -> NutritionPickerSheet(
                    label = stringResource(R.string.macro_protein), unit = stringResource(R.string.unit_g),
                    currentValue = ui.profile?.effectiveProtein ?: 0,
                    range = 10..500, step = 5,
                    accentColor = AppColors.Protein,
                    onSave = { v ->
                        vm.editMacroGoal(AutoBalanceMacro.PROTEIN, v) { onRebalanceBlocked() }
                        onDismiss()
                    },
                    onResetToAuto = if (ui.profile?.isMacroLocked(AutoBalanceMacro.PROTEIN) == true) {
                        { vm.resetMacroLock(AutoBalanceMacro.PROTEIN); onDismiss() }
                    } else null
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    var text by remember(rule.id, override?.gramsPerUnit) {
        mutableStateOf(
            (override?.gramsPerUnit ?: rule.defaultGramsPerUnit).let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            }
        )
    }
    Column(Modifier.fillMaxWidth()) {
        ToggleRow(label = rule.label, checked = enabled, onChange = onToggle)
        if (enabled) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FudGlassTextField(
                    value = text,
                    onValueChange = { new ->
                        text = new
                        val parsed = new.trim().replace(',', '.').toDoubleOrNull()
                        onGramsPerUnitChange(if (parsed != null && parsed > 0) parsed else null)
                    },
                    placeholder = stringResource(
                        R.string.serving_unit_heuristics_default_value,
                        ServingUnitOption.formatQuantity(rule.defaultGramsPerUnit)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                if (override?.gramsPerUnit != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { text = ""; onGramsPerUnitChange(null) }) {
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
            range = nutrient.pickerRange(),
            step = nutrient.pickerStep(),
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
        Text(stringResource(R.string.settings_reset_defaults), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
            )
        }
        Text(
            "$value${nutrient.unit}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent?.copy(alpha = 0.85f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}
