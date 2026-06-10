package org.autojs.autojs.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedTaskRunRecordTest {

    @Test
    fun durationUsesStartTimeWhenAvailable() {
        val record = TimedTaskRunRecord().apply {
            triggeredAt = 1_000L
        }

        record.markStarted(1_200L)
        record.markFinished(TimedTaskRunRecord.FINISH_SUCCESS, null, 1_800L)

        assertEquals(TimedTaskRunRecord.LAUNCH_STARTED, record.launchStatus)
        assertEquals(TimedTaskRunRecord.FINISH_SUCCESS, record.finishStatus)
        assertEquals(600L, record.duration)
        assertTrue(record.isTerminal())
    }

    @Test
    fun skippedRecordIsTerminalAndKeepsReason() {
        val record = TimedTaskRunRecord().apply {
            triggeredAt = 2_000L
        }

        record.markSkipped("Task mutex is locked", 2_050L)

        assertEquals(TimedTaskRunRecord.LAUNCH_SKIPPED, record.launchStatus)
        assertEquals(TimedTaskRunRecord.FINISH_NOT_STARTED, record.finishStatus)
        assertEquals("Task mutex is locked", record.exception)
        assertTrue(record.isTerminal())
    }

}
