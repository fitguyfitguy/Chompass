package app.chompass.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.AIProvider
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.OptionPickerSheet
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.warning

@Composable
internal fun ProviderStep(
    provider: AIProvider,
    model: String,
    apiKey: String,
    apiKeyTesting: Boolean,
    apiKeyTestMessage: String,
    apiKeyTestOk: Boolean?,
    onDeviceAvailable: Boolean,
    onProviderChange: (AIProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTestKey: () -> Unit,
) {
    // Sparkles hero, AI Studio recommended card, expandable setup guide (Gemini only),
    // and BYOK card. Key is optional — empty cloud keys go through the skip dialog.
    var selectorSheet by remember { mutableStateOf<ProviderSelectorSheet?>(null) }
    var howtoExpanded by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val providerChoices = remember(onDeviceAvailable) {
        AIProvider.values().filter { it != AIProvider.ON_DEVICE || onDeviceAvailable }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppColors.Calorie
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.onboarding_provider_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_provider_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        // Recommended provider card — tap selects Google AI Studio (Gemini).
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, AppColors.Calorie.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProviderChange(AIProvider.GEMINI) }
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = AppColors.Calorie,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.onboarding_provider_recommended_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_provider_recommended_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Collapsible how-to — only relevant while Gemini / AI Studio is selected.
        if (provider == AIProvider.GEMINI) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { howtoExpanded = !howtoExpanded }
                    ) {
                        Text(
                            stringResource(R.string.onboarding_provider_howto_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                    if (howtoExpanded) {
                        AiSetupRow(
                            number = "1",
                            text = stringResource(R.string.onboarding_provider_step_1),
                            onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }
                        )
                        AiSetupRow("2", stringResource(R.string.onboarding_provider_step_2))
                        AiSetupRow("3", stringResource(R.string.onboarding_provider_step_3))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // BYOK setup: provider, model, and optional API key.
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                OnboardingSelectorRow(
                    label = stringResource(R.string.settings_ai_provider),
                    value = stringResource(provider.displayNameRes),
                    onClick = { selectorSheet = ProviderSelectorSheet.PROVIDER }
                )
                HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                OnboardingSelectorRow(
                    label = stringResource(R.string.settings_ai_model),
                    value = model.ifEmpty { stringResource(R.string.settings_ai_model_unset) },
                    onClick = { selectorSheet = ProviderSelectorSheet.MODEL }
                )
                if (provider.requiresApiKey) {
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                    FudGlassTextField(
                        value = apiKey,
                        onValueChange = onKeyChange,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = stringResource(provider.apiKeyPlaceholderRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = onTestKey,
                            enabled = !apiKeyTesting && apiKey.isNotBlank()
                        ) {
                            Text(stringResource(R.string.onboarding_api_key_test))
                        }
                        if (apiKeyTestMessage.isNotEmpty()) {
                            Text(
                                apiKeyTestMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (apiKeyTestOk) {
                                    true -> AppColors.SuccessLight
                                    false -> MaterialTheme.colorScheme.error
                                    null -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.onboarding_ai_free_tier_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onboarding_provider_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                when (provider) {
                    AIProvider.ON_DEVICE -> R.string.onboarding_privacy_note_ondevice
                    AIProvider.OLLAMA -> R.string.onboarding_privacy_note_ollama
                    else -> R.string.onboarding_privacy_note_cloud
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    when (selectorSheet) {
        ProviderSelectorSheet.PROVIDER -> OptionPickerSheet(
            title = stringResource(R.string.sheet_ai_provider),
            items = providerChoices,
            label = { stringResource(it.displayNameRes) },
            subtitle = {
                if (it == AIProvider.ON_DEVICE) stringResource(R.string.ai_provider_on_device_subtitle)
                else null
            },
            selected = { it == provider },
            onSelect = { onProviderChange(it); selectorSheet = null },
            onDismiss = { selectorSheet = null }
        )
        ProviderSelectorSheet.MODEL -> OptionPickerSheet(
            title = stringResource(R.string.sheet_model),
            items = provider.models,
            label = { it },
            subtitle = { model ->
                when (provider.modelTiers[model]) {
                    "paid" -> stringResource(R.string.ai_model_tier_paid)
                    "varies" -> stringResource(R.string.ai_model_tier_varies)
                    else -> null
                }
            },
            selected = { it == model },
            onSelect = { onModelChange(it); selectorSheet = null },
            onDismiss = { selectorSheet = null },
            footer = if (provider.supportsCustomModelName) stringResource(R.string.sheet_model_footer) else null,
            customPlaceholder = if (provider.supportsCustomModelName) stringResource(R.string.sheet_any_model_id) else null,
            onCustomSubmit = if (provider.supportsCustomModelName) {
                { onModelChange(it); selectorSheet = null }
            } else null
        )
        null -> Unit
    }
}

/** Which onboarding BYOK picker sheet is open. */
private enum class ProviderSelectorSheet { PROVIDER, MODEL }

@Composable
private fun AiSetupRow(number: String, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AppColors.Calorie),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) AppColors.Calorie else MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Settings-style tappable selector row used by the BYOK provider/model pickers in
 * onboarding: label on the left, the current value in the accent colour, and a
 * trailing chevron. Tapping opens the shared [OptionPickerSheet] bottom sheet.
 */
@Composable
private fun OnboardingSelectorRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Calorie,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * iOS BuildingPlanStepView: animated percentage counter, gradient progress bar,
 * and a five-item checklist that ticks off over ~4 seconds, then auto-advances.
 */
@Composable
internal fun BuildingPlanStep(vm: OnboardingViewModel, onComplete: () -> Unit) {
    val items = listOf(
        stringResource(R.string.onboarding_building_calories) to Icons.Outlined.LocalFireDepartment,
        stringResource(R.string.onboarding_building_carbs) to Icons.Outlined.Restaurant,
        stringResource(R.string.onboarding_building_protein) to Icons.Outlined.FitnessCenter,
        stringResource(R.string.onboarding_building_fats) to Icons.Outlined.Bolt,
        stringResource(R.string.onboarding_building_health_score) to Icons.Filled.Favorite
    )
    var checkedCount by remember { mutableIntStateOf(0) }
    var percent by remember { mutableIntStateOf(0) }
    var aiDone by remember { mutableStateOf(false) }
    val targetProgress = checkedCount / items.size.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 400),
        label = "plan_progress"
    )

    // Compute the plan with AI in parallel with the animation (seeds the Plan Ready targets;
    // leaves them null on failure so the formula values are used).
    LaunchedEffect(Unit) { vm.buildPlanWithAI { aiDone = true } }

    LaunchedEffect(Unit) {
        val percentSteps = listOf(20, 40, 60, 80, 100)
        for (i in 0 until items.size) {
            kotlinx.coroutines.delay(700)
            checkedCount = i + 1
            percent = percentSteps[i]
        }
        kotlinx.coroutines.delay(400)
        // Advance only once the AI plan calc has also finished.
        while (!aiDone) kotlinx.coroutines.delay(100)
        onComplete()
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.onboarding_building_percent_format, percent),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_building_setting_up),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AppColors.CalorieStart, AppColors.CalorieEnd)
                        )
                    )
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.onboarding_building_finalizing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_building_recommendation),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEachIndexed { index, (label, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.onboarding_building_bullet),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (index < checkedCount) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS planReadyStep: large gradient-filled calorie number with "daily calories"
 * caption, and three macro cards (Protein, Carbs, Fat) below. Each value is tappable —
 * opens an edit dialog whose value lands in customCalories/customProtein/customCarbs/customFat
 * on the profile that ProfileRepository persists at the end of onboarding.
 */
@Composable
internal fun PlanReadyStep(state: OnboardingState, vm: OnboardingViewModel) {
    val profile = state.buildProfile()
    var editing by remember { mutableStateOf<PlanField?>(null) }
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_plan_title),
            subtitle = stringResource(R.string.onboarding_plan_subtitle)
        )
        // Adaptive Goals is on by default for new installs — say so up front.
        Text(
            stringResource(R.string.onboarding_plan_adaptive_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { editing = PlanField.CALORIES }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${profile.effectiveCalories}",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(
                    brush = Brush.linearGradient(
                        listOf(AppColors.CalorieStart, AppColors.CalorieEnd)
                    )
                )
            )
            Text(
                stringResource(R.string.onboarding_plan_daily_calories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MacroCard(
                label = stringResource(R.string.macro_protein),
                value = profile.effectiveProtein,
                color = AppColors.Protein,
                modifier = Modifier.weight(1f).clickable { editing = PlanField.PROTEIN }
            )
            MacroCard(
                label = stringResource(R.string.macro_carbs),
                value = profile.effectiveCarbs,
                color = AppColors.Carbs,
                modifier = Modifier.weight(1f).clickable { editing = PlanField.CARBS }
            )
            MacroCard(
                label = stringResource(R.string.macro_fat),
                value = profile.effectiveFat,
                color = AppColors.Fat,
                modifier = Modifier.weight(1f).clickable { editing = PlanField.FAT }
            )
        }
        editing?.let { field ->
            PlanEditDialog(
                field = field,
                currentValue = when (field) {
                    PlanField.CALORIES -> profile.effectiveCalories
                    PlanField.PROTEIN -> profile.effectiveProtein
                    PlanField.CARBS -> profile.effectiveCarbs
                    PlanField.FAT -> profile.effectiveFat
                },
                onDismiss = { editing = null },
                onSave = { newValue ->
                    when (field) {
                        PlanField.CALORIES -> vm.setCustomCalories(newValue)
                        PlanField.PROTEIN -> vm.setCustomProtein(newValue)
                        PlanField.CARBS -> vm.setCustomCarbs(newValue)
                        PlanField.FAT -> vm.setCustomFat(newValue)
                    }
                    editing = null
                },
                onReset = if (when (field) {
                        PlanField.CALORIES -> state.customCalories != null
                        PlanField.PROTEIN -> state.customProtein != null
                        PlanField.CARBS -> state.customCarbs != null
                        PlanField.FAT -> state.customFat != null
                    }
                ) {
                    {
                        when (field) {
                            PlanField.CALORIES -> vm.setCustomCalories(null)
                            PlanField.PROTEIN -> vm.setCustomProtein(null)
                            PlanField.CARBS -> vm.setCustomCarbs(null)
                            PlanField.FAT -> vm.setCustomFat(null)
                        }
                        editing = null
                    }
                } else null
            )
        }
        if (profile.effectiveCalories < 1200) {
            Spacer(Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.onboarding_plan_doctor_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.onboarding_plan_doctor_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Final onboarding step: safety + accuracy notices get their own screen so they
 * are not clipped under the plan targets and footer CTA.
 */
@Composable
internal fun DisclaimersStep() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        StepHeader(
            stringResource(R.string.onboarding_disclaimers_title),
            subtitle = stringResource(R.string.onboarding_disclaimers_subtitle)
        )
        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_safety_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_low_bf),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_not_medical),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_water),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_privacy_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_cloud_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_cloud_lead),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    listOf(
                        R.string.onboarding_privacy_cloud_food,
                        R.string.onboarding_privacy_cloud_whatif,
                        R.string.onboarding_privacy_cloud_coach,
                        R.string.onboarding_privacy_cloud_goals
                    ).forEach { res ->
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        stringResource(R.string.onboarding_privacy_ondevice_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_ondevice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_accuracy_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_accuracy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MacroCard(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$value",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "g",
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class PlanField(@get:androidx.annotation.StringRes val titleRes: Int, @get:androidx.annotation.StringRes val unitRes: Int) {
    CALORIES(R.string.onboarding_plan_field_calories, R.string.unit_kcal),
    PROTEIN(R.string.onboarding_plan_field_protein, R.string.unit_g),
    CARBS(R.string.onboarding_plan_field_carbs, R.string.unit_g),
    FAT(R.string.onboarding_plan_field_fat, R.string.unit_g)
}

@Composable
private fun PlanEditDialog(
    field: PlanField,
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: (() -> Unit)?
) {
    // Match the in-app Settings nutrition pickers: range + step per field, scroll
    // to a value, no keyboard. Saves on the picker's currently-selected value.
    val (min, max, step) = when (field) {
        PlanField.CALORIES -> Triple(800, 6000, 50)
        PlanField.PROTEIN  -> Triple(10, 500, 5)
        PlanField.CARBS    -> Triple(0, 800, 5)
        PlanField.FAT      -> Triple(10, 300, 5)
    }
    var picked by remember(currentValue) {
        mutableStateOf(currentValue.coerceIn(min, max))
    }
    val accent = when (field) {
        PlanField.CALORIES -> AppColors.Calorie
        PlanField.PROTEIN -> AppColors.Protein
        PlanField.CARBS -> AppColors.Carbs
        PlanField.FAT -> AppColors.Fat
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.titleRes), color = accent) },
        text = {
            NumericWheelPicker(
                value = picked,
                onValueChange = { picked = it },
                min = min,
                max = max,
                unit = stringResource(field.unitRes),
                step = step,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked) }) {
                Text(stringResource(R.string.action_save), color = accent)
            }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
