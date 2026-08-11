package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException

class AiErrorConnectionHintTest {

    @Test
    fun cleartextFailureGetsActionableHint() {
        val e = UnknownServiceException(
            "CLEARTEXT communication to 192.168.1.10 not permitted by network security policy"
        )
        val msg = connectionFailureMessage(e)
        assertTrue("expected cleartext hint, got: $msg", msg.contains("Cleartext HTTP is blocked"))
        assertTrue("expected https guidance, got: $msg", msg.contains("https://"))
    }

    @Test
    fun trustAnchorFailureDirectGetsInstallHint() {
        val e = CertPathValidatorException("Trust anchor for certification path not found")
        val msg = connectionFailureMessage(e)
        assertTrue("expected trust hint, got: $msg", msg.contains("certificate isn't trusted"))
        assertTrue("expected install guidance, got: $msg", msg.contains("Install a certificate"))
    }

    @Test
    fun trustAnchorFailureWrappedInSslHandshakeGetsInstallHint() {
        val e = SSLHandshakeException("Handshake failed").apply {
            initCause(CertPathValidatorException("Trust anchor for certification path not found"))
        }
        val msg = connectionFailureMessage(e)
        assertTrue("expected trust hint, got: $msg", msg.contains("certificate isn't trusted"))
    }

    @Test
    fun genericFailureKeepsNetworkErrorPrefix() {
        assertEquals("Network error: connection reset", connectionFailureMessage(IOException("connection reset")))
    }
}
