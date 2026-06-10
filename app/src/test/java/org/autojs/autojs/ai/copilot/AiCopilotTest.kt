package org.autojs.autojs.ai.copilot

import org.autojs.autojs.ai.docs.AiCapabilityEntry
import org.autojs.autojs.ai.docs.AiCapabilityIndex
import org.autojs.autojs.ai.prompt.AiScriptContext
import org.autojs.autojs.ai.prompt.AiTaskType
import org.autojs.autojs.ai.result.AiGeneratedFile
import org.autojs.autojs.ai.result.AiGenerationResult
import org.autojs.autojs.ai.result.AiResultValidator
import org.autojs.autojs.ai.result.AiUsedApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCopilotTest {

    @Test
    fun unknownApisBlockApplyBeforePreviewActionCanRun() {
        val result = AiGenerationResult(
            intent = "create_script",
            summary = "Create script",
            files = listOf(AiGeneratedFile(path = "main.js", operation = "create", content = "toast('ok');\nmissingApi();")),
            usedApis = listOf(AiUsedApi(name = "missingApi", doc = "none", reason = "invented")),
        )

        val validated = AiResultValidator.validate(result, AiTaskType.CREATE_SCRIPT, testIndex())

        assertTrue(validated.blocksApply)
        assertTrue("missingApi" in validated.codeValidation.unknownApis)
    }

    @Test
    fun highRiskActionsMustBeDeclared() {
        val result = AiGenerationResult(
            intent = "create_script",
            summary = "Create risky script",
            files = listOf(AiGeneratedFile(path = "main.js", operation = "create", content = "shell('pm uninstall com.example');")),
            usedApis = listOf(AiUsedApi(name = "shell", doc = "shell.html", reason = "run command")),
        )

        val validated = AiResultValidator.validate(result, AiTaskType.CREATE_SCRIPT, testIndex())

        assertTrue(validated.blocksApply)
        assertTrue(validated.issues.any { it.startsWith("High-risk capabilities must be declared") })

        val declared = AiResultValidator.validate(
            result.copy(risks = listOf("Shell command may uninstall an app.", "应用安装/卸载")),
            AiTaskType.CREATE_SCRIPT,
            testIndex(),
        )
        assertFalse(declared.blocksApply)
    }

    @Test
    fun replaceSelectionKeepsOutsideTextUnchanged() {
        val original = "const a = 1;\nconst b = 2;\n"
        val start = original.indexOf("2")
        val result = AiPatchApplicator.apply(
            currentText = original,
            snapshotText = original,
            selectionStart = start,
            selectionEnd = start + 1,
            file = AiGeneratedFile(path = "main.js", operation = "replace_selection", content = "3"),
        )

        assertTrue(result.applied)
        assertEquals("const a = 1;\nconst b = 3;\n", result.newText)
    }

    @Test
    fun unifiedDiffAppliesOnlyWhenContextMatches() {
        val original = "const a = 1;\nconst b = 2;\nconst c = 4;"
        val patch = """
            --- a/main.js
            +++ b/main.js
            @@ -1,3 +1,3 @@
             const a = 1;
            -const b = 2;
            +const b = 3;
             const c = 4;
        """.trimIndent()

        val applied = AiPatchApplicator.apply(
            currentText = original,
            snapshotText = original,
            selectionStart = 0,
            selectionEnd = 0,
            file = AiGeneratedFile(path = "main.js", operation = "patch", content = patch),
        )
        val drift = AiPatchApplicator.apply(
            currentText = "$original\n// user edit",
            snapshotText = original,
            selectionStart = 0,
            selectionEnd = 0,
            file = AiGeneratedFile(path = "main.js", operation = "patch", content = patch),
        )

        assertTrue(applied.applied)
        assertEquals("const a = 1;\nconst b = 3;\nconst c = 4;", applied.newText)
        assertFalse(drift.applied)
        assertEquals(AiPatchApplyError.CONTEXT_DRIFT, drift.error)
    }

    @Test
    fun privacyPolicySummarizesExcludesAndRedactsSensitiveContext() {
        val context = AiScriptContext(
            taskType = AiTaskType.FIX_ERROR,
            userRequest = "fix",
            filePath = "main.js",
            workingDirectory = "",
            currentText = "const apiKey = 'sk-1234567890abcdef';",
            selectedText = "",
            selectionStart = 0,
            selectionEnd = 0,
            errorMessage = "Authorization: Bearer secret-token-123",
            logSnippet = "token=abc123456789",
        )
        val extras = AiCopilotContextExtras(
            clipboardText = "api_key=secret-clipboard",
            screenCaptureSummary = "screen has login form",
        )
        val policy = AiCopilotPrivacyPolicy(
            includeClipboard = true,
            includeScreenCapture = true,
        )

        val pending = AiCopilotContextBuilder.prepare(context, extras, policy)
        assertTrue(AiContextSource.CLIPBOARD in pending.summary.confirmationRequiredSources)
        assertTrue(AiContextSource.SCREEN_CAPTURE in pending.summary.confirmationRequiredSources)
        assertEquals("", pending.scriptContext.clipboardText)
        assertEquals("", pending.scriptContext.screenCaptureSummary)

        val confirmed = AiCopilotContextBuilder.prepare(
            context,
            extras,
            policy.copy(confirmedSources = setOf(AiContextSource.CLIPBOARD, AiContextSource.SCREEN_CAPTURE)),
        )
        assertEquals("api_key=****", confirmed.scriptContext.clipboardText)
        assertEquals("screen has login form", confirmed.scriptContext.screenCaptureSummary)
        assertFalse("sk-1234567890abcdef" in confirmed.scriptContext.currentText)
        assertFalse("secret-token-123" in confirmed.scriptContext.errorMessage)
    }

    private fun testIndex(): AiCapabilityIndex {
        return AiCapabilityIndex.fromEntriesForTesting(
            listOf(
                AiCapabilityEntry(
                    id = "toast",
                    name = "toast",
                    qualifiedName = "toast",
                    module = "global",
                    docFile = "global.html",
                    signature = "toast(message)",
                    description = "Show a toast",
                    example = "",
                ),
                AiCapabilityEntry(
                    id = "shell",
                    name = "shell",
                    qualifiedName = "shell",
                    module = "shell",
                    docFile = "shell.html",
                    signature = "shell(command)",
                    description = "Run shell commands",
                    example = "",
                    permissions = listOf("Shell"),
                    riskLevel = "high",
                ),
            ),
        )
    }
}
