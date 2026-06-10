package org.autojs.autojs.timing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Stardust on Nov 27, 2017.
 */
public class TaskReceiver extends BroadcastReceiver {

    public static final String ACTION_TASK = "org.autojs.autojs.action.task";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_SCHEDULED_AT = "scheduled_at";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(EXTRA_TASK_ID, -1);
        if (id >= 0) {
            TimedTask task = TimedTaskManager.getTimedTask(id);
            if (task != null) {
                TimedTaskManager.triggerTask(
                        context,
                        task,
                        "TaskReceiver",
                        intent.getLongExtra(EXTRA_SCHEDULED_AT, task.getNextTime(context)),
                        TimedTaskRunRecord.EVENT_RUN,
                        0
                );
            }
        }
    }
}
