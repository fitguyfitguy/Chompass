package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * A per-day free-text note (journaling: thoughts / progress / observations),
 * Codeberg #58a. One record per calendar day; the id derives from the date so
 * upsert-by-id == upsert-by-date and sync tombstone/revive semantics stay
 * correct for "the note for that day".
 */
@Serializable
data class DailyNote(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate = LocalDate.now(),
    val text: String,
) {
    companion object {
        /** Upper bound on note length: keeps month bucket files small and the editor simple. */
        const val MAX_TEXT_LENGTH = 1000

        /**
         * Stable per-day id: same date always maps to the same record. The 128
         * bits are the day count since 1970-01-01 in the low 48 bits and zeros
         * elsewhere, so the PWA mirrors this with plain arithmetic (no MD5/
         * crypto needed) and both sides merge by id — a note written on two
         * devices for the same day collapses to last-write-wins.
         *
         * Wire form: `00000000-0000-0000-0000-<12 lowercase hex digits>`.
         */
        fun idFor(date: LocalDate): UUID {
            val days = date.toEpochDay() and 0x0000FFFFFFFFFFFFL
            return UUID.fromString("00000000-0000-0000-0000-" + days.toString(16).padStart(12, '0'))
        }
    }
}
