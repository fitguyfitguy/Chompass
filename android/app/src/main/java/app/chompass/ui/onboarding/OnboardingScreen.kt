package app.chompass.ui.onboarding

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.CalorieSafety
import java.time.LocalDate
import java.time.Period
import app.chompass.services.ondevice.OnDeviceCapability
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
@Composable
fun OnboardingScreen(container: AppContainer, onComplete: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val onDeviceAvailable = remember(context) { OnDeviceCapability.isSupported(context) }
    var showAiSkipDialog by remember { mutableStateOf(false) }
    var showMinorDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // Shrink for the keyboard so the API-key field (Provider step) and the
            // footer CTA stay reachable while typing instead of being covered.
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // iOS shows a chevron-left back button + a thin Capsule progress bar at
        // the top, only on steps 1..N-2 (hidden on Welcome and Review).
        if (ui.step != OnboardingStep.WELCOME && ui.step != OnboardingStep.BUILDING_PLAN) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.onboarding_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { vm.back() }
                )
                val totalSteps = OnboardingStep.values().size
                val progress = ui.step.ordinal.toFloat() / (totalSteps - 1).toFloat()
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onBackground)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
            when (ui.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.GENDER -> GenderStep(selected = ui.gender, onSelect = vm::setGender)
                OnboardingStep.BIRTHDAY -> BirthdayStep(current = ui.birthday, onChange = vm::setBirthday)
                OnboardingStep.HEIGHT_WEIGHT -> HeightWeightStep(
                    cm = ui.heightCm,
                    kg = ui.weightKg,
                    heightMetric = ui.heightMetric,
                    weightMetric = ui.weightMetric,
                    onHeightChange = vm::setHeight,
                    onWeightChange = vm::setWeight,
                    onToggle = vm::setUseMetric
                )
                OnboardingStep.BODY_FAT -> BodyFatStep(
                    bodyFat = ui.bodyFatPercentage,
                    goalBodyFat = ui.goalBodyFatPercentage,
                    onChange = vm::setBodyFat,
                    onGoalChange = vm::setGoalBodyFat
                )
                OnboardingStep.ACTIVITY -> ActivityStep(
                    selected = ui.activity,
                    onSelect = vm::setActivity
                )
                OnboardingStep.GOAL -> GoalStep(selected = ui.goal, onSelect = vm::setGoal)
                OnboardingStep.DIET_MODE -> DietModeStep(
                    selected = ui.dietMode,
                    ketoCarbMode = ui.ketoCarbMode,
                    ketoCarbManualTarget = ui.ketoCarbManualTarget,
                    onSelect = vm::setDietMode,
                    onKetoCarbModeSelect = vm::setKetoCarbMode,
                    onKetoCarbManualTargetChange = vm::setKetoCarbManualTarget
                )
                OnboardingStep.GOAL_WEIGHT -> GoalWeightStep(
                    current = ui.goalWeightKg,
                    goal = ui.goal,
                    heightMetric = ui.heightMetric,
                    weightMetric = ui.weightMetric,
                    onChange = vm::setGoalWeight,
                    onToggle = vm::setUseMetric
                )
                OnboardingStep.GOAL_SPEED -> {
                    val draft = ui.buildProfile()
                    GoalSpeedStep(
                        weeklyKg = ui.weeklyChangeKg,
                        goal = ui.goal,
                        useMetric = ui.weightMetric,
                        currentKg = ui.weightKg,
                        targetKg = ui.goalWeightKg,
                        onSelect = vm::setWeeklyChange,
                        paceCappedTarget = draft.takeIf { it.rawDailyCalories < it.dailyCalories }?.dailyCalories,
                    )
                }
                OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                    enabled = ui.notificationsEnabled,
                    onToggle = vm::setNotificationsEnabled
                )
                OnboardingStep.HEALTH_CONNECT -> HealthConnectStep(
                    container = container,
                    enabled = ui.healthConnectEnabled,
                    onToggle = vm::setHealthConnectEnabled
                )
                OnboardingStep.PROVIDER -> ProviderStep(
                    provider = ui.aiProvider,
                    model = ui.aiModel,
                    apiKey = ui.apiKey,
                    apiKeyTesting = ui.apiKeyTesting,
                    apiKeyTestMessage = ui.apiKeyTestMessage,
                    apiKeyTestOk = ui.apiKeyTestOk,
                    onDeviceAvailable = onDeviceAvailable,
                    onProviderChange = vm::setAiProvider,
                    onModelChange = vm::setAiModel,
                    onKeyChange = vm::setApiKey,
                    onTestKey = { vm.testApiKey(advanceOnSuccess = false) },
                )
                OnboardingStep.BUILDING_PLAN -> BuildingPlanStep(vm = vm, onComplete = vm::next)
                OnboardingStep.PLAN_READY -> PlanReadyStep(state = ui, vm = vm)
                OnboardingStep.DISCLAIMERS -> DisclaimersStep()
            }
        }

        when (ui.step) {
            OnboardingStep.WELCOME -> {
                // iOS Welcome: full-width pink-gradient "Get Started" capsule.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(AppRadii.Field))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppColors.CalorieStart, AppColors.CalorieEnd)
                                )
                            )
                            .clickable { vm.next() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.action_get_started),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            OnboardingStep.BUILDING_PLAN -> {
                // Auto-advancing animation; no CTA. Reserve the same footer
                // height so layout doesn't jump when we land on this step.
                Spacer(Modifier.height(54.dp + 36.dp + 24.dp))
            }
            OnboardingStep.DISCLAIMERS -> {
                // Final step — completes onboarding after the user has room to
                // read safety + accuracy notices (kept off the crowded plan screen).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AppColors.CalorieStart, AppColors.CalorieEnd)
                            )
                        )
                        .clickable { vm.complete(onComplete) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.action_get_started),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            OnboardingStep.PROVIDER -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (ui.needsAiSkipConfirm) showAiSkipDialog = true
                            else vm.next()
                        },
                        enabled = ui.canAdvance,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            stringResource(R.string.action_continue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { showAiSkipDialog = true }) {
                        Text(stringResource(R.string.onboarding_skip_ai))
                    }
                }
            }
            else -> {
                // iOS continueButton: full-width inverse-coloured capsule.
                Button(
                    onClick = {
                        val age = Period.between(ui.birthday, LocalDate.now()).years
                        if (ui.step == OnboardingStep.BIRTHDAY && age < CalorieSafety.ADULT_MIN_AGE) {
                            showMinorDialog = true
                        } else vm.next()
                    },
                    enabled = ui.canAdvance,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .height(54.dp)
                ) {
                    Text(
                        stringResource(R.string.action_continue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showMinorDialog) {
        AlertDialog(
            onDismissRequest = { showMinorDialog = false },
            title = { Text(stringResource(R.string.onboarding_minor_title)) },
            text = { Text(stringResource(R.string.onboarding_minor_message)) },
            confirmButton = {
                TextButton(onClick = { showMinorDialog = false; vm.next() }) {
                    Text(stringResource(R.string.onboarding_minor_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMinorDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showAiSkipDialog) {
        AlertDialog(
            onDismissRequest = { showAiSkipDialog = false },
            title = { Text(stringResource(R.string.onboarding_skip_ai_title)) },
            text = {
                Text(
                    stringResource(
                        if (onDeviceAvailable) R.string.onboarding_skip_ai_body
                        else R.string.onboarding_skip_ai_later_body
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAiSkipDialog = false
                        vm.skipAiSetup(useOnDevice = onDeviceAvailable)
                    }
                ) {
                    Text(stringResource(R.string.onboarding_skip_ai_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiSkipDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
