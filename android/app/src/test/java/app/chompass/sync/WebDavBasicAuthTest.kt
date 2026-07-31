package app.chompass.sync

import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class WebDavBasicAuthTest {
    @Test
    fun usesUtf8NotLatin1ForNonAsciiPassword() {
        val user = "u123-sub1"
        val password = "eK9ßSThq6CTW§jß"
        val header = webDavBasicAuth(user, password)
        val expectedUtf8 = Credentials.basic(user, password, StandardCharsets.UTF_8)
        val latin1 = Credentials.basic(user, password, StandardCharsets.ISO_8859_1)
        assertEquals(expectedUtf8, header)
        assertNotEquals(latin1, header)
        // Matches curl's UTF-8 Basic encoding
        val payload = Base64.getEncoder().encodeToString("$user:$password".toByteArray(StandardCharsets.UTF_8))
        assertEquals("Basic $payload", header)
    }

    @Test
    fun asciiPasswordMatchesEitherCharset() {
        val header = webDavBasicAuth("user", "secret")
        assertEquals(Credentials.basic("user", "secret", StandardCharsets.UTF_8), header)
        assertEquals(Credentials.basic("user", "secret", StandardCharsets.ISO_8859_1), header)
    }
}
