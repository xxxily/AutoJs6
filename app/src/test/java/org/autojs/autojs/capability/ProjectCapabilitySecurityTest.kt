package org.autojs.autojs.capability

import org.autojs.autojs.project.ProjectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ProjectCapabilitySecurityTest {

    @Test
    fun undeclaredHighRiskCapabilityRequiresAuthorization() {
        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = projectConfig(capabilities = emptyList()),
            request = ProjectCapabilityGuardRequest(
                api = "shizuku.execCommand",
                capabilities = listOf(CapabilityRegistry.SHIZUKU),
                riskLevel = "high",
                target = "settings put secure enabled_accessibility_services x",
            ),
        )

        assertFalse(decision.allowed)
        assertEquals(ProjectCapabilityAction.REJECT, decision.action)
        assertTrue(decision.reason.contains("User authorization required"))
    }

    @Test
    fun declaredHighRiskCapabilityIsAllowed() {
        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = projectConfig(capabilities = listOf(CapabilityRegistry.SHIZUKU)),
            request = ProjectCapabilityGuardRequest(
                api = "shizuku.execCommand",
                capabilities = listOf(CapabilityRegistry.SHIZUKU),
                riskLevel = "high",
            ),
        )

        assertTrue(decision.allowed)
        assertEquals(ProjectCapabilityAction.ALLOW, decision.action)
    }

    @Test
    fun rememberedProjectAuthorizationAllowsMissingCapability() {
        val config = projectConfig(capabilities = emptyList())
        val store = InMemoryProjectCapabilityAuthorizationStore()
        store.remember(config.packageName, CapabilityRegistry.SHIZUKU, ProjectCapabilityAction.ALLOW)

        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = config,
            request = ProjectCapabilityGuardRequest(
                api = "shizuku.execCommand",
                capabilities = listOf(CapabilityRegistry.SHIZUKU),
                riskLevel = "high",
            ),
            authorizationStore = store,
        )

        assertTrue(decision.allowed)
        assertTrue(decision.remembered)
    }

    @Test
    fun riskPolicyCanRejectUndeclaredCapabilityWithoutPrompt() {
        val config = projectConfig(capabilities = emptyList()).apply {
            riskPolicy.undeclaredAction = "reject"
        }

        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = config,
            request = ProjectCapabilityGuardRequest(
                api = "shell",
                capabilities = listOf(CapabilityRegistry.SHELL),
                riskLevel = "high",
            ),
        )

        assertFalse(decision.allowed)
        assertEquals(ProjectCapabilityAction.REJECT, decision.action)
        assertTrue(decision.reason.contains("Risk policy reject"))
    }

    @Test
    fun filePolicyRejectsOutOfScopeOverwrite() {
        val tempProject = createTempDirectory(prefix = "autojs6-capability-test").toFile()
        val config = projectConfig(capabilities = listOf(CapabilityRegistry.STORAGE)).apply {
            sourcePath = tempProject.path
            filePolicy.allowedPaths = listOf("data")
            filePolicy.writablePaths = listOf("data/out")
        }

        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = config,
            request = ProjectCapabilityGuardRequest(
                api = "files.write",
                capabilities = listOf(CapabilityRegistry.STORAGE),
                riskLevel = "high",
                target = File(tempProject.parentFile, "outside.txt").path,
                accessType = "overwrite",
            ),
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("filePolicy.allowedPaths"))
    }

    @Test
    fun auditLogStoresHighRiskDecision() {
        ProjectCapabilityAuditLog.clear()
        val decision = ProjectCapabilitySecurity.evaluate(
            projectConfig = projectConfig(capabilities = listOf(CapabilityRegistry.UNINSTALL_APK)),
            request = ProjectCapabilityGuardRequest(
                api = "app.uninstall",
                capabilities = listOf(CapabilityRegistry.UNINSTALL_APK),
                riskLevel = "critical",
                target = "com.example.app",
            ),
        )

        ProjectCapabilityAuditLog.record(decision, "requested")
        val entry = ProjectCapabilityAuditLog.snapshot().single()

        assertEquals("app.uninstall", entry.api)
        assertEquals(listOf(CapabilityRegistry.UNINSTALL_APK), entry.capabilities)
        assertEquals("critical", entry.riskLevel)
        assertTrue(entry.allowed)
    }

    private fun projectConfig(capabilities: List<String>): ProjectConfig {
        return ProjectConfig()
            .setName("Capability Test")
            .setPackageName("org.autojs.autojs6.capability.test")
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setMainScriptFileName("main.js")
            .setCapabilities(capabilities)
    }
}
