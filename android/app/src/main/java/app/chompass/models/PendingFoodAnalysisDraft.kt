package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class PendingFoodAnalysisDraft(
    val analysis: FoodAnalysis,
    val imageFilename: String? = null,
    val source: FoodSource? = null,
    /**
     * Diary day the review sheet was opened for. Persisted so a process-death
     * restore re-targets [app.chompass.ui.home.HomeViewModel]'s selected date
     * instead of logging to today (Codeberg #16 family: "entry landed on
     * today's log" after the app was killed while the sheet was open).
     * Defaults to now for legacy drafts, matching the pre-fix behavior.
     */
    @Serializable(with = LocalDateSerializer::class)
    val targetDate: LocalDate = LocalDate.now(),
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)
