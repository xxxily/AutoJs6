package org.autojs.autojs.apkbuilder

import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.project.ProjectConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class ProjectBuildPreflightTest {

    @Test
    fun projectPreflightReportsMissingMainScript() {
        val projectDir = createTempDirectory(prefix = "autojs6-preflight-missing-main").toFile()
        val config = baseConfig(projectDir.path)

        val report = ProjectBuildPreflight.run(config)

        assertTrue(report.hasErrors())
        assertTrue(report.errors.any { it.code == "main_script_missing" })
    }

    @Test
    fun projectPreflightReportsMissingResourceReference() {
        val projectDir = createTempDirectory(prefix = "autojs6-preflight-missing-resource").toFile()
        projectDir.resolve("main.js").writeText("""files.read("./data/missing.json");""")
        val config = baseConfig(projectDir.path)

        val report = ProjectBuildPreflight.run(config)

        assertTrue(report.hasErrors())
        assertTrue(report.errors.any { it.code == "resource_missing" && it.path == "data/missing.json" })
    }

    @Test
    fun projectPreflightBlocksUndeclaredDangerousCapability() {
        val projectDir = createTempDirectory(prefix = "autojs6-preflight-capability").toFile()
        projectDir.resolve("main.js").writeText(
            """
            auto.waitFor();
            requestScreenCapture();
            floaty.window(<frame/>);
            """.trimIndent(),
        )
        val config = baseConfig(projectDir.path)

        val report = ProjectBuildPreflight.run(config)

        assertTrue(report.hasErrors())
        assertTrue(CapabilityRegistry.SCREEN_CAPTURE in report.missingCapabilities)
        assertTrue(CapabilityRegistry.OVERLAY in report.missingCapabilities)
        assertTrue(report.errors.any { it.code == "capability_missing" })
    }

    @Test
    fun projectPreflightBlocksUndeclaredPluginDependency() {
        val projectDir = createTempDirectory(prefix = "autojs6-preflight-plugin").toFile()
        projectDir.resolve("main.js").writeText("""plugins.load("org.autojs.plugin.demo");""")
        val config = baseConfig(projectDir.path)

        val report = ProjectBuildPreflight.run(config)

        assertTrue(report.hasErrors())
        assertTrue(report.errors.any { it.code == "plugin_dependency_missing" })
    }

    @Test
    fun declaredCapabilitiesMapPermissionsAndInrtSettings() {
        val projectDir = createTempDirectory(prefix = "autojs6-preflight-mapping").toFile()
        projectDir.resolve("main.js").writeText("""requestScreenCapture(); floaty.window(<frame/>); notice("ok");""")
        val config = baseConfig(projectDir.path).apply {
            capabilities = listOf(
                CapabilityRegistry.SCREEN_CAPTURE,
                CapabilityRegistry.OVERLAY,
                CapabilityRegistry.NOTIFICATIONS,
            )
        }

        val report = ProjectBuildPreflight.run(config)

        assertFalse(report.hasErrors())
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in report.mappedPermissions)
        assertTrue("android.permission.SYSTEM_ALERT_WINDOW" in report.mappedPermissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in report.mappedPermissions)
        assertTrue("mediaProjection" in report.foregroundServiceTypes)
        assertTrue("display_over_other_apps" in report.requiredInrtSettings)
        assertTrue("post_notifications" in report.requiredInrtSettings)
    }

    @Test
    fun singleFilePreflightCanApplyInferredCapabilities() {
        val scriptFile = createTempDirectory(prefix = "autojs6-preflight-single").toFile().resolve("main.js")
        scriptFile.writeText("""requestScreenCapture();""")
        val config = baseConfig(scriptFile.path)

        val report = ProjectBuildPreflight.run(config)
        ProjectBuildPreflight.applyReportToConfig(config, report)

        assertFalse(report.hasErrors())
        assertTrue(CapabilityRegistry.SCREEN_CAPTURE in report.missingCapabilities)
        assertTrue(CapabilityRegistry.SCREEN_CAPTURE in config.capabilities)
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in config.permissions)
    }

    private fun baseConfig(sourcePath: String): ProjectConfig = ProjectConfig()
        .setName("Preflight")
        .setPackageName("org.autojs.preflight")
        .setVersionName("1.0.0")
        .setVersionCode(1)
        .setSourcePath(sourcePath)
        .setPermissions(emptyList())
        .setCapabilities(emptyList())
        .setPluginDependencies(emptyList())
}
