package org.autojs.autojs.timing

import android.annotation.SuppressLint
import android.content.Context
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

/**
 * Created by Stardust on Nov 27, 2017.
 * Modified by SuperMonster003 as of Oct 27, 2025.
 */
object TimedTaskScheduler {

    private const val LOG_TAG = "TimedTaskScheduler"

    private lateinit var backend: TimedTaskBackend

    fun init(context: Context) {
        val backend = run initBackend@{
            val keyRes = R.string.key_timed_task_backend
            val defRes = R.string.default_key_timed_task_backend
            when (val prefValue = Pref.getString(keyRes, defRes)) {
                key(R.string.key_timed_task_backend_alarm) -> AlarmTimedTaskScheduler
                key(R.string.key_timed_task_backend_work) -> WorkTimedTaskScheduler
                key(R.string.key_timed_task_backend_job) -> JobTimedTaskScheduler
                else -> throw RuntimeException("Unknown backend: $prefValue")
            }
        }.also { this.backend = it }

        println("$LOG_TAG: init backend = ${TimedTaskScheduler.backend}")
        backend.init(context)
        backend.schedulePeriodicCheck(context, TimedTaskSchedulingPolicy.schedulePeriodicCheckTimeMillis)

        checkTasks(context, true)
    }

    @JvmStatic
    fun cancel(context: Context, timedTask: TimedTask) {
        backend.cancel(context, timedTask)
        println("$LOG_TAG: cancel task (${backend}): task = $timedTask")
    }

    fun runTask(
        context: Context,
        task: TimedTask,
        scheduledAt: Long = task.getNextTime(context),
        triggerReason: String = TimedTaskRunRecord.EVENT_RUN,
        backendName: String = currentBackendName(),
    ) {
        println("$LOG_TAG: run task: task = $task")
        TimedTaskManager.triggerTask(context, task, backendName, scheduledAt, triggerReason)
    }

    @SuppressLint("CheckResult")
    fun checkTasks(context: Context, force: Boolean) {
        println("$LOG_TAG: check tasks: force = $force")
        when (backend) {
            AlarmTimedTaskScheduler -> TimedTaskSchedulingPolicy
                .selectAlarmCandidates(TimedTaskManager.allTasksAsList) { it.getNextTime(context) }
                .forEach { timedTask -> scheduleTaskIfNeeded(context, timedTask, force) }
            else -> TimedTaskManager.allTasks
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { timedTask -> scheduleTaskIfNeeded(context, timedTask, force) }
        }
    }

    @JvmStatic
    fun scheduleTaskIfNeeded(context: Context, timedTask: TimedTask, force: Boolean) {
        val millis = timedTask.getNextTime(context)
        val now = System.currentTimeMillis()
        if (TimedTaskSchedulingPolicy.shouldRunNow(millis, now)) {
            runTask(context, timedTask, millis, TimedTaskRunRecord.EVENT_COMPENSATED_RUN)
            return
        }
        if (!TimedTaskSchedulingPolicy.shouldSchedule(millis, now, timedTask.isScheduled, force)) {
            return
        }
        scheduleTask(context, timedTask, millis, force)
        TimedTaskManager.notifyTaskScheduled(timedTask)
    }

    @Synchronized
    private fun scheduleTask(context: Context, timedTask: TimedTask, millis: Long, force: Boolean) {
        if (!force && timedTask.isScheduled) {
            return
        }
        timedTask.isScheduled = true
        val timeWindow = TimedTaskSchedulingPolicy.timeWindowMillis(millis, System.currentTimeMillis())
        TimedTaskManager.updateTaskWithoutReScheduling(timedTask)
        if (timeWindow <= 0) {
            runTask(context, timedTask, millis, TimedTaskRunRecord.EVENT_COMPENSATED_RUN)
            return
        }
        cancel(context, timedTask)
        println("$LOG_TAG: schedule task: task = $timedTask, millis = $millis, timeWindow = $timeWindow")

        backend.schedule(context, timedTask, millis)
    }

    fun currentBackendName(): String {
        return if (::backend.isInitialized) backend.javaClass.simpleName.removeSuffix("$") else "uninitialized"
    }

}
