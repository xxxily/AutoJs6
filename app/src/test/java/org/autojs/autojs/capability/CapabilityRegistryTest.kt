package org.autojs.autojs.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CapabilityRegistryTest {

    @Test
    fun registryCoversRequiredCoreCapabilities() {
        val required = setOf(
            CapabilityRegistry.ACCESSIBILITY,
            CapabilityRegistry.SCREEN_CAPTURE,
            CapabilityRegistry.OVERLAY,
            CapabilityRegistry.NOTIFICATIONS,
            CapabilityRegistry.STORAGE,
            CapabilityRegistry.NETWORK,
            CapabilityRegistry.ROOT,
            CapabilityRegistry.SHIZUKU,
            CapabilityRegistry.SHELL,
            CapabilityRegistry.USAGE_STATS,
            CapabilityRegistry.WRITE_SETTINGS,
            CapabilityRegistry.WRITE_SECURE_SETTINGS,
            CapabilityRegistry.EXACT_ALARM,
            CapabilityRegistry.BACKGROUND_RUN,
            CapabilityRegistry.BATTERY_OPTIMIZATION,
            CapabilityRegistry.BOOT_COMPLETED,
            CapabilityRegistry.INSTALL_APK,
            CapabilityRegistry.UNINSTALL_APK,
            CapabilityRegistry.SMS,
            CapabilityRegistry.CONTACTS,
            CapabilityRegistry.PHONE,
            CapabilityRegistry.CAMERA,
            CapabilityRegistry.RECORD_AUDIO,
            CapabilityRegistry.LOCATION,
        )
        val definitions = CapabilityRegistry.allDefinitions()
        val ids = definitions.map { it.id }.toSet()

        assertTrue("Capability count", definitions.size >= 20)
        assertTrue("Missing capabilities: ${(required - ids).joinToString()}", ids.containsAll(required))
        assertEquals("Duplicate capability ids", ids.size, definitions.size)
        definitions.forEach { definition ->
            assertTrue("${definition.id} has no related APIs", definition.relatedApis.isNotEmpty())
            assertTrue("${definition.id} has no description", definition.description.isNotBlank())
            assertTrue("${definition.id} has no request hint", definition.requestHint.isNotBlank())
        }
    }

    @Test
    fun statusModelContainsRequiredWireStates() {
        val wireStates = CapabilityStatus.entries.map { it.wireName }.toSet()

        assertEquals(
            setOf("available", "requestable", "missing", "blocked", "unsupported"),
            wireStates,
        )
    }

    @Test
    fun scriptScannerFindsAutomationAndScreenCaptureCapabilities() {
        val script = """
            auto.waitFor();
            requestScreenCapture();
            click("OK");
            images.findColor(captureScreen(), "#ffffff");
            vision.findButton("登录", { sources: "a11y+ocr" });
        """.trimIndent()
        val ids = CapabilityRegistry.inferCapabilityIdsFromScript(script).toSet()

        assertTrue(CapabilityRegistry.ACCESSIBILITY in ids)
        assertTrue(CapabilityRegistry.SCREEN_CAPTURE in ids)
    }

    @Test
    fun scriptScannerMapsVisionToAccessibilityAndScreenCaptureCapabilities() {
        val ids = CapabilityRegistry.inferCapabilityIdsFromScript(
            """vision.waitForScene({ texts: ["登录"], buttons: ["确定"] }, { sources: "a11y+ocr" });""",
        ).toSet()

        assertTrue(CapabilityRegistry.ACCESSIBILITY in ids)
        assertTrue(CapabilityRegistry.SCREEN_CAPTURE in ids)
    }

    @Test
    fun explainMapsApisToCapabilities() {
        assertEquals(CapabilityRegistry.SCREEN_CAPTURE, CapabilityRegistry.explain("images.captureScreen")?.id)
        assertEquals(CapabilityRegistry.ACCESSIBILITY, CapabilityRegistry.explain("automator.click")?.id)
        assertEquals(CapabilityRegistry.SHIZUKU, CapabilityRegistry.explain("shizuku.execCommand")?.id)
    }

    @Test
    fun manifestPermissionMappingUsesCapabilityDefinitions() {
        val permissions = CapabilityRegistry.manifestPermissionsForCapabilityIds(
            listOf(CapabilityRegistry.NETWORK, CapabilityRegistry.SHIZUKU, CapabilityRegistry.INSTALL_APK),
        )

        assertTrue("android.permission.INTERNET" in permissions)
        assertTrue("moe.shizuku.manager.permission.API_V23" in permissions)
        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
    }

    @Test
    fun aiRiskValidationUsesCapabilityRegistry() {
        val appDir = resolveAppDir()
        val aiIndexSource = appDir.resolve("src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt").readText()

        assertTrue("AI validation should import capability registry", "CapabilityRegistry" in aiIndexSource)
        assertTrue("AI validation should infer capabilities from generated code", "inferCapabilitiesFromScript" in aiIndexSource)
        assertFalse("AI validation should not be keyword-only", "return RISK_RULES\n            .filter" in aiIndexSource)
    }

    private companion object {
        private fun resolveAppDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return listOf(workingDir, workingDir.resolve("app"))
                .firstOrNull { it.resolve("src/main/java/org/autojs/autojs/capability/CapabilityRegistry.kt").isFile }
                ?: error("Cannot resolve app module directory from ${workingDir.path}")
        }
    }
}
