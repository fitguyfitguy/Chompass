package app.chompass.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.data.disambiguateFoodName
import app.chompass.models.ChatMessage
import app.chompass.models.FoodEntry
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import app.chompass.services.ai.AiError
import app.chompass.services.ai.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val suggestions: List<Int> = emptyList(),
    /** A write action the coach proposed, awaiting explicit user confirmation.
     *  At most one of these is non-null at a time. */
    val pendingFood: FoodEntry? = null,
    val pendingWeight: WeightEntry? = null,
    val pendingWater: WaterEntry? = null,
)

class CoachViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CoachUiState())
    val ui: StateFlow<CoachUiState> = _ui.asStateFlow()

    init {
        container.chatRepository.messages
            .onEach { _ui.value = _ui.value.copy(messages = it) }
            .launchIn(viewModelScope)

        // Live-subscribe to profile so chips update when the user changes goal in Settings.
        container.profileRepository.profile
            .onEach { p -> _ui.value = _ui.value.copy(suggestions = chipsFor(p?.goal)) }
            .launchIn(viewModelScope)
    }

    private fun chipsFor(goal: WeightGoal?): List<Int> = when (goal) {
        WeightGoal.LOSE -> listOf(
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_lose_faster,
            R.string.coach_chip_eating_too_much,
            R.string.coach_chip_what_dinner
        )
        WeightGoal.GAIN -> listOf(
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_gain_healthy,
            R.string.coach_chip_eating_enough,
            R.string.coach_chip_high_protein
        )
        WeightGoal.MAINTAIN -> listOf(
            R.string.coach_chip_holding_weight,
            R.string.coach_chip_average_intake,
            R.string.coach_chip_macro_suggestions,
            R.string.coach_chip_trend
        )
        else -> listOf(
            R.string.coach_chip_doing_this_week,
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_log_advice
        )
    }

    fun send(userText: String, imageBytes: ByteArray? = null, thumbnailBytes: ByteArray? = null) {
        val trimmed = userText.trim()
        if ((trimmed.isBlank() && imageBytes == null) || _ui.value.sending) return
        val text = trimmed.ifEmpty { "Analyze this image." }
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
                _ui.value = _ui.value.copy(sending = false, error = e.userMessage(container.appContext))
            } catch (e: Throwable) {
                _ui.value = _ui.value.copy(
                    sending = false,
                    error = e.localizedMessage,
                    errorRes = if (e.localizedMessage.isNullOrBlank()) R.string.coach_chat_failed else null
                )
            }
        }
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
            container.chatRepository.append(
                ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Logged: ${unique.name} (${unique.calories} kcal).")
            )
            _ui.value = _ui.value.copy(pendingFood = null)
        }
    }

    fun confirmPendingWeight() {
        val entry = _ui.value.pendingWeight ?: return
        viewModelScope.launch {
            container.weightRepository.addEntry(entry)
            container.chatRepository.append(
                ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Logged weight: ${String.format(java.util.Locale.US, "%.1f", entry.weightKg)} kg.")
            )
            _ui.value = _ui.value.copy(pendingWeight = null)
        }
    }

    fun confirmPendingWater() {
        val entry = _ui.value.pendingWater ?: return
        viewModelScope.launch {
            container.waterRepository.add(entry)
            container.chatRepository.append(
                ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Logged water: ${entry.milliliters} ml.")
            )
            _ui.value = _ui.value.copy(pendingWater = null)
        }
    }

    /** Discards whichever pending proposal is currently set. */
    fun discardPending() {
        viewModelScope.launch {
            container.chatRepository.append(ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Okay, I won't log that."))
            _ui.value = _ui.value.copy(pendingFood = null, pendingWeight = null, pendingWater = null)
        }
    }

    fun resetConversation() {
        viewModelScope.launch { container.chatRepository.clear() }
    }

    fun dismissError() { _ui.value = _ui.value.copy(error = null, errorRes = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachViewModel(container) as T
    }
}
