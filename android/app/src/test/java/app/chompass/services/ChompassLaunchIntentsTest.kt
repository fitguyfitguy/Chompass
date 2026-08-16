package app.chompass.services

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #27: notification taps carry a `chompass://go/<dest>` destination
 * through the launcher-alias intent (never MainActivity + CLEAR_TOP directly,
 * per the app's intent-invariant matrix), and reminders map per channel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChompassLaunchIntentsTest {
    @Test
    fun openApp_keepsLauncherAliasShape_whenDestinationIsSet() {
        val context = RuntimeEnvironment.getApplication()
        val withDest = ChompassLaunchIntents.openApp(context, destination = "progress")

        assertNotNull(withDest.component)
        assertEquals(Intent.ACTION_MAIN, withDest.action)
        assertTrue(withDest.categories!!.contains(Intent.CATEGORY_LAUNCHER))
        val expectFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectFlags, withDest.flags and expectFlags)
        assertEquals(Uri.parse("chompass://go/progress"), withDest.data)
    }

    @Test
    fun openApp_hasNoData_whenDestinationIsNull() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(ChompassLaunchIntents.openApp(context).data)
    }

    @Test
    fun destinationForChannel_mapsWeightAndGoalToProgress_only() {
        assertEquals("progress", NotificationService.destinationForChannel(NotificationService.CHANNEL_WEIGHT_GOAL))
        assertEquals("progress", NotificationService.destinationForChannel(NotificationService.CHANNEL_WEIGHT_LOG))
        assertEquals("progress", NotificationService.destinationForChannel(NotificationService.CHANNEL_BODY_FAT_LOG))
        assertNull(NotificationService.destinationForChannel(NotificationService.CHANNEL_WATER))
        assertNull(NotificationService.destinationForChannel(NotificationService.CHANNEL_STREAK))
        assertNull(NotificationService.destinationForChannel(NotificationService.CHANNEL_DAILY))
    }
}
