package app.chompass.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the CHOOSE_USER payload shapes the vendored handlers emit (and garbage). */
class ScaleSyncNormalizeTest {
    @Test
    fun handlerPairOfArrayAndIntArrayNormalizes() {
        val data = Pair(arrayOf<CharSequence>("P00 - 0", "Create new user"), intArrayOf(3, -1))
        assertEquals(listOf("P00 - 0", "Create new user") to listOf(3, -1), normalizeChooseUser(data))
    }

    @Test
    fun listFallbacksNormalize() {
        assertEquals(listOf("a") to listOf(1), normalizeChooseUser(Pair(listOf("a"), listOf(1))))
        assertEquals(listOf("a") to listOf(1), normalizeChooseUser(Pair(arrayOf("a"), listOf(1))))
        assertEquals(listOf("a") to listOf(1), normalizeChooseUser(Pair(listOf("a"), intArrayOf(1))))
    }

    @Test
    fun createOnlyShapeKeepsMinusOne() {
        // presentCreateOnlyChoice: one localized label, id −1
        val data = Pair(arrayOf<CharSequence>("Create new user"), intArrayOf(-1))
        assertEquals(listOf("Create new user") to listOf(-1), normalizeChooseUser(data))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(normalizeChooseUser(null))
        assertNull(normalizeChooseUser("CHOOSE"))
        assertNull(normalizeChooseUser(Pair("labels", 1)))
        assertNull(normalizeChooseUser(Pair(arrayOf("a"), listOf("nope"))))
        assertNull(normalizeChooseUser(Pair(arrayOf("a", "b"), intArrayOf(1))))
        assertNull(normalizeChooseUser(Pair(arrayOf<CharSequence>(), intArrayOf())))
    }
}
