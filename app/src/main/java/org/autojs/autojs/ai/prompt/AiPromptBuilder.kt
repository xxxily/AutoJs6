package org.autojs.autojs.ai.prompt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.ai.client.AiChatMessage
import org.autojs.autojs.ai.docs.AiCapabilityEntry

object AiPromptBuilder {

    fun buildMessages(context: AiScriptContext, docs: List<AiCapabilityEntry>): List<AiChatMessage> {
        return listOf(
            AiChatMessage("system", buildSystemPrompt()),
            AiChatMessage("user", buildUserPrompt(context, docs)),
        )
    }

    fun buildResponseSchema(): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("additionalProperties", false.toJsonPrimitive())
        add("required", JsonArray().apply {
            listOf("intent", "summary", "files", "usedApis", "requirements", "risks", "warnings", "verificationSteps", "notes")
                .forEach(::add)
        })
        add("properties", JsonObject().apply {
            add("intent", JsonObject().apply {
                addProperty("type", "string")
                add("enum", JsonArray().apply {
                    listOf("create_script", "modify_selection", "modify_file", "fix_error", "explain", "create_project")
                        .forEach(::add)
                })
            })
            add("summary", stringSchema())
            add("files", JsonObject().apply {
                addProperty("type", "array")
                add("items", JsonObject().apply {
                    addProperty("type", "object")
                    add("additionalProperties", false.toJsonPrimitive())
                    add("required", JsonArray().apply {
                        listOf("path", "language", "operation", "content").forEach(::add)
                    })
                    add("properties", JsonObject().apply {
                        add("path", stringSchema())
                        add("language", stringSchema())
                        add("operation", JsonObject().apply {
                            addProperty("type", "string")
                            add("enum", JsonArray().apply {
                                listOf("create", "replace_all", "replace_selection", "patch").forEach(::add)
                            })
                        })
                        add("content", stringSchema())
                    })
                })
            })
            add("usedApis", JsonObject().apply {
                addProperty("type", "array")
                add("items", JsonObject().apply {
                    addProperty("type", "object")
                    add("additionalProperties", false.toJsonPrimitive())
                    add("required", JsonArray().apply { listOf("name", "doc", "reason").forEach(::add) })
                    add("properties", JsonObject().apply {
                        add("name", stringSchema())
                        add("doc", stringSchema())
                        add("reason", stringSchema())
                    })
                })
            })
            add("requirements", stringArraySchema())
            add("risks", stringArraySchema())
            add("warnings", stringArraySchema())
            add("verificationSteps", stringArraySchema())
            add("notes", stringSchema())
        })
    }

    private fun buildSystemPrompt(): String = """
        You are generating JavaScript automation scripts for AutoJs6 on Android.
        You must use only AutoJs6 APIs present in the provided local documentation snippets and API list.
        If an API is not in the provided docs, say that it is unavailable or uncertain instead of inventing it.
        Generate runnable AutoJs6 JavaScript by default.
        Prefer the smallest valid edit. For current-file changes, return a unified diff patch unless the entire file genuinely needs replacement. For selection changes, return only replacement text.
        For screen understanding, prefer vision.targets, vision.findText, vision.findButton, vision.observe, and vision.waitForScene when OCR, image matching, and accessibility data need to be fused.
        For accessibility UI automation, prefer the reliable automation DSL: auto.waitUntil, auto.retry, auto.stableClick, auto.stableSetText, and auto.findWithScroll.
        For complex automation tasks, first look for provided Automation solution patterns (Solution: / solutions) and reuse their template structure, capability declarations, and failure handling. Use API documentation snippets to fill in details after choosing a solution pattern.
        Avoid bare sleep loops, bare click(text), or coordinate-only clicks when a reliable DSL action can express the same wait, retry, validation, and diagnostics.
        For privileged app/settings/package/input/process/user operations, prefer structured Shizuku APIs such as shizuku.app.forceStop, shizuku.app.grantPermission, shizuku.settings.put, shizuku.package.permissionState, and shizuku.input.injectTap instead of shizuku.execCommand, shell(), or hand-written pm/am/settings strings.
        If the script depends on accessibility service, screen capture, floating windows, Root, Shizuku, Shell, SMS, contacts, installation/removal, files, camera, microphone, location, or other sensitive capabilities, declare it in requirements and risks.
        Do not add dangerous actions unless the user explicitly asked for them.
        Return pure JSON matching the requested schema. Do not wrap JSON in Markdown.
    """.trimIndent()

    private fun buildUserPrompt(context: AiScriptContext, docs: List<AiCapabilityEntry>): String = buildString {
        appendLine("Task type: ${context.taskType.wireName}")
        appendLine("User request:")
        appendLine(context.userRequest.ifBlank { "(empty)" })
        appendLine()
        appendLine("Current file:")
        appendLine("- path: ${context.filePath.ifBlank { "(unknown)" }}")
        appendLine("- working directory: ${context.workingDirectory.ifBlank { "(unknown)" }}")
        appendLine("- selected range: ${context.selectionStart}..${context.selectionEnd}")
        appendLine()
        if (context.selectedText.isNotBlank()) {
            appendLine("Selected code:")
            appendCodeBlock(context.selectedText)
        } else if (context.currentText.isNotBlank()) {
            appendLine("Current file content or cropped context:")
            appendCodeBlock(context.currentText)
        }
        if (context.projectSummary.isNotBlank()) {
            appendLine("Project summary:")
            appendLine(context.projectSummary)
        }
        if (context.errorMessage.isNotBlank()) {
            appendLine("Recent runtime error:")
            appendLine("line=${context.errorLine}, column=${context.errorColumn}")
            appendLine(context.errorMessage)
        }
        if (context.logSnippet.isNotBlank()) {
            appendLine("Recent logs:")
            appendCodeBlock(context.logSnippet)
        }
        if (context.capabilityStateSummary.isNotBlank()) {
            appendLine("Current capability state:")
            appendLine(context.capabilityStateSummary)
        }
        if (context.uiSnapshotSummary.isNotBlank()) {
            appendLine("Confirmed UI snapshot summary:")
            appendCodeBlock(context.uiSnapshotSummary, "json")
        }
        if (context.screenCaptureSummary.isNotBlank()) {
            appendLine("Confirmed screen capture summary:")
            appendLine(context.screenCaptureSummary)
        }
        if (context.ocrSummary.isNotBlank()) {
            appendLine("Confirmed OCR result summary:")
            appendCodeBlock(context.ocrSummary, "text")
        }
        if (context.clipboardText.isNotBlank()) {
            appendLine("Confirmed clipboard text:")
            appendCodeBlock(context.clipboardText, "text")
        }
        appendLine("Relevant local AutoJs6 documentation snippets:")
        docs.forEachIndexed { index, entry ->
            val sourceType = if (entry.module == SOLUTIONS_MODULE) "Automation solution pattern" else "API documentation"
            appendLine("[${index + 1}] $sourceType: ${entry.signature} (${entry.docFile})")
            if (entry.permissions.isNotEmpty()) appendLine("Permissions: ${entry.permissions.joinToString(", ")}")
            appendLine(entry.description.take(800))
            if (entry.example.isNotBlank()) {
                appendLine("Example:")
                appendCodeBlock(entry.example.take(800))
            }
            appendLine()
        }
        appendLine("Output rules:")
        appendLine("- For explain tasks, files must be an empty array and summary/notes must contain the explanation.")
        appendLine("- For modify_selection, return exactly one file with operation replace_selection and content for the replacement only.")
        appendLine("- For modify_file, return one file with operation patch and content as a unified diff with context lines; use replace_all only when a minimal patch is impossible.")
        appendLine("- For create_script, return one .js file.")
        appendLine("- For create_project, return project.json and the main .js file. project.json main must point to an existing generated script.")
        appendLine("- When any Automation solution pattern is provided, choose the closest pattern first and adapt its template before composing lower-level API calls from scratch.")
        appendLine("- If a generated project uses sensitive capabilities, put their ids in project.json capabilities and set riskPolicy/privilegedPolicy when Shell, Root, Shizuku, install/uninstall, file deletion/overwrite, SMS, contacts, phone, camera, microphone, or location are involved.")
        appendLine("- usedApis must name every AutoJs6 API used and its doc file.")
        appendLine("- For screen understanding across accessibility, OCR, image matching, or color/region signals, prefer vision.targets/findText/findButton/observe/waitForScene and use suggestedAction/selector from the returned target.")
        appendLine("- For UI automation, prefer auto.waitUntil/auto.retry/auto.stableClick/auto.stableSetText/auto.findWithScroll over bare sleep/click/input loops when those APIs are present in the snippets.")
        appendLine("- For Shizuku/Root privileged operations, prefer structured APIs: shizuku.app.forceStop/clearData/grantPermission/revokePermission/install/uninstall, shizuku.settings.get/put/delete, shizuku.package.info/apkPath/permissionState, shizuku.input.injectTap/injectSwipe/keyEvent, shizuku.process.list/kill/foreground, and shizuku.users.listUsers/currentUser/runAsUser. Avoid generating raw pm/am/settings shell strings unless no structured API exists.")
        appendLine("- If using vision, screenshot, OCR, UI snapshot, shell, Shizuku, Root, file deletion/overwrite, installation/removal, SMS, contacts, camera, microphone, or location, list the requirement and risk explicitly.")
        appendLine("- warnings must list uncertain APIs, compatibility concerns, or user checks before applying the result.")
    }

    private fun StringBuilder.appendCodeBlock(text: String, language: String = "javascript") {
        appendLine("```$language")
        appendLine(text)
        appendLine("```")
    }

    private fun stringSchema() = JsonObject().apply { addProperty("type", "string") }

    private fun stringArraySchema() = JsonObject().apply {
        addProperty("type", "array")
        add("items", stringSchema())
    }

    private fun Boolean.toJsonPrimitive() = com.google.gson.JsonPrimitive(this)

    private const val SOLUTIONS_MODULE = "solutions"
}

data class AiScriptContext(
    val taskType: AiTaskType,
    val userRequest: String,
    val filePath: String,
    val workingDirectory: String,
    val currentText: String,
    val selectedText: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val projectSummary: String = "",
    val errorMessage: String = "",
    val errorLine: Int = -1,
    val errorColumn: Int = 0,
    val logSnippet: String = "",
    val capabilityStateSummary: String = "",
    val uiSnapshotSummary: String = "",
    val screenCaptureSummary: String = "",
    val ocrSummary: String = "",
    val clipboardText: String = "",
)

enum class AiTaskType(val wireName: String) {
    CREATE_SCRIPT("create_script"),
    MODIFY_SELECTION("modify_selection"),
    MODIFY_FILE("modify_file"),
    FIX_ERROR("fix_error"),
    EXPLAIN("explain"),
    CREATE_PROJECT("create_project"),
}
