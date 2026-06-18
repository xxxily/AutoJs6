package org.autojs.autojs.build

import java.io.File
import java.util.Properties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplyChainGuardTest {

    @Test
    fun gradleWrapperPinsDistributionChecksum() {
        val wrapper = root().resolve("gradle/wrapper/gradle-wrapper.properties").readText()

        assertTrue(wrapper.contains("distributionSha256Sum="))
    }

    @Test
    fun githubActionsVerifyCmakeInstallerAndRequireSigningSecrets() {
        val workflow = root().resolve(".github/workflows/android.yml").readText()

        assertTrue(workflow.contains("sha256sum --check"))
        assertTrue(workflow.contains("CMAKE_INSTALLER_SHA256"))
        assertTrue(workflow.contains("Signing secrets are required for release APK builds."))
        assertFalse(workflow.contains("release APKs will be unsigned"))
    }

    @Test
    fun archiveExtractionRejectsTraversalEntries() {
        val root = root()
        val libDeployer = root.resolve("build-logic/convention/src/main/kotlin/org/autojs/build/LibDeployer.kt").readText()
        val sevenZExtractor = root.resolve("build-logic/convention/src/main/kotlin/org/autojs/build/SevenZExtractor.kt").readText()

        assertTrue(libDeployer.contains("safeArchiveOutputFile"))
        assertTrue(libDeployer.contains("if (relativePath.isBlank())"))
        assertTrue(libDeployer.indexOf("if (relativePath.isBlank())") < libDeployer.indexOf("safeArchiveOutputFile(tempOutCanonical, relativePath)"))
        assertTrue(libDeployer.contains("Archive entry escapes extraction directory"))
        assertTrue(sevenZExtractor.contains("safeOutputFile"))
        assertTrue(sevenZExtractor.contains("7z entry escapes extraction directory"))
    }

    @Test
    fun remoteNativeArchivesRequireTrustedSha256Checksums() {
        val root = root()
        val checksums = root.resolve("gradle/third_party_checksums.toml").readText()
        val libDeployer = root.resolve("build-logic/convention/src/main/kotlin/org/autojs/build/LibDeployer.kt").readText()
        val versions = root.resolve("version.properties").inputStream().use { input ->
            Properties().apply { load(input) }
        }
        val opencvMobile = versions.requireVersion("RAPID_OCR_OPENCV_MOBILE")
        val opencvMobileLabel = versions.requireVersion("RAPID_OCR_OPENCV_MOBILE_LABEL")
        val onnx = versions.requireVersion("RAPID_OCR_ONNX")
        val onnxRuntime = versions.requireVersion("RAPID_OCR_ONNX_RUNTIME")

        listOf(
            "https://github.com/nihui/opencv-mobile/releases/download/v$opencvMobileLabel/opencv-mobile-$opencvMobile-android.zip",
            "https://github.com/RapidAI/RapidOcrOnnx/releases/download/$onnx/Project_RapidOcrOnnx-$onnx.7z",
            "https://github.com/RapidAI/OnnxruntimeBuilder/releases/download/$onnxRuntime/onnxruntime-$onnxRuntime-android-shared.7z",
        ).forEach { url ->
            assertTrue("Missing trusted SHA-256 for $url", checksums.contains("\"$url\""))
        }
        assertTrue(libDeployer.contains("third_party_checksums.toml"))
        assertTrue(libDeployer.contains("Missing SHA-256 checksum for remote archive"))
        assertTrue(libDeployer.contains("SHA-256 checksum mismatch"))
        assertTrue(libDeployer.contains("generateSha256String"))
        assertTrue(libDeployer.contains("\"SHA-256\""))
        assertTrue(libDeployer.indexOf("validateExistingTrustedCache(skip)") < libDeployer.indexOf("if (skip.exists())"))
        assertTrue(libDeployer.indexOf("downloadWithRetry()") < libDeployer.indexOf("requireTrustedSha256File(cacheFile)"))
    }

    private companion object {
        private fun root(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }

        private fun Properties.requireVersion(name: String): String {
            return requireNotNull(getProperty("${name}_VERSION") ?: getProperty(name)) {
                "Missing version property for $name"
            }
        }
    }
}
