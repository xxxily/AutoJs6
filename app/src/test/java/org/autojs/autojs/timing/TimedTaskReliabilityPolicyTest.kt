package org.autojs.autojs.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedTaskReliabilityPolicyTest {

    @Test
    fun retryStopsAtNormalizedMaximum() {
        assertTrue(TimedTaskReliabilityPolicy.shouldRetry(retryAttempt = 0, maxRetries = 1))
        assertFalse(TimedTaskReliabilityPolicy.shouldRetry(retryAttempt = 1, maxRetries = 1))
        assertEquals(10, TimedTaskReliabilityPolicy.normalizeMaxRetries(99))
        assertEquals(0, TimedTaskReliabilityPolicy.normalizeMaxRetries(-1))
    }

    @Test
    fun backoffGrowsExponentiallyAndIsCapped() {
        assertEquals(1_000L, TimedTaskReliabilityPolicy.nextBackoffMillis(baseMillis = 1_000L, nextRetryAttempt = 1))
        assertEquals(2_000L, TimedTaskReliabilityPolicy.nextBackoffMillis(baseMillis = 1_000L, nextRetryAttempt = 2))
        assertEquals(3_600_000L, TimedTaskReliabilityPolicy.nextBackoffMillis(baseMillis = 3_600_000L, nextRetryAttempt = 6))
    }

    @Test
    fun mutexKeyUsesExplicitKeyBeforeScriptPath() {
        assertEquals("explicit", TimedTaskReliabilityPolicy.mutexKey("/sdcard/a.js", " explicit ", enabled = false))
        assertEquals("/sdcard/a.js", TimedTaskReliabilityPolicy.mutexKey("/sdcard/a.js", "", enabled = true))
        assertEquals("", TimedTaskReliabilityPolicy.mutexKey("/sdcard/a.js", "", enabled = false))
    }

}
