package app.chompass

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Security regression: `MainActivity` is exported for legitimate deep links,
 * so ANY installed app can deliver intent extras. Release builds must ignore
 * the debug seed/restore/reset surface, or an unprivileged app can overwrite
 * the diary with sample data, reset onboarding, or swap in the debug snapshot.
 *
 * `consumeDebugIntentExtras` takes an explicit `debugEnabled` so the release
 * path is testable here (unit tests compile against the debug variant).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MainActivityDebugExtrasGateTest {
    @Test
    fun releaseBuild_ignoresEveryDebugExtra() {
        val intent = Intent().apply {
            putExtra("reset_onboarding", true)
            putExtra("seed_test_data", true)
            putExtra("seed_full", true)
            putExtra("seed_busy_home", true)
            putExtra("seed_body_metrics", true)
            putExtra("seed_body_metrics_2y", true)
            putExtra("seed_keto_settings", true)
            putExtra("seed_macro_cycle", true)
            putExtra("macro_cycle", true)
            putExtra("seed_active_calories", true)
            putExtra("seed_gap_fixture", true)
            putExtra("reset_fill_missing_banner", true)
            putExtra("seed_over_goal", true)
            putExtra("restore_real_data", true)
            putExtra("clear_debug_activity", true)
            putExtra("set_gauge_mode", "hero")
            putExtra("set_show_steps", true)
            putExtra("set_show_active_calories", true)
            putExtra("set_show_resting_shade", true)
            putExtra("demo_ai", true)
            putExtra("run_entry_benchmark", true)
            putExtra("benchmark_count", 9)
            putExtra("run_relog_benchmark", true)
            putExtra("run_water_sip_benchmark", true)
            putExtra("run_local_entry_benchmark", true)
            putExtra("run_hub_benchmark", true)
            putExtra("run_day_switch_benchmark", true)
            putExtra("run_flip_benchmark", true)
            putExtra("run_ondevice_llm_test", true)
            putExtra("diagnose_health_connect", true)
            putExtra("preview_daily_summary", true)
            putExtra("seed_progressive_meal", true)
            putExtra("run_mealie_import_test", true)
            putExtra("debug_barcode", "3017620422003")
            putExtra("debug_snackbar_persistent", true)
            putExtra("keep_ai", true)
            putExtra("set_weight_reminder", "16:05")
            putExtra("clear_today_weight", true)
            putExtra("screenshot_appearance", "dark")
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = false)
        // Every flag/extra comes back at its default: no seed, no restore, no reset.
        assertEquals(DebugIntentActions(), actions)
    }

    @Test
    fun debugBuild_parsesAndStripsMacroCycleExtras() {
        val intent = Intent().apply {
            putExtra("seed_macro_cycle", true)
            putExtra("macro_cycle", true)
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertTrue(actions.seedMacroCycle)
        assertTrue(actions.seedFullMacroCycle)
        assertTrue(actions.hasSeedAction)
        assertFalse(intent.hasExtra("seed_macro_cycle"))
        assertFalse(intent.hasExtra("macro_cycle"))
    }

    @Test
    fun debugBuild_stillParsesAndStripsSeedExtras() {
        val intent = Intent().apply {
            putExtra("seed_test_data", true)
            putExtra("seed_full", true)
            putExtra("seed_busy_home", true)
            putExtra("restore_real_data", true)
            putExtra("reset_onboarding", true)
            putExtra("seed_progressive_meal", true)
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertTrue(actions.seedTestData)
        assertTrue(actions.seedFull)
        assertTrue(actions.seedBusyHome)
        assertTrue(actions.restoreRealData)
        assertTrue(actions.resetOnboarding)
        assertTrue(actions.seedProgressiveMeal)
        // Consumed so Activity.recreate() / onNewIntent re-delivery cannot re-fire.
        assertFalse(intent.hasExtra("seed_test_data"))
        assertFalse(intent.hasExtra("seed_full"))
        assertFalse(intent.hasExtra("seed_busy_home"))
        assertFalse(intent.hasExtra("restore_real_data"))
        assertFalse(intent.hasExtra("reset_onboarding"))
        assertFalse(intent.hasExtra("seed_progressive_meal"))
    }

    @Test
    fun debugBuild_keepsExistingGatedExtrasInertWithoutTheirExtra() {
        // demo_ai etc. were already gated per-field; keep honoring that even when
        // debug is enabled but the extra is absent.
        val intent = Intent()
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertFalse(actions.demoAi)
        assertFalse(actions.runOnDeviceLlmTest)
        assertFalse(actions.runMealieImportTest)
        assertEquals(3, actions.entryBenchmarkCount)
    }

    @Test
    fun debugBuild_parsesAndStripsScreenshotAppearance() {
        val intent = Intent().apply { putExtra("screenshot_appearance", "dark") }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertEquals("dark", actions.screenshotAppearance)
        // Stripped so Activity.recreate() cannot re-pin a stale appearance.
        assertFalse(intent.hasExtra("screenshot_appearance"))
    }

    @Test
    fun debugBuild_rejectsUnknownScreenshotAppearance() {
        for (value in listOf("purple", "", "DARK")) {
            val intent = Intent().apply { putExtra("screenshot_appearance", value) }
            assertEquals("", consumeDebugIntentExtras(intent, debugEnabled = true).screenshotAppearance)
            // Stripped either way: the extra is never re-delivered.
            assertFalse(intent.hasExtra("screenshot_appearance"))
        }
    }

    @Test
    fun debugBuild_parsesAndStripsScreenshotLean() {
        val intent = Intent().apply { putExtra("screenshot_lean", true) }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertTrue(actions.screenshotLean)
        assertFalse(intent.hasExtra("screenshot_lean"))
        // Absent extra stays off (idempotent on the capture relaunch).
        assertFalse(consumeDebugIntentExtras(Intent(), debugEnabled = true).screenshotLean)
    }

    @Test
    fun debugBuild_parsesAndClampsDemoHold() {
        val intent = Intent().apply {
            putExtra("demo_ai", true)
            putExtra("demo_ai_hold_ms", 90_000)
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertTrue(actions.demoAi)
        assertEquals(60_000, actions.demoAiHoldMs)
        assertFalse(intent.hasExtra("demo_ai_hold_ms"))
    }

    @Test
    fun debugBuild_parsesAndStripsDevicePassExtras() {
        val intent = Intent().apply {
            putExtra("debug_barcode", "3017620422003")
            putExtra("debug_snackbar_persistent", true)
            putExtra("keep_ai", true)
            putExtra("set_weight_reminder", "16:00")
            putExtra("clear_today_weight", true)
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertEquals("3017620422003", actions.debugBarcode)
        assertTrue(actions.debugSnackbarPersistent)
        assertTrue(actions.keepAiProfiles)
        assertEquals("16:00", actions.weightReminderTime)
        assertTrue(actions.clearTodayWeight)
        assertFalse(intent.hasExtra("debug_barcode"))
        assertFalse(intent.hasExtra("debug_snackbar_persistent"))
        assertFalse(intent.hasExtra("keep_ai"))
        assertFalse(intent.hasExtra("set_weight_reminder"))
        assertFalse(intent.hasExtra("clear_today_weight"))
    }

    @Test
    fun debugBuild_rejectsInvalidReminderTime() {
        val intent = Intent().apply { putExtra("set_weight_reminder", "25:99") }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertEquals("", actions.weightReminderTime)
        // Rejected values are still consumed: no re-fire on recreate.
        assertFalse(intent.hasExtra("set_weight_reminder"))
    }

    @Test
    fun releaseBuild_leavesIntentUntouched_butStaysInertOnReDelivery() {
        val intent = Intent().apply { putExtra("reset_onboarding", true) }
        consumeDebugIntentExtras(intent, debugEnabled = false)
        val first = consumeDebugIntentExtras(intent, debugEnabled = false)
        val second = consumeDebugIntentExtras(intent, debugEnabled = false)
        assertEquals(DebugIntentActions(), first)
        assertEquals(DebugIntentActions(), second)
        assertTrue(intent.hasExtra("reset_onboarding")) // harmless leftover
    }
}
