package org.autojs.autojs.compat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSmokeGateTest {

    @Test
    fun releaseSmokeMatrixAndInstrumentationEntryAreMaintained() {
        val root = resolveRootDir()
        val matrix = root.resolve("docs/实现审计/15-设备兼容实验室与基准测试基础.md").readText()
        val smokeTest = root.resolve("app/src/androidTest/java/org/autojs/autojs/compat/DeviceCompatibilitySmokeTest.kt").readText()
        val workflow = root.resolve(".github/workflows/android.yml").readText()

        REQUIRED_MATRIX_TERMS.forEach { term ->
            assertTrue("Device compatibility matrix is missing: $term", term in matrix)
        }
        assertTrue(smokeTest.contains("instrumentationCanLoadCoreCompatibilitySurfaces"))
        assertTrue(workflow.contains("compileAppDebugAndroidTestKotlin"))
        assertTrue(workflow.contains("AiCapabilityIndexConsistencyTest"))
        assertTrue(workflow.contains("ResourceScopeTest"))
    }

    private companion object {
        private val REQUIRED_MATRIX_TERMS = listOf(
            "Android 8/API 26",
            "Android 16/API 36",
            "MIUI/HyperOS",
            "OriginOS/Funtouch OS",
            "ColorOS",
            "One UI",
            "EMUI/MagicOS/HarmonyOS",
            "connectedAppDebugAndroidTest",
            "无设备连接",
        )

        private fun resolveRootDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }
    }
}
