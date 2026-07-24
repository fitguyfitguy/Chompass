package app.chompass.services.ai

import kotlinx.coroutines.runBlocking
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.ui.home.EntryAnalysisPhase
import app.chompass.ui.home.FoodAnalysisProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAnalysisServiceProgressTest {

  private val foodJsonNoUnits = """
    {
      "name": "Pizza",
      "calories": 800,
      "protein": 30.0,
      "carbs": 90.0,
      "fat": 35.0,
      "serving_size_grams": 360.0,
      "unit_options": []
    }
  """.trimIndent()

  private val unitsJson = """
    {"unit_options":[{"unit":"slice","quantity":8.0,"grams_per_unit":45.0}]}
  """.trimIndent()

  @Test
  fun analyzeText_emitsPhasesParsedAndComplete_whenUnitsInferencePending() = runBlocking {
    val ops = mutableListOf<String>()
    val progress = mutableListOf<FoodAnalysisProgress>()
    val service = FoodAnalysisService(
      callAiDelegate = { _, _, op ->
        ops += op
        when (op) {
          "analyzeText" -> foodJsonNoUnits
          "inferServing" -> unitsJson
          else -> error("unexpected op: $op")
        }
      },
      inferenceModeForTest = ServingUnitInferenceMode.AI_CALL,
    )

    val result = service.analyzeText("pepperoni pizza") { progress += it }

    assertEquals(listOf("analyzeText", "inferServing"), ops)
    assertTrue(progress[0] is FoodAnalysisProgress.Phase)
    assertEquals(EntryAnalysisPhase.Preparing, (progress[0] as FoodAnalysisProgress.Phase).phase)
    assertEquals(EntryAnalysisPhase.CallingAi, (progress[1] as FoodAnalysisProgress.Phase).phase)
    assertEquals(EntryAnalysisPhase.Parsing, (progress[2] as FoodAnalysisProgress.Phase).phase)
    val parsed = progress[3] as FoodAnalysisProgress.Parsed
    assertEquals("Pizza", parsed.analysis.name)
    assertTrue(parsed.unitsPending)
    val complete = progress.last() as FoodAnalysisProgress.Complete
    assertEquals(1, complete.analysis.servingUnitOptions.size)
    assertEquals("slice", complete.analysis.servingUnitOptions.first().unit)
    assertEquals("Pizza", result.name)
  }
}
