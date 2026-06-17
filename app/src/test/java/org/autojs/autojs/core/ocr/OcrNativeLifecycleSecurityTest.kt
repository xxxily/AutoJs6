package org.autojs.autojs.core.ocr

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrNativeLifecycleSecurityTest {

    @Test
    fun paddleNativeLoadFailureRollsBackLoadedStateAndDestroyUsesLock() {
        val source = resolveRootDir()
            .resolve("plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java")
            .readText()

        assertTrue(source.contains("isSOLoaded.set(false);"))
        assertTrue(source.contains("throw new IllegalStateException(\"OCR predictor has been destroyed or failed to initialize.\""))
        assertTrue(source.contains("public void destroy() {\n        lock.lock();"))
    }

    @Test
    fun rapidOcrNativeCallsAreSerializedAtJvmBoundary() {
        val source = resolveRootDir()
            .resolve("libs/rapidocr/src/main/java/com/benjaminwan/ocrlibrary/OcrEngine.kt")
            .readText()

        assertTrue(source.contains("private val nativeLock = ReentrantLock()"))
        assertTrue(source.contains("nativeLock.withLock"))
        assertTrue(source.contains("RapidOCR native engine failed to initialize"))
    }

    private companion object {
        private fun resolveRootDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(workingDir) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory }
                ?: error("Cannot resolve project root from ${workingDir.path}")
        }
    }
}
