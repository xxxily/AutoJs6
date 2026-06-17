package org.autojs.autojs.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityCoreTest {

    @Test
    fun coreCapabilityModelIsPureJvmAndCoversRequiredCapabilities() {
        val definitions = CapabilityCore.allDefinitions()
        val ids = definitions.map { it.id }.toSet()

        assertTrue(definitions.size >= 20)
        assertEquals(ids.size, definitions.size)
        assertTrue(REQUIRED_CAPABILITIES.all { it in ids })
        definitions.forEach { definition ->
            assertTrue(definition.relatedApis.isNotEmpty(), "${definition.id} has no related APIs")
            assertTrue(definition.description.isNotBlank(), "${definition.id} has no description")
            assertTrue(definition.requestHint.isNotBlank(), "${definition.id} has no request hint")
        }
    }

    @Test
    fun scriptScannerFindsAutomationAndScreenCaptureCapabilities() {
        val ids = CapabilityCore.inferCapabilityIdsFromScript(
            """
            auto.waitFor();
            requestScreenCapture();
            click("OK");
            images.findColor(captureScreen(), "#ffffff");
            vision.findButton("登录", { sources: "a11y+ocr" });
            """.trimIndent(),
        ).toSet()

        assertTrue(CapabilityCore.ACCESSIBILITY in ids)
        assertTrue(CapabilityCore.SCREEN_CAPTURE in ids)
    }

    @Test
    fun scriptScannerIgnoresStringsAndComments() {
        val ids = CapabilityCore.inferCapabilityIdsFromScript(
            """
            // shizuku.execCommand("pm clear target")
            const sample = "requestScreenCapture()";
            const template = `images.captureScreen()`;
            """.trimIndent(),
        )

        assertTrue(ids.isEmpty())
    }

    @Test
    fun explainAndManifestPermissionMappingUseCoreDefinitions() {
        assertEquals(CapabilityCore.SCREEN_CAPTURE, CapabilityCore.definitionFor("images.captureScreen")?.id)
        assertEquals(CapabilityCore.ACCESSIBILITY, CapabilityCore.definitionFor("automator.click")?.id)
        assertEquals(CapabilityCore.SHIZUKU, CapabilityCore.definitionFor("shizuku.execCommand")?.id)

        val permissions = CapabilityCore.manifestPermissionsForCapabilityIds(
            listOf(CapabilityCore.NETWORK, CapabilityCore.SHIZUKU, CapabilityCore.INSTALL_APK),
        )

        assertTrue("android.permission.INTERNET" in permissions)
        assertTrue("moe.shizuku.manager.permission.API_V23" in permissions)
        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
    }

    @Test
    fun statusModelContainsRequiredWireStates() {
        assertEquals(
            setOf("available", "requestable", "missing", "blocked", "unsupported"),
            CapabilityStatus.entries.map { it.wireName }.toSet(),
        )
    }

    private companion object {
        private val REQUIRED_CAPABILITIES = setOf(
            CapabilityCore.ACCESSIBILITY,
            CapabilityCore.SCREEN_CAPTURE,
            CapabilityCore.OVERLAY,
            CapabilityCore.NOTIFICATIONS,
            CapabilityCore.STORAGE,
            CapabilityCore.NETWORK,
            CapabilityCore.ROOT,
            CapabilityCore.SHIZUKU,
            CapabilityCore.SHELL,
            CapabilityCore.USAGE_STATS,
            CapabilityCore.WRITE_SETTINGS,
            CapabilityCore.WRITE_SECURE_SETTINGS,
            CapabilityCore.EXACT_ALARM,
            CapabilityCore.BACKGROUND_RUN,
            CapabilityCore.BATTERY_OPTIMIZATION,
            CapabilityCore.BOOT_COMPLETED,
            CapabilityCore.INSTALL_APK,
            CapabilityCore.UNINSTALL_APK,
            CapabilityCore.SMS,
            CapabilityCore.CONTACTS,
            CapabilityCore.PHONE,
            CapabilityCore.CAMERA,
            CapabilityCore.RECORD_AUDIO,
            CapabilityCore.LOCATION,
        )
    }
}
