package app.chompass.services.update

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Update-check comparison contract: Forgejo tag_name parsing (v-prefix, junk
 * tags) and the segment-wise semantic compare that decides the badge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidUpdateCheckerTest {
    private fun releaseJson(tag: String): String =
        """{"id":1,"tag_name":"$tag","name":"Chompass $tag","draft":false,"prerelease":false}"""

    @Test
    fun `parses tag_name and strips the v prefix`() {
        assertEquals("5.1.0", AndroidUpdateChecker.parseLatestVersion(releaseJson("v5.1.0")))
        assertEquals("5.1.0", AndroidUpdateChecker.parseLatestVersion(releaseJson("5.1.0")))
    }

    @Test
    fun `rejects missing empty and non-numeric tags`() {
        assertNull(AndroidUpdateChecker.parseLatestVersion("""{"tag_name":""}"""))
        assertNull(AndroidUpdateChecker.parseLatestVersion("""{"tag_name":"nightly"}"""))
        assertNull(AndroidUpdateChecker.parseLatestVersion("""{"message":"Not found"}"""))
        assertNull(AndroidUpdateChecker.parseLatestVersion("not json at all"))
    }

    @Test
    fun `newer release is detected across segment counts`() {
        assertTrue(AndroidUpdateChecker.isNewer("5.2.0", "5.1.0"))
        assertTrue(AndroidUpdateChecker.isNewer("5.2", "5.1.9"))
        assertTrue(AndroidUpdateChecker.isNewer("6.0", "5.9.9"))
        assertTrue(AndroidUpdateChecker.isNewer("5.1.1", "5.1"))
    }

    @Test
    fun `equal and older versions are not newer`() {
        assertFalse(AndroidUpdateChecker.isNewer("5.1.0", "5.1.0"))
        assertFalse(AndroidUpdateChecker.isNewer("5.1", "5.1.0"))
        assertFalse(AndroidUpdateChecker.isNewer("5.0.9", "5.1.0"))
        assertFalse(AndroidUpdateChecker.isNewer("Unknown", "5.1.0"))
    }
}
