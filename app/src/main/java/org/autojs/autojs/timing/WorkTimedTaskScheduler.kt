package org.autojs.autojs.timing

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import org.autojs.autojs.util.WorkManagerUtils
import java.util.concurrent.TimeUnit

object WorkTimedTaskScheduler : TimedTaskBackend {

    private const val UNIQUE_CHECK_NAME = "work-timed-task-periodic-check"

    override fun cancel(context: Context, task: TimedTask) {
        WorkManagerUtils.getInstance(context).cancelAllWorkByTag(task.id.toString())
    }

    override fun schedule(context: Context, task: TimedTask, triggerAtMillis: Long) {
        val delay = triggerAtMillis - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<TimedTaskWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(task.id.toString())
            .setInputData(
                Data.Builder()
                    .putLong(KEY_TASK_ID, task.id)
                    .putLong(KEY_SCHEDULED_AT, triggerAtMillis)
                    .build(),
            )
            .setConstraints(Constraints.NONE)
            .build()
        WorkManagerUtils.getInstance(context).enqueueUniqueWork(
            "work-timed-task-id-${task.id}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun schedulePeriodicCheck(context: Context, intervalMillis: Long) {
        val request = PeriodicWorkRequestBuilder<CheckTasksWorker>(intervalMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManagerUtils.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_CHECK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    internal class TimedTaskWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val tags = this.tags // contains taskId string. (zh-CN: 包含 taskId 字符串.)
            val taskId = inputData.getLong(KEY_TASK_ID, -1L).takeIf { it > 0L }
                ?: tags.firstNotNullOfOrNull { it.toLongOrNull() }
                ?: return Result.failure()
            val task = TimedTaskManager.getTimedTask(taskId) ?: return Result.failure()
            TimedTaskManager.triggerTask(
                applicationContext,
                task,
                "WorkTimedTaskScheduler",
                inputData.getLong(KEY_SCHEDULED_AT, task.getNextTime(applicationContext)),
            )
            return Result.success()
        }
    }

    internal class CheckTasksWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            TimedTaskScheduler.checkTasks(applicationContext, false)
            return Result.success()
        }
    }

    private const val KEY_TASK_ID = "task_id"
    private const val KEY_SCHEDULED_AT = "scheduled_at"

}
