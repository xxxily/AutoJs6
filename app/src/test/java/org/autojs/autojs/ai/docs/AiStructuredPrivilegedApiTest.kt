package org.autojs.autojs.ai.docs

import org.autojs.autojs.ai.prompt.AiPromptBuilder
import org.autojs.autojs.ai.prompt.AiScriptContext
import org.autojs.autojs.ai.prompt.AiTaskType
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredPrivilegedApiTest {

    @Test
    fun riskDetectionRecognizesStructuredShizukuSettingsAndPermissionApis() {
        val index = AiCapabilityIndex.fromEntriesForTesting(
            listOf(
                entry("shizuku.settings.secure.put"),
                entry("shizuku.settings.put"),
                entry("shizuku.app.grantPermission"),
            )
        )

        val validation = index.validateGeneratedCode(
            """
            shizuku.settings.secure.put("enabled_accessibility_services", "pkg/.Service");
            shizuku.app.grantPermission(autojs.packageName, "android.permission.WRITE_SECURE_SETTINGS");
            """.trimIndent()
        )
        val risks = validation.risks.map { it.name }.toSet()

        assertTrue("Unexpected unknown APIs: ${validation.unknownApis}", validation.unknownApis.isEmpty())
        assertTrue("Shizuku risk missing: $risks", "Shizuku" in risks)
        assertTrue("Settings risk missing: $risks", "修改系统设置" in risks)
        assertTrue("Permission risk missing: $risks", "应用强停/清数据/授权" in risks)
    }

    @Test
    fun promptPrefersStructuredShizukuApisOverRawPmAmSettingsCommands() {
        val messages = AiPromptBuilder.buildMessages(
            context = AiScriptContext(
                taskType = AiTaskType.CREATE_SCRIPT,
                userRequest = "用 Shizuku 给 AutoJs6 授权并修改 secure 设置",
                filePath = "main.js",
                workingDirectory = "/tmp",
                currentText = "",
                selectedText = "",
                selectionStart = 0,
                selectionEnd = 0,
            ),
            docs = listOf(entry("shizuku.settings.put"), entry("shizuku.app.grantPermission")),
        )
        val prompt = messages.joinToString("\n") { it.content }

        assertTrue(prompt.contains("shizuku.app.grantPermission"))
        assertTrue(prompt.contains("shizuku.settings.put"))
        assertTrue(prompt.contains("Avoid generating raw pm/am/settings shell strings"))
    }

    private fun entry(name: String) = AiCapabilityEntry(
        id = name,
        name = name.substringAfterLast('.'),
        qualifiedName = name,
        module = "shizuku",
        docFile = "shizuku.html",
        signature = name,
        description = "$name structured privileged API",
        example = "",
        permissions = listOf("需要 Shizuku 授权"),
        riskLevel = "high",
        aliases = emptyList(),
    )
}
