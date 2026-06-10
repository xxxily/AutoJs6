package org.autojs.autojs.ai.copilot

import org.autojs.autojs.ai.prompt.AiScriptContext
import org.autojs.autojs.ai.prompt.AiTaskType
import org.autojs.autojs.capability.CapabilityCheck

enum class AiCopilotStage(val wireName: String) {
    GENERATE("generate"),
    STATIC_CHECK("static_check"),
    PRE_RUN_CHECK("pre_run_check"),
    USER_CONFIRM("user_confirm"),
    RUN("run"),
    CAPTURE_FAILURE("capture_failure"),
    FIX("fix"),
}

data class AiCopilotLoopState(
    val stage: AiCopilotStage = AiCopilotStage.GENERATE,
    val generationSummary: String = "",
    val staticIssues: List<String> = emptyList(),
    val preRunIssues: List<String> = emptyList(),
    val userConfirmed: Boolean = false,
    val runReport: AiCopilotRunReport? = null,
) {
    fun nextAfterGeneration(staticIssues: List<String>): AiCopilotLoopState {
        return copy(stage = AiCopilotStage.STATIC_CHECK, staticIssues = staticIssues)
    }

    fun nextAfterStaticCheck(preRunIssues: List<String>): AiCopilotLoopState {
        return copy(stage = AiCopilotStage.PRE_RUN_CHECK, preRunIssues = preRunIssues)
    }

    fun nextAfterPreRunCheck(): AiCopilotLoopState {
        return copy(stage = AiCopilotStage.USER_CONFIRM)
    }

    fun nextAfterUserConfirm(): AiCopilotLoopState {
        return copy(stage = AiCopilotStage.RUN, userConfirmed = true)
    }

    fun nextAfterRun(report: AiCopilotRunReport): AiCopilotLoopState {
        return copy(
            stage = if (report.exceptionMessage.isBlank()) AiCopilotStage.RUN else AiCopilotStage.CAPTURE_FAILURE,
            runReport = report,
        )
    }

    fun nextForFix(): AiCopilotLoopState {
        return copy(stage = AiCopilotStage.FIX)
    }
}

data class AiCopilotRunReport(
    val scriptPath: String = "",
    val logs: String = "",
    val exceptionMessage: String = "",
    val exceptionLine: Int = -1,
    val exceptionColumn: Int = 0,
    val capabilityChecks: List<CapabilityCheck> = emptyList(),
)

object AiCopilotFixContextBuilder {
    fun buildFixContext(
        previousContext: AiScriptContext,
        report: AiCopilotRunReport,
    ): AiScriptContext {
        val capabilitySummary = AiCopilotPreflight.summarizeCapabilityChecks(report.capabilityChecks)
        val request = buildString {
            appendLine(previousContext.userRequest)
            appendLine()
            appendLine("The previous generated script failed at runtime. Use the exception, logs, and capability state to propose the smallest fix.")
        }.trim()
        return previousContext.copy(
            taskType = AiTaskType.FIX_ERROR,
            userRequest = request,
            filePath = report.scriptPath.ifBlank { previousContext.filePath },
            errorMessage = AiPrivacyRedactor.redact(report.exceptionMessage),
            errorLine = report.exceptionLine,
            errorColumn = report.exceptionColumn,
            logSnippet = AiPrivacyRedactor.redact(report.logs),
            capabilityStateSummary = capabilitySummary,
        )
    }
}
