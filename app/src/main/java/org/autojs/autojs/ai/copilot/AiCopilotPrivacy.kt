package org.autojs.autojs.ai.copilot

import org.autojs.autojs.ai.config.AiAssistantSettings
import org.autojs.autojs.ai.prompt.AiScriptContext
import java.util.Locale

enum class AiContextSource(val wireName: String, val sensitive: Boolean) {
    FILE("file", false),
    PROJECT_STRUCTURE("project_structure", false),
    RECENT_LOGS("recent_logs", true),
    CLIPBOARD("clipboard", true),
    UI_SNAPSHOT("ui_snapshot", true),
    SCREEN_CAPTURE("screen_capture", true),
    OCR("ocr", true),
}

data class AiCopilotPrivacyPolicy(
    val includeFileContext: Boolean = true,
    val includeProjectStructure: Boolean = true,
    val includeRecentLogs: Boolean = true,
    val includeClipboard: Boolean = false,
    val includeUiSnapshot: Boolean = false,
    val includeScreenCapture: Boolean = false,
    val includeOcr: Boolean = false,
    val confirmedSources: Set<AiContextSource> = emptySet(),
) {
    fun allows(source: AiContextSource): Boolean = when (source) {
        AiContextSource.FILE -> includeFileContext
        AiContextSource.PROJECT_STRUCTURE -> includeProjectStructure
        AiContextSource.RECENT_LOGS -> includeRecentLogs
        AiContextSource.CLIPBOARD -> includeClipboard
        AiContextSource.UI_SNAPSHOT -> includeUiSnapshot
        AiContextSource.SCREEN_CAPTURE -> includeScreenCapture
        AiContextSource.OCR -> includeOcr
    }

    fun canSend(source: AiContextSource): Boolean {
        return allows(source) && (!source.sensitive || source in confirmedSources || source == AiContextSource.RECENT_LOGS)
    }

    fun requiresConfirmation(source: AiContextSource): Boolean {
        return allows(source) && source.sensitive && source !in confirmedSources && source != AiContextSource.RECENT_LOGS
    }

    companion object {
        fun fromSettings(
            settings: AiAssistantSettings,
            confirmedSources: Set<AiContextSource> = emptySet(),
        ): AiCopilotPrivacyPolicy = AiCopilotPrivacyPolicy(
            includeFileContext = settings.allowFullFileContext,
            includeProjectStructure = settings.allowProjectStructure,
            includeRecentLogs = settings.allowRecentLogs,
            includeClipboard = settings.allowClipboardContext,
            includeUiSnapshot = settings.allowUiSnapshotContext,
            includeScreenCapture = settings.allowScreenCaptureContext,
            includeOcr = settings.allowOcrContext,
            confirmedSources = confirmedSources,
        )
    }
}

data class AiCopilotContextExtras(
    val clipboardText: String = "",
    val uiSnapshotSummary: String = "",
    val screenCaptureSummary: String = "",
    val ocrSummary: String = "",
)

data class AiCopilotPreparedContext(
    val scriptContext: AiScriptContext,
    val summary: AiCopilotContextSummary,
)

data class AiCopilotContextSummary(
    val includedSources: List<AiContextSource>,
    val excludedSources: List<AiContextSource>,
    val confirmationRequiredSources: List<AiContextSource>,
    val lines: List<String>,
) {
    val canSend: Boolean
        get() = confirmationRequiredSources.isEmpty()

    fun asText(): String = lines.joinToString("\n")
}

object AiCopilotContextBuilder {

    fun prepare(
        context: AiScriptContext,
        extras: AiCopilotContextExtras,
        policy: AiCopilotPrivacyPolicy,
    ): AiCopilotPreparedContext {
        val summary = summarize(context, extras, policy)
        val sanitized = context.copy(
            currentText = if (policy.canSend(AiContextSource.FILE)) AiPrivacyRedactor.redact(context.currentText) else "",
            selectedText = if (policy.canSend(AiContextSource.FILE)) AiPrivacyRedactor.redact(context.selectedText) else "",
            projectSummary = if (policy.canSend(AiContextSource.PROJECT_STRUCTURE)) AiPrivacyRedactor.redact(context.projectSummary) else "",
            errorMessage = if (policy.canSend(AiContextSource.RECENT_LOGS)) AiPrivacyRedactor.redact(context.errorMessage) else "",
            logSnippet = if (policy.canSend(AiContextSource.RECENT_LOGS)) AiPrivacyRedactor.redact(context.logSnippet) else "",
            clipboardText = if (policy.canSend(AiContextSource.CLIPBOARD)) AiPrivacyRedactor.redact(extras.clipboardText) else "",
            uiSnapshotSummary = if (policy.canSend(AiContextSource.UI_SNAPSHOT)) AiPrivacyRedactor.redact(extras.uiSnapshotSummary) else "",
            screenCaptureSummary = if (policy.canSend(AiContextSource.SCREEN_CAPTURE)) AiPrivacyRedactor.redact(extras.screenCaptureSummary) else "",
            ocrSummary = if (policy.canSend(AiContextSource.OCR)) AiPrivacyRedactor.redact(extras.ocrSummary) else "",
        )
        return AiCopilotPreparedContext(sanitized, summary)
    }

    fun summarize(
        context: AiScriptContext,
        extras: AiCopilotContextExtras,
        policy: AiCopilotPrivacyPolicy,
    ): AiCopilotContextSummary {
        val present = linkedMapOf(
            AiContextSource.FILE to (context.currentText.isNotBlank() || context.selectedText.isNotBlank()),
            AiContextSource.PROJECT_STRUCTURE to context.projectSummary.isNotBlank(),
            AiContextSource.RECENT_LOGS to (context.errorMessage.isNotBlank() || context.logSnippet.isNotBlank()),
            AiContextSource.CLIPBOARD to extras.clipboardText.isNotBlank(),
            AiContextSource.UI_SNAPSHOT to extras.uiSnapshotSummary.isNotBlank(),
            AiContextSource.SCREEN_CAPTURE to extras.screenCaptureSummary.isNotBlank(),
            AiContextSource.OCR to extras.ocrSummary.isNotBlank(),
        )
        val included = present.filter { (source, hasContent) -> hasContent && policy.canSend(source) }.keys.toList()
        val excluded = present.filter { (source, hasContent) -> hasContent && !policy.allows(source) }.keys.toList()
        val confirmation = present.filter { (source, hasContent) -> hasContent && policy.requiresConfirmation(source) }.keys.toList()
        val lines = buildList {
            add("Task: ${context.taskType.wireName}")
            add("File: ${context.filePath.ifBlank { "(unknown)" }}")
            add("Included context: ${included.joinWireNames().ifBlank { "(none)" }}")
            if (excluded.isNotEmpty()) add("Excluded by privacy policy: ${excluded.joinWireNames()}")
            if (confirmation.isNotEmpty()) add("Requires explicit confirmation before send: ${confirmation.joinWireNames()}")
            add("Current text chars: ${if (AiContextSource.FILE in included) context.currentText.length else 0}")
            add("Selected text chars: ${if (AiContextSource.FILE in included) context.selectedText.length else 0}")
            add("Project summary chars: ${if (AiContextSource.PROJECT_STRUCTURE in included) context.projectSummary.length else 0}")
            add("Log/error chars: ${if (AiContextSource.RECENT_LOGS in included) context.errorMessage.length + context.logSnippet.length else 0}")
        }
        return AiCopilotContextSummary(included, excluded, confirmation, lines)
    }

    private fun List<AiContextSource>.joinWireNames(): String = joinToString(", ") { it.wireName }
}

object AiPrivacyRedactor {
    private val secretPatterns = listOf(
        Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE) to "Bearer ****",
        Regex("\\b(?:sk|rk|sess)-[A-Za-z0-9._\\-]{8,}\\b", RegexOption.IGNORE_CASE) to "****-redacted",
        Regex("(?i)\\b(api[_-]?key|authorization|token|secret|password)\\b\\s*[:=]\\s*[^\\s,;]+") to "\$1=****",
        Regex("(?i)(api[_-]?key|access[_-]?token|token)=([^&\\s]+)") to "\$1=****",
    )

    fun redact(text: String): String {
        if (text.isBlank()) return text
        return secretPatterns.fold(text) { acc, (pattern, replacement) ->
            acc.replace(pattern, replacement)
        }
    }

    fun redactThrowable(error: Throwable): String {
        return redact(error.message ?: error.toString()).take(800)
    }

    fun maskKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        val trimmed = key.trim()
        return "****${trimmed.takeLast(4).lowercase(Locale.ROOT)}"
    }
}
