package org.autojs.autojs.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigTest {

    @Test
    fun parsesCanonicalProjectConfig() {
        val config = ProjectConfig.fromJson(
            """
            {
              "name": "Compat Lab",
              "packageName": "org.autojs.autojs6.compatlab",
              "versionName": "1.0.0",
              "versionCode": 7,
              "main": "main.js",
              "permissions": [
                "android.permission.INTERNET",
                "android.permission.SYSTEM_ALERT_WINDOW"
              ],
              "launchConfig": {
                "logsVisible": false,
                "splashVisible": true,
                "runOnBoot": true
              }
            }
            """.trimIndent(),
        )

        assertNotNull(config)
        requireNotNull(config)
        assertEquals("Compat Lab", config.name)
        assertEquals("org.autojs.autojs6.compatlab", config.packageName)
        assertEquals("1.0.0", config.versionName)
        assertEquals(7, config.versionCode)
        assertEquals("main.js", config.mainScriptFileName)
        assertEquals(listOf("android.permission.INTERNET", "android.permission.SYSTEM_ALERT_WINDOW"), config.permissions)
        assertEquals(false, config.launchConfig.isLogsVisible)
        assertEquals(true, config.launchConfig.isSplashVisible)
        assertEquals(true, config.launchConfig.isRunOnBoot)
    }

    @Test
    fun parsesCompatibleProjectConfigKeys() {
        val config = ProjectConfig.fromJson(
            """
            {
              "projectName": "Legacy Compat",
              "package": "org.autojs.autojs6.legacy",
              "version": "2.1.0",
              "versionCode": "9",
              "mainScriptFile": "legacy.js",
              "launch": {
                "hideLogs": true,
                "hideSplash": true,
                "autoRunOnBoot": true
              },
              "signature": "v2, v1"
            }
            """.trimIndent(),
        )

        assertNotNull(config)
        requireNotNull(config)
        assertEquals("Legacy Compat", config.name)
        assertEquals("org.autojs.autojs6.legacy", config.packageName)
        assertEquals("2.1.0", config.versionName)
        assertEquals(9, config.versionCode)
        assertEquals("legacy.js", config.mainScriptFileName)
        assertEquals(false, config.launchConfig.isLogsVisible)
        assertEquals(false, config.launchConfig.isSplashVisible)
        assertEquals(true, config.launchConfig.isRunOnBoot)
        assertEquals("V1 + V2", config.signatureScheme)
    }

    @Test
    fun rejectsProjectConfigWithoutRequiredFields() {
        assertNull(ProjectConfig.fromJson("""{"name":"Missing package","versionName":"1.0.0","main":"main.js"}"""))
    }

    @Test
    fun defaultPermissionsContainCoreRuntimeNeeds() {
        assertTrue(ProjectConfig.DEFAULT_PERMISSIONS.contains("android.permission.INTERNET"))
        assertTrue(ProjectConfig.DEFAULT_PERMISSIONS.contains("android.permission.WAKE_LOCK"))
        assertTrue(ProjectConfig.DEFAULT_PERMISSIONS.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(ProjectConfig.DEFAULT_PERMISSIONS.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"))
    }

    @Test
    fun parsesProjectCapabilityManifestAndPolicies() {
        val config = ProjectConfig.fromJson(
            """
            {
              "name": "Secure Project",
              "packageName": "org.autojs.autojs6.secure",
              "versionName": "1.0.0",
              "versionCode": 1,
              "main": "main.js",
              "capabilityList": ["shizuku", "storage", "uninstall_apk"],
              "pluginDeps": ["org.autojs.plugin.demo", "project-plugin-a"],
              "riskPolicies": {
                "defaultPolicy": "prompt",
                "missingCapability": "reject",
                "criticalRisk": "reject",
                "rememberChoice": false
              },
              "network": {
                "domainWhitelist": ["api.example.com"],
                "cleartext": false
              },
              "files": {
                "allowPaths": ["data"],
                "writePaths": ["data/out"],
                "deletePaths": ["data/tmp"]
              },
              "privileged": {
                "usesShizuku": true,
                "usesRoot": false,
                "usesShell": true,
                "preferredBackend": "shizuku"
              }
            }
            """.trimIndent(),
        )

        assertNotNull(config)
        requireNotNull(config)
        assertEquals(listOf("shizuku", "storage", "uninstall_apk"), config.capabilities)
        assertEquals(listOf("org.autojs.plugin.demo", "project-plugin-a"), config.pluginDependencies)
        assertEquals("reject", config.riskPolicy.undeclaredAction)
        assertFalse(config.riskPolicy.rememberAllowed)
        assertEquals(listOf("api.example.com"), config.networkPolicy.allowedDomains)
        assertFalse(config.networkPolicy.allowCleartext)
        assertEquals(listOf("data"), config.filePolicy.allowedPaths)
        assertEquals(listOf("data/out"), config.filePolicy.writablePaths)
        assertEquals(listOf("data/tmp"), config.filePolicy.deletablePaths)
        assertEquals(true, config.privilegedPolicy.shizuku)
        assertEquals(false, config.privilegedPolicy.root)
        assertEquals(true, config.privilegedPolicy.shell)
        assertEquals("shizuku", config.privilegedPolicy.backend)
    }
}
