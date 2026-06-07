package org.autojs.autojs.util

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

object WorkManagerUtils {

    private const val LOG_TAG = "WorkManagerUtils"

    @Volatile
    private var initialized = false

    @JvmStatic
    fun ensureInitialized(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val appContext = context.applicationContext
            if (runCatching { WorkManager.getInstance(appContext) }.isSuccess) {
                initialized = true
                return
            }

            runCatching {
                WorkManager.initialize(appContext, Configuration.Builder().build())
            }.onFailure { e ->
                if (!e.isAlreadyInitialized()) {
                    Log.e(LOG_TAG, "Failed to initialize WorkManager", e)
                    throw e
                }
            }

            initialized = true
        }
    }

    @JvmStatic
    fun getInstance(context: Context): WorkManager {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        return WorkManager.getInstance(appContext)
    }

    private fun Throwable.isAlreadyInitialized(): Boolean {
        return this is IllegalStateException && message?.contains("already initialized", ignoreCase = true) == true
    }

}
