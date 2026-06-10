package org.autojs.autojs.timing

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.TextUtils
import io.reactivex.Flowable
import io.reactivex.Observable
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.storage.database.IntentTaskDatabase
import org.autojs.autojs.storage.database.ModelChange
import org.autojs.autojs.storage.database.TimedTaskDatabase
import org.autojs.autojs.storage.database.TimedTaskRunRecordDatabase
import org.autojs.autojs.timing.TimedTaskScheduler.cancel
import org.autojs.autojs.timing.TimedTaskScheduler.scheduleTaskIfNeeded
import org.autojs.autojs.util.Observers

/**
 * Created by Stardust on Nov 27, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Apr 2, 2023.
 */
object TimedTaskManager {

    private val globalAppContext by lazy { GlobalAppContext.get() }
    private val mTimedTaskDatabase by lazy { TimedTaskDatabase(globalAppContext) }
    private val mIntentTaskDatabase by lazy { IntentTaskDatabase(globalAppContext) }
    private val mTimedTaskRunRecordDatabase by lazy { TimedTaskRunRecordDatabase(globalAppContext) }
    private val runningMutexKeys = mutableSetOf<String>()

    @JvmStatic
    val allTasks: Flowable<TimedTask>
        get() = mTimedTaskDatabase.queryAllAsFlowable()

    @JvmStatic
    val allIntentTasks: Flowable<IntentTask>
        get() = mIntentTaskDatabase.queryAllAsFlowable()

    @JvmStatic
    val allTasksAsList: List<TimedTask>
        get() = mTimedTaskDatabase.queryAll()

    @JvmStatic
    val allIntentTasksAsList: List<IntentTask>
        get() = mIntentTaskDatabase.queryAll()

    @JvmStatic
    val allRunRecordsAsList: List<TimedTaskRunRecord>
        get() = mTimedTaskRunRecordDatabase.queryAll().sortedByDescending { it.timelineSortKey() }

    @JvmStatic
    val timeTaskChanges: Observable<ModelChange<TimedTask>>
        get() = mTimedTaskDatabase.modelChange

    @JvmStatic
    val intentTaskChanges: Observable<ModelChange<IntentTask>>
        get() = mIntentTaskDatabase.modelChange

    @JvmStatic
    fun getTimedTask(taskId: Long): TimedTask? {
        return mTimedTaskDatabase.queryById(taskId)
    }

    @JvmStatic
    fun getIntentTask(intentTaskId: Long): IntentTask? = mIntentTaskDatabase.queryById(intentTaskId)

    @JvmStatic
    fun queryRunRecords(taskId: Long? = null, limit: Int = 50): List<TimedTaskRunRecord> {
        val boundedLimit = limit.coerceIn(1, 500)
        return allRunRecordsAsList
            .asSequence()
            .filter { taskId == null || it.taskId == taskId }
            .take(boundedLimit)
            .toList()
    }

    @JvmStatic
    fun currentTimedTaskQueue(backend: String): List<TimedTaskQueueItem> {
        return allTasksAsList
            .map { task ->
                TimedTaskQueueItem(
                    taskId = task.id,
                    scriptPath = task.scriptPath.orEmpty(),
                    nextScheduledAt = task.getNextTime(globalAppContext),
                    scheduled = task.isScheduled,
                    backend = backend,
                    delay = task.delay,
                    interval = task.interval,
                    loopTimes = task.loopTimes,
                    maxRetries = task.maxRetries,
                    retryBackoffMillis = task.retryBackoffMillis,
                    mutex = task.isMutex,
                    mutexKey = TimedTaskReliabilityPolicy.mutexKey(task.scriptPath, task.mutexKey, task.isMutex),
                    timeoutMillis = task.timeoutMillis,
                )
            }
            .sortedBy { it.nextScheduledAt }
    }

    @JvmStatic
    fun getIntentTaskOfAction(action: String?): Flowable<IntentTask> = mIntentTaskDatabase.query("action = ?", action)

    @JvmStatic
    @SuppressLint("CheckResult")
    fun addTask(timedTask: TimedTask) {
        mTimedTaskDatabase.insert(timedTask)
            .subscribe({ id: Long? ->
                timedTask.id = id!!
                scheduleTaskIfNeeded(globalAppContext, timedTask, false)
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun addTask(intentTask: IntentTask) {
        mIntentTaskDatabase.insert(intentTask)
            .subscribe({
                if (!TextUtils.isEmpty(intentTask.action)) {
                    app.dynamicBroadcastReceivers
                        .registerIntent(intentTask)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    fun addTaskSync(timedTask: TimedTask) {
        timedTask.id = mTimedTaskDatabase.insertSync(timedTask)
        scheduleTaskIfNeeded(globalAppContext, timedTask, false)
    }

    @JvmStatic
    fun addTaskSync(intentTask: IntentTask) {
        intentTask.id = mIntentTaskDatabase.insertSync(intentTask)
        app.dynamicBroadcastReceivers.registerIntent(intentTask)
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun removeTask(timedTask: TimedTask) {
        cancel(globalAppContext, timedTask)
        mTimedTaskDatabase.delete(timedTask)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun removeTask(intentTask: IntentTask) {
        mIntentTaskDatabase.delete(intentTask)
            .subscribe({
                if (!TextUtils.isEmpty(intentTask.action)) {
                    app.dynamicBroadcastReceivers
                        .unregister(intentTask.action)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    fun removeTaskSync(timedTask: TimedTask): Boolean {
        cancel(globalAppContext, timedTask)
        return mTimedTaskDatabase.deleteSync(timedTask) > 0
    }

    @JvmStatic
    fun removeTaskSync(intentTask: IntentTask): Boolean {
        return mIntentTaskDatabase.deleteSync(intentTask) > 0
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTask(task: TimedTask) {
        mTimedTaskDatabase.update(task)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        cancel(globalAppContext, task)
        scheduleTaskIfNeeded(globalAppContext, task, false)
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTaskSync(task: TimedTask): Boolean {
        val id = mTimedTaskDatabase.updateSync(task)
        cancel(globalAppContext, task)
        scheduleTaskIfNeeded(globalAppContext, task, false)
        return id > 0
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTask(task: IntentTask) {
        mIntentTaskDatabase.update(task)
            .subscribe({ i: Int ->
                if (i > 0 && !TextUtils.isEmpty(task.action)) {
                    app.dynamicBroadcastReceivers
                        .registerIntent(task)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTaskSync(task: IntentTask): Boolean {
        val id = mIntentTaskDatabase.updateSync(task)
        if (id > 0 && !TextUtils.isEmpty(task.action)) {
            app.dynamicBroadcastReceivers
                .registerIntent(task)
        }
        return id > 0
    }

    @SuppressLint("CheckResult")
    fun updateTaskWithoutReScheduling(task: TimedTask) {
        mTimedTaskDatabase.update(task)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @SuppressLint("CheckResult")
    fun notifyTaskScheduled(timedTask: TimedTask) {
        timedTask.isScheduled = true
        mTimedTaskDatabase.update(timedTask)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    fun notifyTaskScheduleDegraded(
        timedTask: TimedTask,
        scheduledAt: Long,
        backend: String,
        reason: String,
    ) {
        val now = System.currentTimeMillis()
        TimedTaskRunRecord().apply {
            taskId = timedTask.id
            scriptPath = timedTask.scriptPath.orEmpty()
            eventType = TimedTaskRunRecord.EVENT_SCHEDULE_DEGRADED
            this.scheduledAt = scheduledAt
            triggeredAt = now
            launchStatus = TimedTaskRunRecord.LAUNCH_DEGRADED
            finishStatus = TimedTaskRunRecord.FINISH_SCHEDULED
            this.backend = backend
            deviceState = deviceState(globalAppContext)
            degradationReason = reason
            nextScheduledAt = scheduledAt
        }.also { mTimedTaskRunRecordDatabase.insertSync(it) }
    }

    @JvmStatic
    fun triggerTask(
        context: Context,
        task: TimedTask,
        backend: String,
        scheduledAt: Long,
        triggerReason: String = TimedTaskRunRecord.EVENT_RUN,
        retryAttempt: Int = 0,
    ): TimedTaskRunRecord {
        val now = System.currentTimeMillis()
        val mutexKey = TimedTaskReliabilityPolicy.mutexKey(task.scriptPath, task.mutexKey, task.isMutex)
        val record = TimedTaskRunRecord().apply {
            taskId = task.id
            scriptPath = task.scriptPath.orEmpty()
            eventType = triggerReason
            this.scheduledAt = scheduledAt
            triggeredAt = now
            launchStatus = TimedTaskRunRecord.LAUNCH_PENDING
            finishStatus = TimedTaskRunRecord.FINISH_PENDING
            this.backend = backend
            deviceState = deviceState(context)
            nextScheduledAt = task.getNextTime(context)
            this.retryAttempt = retryAttempt
            maxRetries = TimedTaskReliabilityPolicy.normalizeMaxRetries(task.maxRetries)
            backoffMillis = task.retryBackoffMillis
            this.mutexKey = mutexKey
            timeoutMillis = task.timeoutMillis
        }
        val recordId = mTimedTaskRunRecordDatabase.insertSync(record)

        if (!acquireMutex(mutexKey)) {
            val updated = updateRunRecord(recordId) {
                it.markSkipped("Task mutex is locked: $mutexKey", System.currentTimeMillis())
            }
            notifyTaskFinished(task.id)
            return updated ?: record
        }

        return try {
            val execution = ScriptIntents.handleIntent(context, task.createIntent(), runRecordListener(context, recordId, task, mutexKey, backend, scheduledAt, retryAttempt))
            val updated = updateRunRecord(recordId) {
                if (execution == null) {
                    it.markSkipped("No script source resolved", System.currentTimeMillis())
                } else {
                    it.markLaunched(execution.id)
                }
            }
            if (execution == null) {
                releaseMutex(mutexKey)
                notifyTaskFinished(task.id)
            } else {
                scheduleTimeoutIfNeeded(context, recordId, task, execution, mutexKey, backend, scheduledAt, retryAttempt)
            }
            updated ?: record
        } catch (e: Throwable) {
            val retryAt = scheduleRetryIfNeeded(context, task, backend, scheduledAt, retryAttempt)
            val updated = finishRunRecord(recordId) {
                it.markFailed(e, System.currentTimeMillis())
                if (retryAt > 0L) {
                    it.nextScheduledAt = retryAt
                }
            }
            releaseMutex(mutexKey)
            if (retryAt <= 0L) {
                notifyTaskFinished(task.id)
            }
            updated ?: record
        }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun notifyTaskFinished(id: Long) {
        val task = getTimedTask(id) ?: return
        if (task.isDisposable) {
            mTimedTaskDatabase.delete(task)
                .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        } else {
            task.isScheduled = false
            mTimedTaskDatabase.update(task)
                .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        }
    }

    fun countTasks() = mTimedTaskDatabase.count()

    private fun runRecordListener(
        context: Context,
        recordId: Long,
        task: TimedTask,
        mutexKey: String,
        backend: String,
        scheduledAt: Long,
        retryAttempt: Int,
    ) = object : SimpleScriptExecutionListener() {
        override fun onStart(execution: ScriptExecution) {
            updateRunRecord(recordId) {
                if (!it.isTerminal()) {
                    it.executionId = execution.id.toString()
                    it.markStarted(System.currentTimeMillis())
                }
            }
        }

        override fun onSuccess(execution: ScriptExecution, result: Any?) {
            val updated = finishRunRecord(recordId) {
                it.markFinished(TimedTaskRunRecord.FINISH_SUCCESS, null, System.currentTimeMillis())
            }
            releaseMutex(mutexKey)
            if (updated?.finishStatus == TimedTaskRunRecord.FINISH_SUCCESS) {
                notifyTaskFinished(task.id)
            }
        }

        override fun onException(execution: ScriptExecution, e: Throwable) {
            if (isRunRecordTerminal(recordId)) {
                releaseMutex(mutexKey)
                return
            }
            val retryAt = scheduleRetryIfNeeded(context, task, backend, scheduledAt, retryAttempt)
            val updated = finishRunRecord(recordId) {
                it.markFinished(TimedTaskRunRecord.FINISH_EXCEPTION, e, System.currentTimeMillis())
                if (retryAt > 0L) {
                    it.nextScheduledAt = retryAt
                }
            }
            releaseMutex(mutexKey)
            if (updated?.finishStatus == TimedTaskRunRecord.FINISH_EXCEPTION && retryAt <= 0L) {
                notifyTaskFinished(task.id)
            }
        }
    }

    private fun updateRunRecord(recordId: Long, block: (TimedTaskRunRecord) -> Unit): TimedTaskRunRecord? {
        val record = mTimedTaskRunRecordDatabase.queryById(recordId) ?: return null
        block(record)
        mTimedTaskRunRecordDatabase.updateSync(record)
        return record
    }

    private fun finishRunRecord(recordId: Long, block: (TimedTaskRunRecord) -> Unit): TimedTaskRunRecord? {
        val record = mTimedTaskRunRecordDatabase.queryById(recordId) ?: return null
        if (record.isTerminal()) {
            return record
        }
        block(record)
        mTimedTaskRunRecordDatabase.updateSync(record)
        return record
    }

    private fun isRunRecordTerminal(recordId: Long): Boolean {
        return mTimedTaskRunRecordDatabase.queryById(recordId)?.isTerminal() == true
    }

    private fun scheduleRetryIfNeeded(
        context: Context,
        task: TimedTask,
        backend: String,
        scheduledAt: Long,
        retryAttempt: Int,
    ): Long {
        if (!TimedTaskReliabilityPolicy.shouldRetry(retryAttempt, task.maxRetries)) {
            return 0L
        }
        val nextRetryAttempt = retryAttempt + 1
        val delayMillis = TimedTaskReliabilityPolicy.nextBackoffMillis(task.retryBackoffMillis, nextRetryAttempt)
        val retryAt = System.currentTimeMillis() + delayMillis
        GlobalAppContext.postDelayed(
            Runnable {
                triggerTask(
                    context.applicationContext,
                    task,
                    "$backend/retry",
                    scheduledAt,
                    TimedTaskRunRecord.EVENT_RETRY_RUN,
                    nextRetryAttempt,
                )
            },
            delayMillis,
        )
        return retryAt
    }

    private fun scheduleTimeoutIfNeeded(
        context: Context,
        recordId: Long,
        task: TimedTask,
        execution: ScriptExecution,
        mutexKey: String,
        backend: String,
        scheduledAt: Long,
        retryAttempt: Int,
    ) {
        val timeoutMillis = task.timeoutMillis.takeIf { it > 0L } ?: return
        GlobalAppContext.postDelayed(
            Runnable {
                if (isRunRecordTerminal(recordId)) {
                    return@Runnable
                }
                val retryAt = scheduleRetryIfNeeded(context, task, backend, scheduledAt, retryAttempt)
                val updated = finishRunRecord(recordId) {
                    it.markFinished(
                        TimedTaskRunRecord.FINISH_TIMEOUT,
                        RuntimeException("Timed task execution timeout after ${timeoutMillis}ms"),
                        System.currentTimeMillis(),
                    )
                    if (retryAt > 0L) {
                        it.nextScheduledAt = retryAt
                    }
                }
                if (updated != null && updated.finishStatus == TimedTaskRunRecord.FINISH_TIMEOUT) {
                    execution.engine?.forceStop()
                    releaseMutex(mutexKey)
                    if (retryAt <= 0L) {
                        notifyTaskFinished(task.id)
                    }
                }
            },
            timeoutMillis,
        )
    }

    private fun acquireMutex(mutexKey: String): Boolean {
        if (mutexKey.isBlank()) {
            return true
        }
        synchronized(runningMutexKeys) {
            return runningMutexKeys.add(mutexKey)
        }
    }

    private fun releaseMutex(mutexKey: String) {
        if (mutexKey.isBlank()) {
            return
        }
        synchronized(runningMutexKeys) {
            runningMutexKeys.remove(mutexKey)
        }
    }

    private fun TimedTaskRunRecord.timelineSortKey(): Long {
        return maxOf(finishedAt, startedAt, triggeredAt, scheduledAt)
    }

    private fun deviceState(context: Context): String {
        return listOf(
            "api=${Build.VERSION.SDK_INT}",
            "manufacturer=${Build.MANUFACTURER}",
            "model=${Build.MODEL}",
            "package=${context.packageName}",
        ).joinToString(";")
    }

}
