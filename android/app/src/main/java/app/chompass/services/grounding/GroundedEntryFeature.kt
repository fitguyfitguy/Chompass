package app.chompass.services.grounding

/**
 * Product gate for optional grounded food entry.
 *
 * **WIP — not production.** Keep [ENABLED] **false** until every readiness item in
 * `docs/GROUNDED_ENTRY.md` is met (text grounded still trails single-shot on
 * WMAPE / ±20% kcal as of 2026-07-22). The offline USDA SQLite ships in **debug**
 * APKs only (`src/debug/assets`); release builds omit it. Heavy grounded Kotlin
 * (orchestrator, USDA index, tool loop, sheets) is compiled from `src/grounded`
 * for debug/test and replaced by `src/groundedStubs` in release. Entry UI stays
 * hidden while this flag is false.
 */
object GroundedEntryFeature {
    /** Do not set true for release until docs/GROUNDED_ENTRY.md checklist is green. */
    const val ENABLED: Boolean = false

    /**
     * On-device LiteRT providers cannot run the cloud tool loop. When grounded is
     * enabled for cloud BYOK, keep this **false** unless the deterministic path
     * has passed the same regression suite (see readiness checklist item 5).
     */
    const val ALLOW_ON_DEVICE: Boolean = false

    /** True when the UI/tile may offer grounded entry for the active provider. */
    fun availableFor(onDeviceProvider: Boolean): Boolean =
        ENABLED && (!onDeviceProvider || ALLOW_ON_DEVICE)
}
