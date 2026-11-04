package app.chompass.ui.coach

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.disambiguateFoodName
import app.chompass.models.ChatMessage
import app.chompass.models.CoachQuickPromptEntry
import app.chompass.models.CoachQuickPrompts
import app.chompass.models.EnergyFormat
import app.chompass.models.EnergyUnit
import app.chompass.models.FoodEntry
import app.chompass.models.LocaleFormat
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import app.chompass.BuildConfig
import app.chompass.services.ai.AiError
import app.chompass.services.ai.userMessage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Base64

/**
 * Sealed wrapper around chip text — either a resource (for our preset chips,
 * so they translate) or a literal string (for user-typed sends, which already
 * go through the localized input path).
 */
sealed class CoachError {
    data class FromResource(val resId: Int) : CoachError()
    data class Literal(val message: String) : CoachError()
}

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val suggestions: List<CoachPromptChip> = emptyList(),
    /** A write action the coach proposed, awaiting explicit user confirmation.
     *  At most one of these is non-null at a time. */
    val pendingFood: FoodEntry? = null,
    val pendingWeight: WeightEntry? = null,
    val pendingWater: WaterEntry? = null,
)

/**
 * One coach quick-prompt chip (Codeberg #155): a builtin (resource-backed,
 * translates) or a custom entry (user label + prompt).
 */
sealed interface CoachPromptChip {
    data class Builtin(val id: String, @StringRes val labelRes: Int) : CoachPromptChip
    data class Custom(val id: String, val label: String, val prompt: String) : CoachPromptChip
}

/** Stable builtin chip ids (snake case of the string-key suffix) → label resource. */
internal val CoachBuiltinPromptLabels: Map<String, Int> = mapOf(
    "predict_30_days" to R.string.coach_chip_predict_30_days,
    "lose_faster" to R.string.coach_chip_lose_faster,
    "eating_too_much" to R.string.coach_chip_eating_too_much,
    "what_dinner" to R.string.coach_chip_what_dinner,
    "gain_healthy" to R.string.coach_chip_gain_healthy,
    "eating_enough" to R.string.coach_chip_eating_enough,
    "high_protein" to R.string.coach_chip_high_protein,
    "holding_weight" to R.string.coach_chip_holding_weight,
    "average_intake" to R.string.coach_chip_average_intake,
    "macro_suggestions" to R.string.coach_chip_macro_suggestions,
    "trend" to R.string.coach_chip_trend,
    "doing_this_week" to R.string.coach_chip_doing_this_week,
    "log_advice" to R.string.coach_chip_log_advice,
)

/** Goal defaults as builtin ids; null storage falls back to these. */
internal fun coachBuiltinDefaultIds(goal: WeightGoal?): List<String> = when (goal) {
    WeightGoal.LOSE -> listOf("predict_30_days", "lose_faster", "eating_too_much", "what_dinner")
    WeightGoal.GAIN -> listOf("predict_30_days", "gain_healthy", "eating_enough", "high_protein")
    WeightGoal.MAINTAIN -> listOf("holding_weight", "average_intake", "macro_suggestions", "trend")
    else -> listOf("doing_this_week", "predict_30_days", "log_advice")
}

class CoachViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CoachUiState())
    val ui: StateFlow<CoachUiState> = _ui.asStateFlow()

    init {
        container.chatRepository.messages
            .onEach { _ui.value = _ui.value.copy(messages = it) }
            .launchIn(viewModelScope)

        // Live-subscribe: chips update when the goal changes in Settings or the
        // quick-prompt selection changes (#155). Null storage falls back to the
        // goal defaults; a present-but-empty selection hides the strip.
        combine(container.profileRepository.profile, container.prefs.coachQuickPrompts) { p, stored ->
            promptChipsFor(p?.goal, stored)
        }
            .onEach { chips -> _ui.value = _ui.value.copy(suggestions = chips) }
            .launchIn(viewModelScope)
    }

    private fun promptChipsFor(goal: WeightGoal?, stored: CoachQuickPrompts?): List<CoachPromptChip> {
        val defaults = coachBuiltinDefaultIds(goal).map { CoachQuickPromptEntry(id = it) }
        val entries = stored?.resolveFor(defaults) ?: defaults
        return entries.mapNotNull { entry ->
            when {
                entry.isBuiltin -> CoachBuiltinPromptLabels[entry.id]?.let {
                    CoachPromptChip.Builtin(entry.id, it)
                }
                entry.label != null && entry.prompt != null ->
                    CoachPromptChip.Custom(entry.id, entry.label, entry.prompt)
                else -> null
            }
        }
    }

    fun send(userText: String, imageBytes: ByteArray? = null, thumbnailBytes: ByteArray? = null) {
        val trimmed = userText.trim()
        if ((trimmed.isBlank() && imageBytes == null) || _ui.value.sending) return
        val text = trimmed.ifEmpty { container.appContext.getString(R.string.coach_image_only_message) }
        viewModelScope.launch {
            val userMsg = ChatMessage(
                role = ChatMessage.Role.USER,
                content = text,
                attachmentImageBase64 = thumbnailBytes?.let { Base64.getEncoder().encodeToString(it) }
            )
            container.chatRepository.append(userMsg)
            _ui.value = _ui.value.copy(sending = true, error = null, errorRes = null)
            try {
                val history = container.chatRepository.contextMessages(limit = 20).dropLast(1) // exclude the just-appended user msg — it's passed separately
                val profile = container.profileRepository.current()
                    ?: return@launch run {
                        _ui.value = _ui.value.copy(
                            sending = false,
                            errorRes = R.string.coach_no_profile_error
                        )
                    }
                val weights = container.weightRepository.entries.first()
                val bodyFats = container.bodyFatRepository.entries.first()
                val measurements = container.bodyMeasurementRepository.entries.first()
                val foods = container.foodRepository.entries.first()
                val heightMetric = container.prefs.heightUnit.first() == "cm"
                val weightMetric = container.prefs.weightUnit.first() == "kg"

                val result = container.chatService.sendMessage(
                    history = history,
                    newUserMessage = text,
                    profile = profile,
                    weights = weights,
                    bodyFats = bodyFats,
                    measurements = measurements,
                    foods = foods,
                    heightMetric = heightMetric,
                    weightMetric = weightMetric,
                    imageBytes = imageBytes
                )
                container.chatRepository.append(ChatMessage(role = ChatMessage.Role.ASSISTANT, content = result.reply.trim()))
                _ui.value = _ui.value.copy(
                    sending = false,
                    pendingFood = result.proposedFood,
                    pendingWeight = result.proposedWeight,
                    pendingWater = result.proposedWater,
                )
            } catch (e: AiError) {
                logFailure(e)
                _ui.value = _ui.value.copy(sending = false, error = e.userMessage(container.appContext))
            } catch (e: Throwable) {
                // Leaving the screen cancels the send; that is not a chat failure.
                ensureActive()
                logFailure(e)
                _ui.value = _ui.value.copy(
                    sending = false,
                    error = if (BuildConfig.DEBUG) e.javaClass.simpleName else null,
                    errorRes = R.string.coach_chat_failed,
                )
            }
        }
    }

    /** Surfaces a voice-input failure (mic permission / transcription) in the chat error row. */
    fun onVoiceError(res: Int) {
        _ui.value = _ui.value.copy(error = null, errorRes = res)
    }

    fun confirmPendingFood() {
        val entry = _ui.value.pendingFood ?: return
        viewModelScope.launch {
            val unique = entry.copy(
                name = disambiguateFoodName(
                    entry.name,
                    container.foodRepository.existingFoodIdentityKeys(),
                )
            )
            container.foodRepository.addEntry(unique)
            val unit = EnergyUnit.fromStorage(container.prefs.energyUnit.first())
            val ctx = container.appContext
            val unitLabel = ctx.getString(if (unit == EnergyUnit.KJ) R.string.unit_kj else R.string.unit_kcal)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = ctx.getString(
                        R.string.coach_logged_food,
                        unique.name,
                        EnergyFormat.quantity(unique.calories, unit).toString(),
                        unitLabel,
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingFood = null)
        }
    }

    fun confirmPendingWeight() {
        val entry = _ui.value.pendingWeight ?: return
        viewModelScope.launch {
            container.weightRepository.addEntry(entry)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(
                        R.string.coach_logged_weight,
                        LocaleFormat.decimal(entry.weightKg, 1),
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingWeight = null)
        }
    }

    fun confirmPendingWater() {
        val entry = _ui.value.pendingWater ?: return
        viewModelScope.launch {
            container.waterRepository.add(entry)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(
                        R.string.coach_logged_water,
                        entry.milliliters,
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingWater = null)
        }
    }

    /** Discards whichever pending proposal is currently set. */
    fun discardPending() {
        viewModelScope.launch {
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(R.string.coach_logged_discard),
                )
            )
            _ui.value = _ui.value.copy(pendingFood = null, pendingWeight = null, pendingWater = null)
        }
    }

    fun resetConversation() {
        viewModelScope.launch { container.chatRepository.clear() }
    }

    fun dismissError() { _ui.value = _ui.value.copy(error = null, errorRes = null) }

    private fun logFailure(error: Throwable) {
        // Log is a stub in JVM unit tests; logging must never change the send's outcome.
        runCatching { Log.w(TAG, "Coach chat failed: ${coachFailureSummary(error)}") }
    }

    private companion object {
        const val TAG = "ChompassCoach"
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachViewModel(container) as T
    }
}

/**
 * Why a send failed, for Logcat: exception types (with causes) and the top code locations.
 * Messages are left out because provider/HTTP errors can carry URLs, response text or keys.
 */
internal fun coachFailureSummary(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(5).joinToString(" <- ") { it.javaClass.name }
    val frames = error.stackTrace.take(3).joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    return if (frames.isEmpty()) causes else "$causes at $frames"
}
