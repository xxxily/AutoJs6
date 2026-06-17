package org.autojs.autojs.build

import java.io.File
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
        assertTrue(libDeployer.contains("Archive entry escapes extraction directory"))
        assertTrue(sevenZExtractor.contains("safeOutputFile"))
        assertTrue(sevenZExtractor.contains("7z entry escapes extraction directory"))
    }

    private companion object {
        private fun root(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }
    }
}
