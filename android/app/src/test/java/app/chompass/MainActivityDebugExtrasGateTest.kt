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
 * Security regression (docs/SECURITY_HARDENING_PLAN.md P1-1): `MainActivity` is
 * exported for legitimate deep links, so ANY installed app can deliver intent
 * extras. Release builds must ignore the debug seed/restore/reset surface, or
 * an unprivileged app can overwrite the diary with sample data, reset
 * onboarding, or swap in the debug snapshot.
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
            putExtra("seed_active_calories", true)
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
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = false)
        // Every flag/extra comes back at its default: no seed, no restore, no reset.
        assertEquals(DebugIntentActions(), actions)
    }

    @Test
    fun debugBuild_stillParsesAndStripsSeedExtras() {
        val intent = Intent().apply {
            putExtra("seed_test_data", true)
            putExtra("seed_full", true)
            putExtra("seed_busy_home", true)
            putExtra("restore_real_data", true)
            putExtra("reset_onboarding", true)
        }
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertTrue(actions.seedTestData)
        assertTrue(actions.seedFull)
        assertTrue(actions.seedBusyHome)
        assertTrue(actions.restoreRealData)
        assertTrue(actions.resetOnboarding)
        // Consumed so Activity.recreate() / onNewIntent re-delivery cannot re-fire.
        assertFalse(intent.hasExtra("seed_test_data"))
        assertFalse(intent.hasExtra("seed_full"))
        assertFalse(intent.hasExtra("seed_busy_home"))
        assertFalse(intent.hasExtra("restore_real_data"))
        assertFalse(intent.hasExtra("reset_onboarding"))
    }

    @Test
    fun debugBuild_keepsExistingGatedExtrasInertWithoutTheirExtra() {
        // demo_ai etc. were already gated per-field; keep honoring that even when
        // debug is enabled but the extra is absent.
        val intent = Intent()
        val actions = consumeDebugIntentExtras(intent, debugEnabled = true)
        assertFalse(actions.demoAi)
        assertFalse(actions.runOnDeviceLlmTest)
        assertEquals(3, actions.entryBenchmarkCount)
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
