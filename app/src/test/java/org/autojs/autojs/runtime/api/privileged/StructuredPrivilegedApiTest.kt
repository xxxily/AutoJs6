package org.autojs.autojs.runtime.api.privileged

import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.runtime.api.AbstractShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StructuredPrivilegedApiTest {

    @Before
    fun setUp() {
        PrivilegedAuditLog.clear()
    }

    @Test
    fun appForceStopBuildsStructuredRequestWithRiskAndCapabilities() {
        val request = StructuredPrivilegedCommands.appForceStop(
            "com.example.target",
            PrivilegedOptions(userId = 10),
        )

        assertEquals("app.forceStop", request.operation)
        assertEquals("am.force-stop", request.commandKind)
        assertEquals("am force-stop --user 10 com.example.target", request.command)
        assertEquals("high", request.riskLevel.wireName)
        assertTrue(CapabilityRegistry.SHIZUKU in request.capabilities)
        assertTrue(CapabilityRegistry.ROOT in request.capabilities)
        assertFalse(request.allowShellFallback)
    }

    @Test
    fun settingsPutQuotesValuesAndDeclaresSecureSettingsCapability() {
        val request = StructuredPrivilegedCommands.settingsPut(
            namespace = "secure",
            key = "enabled_accessibility_services",
            value = "com.example/.Service:other.pkg/.Other Service",
        )

        assertEquals("settings.put", request.operation)
        assertEquals(
            "settings put secure enabled_accessibility_services 'com.example/.Service:other.pkg/.Other Service'",
            request.command,
        )
        assertTrue(CapabilityRegistry.WRITE_SECURE_SETTINGS in request.capabilities)
    }

    @Test
    fun validationRejectsShellInjectionLikeArguments() {
        assertFailsWithMessage("Invalid package name") {
            StructuredPrivilegedCommands.appForceStop("com.example;rm -rf /")
        }
        assertFailsWithMessage("Settings namespace must be one of") {
            StructuredPrivilegedCommands.settingsGet("secure;rm", "enabled_accessibility_services")
        }
        assertFailsWithMessage("Coordinate cannot be negative") {
            StructuredPrivilegedCommands.inputTap(-1, 10)
        }
    }

    @Test
    fun executorPrefersShizukuAndRecordsCommandFreeAudit() {
        val request = StructuredPrivilegedCommands.appForceStop("com.example.target")
        val result = StructuredPrivilegedExecutor.execute(
            request,
            fakeEnv(shizuku = true, root = true),
        )

        assertTrue(result.ok)
        assertEquals(PrivilegedBackend.SHIZUKU, result.backend)
        assertEquals("shizuku:am.force-stop", result.result)
        val audit = PrivilegedAuditLog.snapshot().single()
        assertEquals("app.forceStop", audit.operation)
        assertEquals("am.force-stop", audit.commandKind)
        assertEquals("shizuku", audit.backend)
        assertFalse("Audit export must not expose raw shell commands", PrivilegedAuditLog.exportJson().contains("am force-stop"))
    }

    @Test
    fun executorFallsBackToRootBeforeShell() {
        val request = StructuredPrivilegedCommands.appClearData("com.example.target")
        val result = StructuredPrivilegedExecutor.execute(
            request,
            fakeEnv(shizuku = false, root = true),
        )

        assertTrue(result.ok)
        assertEquals(PrivilegedBackend.ROOT, result.backend)
        assertEquals("root:pm.clear", result.result)
    }

    @Test
    fun shellFallbackIsOnlyAvailableForRequestsThatAllowIt() {
        val noShell = StructuredPrivilegedExecutor.execute(
            StructuredPrivilegedCommands.appForceStop("com.example.target"),
            fakeEnv(shizuku = false, root = false),
        )
        assertFalse(noShell.ok)
        assertEquals(PrivilegedBackend.UNAVAILABLE, noShell.backend)

        val shellAllowed = StructuredPrivilegedExecutor.execute(
            StructuredPrivilegedCommands.settingsGet("secure", "enabled_accessibility_services"),
            fakeEnv(shizuku = false, root = false),
        )
        assertTrue(shellAllowed.ok)
        assertEquals(PrivilegedBackend.SHELL, shellAllowed.backend)
        assertEquals("shell:settings.get", shellAllowed.result)
    }

    @Test
    fun metadataCoversRequiredStructuredFamilies() {
        val operations = StructuredPrivilegedCommands.metadata.map { it.operation }.toSet()

        listOf(
            "app.forceStop",
            "app.clearData",
            "app.grantPermission",
            "app.install",
            "settings.get",
            "settings.put",
            "package.info",
            "package.apkPath",
            "package.permissionState",
            "input.injectTap",
            "input.injectSwipe",
            "input.keyEvent",
            "process.list",
            "process.kill",
            "process.foreground",
            "users.list",
            "users.current",
            "users.runAsUser",
            "users.dualApps",
        ).forEach { operation ->
            assertTrue("$operation missing from metadata", operation in operations)
        }
    }

    private fun fakeEnv(
        shizuku: Boolean,
        root: Boolean,
    ) = PrivilegedExecutionEnvironment(
        isShizukuAvailable = { shizuku },
        isRootAvailable = { root },
        runShizuku = { command -> shellResult("shizuku:${kindOf(command)}") },
        runRoot = { command -> shellResult("root:${kindOf(command)}") },
        runShell = { command -> shellResult("shell:${kindOf(command)}") },
    )

    private fun kindOf(command: String): String = when {
        command.startsWith("am force-stop") -> "am.force-stop"
        command.startsWith("pm clear") -> "pm.clear"
        command.startsWith("settings get") -> "settings.get"
        else -> "unknown"
    }

    private fun shellResult(result: String) = AbstractShell.Result(0, result, "")

    private fun assertFailsWithMessage(
        messagePart: String,
        block: () -> Unit,
    ) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected failure containing '$messagePart'", error is IllegalArgumentException)
        assertTrue("Actual error: ${error?.message}", error?.message?.contains(messagePart) == true)
    }
}
