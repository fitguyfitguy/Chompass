package app.chompass.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.ui.navigation.LocalLaunchFillEpoch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Layout gate for every [CalorieHero] variant: the compact default keeps the goal
 * under the eaten number with chips in the arc's top-left wedge, and no variant
 * lets localized text escape its own bounds on a 360dp phone at 1.3x font scale.
 *
 * Heights: default 150dp, compact arc ~124dp, side gauge ~126dp, horizontal
 * ~128dp with a burn caption. The old stacked default measured ~244dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w411dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeCalorieHeroLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val goalText = "of 2,000 kcal"
    private val remainingText = "766 left"
    private val eatenA11y = "Eaten 1,234 kcal"
    private val untrackedA11y = "Mark this day as not tracked."
    private val burnA11y = "Active burn 380"

    private fun setHero(
        layout: HomeCalorieHeroLayout?,
        width: Dp = 411.dp,
        fontScale: Float? = null,
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
                            dayTypeLabel = "Training day",
                            onDayTypeClick = {},
                            untracked = false,
                            onUntrackedClick = {},
                            showActiveCalories = withBurn,
                            liveActiveBurn = if (withBurn) 380 else 0,
                            freezeProgress = true,
                            layout = layout,
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

    private fun remainingBounds(): Rect = bounds(composeRule.onNodeWithText(remainingText))

    private fun chipBounds(): Rect = bounds(composeRule.onNodeWithContentDescription(untrackedA11y))

    private fun burnBounds(): Rect = bounds(composeRule.onNodeWithContentDescription(burnA11y))

    /** [top] hangs off [above]'s bottom edge and shares its centre. */
    private fun assertStackedAndCentred(above: Rect, top: Rect, label: String) {
        assert(top.top >= above.bottom - 1f) {
            "$label does not sit under the caption above it: ${top.top} vs ${above.bottom}"
        }
        assert(top.top - above.bottom <= dp(8f)) {
            "$label drifted ${((top.top - above.bottom) / density().density).roundToInt()}dp below"
        }
        assert(abs(top.center.x - above.center.x) <= 2f) {
            "$label is not centred: center=${top.center.x} vs ${above.center.x}"
        }
    }

    private fun assertInsideHero(label: String, b: Rect, hero: Rect) {
        assert(b.width > 0f && b.height > 0f) { "$label collapsed: $b" }
        assert(b.left >= hero.left - 1f && b.right <= hero.right + 1f) {
            "$label escapes the hero horizontally: $b vs $hero"
        }
        assert(b.top >= hero.top - 1f && b.bottom <= hero.bottom + 1f) {
            "$label escapes the hero vertically: $b vs $hero"
        }
    }

    @Test
    fun default_keepsGoalUnderTheNumber_andChipsInTheLeftWedge() {
        setHero(layout = null)

        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        val remaining = remainingBounds()
        val chip = chipBounds()

        assertStackedAndCentred(number, goal, "goal caption")
        assert(remaining.top < number.top) { "remaining caption must clear the number: $remaining" }
        assert(remaining.left > number.right) {
            "remaining caption must sit in the right wedge: $remaining vs $number"
        }
        assert(abs(chip.left - (hero.left + dp(16f))) <= 1f) {
            "chips are not in the left wedge: chip.left=${chip.left} hero.left=${hero.left}"
        }
        assert(chip.right < number.left) { "chip must clear the number: $chip vs $number" }

        assert(hero.height <= dp(154f)) {
            "default hero is ${(hero.height / density().density).roundToInt()}dp; expected 150dp"
        }
    }

    @Test
    fun compactArc_keepsGoalAndBurnInTheBowl() {
        setHero(layout = HomeCalorieHeroLayout.COMPACT_ARC, withBurn = true)

        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        val burn = burnBounds()

        assertStackedAndCentred(number, goal, "goal caption")
        assertStackedAndCentred(goal, burn, "burn caption")

        assert(hero.height <= dp(134f)) {
            "compact arc is ${(hero.height / density().density).roundToInt()}dp; expected 124dp"
        }
    }

    private fun sideGaugeAssertions() {
        val hero = heroBounds()
        val number = numberBounds()
        val goal = goalBounds()
        // The side-gauge column is left-aligned: the goal hangs under the number
        // on the same start edge rather than sharing its centre.
        assert(goal.top >= number.bottom - 1f) { "goal must sit under the number: $goal vs $number" }
        assert(abs(goal.left - number.left) <= 1f) {
            "goal must start on the number's edge: goal.left=${goal.left} number.left=${number.left}"
        }
        assertInsideHero("number", number, hero)
        assertInsideHero("goal", goal, hero)
        assertInsideHero("remaining", remainingBounds(), hero)
        assertInsideHero("chip", chipBounds(), hero)
        assertInsideHero("burn", burnBounds(), hero)
    }

    @Test
    fun sideGauge_staysInsideAtFontScale130() {
        setHero(layout = HomeCalorieHeroLayout.SIDE_GAUGE, width = 360.dp, fontScale = 1.3f, withBurn = true)
        sideGaugeAssertions()
    }

    @Test
    fun horizontal_staysInsideAtFontScale130() {
        setHero(
            layout = HomeCalorieHeroLayout.COMPACT_HORIZONTAL,
            width = 360.dp,
            fontScale = 1.3f,
            withBurn = true,
        )

        val hero = heroBounds()
        listOf(
            "number" to numberBounds(),
            "goal" to goalBounds(),
            "remaining" to remainingBounds(),
            "chip" to chipBounds(),
            "burn" to burnBounds(),
        ).forEach { (label, b) -> assertInsideHero(label, b, hero) }

        assert(hero.height <= dp(180f)) {
            "horizontal variant is ${(hero.height / density().density).roundToInt()}dp; expected ~128dp"
        }
    }

    @Test
    fun compactHorizontal_daySwitchSpringsTheFill() {
        val current = mutableStateOf(400)
        composeRule.setContent {
            CompositionLocalProvider(LocalLaunchFillEpoch provides 1) {
                MaterialTheme {
                    CalorieHero(
                        current = current.value,
                        baseGoal = 2000,
                        activeCalories = 0,
                        displayMode = HomeCalorieDisplayMode.STATIC,
                        freezeProgress = false,
                        layout = HomeCalorieHeroLayout.COMPACT_HORIZONTAL,
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()
        assertClose(barProgress(), 0.2f, 0.02f)

        composeRule.mainClock.autoAdvance = false
        current.value = 1600
        // Semantics fetch registers the recomposer. The frozen test snaps to
        // 0.8 on this same pump; here the first 16ms must still be the old fill.
        barProgress()
        composeRule.mainClock.advanceTimeBy(16)
        val mid = barProgress()
        assert(mid < 0.5f && mid > 0.18f) {
            "day switch snapped or reset: progress=$mid"
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()
        assertClose(barProgress(), 0.8f, 0.02f)
        composeRule.onNodeWithContentDescription("Eaten 1,600 kcal").assertExists()
    }

    @Test
    fun compactHorizontal_frozenFillSnaps() {
        val current = mutableStateOf(400)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalLaunchFillEpoch provides 1) {
                MaterialTheme {
                    CalorieHero(
                        current = current.value,
                        baseGoal = 2000,
                        activeCalories = 0,
                        displayMode = HomeCalorieDisplayMode.STATIC,
                        freezeProgress = true,
                        layout = HomeCalorieHeroLayout.COMPACT_HORIZONTAL,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertClose(barProgress(), 0.2f, 0.001f)

        current.value = 1600
        barProgress()
        composeRule.mainClock.advanceTimeByFrame()
        assertClose(barProgress(), 0.8f, 0.001f)
    }

    @Test
    fun defaultArc_daySwitchSpringsTheFill() = assertDaySwitchSprings(null)

    @Test
    fun sideGauge_daySwitchSpringsTheFill() = assertDaySwitchSprings(HomeCalorieHeroLayout.SIDE_GAUGE)

    @Test
    fun compactArc_daySwitchSpringsTheFill() = assertDaySwitchSprings(HomeCalorieHeroLayout.COMPACT_ARC)

    @Test
    fun defaultArc_frozenFillSnaps() = assertFrozenFillSnaps(null)

    private fun assertDaySwitchSprings(layout: HomeCalorieHeroLayout?) {
        val current = mutableStateOf(400)
        composeRule.setContent {
            CompositionLocalProvider(LocalLaunchFillEpoch provides 1) {
                MaterialTheme {
                    CalorieHero(
                        current = current.value,
                        baseGoal = 2000,
                        activeCalories = 0,
                        displayMode = HomeCalorieDisplayMode.STATIC,
                        freezeProgress = false,
                        layout = layout,
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()
        assertClose(barProgress(), 0.2f, 0.02f)

        composeRule.mainClock.autoAdvance = false
        current.value = 1600
        barProgress()
        composeRule.mainClock.advanceTimeBy(16)
        val mid = barProgress()
        assert(mid < 0.5f && mid > 0.18f) {
            "day switch snapped or reset: progress=$mid"
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()
        assertClose(barProgress(), 0.8f, 0.02f)
        composeRule.onNodeWithContentDescription("Eaten 1,600 kcal").assertExists()
    }

    private fun assertFrozenFillSnaps(layout: HomeCalorieHeroLayout?) {
        val current = mutableStateOf(400)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalLaunchFillEpoch provides 1) {
                MaterialTheme {
                    CalorieHero(
                        current = current.value,
                        baseGoal = 2000,
                        activeCalories = 0,
                        displayMode = HomeCalorieDisplayMode.STATIC,
                        freezeProgress = true,
                        layout = layout,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertClose(barProgress(), 0.2f, 0.001f)

        current.value = 1600
        barProgress()
        composeRule.mainClock.advanceTimeByFrame()
        assertClose(barProgress(), 0.8f, 0.001f)
    }

    private fun barProgress(): Float {
        val node = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    private fun assertClose(actual: Float, expected: Float, tolerance: Float) {
        assert(abs(actual - expected) <= tolerance) {
            "progress $actual not within $tolerance of $expected"
        }
    }
}
