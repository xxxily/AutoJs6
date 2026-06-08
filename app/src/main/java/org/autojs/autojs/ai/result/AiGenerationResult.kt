package org.autojs.autojs.ai.result

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.autojs.autojs.ai.docs.AiCapabilityIndex
import org.autojs.autojs.ai.docs.AiCodeValidation
import org.autojs.autojs.ai.prompt.AiTaskType

data class AiGenerationResult(
    val intent: String = "",
    val summary: String = "",
    val files: List<AiGeneratedFile> = emptyList(),
    val usedApis: List<AiUsedApi> = emptyList(),
    val requirements: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val verificationSteps: List<String> = emptyList(),
    val notes: String = "",
)

data class AiGeneratedFile(
    val path: String = "",
    val language: String = "javascript",
    val operation: String = "create",
    val content: String = "",
)

data class AiUsedApi(
    val name: String = "",
    val doc: String = "",
    val reason: String = "",
)

object AiResultParser {
    private val gson = Gson()

    @Throws(AiResultException::class)
    fun parse(content: String): AiGenerationResult {
        val json = extractJsonObject(content)
        return try {
            gson.fromJson(json, AiGenerationResult::class.java)
                ?: throw AiResultException("AI response is empty")
        } catch (e: JsonSyntaxException) {
            throw AiResultException("AI response is not valid structured JSON", e)
        }
    }

    private fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!fenced.isNullOrBlank()) return fenced
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        throw AiResultException("AI response does not contain a JSON object")
    }
}

object AiResultValidator {

    fun validate(
        result: AiGenerationResult,
        expectedTaskType: AiTaskType,
        capabilityIndex: AiCapabilityIndex,
    ): AiValidatedResult {
        val issues = mutableListOf<String>()
        val expectedIntent = expectedTaskType.wireName
        if (result.intent != expectedIntent) {
            issues += "Intent mismatch: expected $expectedIntent, got ${result.intent.ifBlank { "(empty)" }}"
        }
        if (result.summary.isBlank()) {
            issues += "Missing summary"
        }
        if (expectedTaskType != AiTaskType.EXPLAIN && result.files.isEmpty()) {
            issues += "Missing generated file content"
        }
        result.files.forEach { file ->
            if (file.path.isBlank()) issues += "Generated file path is empty"
            if (file.content.isBlank()) issues += "Generated file content is empty: ${file.path.ifBlank { "(unknown)" }}"
        }
        val unknownUsedApis = result.usedApis
            .map { it.name }
            .filter { it.isNotBlank() && !capabilityIndex.containsApi(it) }
            .distinct()
        if (unknownUsedApis.isNotEmpty()) {
            issues += "usedApis contains APIs not found in local docs: ${unknownUsedApis.joinToString(", ")}"
        }
        val mergedCode = result.files.joinToString("\n") { it.content }
        val codeValidation = capabilityIndex.validateGeneratedCode(mergedCode)
        return AiValidatedResult(result, issues, codeValidation)
    }
}

data class AiValidatedResult(
    val result: AiGenerationResult,
    val issues: List<String>,
    val codeValidation: AiCodeValidation,
) {
    val hasWarnings: Boolean
        get() = issues.isNotEmpty() || codeValidation.unknownApis.isNotEmpty() || codeValidation.risks.isNotEmpty()

    val requiresSecondConfirmation: Boolean
        get() = issues.isNotEmpty() || codeValidation.unknownApis.isNotEmpty() || codeValidation.risks.isNotEmpty()
}

class AiResultException(message: String, cause: Throwable? = null) : Exception(message, cause)

object AiDiffUtils {
    fun summarize(oldText: String, newText: String): String {
        if (oldText == newText) return "No text changes."
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val prefix = commonPrefix(oldLines, newLines)
        val suffix = commonSuffix(oldLines, newLines, prefix)
        val removed = oldLines.size - prefix - suffix
        val added = newLines.size - prefix - suffix
        return "Changed lines: +$added / -$removed"
    }

    private fun commonPrefix(a: List<String>, b: List<String>): Int {
        var i = 0
        while (i < a.size && i < b.size && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: List<String>, b: List<String>, prefix: Int): Int {
        var i = 0
        while (i + prefix < a.size && i + prefix < b.size && a[a.lastIndex - i] == b[b.lastIndex - i]) i++
        return i
    }
}
