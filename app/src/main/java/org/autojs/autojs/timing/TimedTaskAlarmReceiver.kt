package org.autojs.autojs.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.autojs.autojs.timing.AlarmTimedTaskScheduler.ACTION_RUN_ALARM_TIMED_TASK
import org.autojs.autojs.timing.TaskReceiver.EXTRA_SCHEDULED_AT
import org.autojs.autojs.timing.TaskReceiver.EXTRA_TASK_ID

class TimedTaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_ALARM_TIMED_TASK) return
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id <= 0) return
        val task = TimedTaskManager.getTimedTask(id)
        Log.d("TimedTaskAlarmReceiver", "onReceive: id=$id, task=$task")
        if (task == null) return
        TimedTaskManager.triggerTask(
            context,
            task,
            "AlarmTimedTaskScheduler",
            intent.getLongExtra(EXTRA_SCHEDULED_AT, task.getNextTime(context)),
        )
    }

}
