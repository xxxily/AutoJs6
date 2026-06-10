package org.autojs.autojs.ai.copilot

import org.autojs.autojs.ai.docs.AiCapabilityIndex
import org.autojs.autojs.ai.result.AiGenerationResult
import org.autojs.autojs.capability.CapabilityCheck
import org.autojs.autojs.capability.CapabilityDefinition
import org.autojs.autojs.capability.CapabilityRegistry
import java.util.Locale

object AiCopilotPreflight {

    fun analyze(
        result: AiGenerationResult,
        capabilityIndex: AiCapabilityIndex,
        capabilityChecks: List<CapabilityCheck> = emptyList(),
        declaredProjectCapabilities: Collection<String> = emptyList(),
    ): AiCopilotPreflightReport {
        val code = result.files.joinToString("\n") { it.content }
        val codeValidation = capabilityIndex.validateGeneratedCode(code)
        val unknownFromUsedApis = result.usedApis
            .map { it.name }
            .filter { it.isNotBlank() && !capabilityIndex.containsApi(it) }
        val unknownApis = (codeValidation.unknownApis + unknownFromUsedApis).distinct()
        val checksById = capabilityChecks.associateBy { it.id.lowercase(Locale.ROOT) }
        val declaredProjectCapabilityIds = declaredProjectCapabilities.map { it.lowercase(Locale.ROOT) }.toSet()
        val inferred = CapabilityRegistry.inferCapabilitiesFromScript(code)
        val findings = inferred.map { definition -> definition.toFinding(checksById[definition.id.lowercase(Locale.ROOT)]) }
        val undeclaredHighRisk = findings
            .filter { it.dangerous }
            .filterNot { finding ->
                finding.id.lowercase(Locale.ROOT) in declaredProjectCapabilityIds ||
                result.declares(finding.name) ||
                    result.declares(finding.id) ||
                    finding.relatedApis.any { api -> result.declares(api) }
            }
        val issues = buildList {
            unknownApis.forEach { add("Unknown API before apply: $it") }
            undeclaredHighRisk.forEach { add("High-risk capability not declared: ${it.name}") }
            findings
                .filter { it.status in setOf("missing", "blocked", "unsupported") }
                .forEach { add("Capability not ready: ${it.name} (${it.status})") }
        }
        return AiCopilotPreflightReport(
            unknownApis = unknownApis,
            capabilityFindings = findings,
            undeclaredHighRisk = undeclaredHighRisk,
            issues = issues,
        )
    }

    fun summarizeCapabilityChecks(checks: List<CapabilityCheck>): String {
        return checks.joinToString("\n") { check ->
            buildString {
                append("- ${check.id}: ${check.status.wireName}")
                if (check.missing.isNotEmpty()) append("; missing=${check.missing.joinToString("|")}")
                if (check.blocked.isNotEmpty()) append("; blocked=${check.blocked.joinToString("|")}")
                if (check.unsupported.isNotEmpty()) append("; unsupported=${check.unsupported.joinToString("|")}")
                if (check.requestHint.isNotBlank()) append("; hint=${check.requestHint}")
            }
        }
    }

    private fun CapabilityDefinition.toFinding(check: CapabilityCheck?): AiCapabilityFinding {
        return AiCapabilityFinding(
            id = id,
            name = name,
            dangerous = dangerous,
            permissions = permissions,
            services = services,
            relatedApis = relatedApis,
            description = description,
            requestHint = requestHint,
            status = check?.status?.wireName ?: "unchecked",
            missing = check?.missing.orEmpty(),
            blocked = check?.blocked.orEmpty(),
            unsupported = check?.unsupported.orEmpty(),
        )
    }

    private fun AiGenerationResult.declares(needle: String): Boolean {
        if (needle.isBlank()) return false
        val lower = needle.lowercase(Locale.ROOT)
        return (requirements + risks + warnings).any { item ->
            val text = item.lowercase(Locale.ROOT)
            lower in text || text in lower
        }
    }
}

data class AiCopilotPreflightReport(
    val unknownApis: List<String>,
    val capabilityFindings: List<AiCapabilityFinding>,
    val undeclaredHighRisk: List<AiCapabilityFinding>,
    val issues: List<String>,
) {
    val blocksApply: Boolean
        get() = unknownApis.isNotEmpty() || undeclaredHighRisk.isNotEmpty()

    val requiresUserConfirmation: Boolean
        get() = capabilityFindings.any { it.dangerous } || issues.isNotEmpty()
}

data class AiCapabilityFinding(
    val id: String,
    val name: String,
    val dangerous: Boolean,
    val permissions: List<String>,
    val services: List<String>,
    val relatedApis: List<String>,
    val description: String,
    val requestHint: String,
    val status: String,
    val missing: List<String>,
    val blocked: List<String>,
    val unsupported: List<String>,
)
