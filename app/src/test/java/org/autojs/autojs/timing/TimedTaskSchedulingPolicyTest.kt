package org.autojs.autojs.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedTaskSchedulingPolicyTest {

    @Test
    fun shouldRunNowWhenTriggerTimeHasPassed() {
        assertTrue(TimedTaskSchedulingPolicy.shouldRunNow(triggerAtMillis = 1_000L, nowMillis = 1_000L))
        assertTrue(TimedTaskSchedulingPolicy.shouldRunNow(triggerAtMillis = 999L, nowMillis = 1_000L))
        assertFalse(TimedTaskSchedulingPolicy.shouldRunNow(triggerAtMillis = 1_001L, nowMillis = 1_000L))
    }

    @Test
    fun schedulesOnlyWithinTwoDayWindowUnlessAlreadyScheduled() {
        val now = 10_000L
        val withinWindow = now + TimedTaskSchedulingPolicy.scheduleTaskMinTimeMillis
        val outsideWindow = withinWindow + 1L

        assertTrue(TimedTaskSchedulingPolicy.shouldSchedule(withinWindow, now, isScheduled = false, force = false))
        assertFalse(TimedTaskSchedulingPolicy.shouldSchedule(outsideWindow, now, isScheduled = false, force = false))
        assertFalse(TimedTaskSchedulingPolicy.shouldSchedule(withinWindow, now, isScheduled = true, force = false))
        assertTrue(TimedTaskSchedulingPolicy.shouldSchedule(withinWindow, now, isScheduled = true, force = true))
    }

    @Test
    fun doesNotSchedulePastTriggerBecauseItShouldRunImmediately() {
        assertFalse(TimedTaskSchedulingPolicy.shouldSchedule(triggerAtMillis = 999L, nowMillis = 1_000L, isScheduled = false, force = true))
    }

    @Test
    fun selectAlarmCandidatesSortsByNextTimeAndLimitsQuota() {
        val tasks = (0..TimedTaskSchedulingPolicy.MAX_ALARM_SLOTS + 4).map { index ->
            TestTask(id = index, nextTimeMillis = 10_000L - index)
        }

        val selected = TimedTaskSchedulingPolicy.selectAlarmCandidates(tasks) { it.nextTimeMillis }

        assertEquals(TimedTaskSchedulingPolicy.MAX_ALARM_SLOTS, selected.size)
        assertEquals(tasks.last(), selected.first())
        assertEquals(5, selected.last().id)
    }

    private data class TestTask(val id: Int, val nextTimeMillis: Long)
}
