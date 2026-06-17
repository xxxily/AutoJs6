package org.autojs.autojs.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceScopeTest {

    @Test
    fun releaseContinuesAfterFailureAndRecordsAuditSummary() {
        val released = mutableListOf<String>()
        val scope = ResourceScope("runtime@test")

        scope.release("timers") { released += "timers" }
        scope.release("shell") { error("boom") }
        scope.release("closeables") { released += "closeables" }

        val summary = scope.snapshot()
        assertEquals("runtime@test", summary.ownerId)
        assertEquals(3, summary.total)
        assertEquals(2, summary.succeeded)
        assertEquals(1, summary.failed)
        assertEquals(listOf("timers", "closeables"), released)
        assertEquals("shell", summary.resources.single { !it.success }.name)
        assertTrue(summary.toAuditString().contains("failedResources=shell:java.lang.IllegalStateException"))
    }

    @Test
    fun clearDropsPreviousReleaseResults() {
        val scope = ResourceScope("runtime@test")
        scope.release("ui") {}

        assertFalse(scope.snapshot().resources.isEmpty())

        scope.clear()

        assertTrue(scope.snapshot().resources.isEmpty())
    }
}
