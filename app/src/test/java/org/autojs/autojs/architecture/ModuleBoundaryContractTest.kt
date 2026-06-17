package org.autojs.autojs.architecture

import java.io.File
import org.autojs.autojs.capability.CapabilityCore
import org.autojs.autojs.capability.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleBoundaryContractTest {

    @Test
    fun capabilityGovernanceIsExtractedToIndependentJvmModule() {
        val root = resolveRootDir()
        val settings = root.resolve("settings.gradle.kts").readText()
        val moduleBuild = root.resolve("capability-governance/build.gradle.kts").readText()
        val appBuild = root.resolve("app/build.gradle.kts").readText()

        assertTrue("settings.gradle.kts must include capability-governance module", "\"capability-governance\"" in settings)
        assertTrue("capability-governance must use Kotlin JVM plugin", "kotlin(\"jvm\")" in moduleBuild)
        assertTrue("capability-governance must not depend on Android Gradle plugin", "com.android." !in moduleBuild)
        assertTrue("app module must depend on capability-governance", "project(\":capability-governance\")" in appBuild)
    }

    @Test
    fun capabilityGovernanceCoreKeepsAndroidDependenciesAtRegistryBoundary() {
        val root = resolveRootDir()
        val capabilityDir = root.resolve("capability-governance/src/main/kotlin/org/autojs/autojs/capability")
        val violations = capabilityDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .mapNotNull { file ->
                val text = file.readText()
                val forbiddenImport = FORBIDDEN_ANDROID_IMPORTS.firstOrNull { it in text }
                forbiddenImport?.let { "${file.relativeTo(root).path}: $it" }
            }
            .toList()

        assertTrue(
            "Capability governance files outside the Android registry boundary must stay pure JVM:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun androidRegistryDelegatesStableCapabilityModelToGovernanceCore() {
        val coreIds = CapabilityCore.allDefinitions().map { it.id }.toSet()
        val androidIds = CapabilityRegistry.allDefinitions().map { it.id }.toSet()

        assertEquals("Android registry must not drift from capability-governance core", coreIds, androidIds)
    }

    @Test
    fun pluginSdkTemplateDocumentsStrictOfficialIndexFields() {
        val root = resolveRootDir()
        val template = root.resolve("plugin-api/templates/plugin-manifest.template.json").readText()

        REQUIRED_PLUGIN_TEMPLATE_FIELDS.forEach { field ->
            assertTrue("Plugin SDK template missing field: $field", "\"$field\"" in template)
        }
    }

    private companion object {
        private val FORBIDDEN_ANDROID_IMPORTS = listOf(
            "import android.app.",
            "import android.view.",
            "import androidx.appcompat.",
            "import androidx.fragment.",
            "import com.afollestad.materialdialogs.",
        )

        private val REQUIRED_PLUGIN_TEMPLATE_FIELDS = listOf(
            "packageName",
            "title",
            "manifest",
            "capabilities",
            "riskLevel",
            "minAutoJsVersion",
            "documentationUrl",
            "releases",
            "apkSha256",
            "certificateSha256",
        )

        private fun resolveRootDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }
    }
}
