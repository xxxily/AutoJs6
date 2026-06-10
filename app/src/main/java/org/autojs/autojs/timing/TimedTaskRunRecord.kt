package org.autojs.autojs.timing

import org.autojs.autojs.storage.database.BaseModel
import java.util.UUID

class TimedTaskRunRecord : BaseModel() {

    var taskId: Long = -1L
    var executionId: String = UUID.randomUUID().toString()
    var scriptPath: String = ""
    var eventType: String = EVENT_RUN
    var scheduledAt: Long = 0L
    var triggeredAt: Long = 0L
    var startedAt: Long = 0L
    var finishedAt: Long = 0L
    var launchStatus: String = LAUNCH_PENDING
    var finishStatus: String = FINISH_PENDING
    var exception: String = ""
    var duration: Long = 0L
    var backend: String = ""
    var deviceState: String = ""
    var degradationReason: String = ""
    var nextScheduledAt: Long = 0L
    var retryAttempt: Int = 0
    var maxRetries: Int = 0
    var backoffMillis: Long = 0L
    var mutexKey: String = ""
    var timeoutMillis: Long = 0L

    fun markStarted(nowMillis: Long) {
        startedAt = nowMillis
        launchStatus = LAUNCH_STARTED
        finishStatus = FINISH_RUNNING
    }

    fun markLaunched(scriptExecutionId: Int?) {
        scriptExecutionId?.takeIf { it >= 0 }?.let { executionId = it.toString() }
        launchStatus = LAUNCH_LAUNCHED
        if (finishStatus == FINISH_PENDING) {
            finishStatus = FINISH_RUNNING
        }
    }

    fun markSkipped(reason: String, nowMillis: Long) {
        launchStatus = LAUNCH_SKIPPED
        finishStatus = FINISH_NOT_STARTED
        exception = reason
        finishedAt = nowMillis
        duration = durationMillis(nowMillis)
    }

    fun markFailed(error: Throwable, nowMillis: Long) {
        launchStatus = LAUNCH_FAILED
        finishStatus = FINISH_EXCEPTION
        exception = "${error.javaClass.name}: ${error.message.orEmpty()}"
        finishedAt = nowMillis
        duration = durationMillis(nowMillis)
    }

    fun markFinished(status: String, error: Throwable?, nowMillis: Long) {
        finishStatus = status
        exception = error?.let { "${it.javaClass.name}: ${it.message.orEmpty()}" }.orEmpty()
        finishedAt = nowMillis
        duration = durationMillis(nowMillis)
    }

    fun isTerminal(): Boolean = finishStatus in setOf(
        FINISH_SUCCESS,
        FINISH_EXCEPTION,
        FINISH_NOT_STARTED,
        FINISH_SCHEDULED,
        FINISH_TIMEOUT,
    )

    private fun durationMillis(nowMillis: Long): Long {
        val start = startedAt.takeIf { it > 0L } ?: triggeredAt.takeIf { it > 0L } ?: nowMillis
        return (nowMillis - start).coerceAtLeast(0L)
    }

    companion object {
        const val TABLE = "TimedTaskRunRecord"

        const val EVENT_RUN = "run"
        const val EVENT_RETRY_RUN = "retry_run"
        const val EVENT_COMPENSATED_RUN = "compensated_run"
        const val EVENT_SCHEDULE_DEGRADED = "schedule_degraded"

        const val LAUNCH_PENDING = "pending"
        const val LAUNCH_STARTED = "started"
        const val LAUNCH_LAUNCHED = "launched"
        const val LAUNCH_FAILED = "launch_failed"
        const val LAUNCH_SKIPPED = "skipped"
        const val LAUNCH_DEGRADED = "degraded"

        const val FINISH_PENDING = "pending"
        const val FINISH_RUNNING = "running"
        const val FINISH_SUCCESS = "success"
        const val FINISH_EXCEPTION = "exception"
        const val FINISH_NOT_STARTED = "not_started"
        const val FINISH_SCHEDULED = "scheduled"
        const val FINISH_TIMEOUT = "timeout"
    }
}

data class TimedTaskQueueItem(
    val taskId: Long,
    val scriptPath: String,
    val nextScheduledAt: Long,
    val scheduled: Boolean,
    val backend: String,
    val delay: Long,
    val interval: Long,
    val loopTimes: Int,
    val maxRetries: Int,
    val retryBackoffMillis: Long,
    val mutex: Boolean,
    val mutexKey: String,
    val timeoutMillis: Long,
)
