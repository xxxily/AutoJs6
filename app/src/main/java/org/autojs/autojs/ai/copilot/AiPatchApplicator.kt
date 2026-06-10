package org.autojs.autojs.ai.copilot

import org.autojs.autojs.ai.result.AiGeneratedFile

object AiPatchApplicator {

    fun apply(
        currentText: String,
        snapshotText: String,
        selectionStart: Int,
        selectionEnd: Int,
        file: AiGeneratedFile,
    ): AiPatchApplyResult {
        if (currentText != snapshotText) {
            return AiPatchApplyResult.failure(
                AiPatchApplyError.CONTEXT_DRIFT,
                "Current editor content changed after the AI request.",
            )
        }
        return when (file.operation) {
            "replace_selection" -> replaceSelection(snapshotText, selectionStart, selectionEnd, file.content)
            "patch" -> applyUnifiedDiff(snapshotText, file.content)
            "replace_all", "create" -> AiPatchApplyResult.success(file.content, file.operation)
            else -> AiPatchApplyResult.failure(
                AiPatchApplyError.UNSUPPORTED_OPERATION,
                "Unsupported AI file operation: ${file.operation}",
            )
        }
    }

    fun applyUnifiedDiff(originalText: String, diffText: String): AiPatchApplyResult {
        val source = originalText.split('\n')
        val diffLines = diffText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val hunks = parseHunks(diffLines)
        if (hunks.isEmpty()) {
            return AiPatchApplyResult.failure(AiPatchApplyError.INVALID_PATCH, "Unified diff contains no hunks.")
        }

        val output = mutableListOf<String>()
        var sourceIndex = 0
        hunks.forEach { hunk ->
            val targetIndex = (hunk.oldStart - 1).coerceAtLeast(0)
            if (targetIndex < sourceIndex || targetIndex > source.size) {
                return AiPatchApplyResult.failure(AiPatchApplyError.CONTEXT_MISMATCH, "Patch hunk starts outside the current context.")
            }
            while (sourceIndex < targetIndex) {
                output += source[sourceIndex++]
            }
            hunk.lines.forEach { line ->
                if (line.isEmpty()) {
                    return AiPatchApplyResult.failure(AiPatchApplyError.INVALID_PATCH, "Patch hunk has an invalid empty marker line.")
                }
                when (line[0]) {
                    ' ' -> {
                        val expected = line.drop(1)
                        if (!source.matchesAt(sourceIndex, expected)) {
                            return AiPatchApplyResult.failure(AiPatchApplyError.CONTEXT_MISMATCH, "Patch context does not match: $expected")
                        }
                        output += source[sourceIndex++]
                    }
                    '-' -> {
                        val expected = line.drop(1)
                        if (!source.matchesAt(sourceIndex, expected)) {
                            return AiPatchApplyResult.failure(AiPatchApplyError.CONTEXT_MISMATCH, "Patch removal does not match: $expected")
                        }
                        sourceIndex++
                    }
                    '+' -> output += line.drop(1)
                    '\\' -> Unit
                    else -> return AiPatchApplyResult.failure(AiPatchApplyError.INVALID_PATCH, "Invalid patch line marker: ${line[0]}")
                }
            }
        }
        while (sourceIndex < source.size) {
            output += source[sourceIndex++]
        }
        return AiPatchApplyResult.success(output.joinToString("\n"), "patch")
    }

    private fun replaceSelection(
        text: String,
        rawStart: Int,
        rawEnd: Int,
        replacement: String,
    ): AiPatchApplyResult {
        val start = minOf(rawStart, rawEnd).coerceIn(0, text.length)
        val end = maxOf(rawStart, rawEnd).coerceIn(start, text.length)
        val next = text.substring(0, start) + replacement + text.substring(end)
        return AiPatchApplyResult.success(next, "replace_selection")
    }

    private fun parseHunks(lines: List<String>): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        var current: DiffHunk? = null
        lines.forEach { line ->
            val header = HUNK_HEADER.matchEntire(line)
            when {
                header != null -> {
                    current = DiffHunk(
                        oldStart = header.groupValues[1].toIntOrNull() ?: 1,
                        oldCount = header.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1,
                        newStart = header.groupValues[3].toIntOrNull() ?: 1,
                        newCount = header.groupValues[4].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1,
                    ).also { hunks += it }
                }
                current != null && (line.startsWith(" ") || line.startsWith("-") || line.startsWith("+") || line.startsWith("\\")) -> {
                    current.lines.add(line)
                }
                current == null -> Unit
                line.startsWith("@@") -> Unit
                else -> Unit
            }
        }
        return hunks
    }

    private fun List<String>.matchesAt(index: Int, expected: String): Boolean {
        return index in indices && this[index] == expected
    }

    private data class DiffHunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val lines: MutableList<String> = mutableListOf(),
    )

    private val HUNK_HEADER = Regex("""@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@.*""")
}

data class AiPatchApplyResult(
    val applied: Boolean,
    val newText: String,
    val operation: String,
    val error: AiPatchApplyError? = null,
    val message: String = "",
) {
    companion object {
        fun success(newText: String, operation: String): AiPatchApplyResult {
            return AiPatchApplyResult(true, newText, operation)
        }

        fun failure(error: AiPatchApplyError, message: String): AiPatchApplyResult {
            return AiPatchApplyResult(false, "", "", error, message)
        }
    }
}

enum class AiPatchApplyError {
    CONTEXT_DRIFT,
    CONTEXT_MISMATCH,
    INVALID_PATCH,
    UNSUPPORTED_OPERATION,
}
