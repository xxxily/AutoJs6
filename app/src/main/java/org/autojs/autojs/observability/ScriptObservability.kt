package org.autojs.autojs.observability

import com.google.gson.GsonBuilder
import org.autojs.autojs.capability.ProjectCapabilityAuditEntry
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ScriptRunStatus(val wireName: String) {
    RUNNING("running"),
    SUCCESS("success"),
    EXCEPTION("exception"),
    STOPPED("stopped"),
}

data class ScriptExceptionSnapshot(
    val className: String,
    val message: String,
    val stackTrace: String,
)

data class ScriptRunLogEntry(
    val id: Long,
    val timestamp: Long,
    val executionId: Int?,
    val level: Int,
    val levelName: String,
    val message: String,
    val threadId: Long,
    val threadName: String,
)

data class ScriptCapabilityEvent(
    val id: Long,
    val timestamp: Long,
    val executionId: Int?,
    val projectKey: String,
    val api: String,
    val capabilities: List<String>,
    val riskLevel: String,
    val action: String,
    val allowed: Boolean,
    val target: String,
    val message: String,
)

data class ScriptResourceSnapshot(
    val timestamp: Long,
    val usedMemoryBytes: Long,
    val freeMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val threadCount: Int,
    val availableProcessors: Int,
)

data class ScriptDebugSnapshot(
    val variablesAvailable: Boolean,
    val variables: Map<String, Any?>,
    val variablesUnavailableReason: String,
    val stackAvailable: Boolean,
    val stackTrace: String,
    val threadId: Long,
    val threadName: String,
)

data class ScriptRunSnapshot(
    val executionId: Int,
    val engineId: Int,
    val sourceName: String,
    val sourcePath: String,
    val sourceType: String,
    val filePath: String?,
    val workingDirectory: String,
    val threadId: Long,
    val threadName: String,
    val status: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMillis: Long,
    val exception: ScriptExceptionSnapshot?,
    val logs: List<ScriptRunLogEntry>,
    val capabilityEvents: List<ScriptCapabilityEvent>,
    val resource: ScriptResourceSnapshot,
    val debug: ScriptDebugSnapshot,
)

object ScriptObservability {

    private val runs = LinkedHashMap<Int, MutableRun>()
    private val runningExecutionsByThread = ConcurrentHashMap<Long, Int>()
    private val nextLogId = AtomicLong(1L)
    private val nextCapabilityEventId = AtomicLong(1L)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmField
    val executionListener: ScriptExecutionListener = object : ScriptExecutionListener {
        override fun onStart(execution: ScriptExecution) {
            this@ScriptObservability.onStart(execution)
        }

        override fun onSuccess(execution: ScriptExecution, result: Any?) {
            this@ScriptObservability.onSuccess(execution)
        }

        override fun onException(execution: ScriptExecution, e: Throwable) {
            this@ScriptObservability.onException(execution, e)
        }
    }

    @JvmStatic
    fun onStart(execution: ScriptExecution) {
        val thread = Thread.currentThread()
        val source = execution.source
        val run = MutableRun(
            executionId = execution.id,
            engineId = execution.engine.id,
            sourceName = source.name,
            sourcePath = source.fullPath,
            sourceType = source.javaClass.simpleName,
            filePath = source.filePathOrNull(),
            workingDirectory = execution.engine.cwd().orEmpty(),
            threadId = thread.id,
            threadName = thread.name,
            status = ScriptRunStatus.RUNNING,
            startTime = System.currentTimeMillis(),
            startStackTrace = thread.stackTrace.joinToString("\n") { it.toString() },
        )
        synchronized(runs) {
            runs[run.executionId] = run
            trimRunsLocked()
        }
        runningExecutionsByThread[thread.id] = execution.id
    }

    @JvmStatic
    fun onSuccess(execution: ScriptExecution) {
        finish(execution.id, ScriptRunStatus.SUCCESS, null)
    }

    @JvmStatic
    fun onException(execution: ScriptExecution, throwable: Throwable) {
        val status = if (ScriptInterruptedException.causedByInterrupt(throwable)) {
            ScriptRunStatus.STOPPED
        } else {
            ScriptRunStatus.EXCEPTION
        }
        finish(execution.id, status, throwable.toSnapshot())
    }

    @JvmStatic
    fun recordLog(level: Int, message: String?) {
        val currentThread = Thread.currentThread()
        val executionId = runningExecutionsByThread[currentThread.id] ?: latestRunningExecutionId()
        val entry = ScriptRunLogEntry(
            id = nextLogId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            executionId = executionId,
            level = level,
            levelName = levelName(level),
            message = message.orEmpty().take(MAX_LOG_MESSAGE_LENGTH),
            threadId = currentThread.id,
            threadName = currentThread.name,
        )
        synchronized(runs) {
            executionId?.let { runs[it]?.addLog(entry) }
        }
    }

    @JvmStatic
    fun recordCapabilityEvent(auditEntry: ProjectCapabilityAuditEntry) {
        val currentThread = Thread.currentThread()
        val executionId = runningExecutionsByThread[currentThread.id] ?: latestRunningExecutionId()
        val event = ScriptCapabilityEvent(
            id = nextCapabilityEventId.getAndIncrement(),
            timestamp = auditEntry.timestamp,
            executionId = executionId,
            projectKey = auditEntry.projectKey,
            api = auditEntry.api,
            capabilities = auditEntry.capabilities,
            riskLevel = auditEntry.riskLevel,
            action = auditEntry.action,
            allowed = auditEntry.allowed,
            target = auditEntry.target,
            message = auditEntry.message,
        )
        synchronized(runs) {
            executionId?.let { runs[it]?.addCapabilityEvent(event) }
        }
    }

    @JvmStatic
    fun recentRuns(limit: Int = DEFAULT_RECENT_RUN_LIMIT): List<ScriptRunSnapshot> {
        synchronized(runs) {
            return runs.values
                .toList()
                .takeLast(limit.coerceAtLeast(0))
                .map { it.toSnapshot() }
                .asReversed()
        }
    }

    @JvmStatic
    fun runningRuns(): List<ScriptRunSnapshot> {
        synchronized(runs) {
            return runs.values
                .filter { it.status == ScriptRunStatus.RUNNING }
                .map { it.toSnapshot() }
        }
    }

    @JvmStatic
    fun snapshot(executionId: Int): ScriptRunSnapshot? {
        synchronized(runs) {
            return runs[executionId]?.toSnapshot()
        }
    }

    @JvmStatic
    fun latestForPath(path: String?): ScriptRunSnapshot? {
        if (path.isNullOrBlank()) return null
        synchronized(runs) {
            return runs.values
                .asSequence()
                .filter { it.filePath == path || it.sourcePath == path }
                .lastOrNull()
                ?.toSnapshot()
        }
    }

    @JvmStatic
    fun latest(): ScriptRunSnapshot? {
        synchronized(runs) {
            return runs.values.lastOrNull()?.toSnapshot()
        }
    }

    @JvmStatic
    fun exportDiagnosticsJson(executionId: Int? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "version" to 1,
            "timestamp" to System.currentTimeMillis(),
            "requestedExecutionId" to executionId,
            "runs" to when (executionId) {
                null -> recentRuns(MAX_EXPORT_RUNS)
                else -> snapshot(executionId)?.let(::listOf).orEmpty()
            },
        )
        return gson.toJson(payload)
    }

    @JvmStatic
    fun clearForTest() {
        synchronized(runs) {
            runs.clear()
        }
        runningExecutionsByThread.clear()
        nextLogId.set(1L)
        nextCapabilityEventId.set(1L)
    }

    @JvmStatic
    fun recordStartForTest(executionId: Int, sourcePath: String, threadId: Long = Thread.currentThread().id) {
        val thread = Thread.currentThread()
        synchronized(runs) {
            runs[executionId] = MutableRun(
                executionId = executionId,
                engineId = executionId,
                sourceName = sourcePath.substringAfterLast('/').substringBeforeLast('.'),
                sourcePath = sourcePath,
                sourceType = "TestSource",
                filePath = sourcePath,
                workingDirectory = sourcePath.substringBeforeLast('/', ""),
                threadId = threadId,
                threadName = thread.name,
                status = ScriptRunStatus.RUNNING,
                startTime = System.currentTimeMillis(),
                startStackTrace = thread.stackTrace.joinToString("\n") { it.toString() },
            )
        }
        runningExecutionsByThread[threadId] = executionId
    }

    @JvmStatic
    fun recordFinishForTest(executionId: Int, success: Boolean, throwable: Throwable? = null) {
        finish(executionId, if (success) ScriptRunStatus.SUCCESS else ScriptRunStatus.EXCEPTION, throwable?.toSnapshot())
    }

    private fun finish(executionId: Int, status: ScriptRunStatus, exception: ScriptExceptionSnapshot?) {
        synchronized(runs) {
            runs[executionId]?.let { run ->
                run.status = status
                run.endTime = System.currentTimeMillis()
                run.exception = exception
                runningExecutionsByThread.entries.removeIf { it.value == executionId }
            }
        }
    }

    private fun latestRunningExecutionId(): Int? {
        synchronized(runs) {
            return runs.values.lastOrNull { it.status == ScriptRunStatus.RUNNING }?.executionId
        }
    }

    private fun trimRunsLocked() {
        while (runs.size > MAX_RUNS) {
            runs.remove(runs.entries.iterator().next().key)
        }
    }

    private fun ScriptSource.filePathOrNull(): String? {
        return when (this) {
            is JavaScriptFileSource -> file.absolutePath
            is AutoFileSource -> file.absolutePath
            else -> null
        }
    }

    private fun Throwable.toSnapshot(): ScriptExceptionSnapshot {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return ScriptExceptionSnapshot(
            className = javaClass.name,
            message = message.orEmpty(),
            stackTrace = writer.toString().take(MAX_STACK_LENGTH),
        )
    }

    private fun levelName(level: Int): String {
        return when (level) {
            0 -> "verbose"
            1 -> "debug"
            2 -> "info"
            3 -> "warn"
            4 -> "error"
            5 -> "assert"
            else -> "level_${level.toString().lowercase(Locale.ROOT)}"
        }
    }

    private fun resourceSnapshot(): ScriptResourceSnapshot {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        return ScriptResourceSnapshot(
            timestamp = System.currentTimeMillis(),
            usedMemoryBytes = total - free,
            freeMemoryBytes = free,
            totalMemoryBytes = total,
            maxMemoryBytes = runtime.maxMemory(),
            threadCount = Thread.activeCount(),
            availableProcessors = runtime.availableProcessors(),
        )
    }

    private class MutableRun(
        val executionId: Int,
        val engineId: Int,
        val sourceName: String,
        val sourcePath: String,
        val sourceType: String,
        val filePath: String?,
        val workingDirectory: String,
        val threadId: Long,
        val threadName: String,
        var status: ScriptRunStatus,
        val startTime: Long,
        val startStackTrace: String,
        var endTime: Long? = null,
        var exception: ScriptExceptionSnapshot? = null,
        val logs: MutableList<ScriptRunLogEntry> = mutableListOf(),
        val capabilityEvents: MutableList<ScriptCapabilityEvent> = mutableListOf(),
    ) {
        fun addLog(entry: ScriptRunLogEntry) {
            logs += entry
            while (logs.size > MAX_LOGS_PER_RUN) {
                logs.removeAt(0)
            }
        }

        fun addCapabilityEvent(event: ScriptCapabilityEvent) {
            capabilityEvents += event
            while (capabilityEvents.size > MAX_CAPABILITY_EVENTS_PER_RUN) {
                capabilityEvents.removeAt(0)
            }
        }

        fun toSnapshot(): ScriptRunSnapshot {
            val now = System.currentTimeMillis()
            val end = endTime
            val stack = exception?.stackTrace ?: startStackTrace
            return ScriptRunSnapshot(
                executionId = executionId,
                engineId = engineId,
                sourceName = sourceName,
                sourcePath = sourcePath,
                sourceType = sourceType,
                filePath = filePath,
                workingDirectory = workingDirectory,
                threadId = threadId,
                threadName = threadName,
                status = status.wireName,
                startTime = startTime,
                endTime = end,
                durationMillis = (end ?: now) - startTime,
                exception = exception,
                logs = logs.toList(),
                capabilityEvents = capabilityEvents.toList(),
                resource = resourceSnapshot(),
                debug = ScriptDebugSnapshot(
                    variablesAvailable = false,
                    variables = emptyMap(),
                    variablesUnavailableReason = "Rhino local variable inspection is not attached; remote bridge exposes timeline, logs, exceptions and thread stack metadata.",
                    stackAvailable = stack.isNotBlank(),
                    stackTrace = stack,
                    threadId = threadId,
                    threadName = threadName,
                ),
            )
        }
    }

    private const val MAX_RUNS = 100
    private const val MAX_LOGS_PER_RUN = 500
    private const val MAX_CAPABILITY_EVENTS_PER_RUN = 200
    private const val MAX_LOG_MESSAGE_LENGTH = 4_000
    private const val MAX_STACK_LENGTH = 32_000
    private const val DEFAULT_RECENT_RUN_LIMIT = 20
    private const val MAX_EXPORT_RUNS = 50
}
