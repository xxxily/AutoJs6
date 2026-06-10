package org.autojs.autojs.ai.config

import java.util.UUID

data class AiProviderConfig(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var baseUrl: String = "https://api.openai.com/v1",
    var model: String = "",
    var apiKeyRef: String = "",
    var organizationId: String = "",
    var projectId: String = "",
    var enabled: Boolean = true,
    var defaultProvider: Boolean = false,
    var streamEnabled: Boolean = false,
    var structuredOutputMode: StructuredOutputMode = StructuredOutputMode.JSON_SCHEMA,
    var timeoutMillis: Long = 60_000L,
    var temperature: Double = 0.2,
    var maxOutputTokens: Int = 2048,
    var customHeaders: MutableList<AiHeader> = mutableListOf(),
    var useCompatibilityFallback: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    fun displayName(): String = name.ifBlank { baseUrl }
}

data class AiHeader(
    var key: String = "",
    var value: String = "",
    var sensitive: Boolean = false,
) {
    fun normalizedKey(): String = key.trim()

    fun isEffectivelySensitive(): Boolean {
        val lower = normalizedKey().lowercase()
        return sensitive ||
            "authorization" in lower ||
            "api-key" in lower ||
            "apikey" in lower ||
            "token" in lower ||
            "secret" in lower
    }
}

enum class StructuredOutputMode {
    JSON_SCHEMA,
    JSON_OBJECT,
    PROMPT_JSON,
}

data class AiAssistantSettings(
    var enabled: Boolean = false,
    var maxContextChars: Int = 24_000,
    var maxLogChars: Int = 4_000,
    var allowFullFileContext: Boolean = true,
    var allowProjectStructure: Boolean = true,
    var allowRecentLogs: Boolean = true,
    var allowClipboardContext: Boolean = false,
    var allowUiSnapshotContext: Boolean = false,
    var allowScreenCaptureContext: Boolean = false,
    var allowOcrContext: Boolean = false,
    var highRiskConfirmation: Boolean = true,
)
