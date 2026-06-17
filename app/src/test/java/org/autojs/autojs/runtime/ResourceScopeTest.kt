package org.autojs.autojs.runtime

import java.io.File
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

    @Test
    fun scriptRuntimeExitPathPublishesResourceReleaseAuditSummary() {
        val runtimeSource = resolveRootDir()
            .resolve("app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt")
            .readText()

        assertTrue("ScriptRuntime.onExit must reset resource scope before release", "resourceScope.clear()" in runtimeSource)
        assertTrue("ScriptRuntime.onExit must publish latest release summary", "lastResourceReleaseSummary = resourceScope.snapshot()" in runtimeSource)
        assertTrue("ScriptRuntime.onExit must log audit summary", "Resource release summary:" in runtimeSource)
        REQUIRED_RUNTIME_RESOURCE_NAMES.forEach { resourceName ->
            assertTrue("ScriptRuntime.onExit must release $resourceName through ResourceScope", "resourceScope.release(\"$resourceName\")" in runtimeSource)
        }
    }

    private companion object {
        private val REQUIRED_RUNTIME_RESOURCE_NAMES = listOf(
            "websocket",
            "accessibility-events",
            "image-wrappers",
            "floaty",
            "threads",
            "events",
            "media",
            "ipc",
            "shell",
            "screen-capturer",
            "ocr-mlkit",
            "timers",
            "ui",
            "closeables",
        )

        private fun resolveRootDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }
    }
}
