package org.autojs.autojs.external

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptIntentsSecurityTest {

    @Test
    fun trustCheckRequiresTokenAndDoesNotAcceptBooleanOnly() {
        val source = readScriptIntentsSource()

        assertTrue(source.contains("EXTRA_KEY_TRUST_TOKEN"))
        assertTrue(source.contains("token == trustToken()"))
        assertTrue(source.contains("putExtra(EXTRA_KEY_TRUST_TOKEN, trustToken())"))
    }

    @Test
    fun shortcutAndWidgetIntentsAreExplicitlyMarkedTrusted() {
        val appDir = resolveAppDir()
        val scripts = appDir.resolve("src/main/java/org/autojs/autojs/model/script/Scripts.kt").readText()
        val widget = appDir.resolve("src/main/java/org/autojs/autojs/external/widget/ScriptWidget.java").readText()
        val shortcutCreate = appDir.resolve("src/main/java/org/autojs/autojs/ui/shortcut/ShortcutCreateActivity.java").readText()

        assertTrue(scripts.contains("ScriptIntents.markTrusted"))
        assertTrue(widget.contains("ScriptIntents.markTrusted"))
        assertTrue(shortcutCreate.contains("ScriptIntents.markTrusted"))
    }

    @Test
    fun externalRunActivityConfirmsUntrustedRequests() {
        val appDir = resolveAppDir()
        val runIntentActivity = appDir.resolve("src/main/java/org/autojs/autojs/external/open/RunIntentActivity.java").readText()

        assertTrue(runIntentActivity.contains("ScriptIntents.isTrusted(getIntent())"))
        assertTrue(runIntentActivity.contains("confirmExternalRun"))
        assertTrue(runIntentActivity.contains("MaterialDialog.Builder"))
    }

    private companion object {
        private fun readScriptIntentsSource(): String =
            resolveAppDir().resolve("src/main/java/org/autojs/autojs/external/ScriptIntents.kt").readText()

        private fun resolveAppDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return listOf(workingDir, workingDir.resolve("app"))
                .firstOrNull { it.resolve("src/main/java/org/autojs/autojs/external/ScriptIntents.kt").isFile }
                ?: error("Cannot resolve app module directory from ${workingDir.path}")
        }
    }
}
