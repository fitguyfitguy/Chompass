package app.chompass.services

/**
 * Launcher long-press shortcuts that deep-link into Home food-entry flows.
 * Stable action strings are intentional — OEM launchers and pinned shortcuts
 * may keep them across app updates.
 */
enum class ShortcutEntryAction(val action: String) {
    CAMERA("app.chompass.action.SHORTCUT_CAMERA"),
    VOICE("app.chompass.action.SHORTCUT_VOICE"),
    BARCODE("app.chompass.action.SHORTCUT_BARCODE");

    companion object {
        fun fromAction(action: String?): ShortcutEntryAction? =
            entries.firstOrNull { it.action == action }

        fun fromIntentExtra(extra: String?): ShortcutEntryAction? = when (extra?.lowercase()) {
            "camera", "photo", "camera_note" -> CAMERA
            "voice", "mic" -> VOICE
            "barcode", "scan" -> BARCODE
            else -> null
        }
    }
}
