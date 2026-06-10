package org.autojs.autojs.observability

import org.autojs.autojs.capability.ProjectCapabilityAuditEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptObservabilityTest {

    @After
    fun tearDown() {
        ScriptObservability.clearForTest()
    }

    @Test
    fun recordsRunTimelineLogsCapabilityEventsAndDiagnostics() {
        ScriptObservability.recordStartForTest(7, "/sdcard/脚本/demo.js")
        ScriptObservability.recordLog(2, "hello from script")
        ScriptObservability.recordCapabilityEvent(
            ProjectCapabilityAuditEntry(
                id = 9L,
                timestamp = 1234L,
                projectKey = "demo",
                api = "images.captureScreen",
                capabilities = listOf("screen_capture"),
                riskLevel = "high",
                action = "allow",
                allowed = true,
                target = "screen",
                message = "ok",
            )
        )

        val running = ScriptObservability.runningRuns().single()
        assertEquals(7, running.executionId)
        assertEquals("running", running.status)
        assertEquals("hello from script", running.logs.single().message)
        assertEquals("images.captureScreen", running.capabilityEvents.single().api)

        ScriptObservability.recordFinishForTest(7, success = false, throwable = IllegalStateException("boom"))

        val snapshot = ScriptObservability.snapshot(7)
        assertNotNull(snapshot)
        assertEquals("exception", snapshot!!.status)
        assertEquals("java.lang.IllegalStateException", snapshot.exception!!.className)
        assertTrue(snapshot.exception.stackTrace.contains("boom"))
        assertTrue(snapshot.resource.maxMemoryBytes > 0)
        assertTrue(snapshot.debug.stackAvailable)

        val diagnosticsJson = ScriptObservability.exportDiagnosticsJson(7)
        assertTrue(diagnosticsJson.contains("\"executionId\": 7"))
        assertTrue(diagnosticsJson.contains("boom"))
    }
}
