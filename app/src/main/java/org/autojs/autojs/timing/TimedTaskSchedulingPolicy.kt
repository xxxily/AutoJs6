package org.autojs.autojs.timing

import java.util.concurrent.TimeUnit

object TimedTaskSchedulingPolicy {

    // Alarm quota reserved for the current application (not exceeding 500).
    // zh-CN: 为当前应用保留的闹钟配额 (不超过 500).
    const val MAX_ALARM_SLOTS = 450

    val scheduleTaskMinTimeMillis: Long = TimeUnit.DAYS.toMillis(2)
    val schedulePeriodicCheckTimeMillis: Long = TimeUnit.MINUTES.toMillis(20)

    fun shouldRunNow(triggerAtMillis: Long, nowMillis: Long): Boolean {
        return triggerAtMillis <= nowMillis
    }

    fun shouldSchedule(triggerAtMillis: Long, nowMillis: Long, isScheduled: Boolean, force: Boolean): Boolean {
        if (shouldRunNow(triggerAtMillis, nowMillis)) return false
        if (!force && isScheduled) return false
        return triggerAtMillis - nowMillis <= scheduleTaskMinTimeMillis
    }

    fun timeWindowMillis(triggerAtMillis: Long, nowMillis: Long): Long {
        return triggerAtMillis - nowMillis
    }

    fun <T> selectAlarmCandidates(tasks: List<T>, nextTimeMillis: (T) -> Long): List<T> {
        return tasks.sortedBy(nextTimeMillis).take(MAX_ALARM_SLOTS)
    }
}
