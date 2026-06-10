package org.autojs.autojs.timing

import kotlin.math.pow

object TimedTaskReliabilityPolicy {

    private const val DEFAULT_BACKOFF_MILLIS = 30_000L
    private const val MAX_BACKOFF_MILLIS = 60L * 60L * 1000L

    fun normalizeMaxRetries(value: Int): Int = value.coerceIn(0, 10)

    fun shouldRetry(retryAttempt: Int, maxRetries: Int): Boolean {
        return retryAttempt < normalizeMaxRetries(maxRetries)
    }

    fun nextBackoffMillis(baseMillis: Long, nextRetryAttempt: Int): Long {
        val base = baseMillis.takeIf { it > 0L } ?: DEFAULT_BACKOFF_MILLIS
        val multiplier = 2.0.pow((nextRetryAttempt - 1).coerceAtLeast(0).toDouble())
        return (base * multiplier).toLong().coerceIn(0L, MAX_BACKOFF_MILLIS)
    }

    fun mutexKey(scriptPath: String?, explicitKey: String?, enabled: Boolean): String {
        val normalizedExplicitKey = explicitKey.orEmpty().trim()
        return when {
            normalizedExplicitKey.isNotEmpty() -> normalizedExplicitKey
            enabled -> scriptPath.orEmpty().trim()
            else -> ""
        }
    }

}
