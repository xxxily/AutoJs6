package org.autojs.autojs.storage.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.autojs.autojs.timing.TimedTaskRunRecord

class TimedTaskRunRecordDatabase(context: Context) : Database<TimedTaskRunRecord>(
    SQLHelper(context),
    TimedTaskRunRecord.TABLE,
) {

    override fun asContentValues(model: TimedTaskRunRecord): ContentValues = ContentValues().apply {
        put("task_id", model.taskId)
        put("execution_id", model.executionId)
        put("script_path", model.scriptPath)
        put("event_type", model.eventType)
        put("scheduled_at", model.scheduledAt)
        put("triggered_at", model.triggeredAt)
        put("started_at", model.startedAt)
        put("finished_at", model.finishedAt)
        put("launch_status", model.launchStatus)
        put("finish_status", model.finishStatus)
        put("exception", model.exception)
        put("duration", model.duration)
        put("backend", model.backend)
        put("device_state", model.deviceState)
        put("degradation_reason", model.degradationReason)
        put("next_scheduled_at", model.nextScheduledAt)
        put("retry_attempt", model.retryAttempt)
        put("max_retries", model.maxRetries)
        put("backoff_millis", model.backoffMillis)
        put("mutex_key", model.mutexKey)
        put("timeout_millis", model.timeoutMillis)
    }

    override fun createModelFromCursor(cursor: Cursor): TimedTaskRunRecord = TimedTaskRunRecord().apply {
        id = cursor.getLong(0)
        taskId = cursor.getLong(1)
        executionId = cursor.getString(2).orEmpty()
        scriptPath = cursor.getString(3).orEmpty()
        eventType = cursor.getString(4).orEmpty()
        scheduledAt = cursor.getLong(5)
        triggeredAt = cursor.getLong(6)
        startedAt = cursor.getLong(7)
        finishedAt = cursor.getLong(8)
        launchStatus = cursor.getString(9).orEmpty()
        finishStatus = cursor.getString(10).orEmpty()
        exception = cursor.getString(11).orEmpty()
        duration = cursor.getLong(12)
        backend = cursor.getString(13).orEmpty()
        deviceState = cursor.getString(14).orEmpty()
        degradationReason = cursor.getString(15).orEmpty()
        nextScheduledAt = cursor.getLong(16)
        retryAttempt = cursor.getInt(17)
        maxRetries = cursor.getInt(18)
        backoffMillis = cursor.getLong(19)
        mutexKey = cursor.getString(20).orEmpty()
        timeoutMillis = cursor.getLong(21)
    }

    private class SQLHelper(context: Context) : SQLiteOpenHelper(context, "$NAME.db", null, VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE `${TimedTaskRunRecord.TABLE}`(" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`task_id` INTEGER, " +
                    "`execution_id` TEXT, " +
                    "`script_path` TEXT, " +
                    "`event_type` TEXT, " +
                    "`scheduled_at` INTEGER, " +
                    "`triggered_at` INTEGER, " +
                    "`started_at` INTEGER, " +
                    "`finished_at` INTEGER, " +
                    "`launch_status` TEXT, " +
                    "`finish_status` TEXT, " +
                    "`exception` TEXT, " +
                    "`duration` INTEGER, " +
                    "`backend` TEXT, " +
                    "`device_state` TEXT, " +
                    "`degradation_reason` TEXT, " +
                    "`next_scheduled_at` INTEGER, " +
                    "`retry_attempt` INTEGER, " +
                    "`max_retries` INTEGER, " +
                    "`backoff_millis` INTEGER, " +
                    "`mutex_key` TEXT, " +
                    "`timeout_millis` INTEGER);",
            )
            db.execSQL("CREATE INDEX `idx_timed_task_run_record_task_id` ON `${TimedTaskRunRecord.TABLE}`(`task_id`);")
            db.execSQL("CREATE INDEX `idx_timed_task_run_record_triggered_at` ON `${TimedTaskRunRecord.TABLE}`(`triggered_at`);")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                listOf(
                    "`retry_attempt` INTEGER DEFAULT 0",
                    "`max_retries` INTEGER DEFAULT 0",
                    "`backoff_millis` INTEGER DEFAULT 0",
                    "`mutex_key` TEXT DEFAULT ''",
                    "`timeout_millis` INTEGER DEFAULT 0",
                ).forEach { column ->
                    db.execSQL("ALTER TABLE `${TimedTaskRunRecord.TABLE}` ADD COLUMN $column;")
                }
            }
        }
    }

    private companion object {
        const val VERSION = 2
        const val NAME = "TimedTaskRunRecordDatabase"
    }
}
