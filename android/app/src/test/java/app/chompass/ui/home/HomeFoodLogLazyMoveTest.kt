package app.chompass.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.UUID

/**
 * Codeberg #56 repro: after editing an entry's Meal Type, the moved row's card
 * vanishes from the Home LazyColumn until restart, even though state, groups,
 * and row composition are all correct (verified by on-device trace + video:
 * the section totals count both entries but only one card is drawn).
 *
 * This test mirrors the Home food-log LazyColumn structure (for-loop over
 * meal groups, `item(key = "header-...")`, `itemsIndexed(key = entry.id)`)
 * and moves an entry between groups, then asserts both rows are on screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFoodLogLazyMoveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(id: UUID, name: String, meal: MealType, ts: Instant) = FoodEntry(
        id = id,
        name = name,
        calories = 100,
        protein = 1.0,
        carbs = 1.0,
        fat = 1.0,
        timestamp = ts,
        source = FoodSource.TEXT_INPUT,
        mealType = meal,
    )

    @Test
    fun movedRowIsStillRendered_afterMealTypeEdit() {
        movedRowRepro(row = { Text(it, Modifier.fillMaxWidth()) })
    }

    @Test
    fun movedRowIsStillRendered_afterMealTypeEdit_scrolled_behindSheet() {
        movedRowRepro(fillerItems = 30, changeBehindSheet = true)
    }

    @Test
    fun movedRowIsStillRendered_afterMealTypeEdit_boxWithConstraintsRow() {
        // Mirrors SwipeableFoodRow: BoxWithConstraints (SubcomposeLayout) +
        // offset-modifier foreground box inside the moved lazy item.
        movedRowRepro(row = { name ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val maxSwipePx = with(LocalDensity.current) { maxWidth.toPx() } * 0.72f
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, 0) }
                            .pointerInput(name, maxSwipePx) { }
                    ) {
                        Text(name, Modifier.fillMaxWidth())
                    }
                }
            }
        })
    }

    private fun movedRowRepro(
        row: @Composable (String) -> Unit = { Text(it, Modifier.fillMaxWidth()) },
        fillerItems: Int = 0,
        changeBehindSheet: Boolean = false,
    ) {
        val breakfastId = UUID.randomUUID()
        val movedId = UUID.randomUUID()
        val base = Instant.parse("2026-08-25T15:30:00Z")
        var entries by mutableStateOf(
            listOf(
                entry(breakfastId, "Milk chocolate", MealType.BREAKFAST, base),
                entry(movedId, "Bread roll", MealType.LUNCH, base.plusSeconds(19)),
            )
        )
        val listState = androidx.compose.foundation.lazy.LazyListState()
        val composeCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

        composeRule.setContent {
            HomeFoodLogTestList(entries, row, fillerItems, changeBehindSheet, listState, composeCount)
        }

        if (fillerItems > 0) {
            // Scroll the food log into view like on device (week strip + cards above).
            composeRule.runOnUiThread {
                kotlinx.coroutines.runBlocking { listState.scrollToItem(fillerItems) }
            }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("Bread roll").assertExists()
        composeRule.onNodeWithText("HEADER-LUNCH").assertExists()

        if (changeBehindSheet) {
            // Mutate while the sheet is up, then dismiss like EditFoodEntrySheet.
            composeRule.runOnIdle {
                entries = entries.map {
                    if (it.id == movedId) {
                        it.copy(mealType = MealType.BREAKFAST, timestamp = base.plusSeconds(15))
                    } else it
                }
            }
            composeRule.onNodeWithText("Close sheet").performClick()
        } else {

        // Meal Type edit: LUNCH -> BREAKFAST, timestamp rewritten to the meal's
        // default time (matches the on-device trace: ts ...464 -> ...460).
            entries = entries.map {
                if (it.id == movedId) it.copy(mealType = MealType.BREAKFAST, timestamp = base.plusSeconds(15))
                else it
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        // Both rows must be present and visible after the move.
        composeRule.onNodeWithText("Bread roll").assertExists()
        composeRule.onNodeWithText("Milk chocolate").assertExists()

        // #56 fix contract: the moved row must be re-composed under the
        // destination group (dispose + compose), not moved in place — the
        // move-in-place path is what dropped the card on device.
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(
                "moved row must be composed twice (LUNCH then BREAKFAST)",
                2, composeCount["Bread roll"],
            )
            org.junit.Assert.assertEquals(
                "unmoved row must not be recomposed",
                1, composeCount["Milk chocolate"],
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeFoodLogTestList(
    entries: List<FoodEntry>,
    row: @Composable (String) -> Unit,
    fillerItems: Int,
    sheetOverlay: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    composeCount: java.util.concurrent.ConcurrentHashMap<String, Int>,
) {
    val mealGroups = remember(entries) { foodLogMealGroups(entries, FoodLogSortOrder.STANDARD) }
    var showSheet by remember { mutableStateOf(sheetOverlay) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            repeat(fillerItems) { i ->
                item(key = "filler-$i") { Text("filler-$i", Modifier.fillMaxWidth()) }
            }
            for (group in mealGroups) {
                item(key = "header-${group.id}") {
                    Text("HEADER-${group.meal.name}", Modifier.fillMaxWidth())
                }
                // Group-scoped keys — mirrors the #56 fix in HomeScreen.
                itemsIndexed(group.entries, key = { _, entry -> "${group.id}:${entry.id}" }) { _, entry ->
                    androidx.compose.runtime.DisposableEffect(entry.id, group.id) {
                        composeCount.merge(entry.name, 1, Int::plus)
                        onDispose { }
                    }
                    row(entry.name)
                }
            }
        }
        if (showSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                Text("Close sheet")
            }
        }
    }
}
