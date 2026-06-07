package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import org.autojs.autojs.util.WorkManagerUtils
import java.util.concurrent.TimeUnit

object PluginIndexSyncScheduler {

    private const val UNIQUE_WORK_NAME = "plugin_index_auto_sync"

    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PluginIndexSyncWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        ).setConstraints(constraints)
            .build()

        WorkManagerUtils.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManagerUtils.getInstance(context)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }

}
