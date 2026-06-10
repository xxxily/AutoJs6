package org.autojs.autojs.ai.docs

import org.autojs.autojs.ai.prompt.AiPromptBuilder
import org.autojs.autojs.ai.prompt.AiScriptContext
import org.autojs.autojs.ai.prompt.AiTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiAutomationSolutionsTest {

    private val appDir = resolveAppDir()
    private val solutionsFile = appDir.resolve("src/main/assets-app/solutions/automation-solutions.json")

    @Test
    fun parsesAllRequiredAutomationSolutions() {
        val entries = solutionEntries()
        val ids = entries.map { it.qualifiedName.removePrefix("solutions.") }.toSet()

        assertEquals(8, entries.size)
        assertEquals(
            setOf(
                "app_launch_wait",
                "list_scroll_find",
                "form_fill_submit",
                "ocr_text_click",
                "screenshot_image_match",
                "timed_task_reliable",
                "shizuku_app_management",
                "plugin_ocr",
            ),
            ids,
        )
        entries.forEach { entry ->
            assertEquals("solutions", entry.module)
            assertTrue("${entry.name} must link automation-solutions.html", entry.docFile.startsWith("automation-solutions.html#"))
            assertTrue("${entry.name} must expose scenario", "Scenario:" in entry.description)
            assertTrue("${entry.name} must expose capabilities", "Capabilities:" in entry.description)
            assertTrue("${entry.name} must include copyable template", entry.example.isNotBlank())
        }
    }

    @Test
    fun searchPrioritizesSolutionPatternsForCommonScenarioQueries() {
        val index = AiCapabilityIndex.fromEntriesForTesting(
            solutionEntries() + listOf(
                apiEntry("auto.findWithScroll", "automator", "automator.html", "列表滚动查找 API"),
                apiEntry("auto.stableSetText", "automator", "automator.html", "表单稳定输入 API"),
                apiEntry("ocr.paddle.detect", "ocr", "ocr.html", "Paddle OCR API"),
                apiEntry("shizuku.app.forceStop", "shizuku", "shizuku.html", "Shizuku 强停 API"),
            )
        )

        assertEquals("solutions.list_scroll_find", index.search("列表滚动查找").first().qualifiedName)
        assertEquals("solutions.form_fill_submit", index.search("表单填写提交").first().qualifiedName)
        assertEquals("solutions.plugin_ocr", index.search("插件 OCR 打包 pluginDependencies").first().qualifiedName)
        assertEquals("solutions.shizuku_app_management", index.search("Shizuku 应用管理 强停").first().qualifiedName)
    }

    @Test
    fun solutionExamplesCarryReusableTemplatesAndProjectHints() {
        val entries = solutionEntries().associateBy { it.qualifiedName }

        assertTrue(entries.getValue("solutions.list_scroll_find").example.contains("auto.findWithScroll"))
        assertTrue(entries.getValue("solutions.form_fill_submit").example.contains("auto.stableSetText"))
        assertTrue(entries.getValue("solutions.plugin_ocr").description.contains("pluginDependencies"))
        assertTrue(entries.getValue("solutions.shizuku_app_management").description.contains("privilegedPolicy"))
    }

    @Test
    fun promptLabelsSolutionPatternsBeforeApiDocumentation() {
        val docs = listOf(
            solutionEntries().first { it.qualifiedName == "solutions.list_scroll_find" },
            apiEntry("auto.findWithScroll", "automator", "automator.html", "查找控件并按需滚动容器"),
        )
        val messages = AiPromptBuilder.buildMessages(
            context = AiScriptContext(
                taskType = AiTaskType.CREATE_SCRIPT,
                userRequest = "在列表里找到目标项并点击",
                filePath = "main.js",
                workingDirectory = "/tmp",
                currentText = "",
                selectedText = "",
                selectionStart = 0,
                selectionEnd = 0,
            ),
            docs = docs,
        )
        val prompt = messages.joinToString("\n") { it.content }

        assertTrue(prompt.contains("Automation solution pattern: Solution: 列表滚动查找"))
        assertTrue(prompt.contains("API documentation: auto.findWithScroll"))
        assertTrue(prompt.contains("choose the closest pattern first"))
    }

    private fun solutionEntries(): List<AiCapabilityEntry> {
        return AiCapabilityIndex.parseSolutionIndexJson(solutionsFile.readText())
    }

    private fun apiEntry(name: String, module: String, docFile: String, description: String) = AiCapabilityEntry(
        id = name,
        name = name.substringAfterLast('.'),
        qualifiedName = name,
        module = module,
        docFile = docFile,
        signature = name,
        description = description,
        example = "",
    )

    private companion object {
        private fun resolveAppDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return listOf(workingDir, workingDir.resolve("app"))
                .firstOrNull { it.resolve("src/main/assets-app/solutions/automation-solutions.json").isFile }
                ?: error("Cannot resolve app module directory from ${workingDir.path}")
        }
    }
}
