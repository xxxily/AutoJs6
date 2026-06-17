package org.autojs.autojs.core.web

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewBridgeSecurityTest {

    @Test
    fun injectableWebClientDoesNotEnableUniversalFileAccessOrBridgeByDefault() {
        val source = resolveAppDir().resolve("src/main/java/org/autojs/autojs/core/web/InjectableWebClient.java").readText()

        assertTrue(source.contains("setAllowUniversalAccessFromFileURLs(false)"))
        assertTrue(source.contains("setBridgeEnabled"))
        assertFalse(source.contains("view.addJavascriptInterface(mScriptBridge, \"rhino\");\n        WebSettings"))
    }

    @Test
    fun scriptWebViewsDisableFileUrlCrossOriginByDefault() {
        val appDir = resolveAppDir()
        val injectableWebView = appDir.resolve("src/main/java/org/autojs/autojs/core/web/InjectableWebView.kt").readText()
        val eventWebView = appDir.resolve("src/main/java/org/autojs/autojs/core/ui/widget/EventWebView.kt").readText()
        val legacyClient = appDir.resolve("src/main/java/com/stardust/autojs/core/web/InjectableWebClient.java").readText()

        assertTrue(injectableWebView.contains("allowUniversalAccessFromFileURLs = false"))
        assertTrue(eventWebView.contains("allowFileAccessFromFileURLs = false"))
        assertTrue(eventWebView.contains("allowUniversalAccessFromFileURLs = false"))
        assertTrue(legacyClient.contains("setAllowUniversalAccessFromFileURLs(false)"))
    }

    private companion object {
        private fun resolveAppDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return listOf(workingDir, workingDir.resolve("app"))
                .firstOrNull { it.resolve("src/main/java/org/autojs/autojs/core/web/InjectableWebClient.java").isFile }
                ?: error("Cannot resolve app module directory from ${workingDir.path}")
        }
    }
}
