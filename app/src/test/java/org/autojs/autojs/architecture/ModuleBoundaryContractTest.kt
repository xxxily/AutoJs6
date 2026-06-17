package org.autojs.autojs.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleBoundaryContractTest {

    @Test
    fun capabilityGovernanceCoreKeepsAndroidDependenciesAtRegistryBoundary() {
        val root = resolveRootDir()
        val capabilityDir = root.resolve("app/src/main/java/org/autojs/autojs/capability")
        val allowedAndroidFiles = setOf("CapabilityRegistry.kt", "ProjectCapabilitySecurity.kt")
        val violations = capabilityDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { it.name !in allowedAndroidFiles }
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
