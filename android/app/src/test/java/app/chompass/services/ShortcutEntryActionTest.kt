package app.chompass.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutEntryActionTest {
    @Test
    fun fromAction_matchesStableStrings() {
        assertEquals(
            ShortcutEntryAction.CAMERA,
            ShortcutEntryAction.fromAction("app.chompass.action.SHORTCUT_CAMERA"),
        )
        assertEquals(
            ShortcutEntryAction.VOICE,
            ShortcutEntryAction.fromAction("app.chompass.action.SHORTCUT_VOICE"),
        )
        assertEquals(
            ShortcutEntryAction.BARCODE,
            ShortcutEntryAction.fromAction("app.chompass.action.SHORTCUT_BARCODE"),
        )
        assertNull(ShortcutEntryAction.fromAction(IntentMain))
        assertNull(ShortcutEntryAction.fromAction(null))
    }

    @Test
    fun fromIntentExtra_acceptsAliases() {
        assertEquals(ShortcutEntryAction.CAMERA, ShortcutEntryAction.fromIntentExtra("camera"))
        assertEquals(ShortcutEntryAction.CAMERA, ShortcutEntryAction.fromIntentExtra("photo"))
        assertEquals(ShortcutEntryAction.CAMERA, ShortcutEntryAction.fromIntentExtra("camera_note"))
        assertEquals(ShortcutEntryAction.VOICE, ShortcutEntryAction.fromIntentExtra("voice"))
        assertEquals(ShortcutEntryAction.VOICE, ShortcutEntryAction.fromIntentExtra("MIC"))
        assertEquals(ShortcutEntryAction.BARCODE, ShortcutEntryAction.fromIntentExtra("barcode"))
        assertEquals(ShortcutEntryAction.BARCODE, ShortcutEntryAction.fromIntentExtra("scan"))
        assertNull(ShortcutEntryAction.fromIntentExtra("settings"))
        assertNull(ShortcutEntryAction.fromIntentExtra(null))
    }

    companion object {
        private const val IntentMain = "android.intent.action.MAIN"
    }
}
