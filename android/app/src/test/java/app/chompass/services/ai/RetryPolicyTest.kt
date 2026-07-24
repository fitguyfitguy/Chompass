package app.chompass.services.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun overloadStatusesAreRetryable() {
        assertTrue(RetryPolicy.isRetryableHttpStatus(503))
        assertTrue(RetryPolicy.isRetryableHttpStatus(529))
    }

    @Test
    fun rateLimitIsNotRetryable() {
        assertFalse(RetryPolicy.isRetryableHttpStatus(429))
    }

    @Test
    fun clientErrorsAreNotRetryable() {
        assertFalse(RetryPolicy.isRetryableHttpStatus(400))
        assertFalse(RetryPolicy.isRetryableHttpStatus(401))
    }
}
