package app.chompass.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.HomeCalorieDisplayMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact hero gate: no `CALORIES` title; the goal caption and the active-burn
 * caption hang off the eaten number inside the bowl; the chips sit in the empty
 * wedge at the arc's top left (under the section's "Today" label) and the
 * calories-left caption in the matching wedge at the top right.
 *
 * Because the chips and the captions are overlaid on the arc, the hero does not
 * grow when they appear: 150dp flat, 151dp with a burn caption. The old stacked
 * layout measured ~244dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w411dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalorieHeroLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val goalText = "of 2,000 kcal"
    private val remainingText = "766 left"
    private val eatenA11y = "Eaten 1,234 kcal"
    private val untrackedA11y = "Mark this day as not tracked."
    private val burnA11y = "Active burn 380"

    private fun setHero(
        width: Dp = 411.dp,
        fontScale: Float? = null,
        withDayTypeChip: Boolean = true,
        withUntrackedChip: Boolean = withDayTypeChip,
        withBurn: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme {
                val content: @Composable () -> Unit = {
                    Box(Modifier.width(width)) {
                        CalorieHero(
                            current = 1234,
                            baseGoal = 2000,
                            activeCalories = 0,
                            displayMode = HomeCalorieDisplayMode.STATIC,
                            dayTypeLabel = if (withDayTypeChip) "Training day" else null,
                            onDayTypeClick = if (withDayTypeChip) ({}) else null,
                            untracked = false,
                            onUntrackedClick = if (withUntrackedChip) ({}) else null,
                            showActiveCalories = withBurn,
                            liveActiveBurn = if (withBurn) 380 else 0,
                            freezeProgress = true,
                            modifier = Modifier.testTag("hero"),
                        )
                    }
                }
                if (fontScale == null) {
                    content()
                } else {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                        content()
                    }
                }
            }
        }
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    private fun density(): Density = composeRule.density

    private fun dp(value: Float): Float = with(density()) { value.dp.toPx() }

    private fun heroBounds(): Rect = bounds(composeRule.onNodeWithTag("hero"))

    private fun numberBounds(): Rect = bounds(composeRule.onNodeWithContentDescription(eatenA11y))

    private fun goalBounds(): Rect = bounds(composeRule.onNodeWithText(goalText))

    private fun burnBounds(): Rect = bounds(composeRule.onNodeWithContentDescription(burnA11y))

    private fun chipBounds(): Rect = bounds(composeRule.onNodeWithContentDescription(untrackedA11y))

    /** [top] hangs off [above]'s bottom edge and shares its centre. */
    private fun assertStackedAndCentred(above: Rect, top: Rect, label: String) {
        assert(top.top >= above.bottom - 1f) {
            "$label does not sit under its caption: $top vs ${above.bottom}"
        }
        assert(top.top - above.bottom <= dp(6f)) {
            "$label drifted ${((top.top - above.bottom) / density().density).roundToInt()}dp below its caption"
        }
        assert(abs(top.center.x - above.center.x) <= 2f) {
            "$label is not centred: center=${top.center.x} vs ${above.center.x}"
        }
    }

    @Test
    fun goalSitsUnderTheNumber_andRemainingInTheTopRightWedge() {
        setHero()

        composeRule.onNodeWithText("CALORIES").assertDoesNotExist()

        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        val remaining = bounds(composeRule.onNodeWithText(remainingText))

        assertStackedAndCentred(number, goal, "goal caption")
        assert(remaining.top < number.top) {
            "remaining caption must clear the number: $remaining vs $number"
        }
        assert(remaining.left > number.right) {
            "remaining caption must sit in the right wedge: $remaining vs $number"
        }

        assert(hero.height <= dp(154f)) {
            "hero is ${(hero.height / density().density).roundToInt()}dp; expected 150dp " +
                "(8 + 130 arc + 12)"
        }
    }

    @Test
    fun chipsDoNotAddHeight() {
        setHero(withDayTypeChip = false, withUntrackedChip = false)

        val hero = heroBounds()
        assert(hero.height <= dp(154f)) {
            "chipless hero is ${(hero.height / density().density).roundToInt()}dp; the chips are " +
                "overlaid on the arc, so this must stay 150dp"
        }
    }

    @Test
    fun chipSitsInTheTopLeftWedge_andBurnUnderTheGoal() {
        // A single chip so its own left edge is the row's left edge.
        setHero(withDayTypeChip = false, withUntrackedChip = true, withBurn = true)

        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        val chip = chipBounds()
        val burn = burnBounds()

        assert(abs(chip.left - (hero.left + dp(16f))) <= 1f) {
            "chip is not in the top-left wedge: chip.left=${chip.left} hero.left=${hero.left}"
        }
        assert(chip.top < number.top) { "chip must sit above the bowl: $chip vs $number" }
        assert(chip.right < number.left) { "chip must clear the number: $chip vs $number" }

        assertStackedAndCentred(goal, burn, "burn caption")

        assert(hero.height <= dp(155f)) {
            "hero with a burn caption is ${(hero.height / density().density).roundToInt()}dp; expected 151dp"
        }
    }

    @Test
    fun fontScale130_narrowPhone_keepsEverythingInside() {
        setHero(width = 360.dp, fontScale = 1.3f, withBurn = true)

        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        val remaining = bounds(composeRule.onNodeWithText(remainingText))
        val chip = chipBounds()
        val burn = burnBounds()

        assertStackedAndCentred(number, goal, "goal caption")

        listOf(
            "goal" to goal,
            "remaining" to remaining,
            "number" to number,
            "chip" to chip,
            "burn" to burn,
        ).forEach { (label, b) ->
            assert(b.width > 0f && b.height > 0f) { "$label collapsed: $b" }
            assert(b.left >= hero.left - 1f && b.right <= hero.right + 1f) {
                "$label escapes the hero horizontally: $b vs $hero"
            }
            assert(b.top >= hero.top - 1f && b.bottom <= hero.bottom + 1f) {
                "$label escapes the hero vertically: $b vs $hero"
            }
        }

        assert(remaining.left > number.right) { "remaining overlaps the number: $remaining vs $number" }
        assert(chip.right < number.left) { "chip overlaps the number: $chip vs $number" }
    }
}
