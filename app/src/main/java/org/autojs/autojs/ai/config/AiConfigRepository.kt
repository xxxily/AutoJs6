package org.autojs.autojs.ai.config

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class AiConfigRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val keyStore = AiKeyStore(appContext)
    private val gson = Gson()
    private val providerListType = object : TypeToken<List<AiProviderConfig>>() {}.type

    fun getSettings(): AiAssistantSettings = AiAssistantSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        maxContextChars = prefs.getInt(KEY_MAX_CONTEXT_CHARS, 24_000),
        maxLogChars = prefs.getInt(KEY_MAX_LOG_CHARS, 4_000),
        allowFullFileContext = prefs.getBoolean(KEY_ALLOW_FULL_FILE, true),
        allowProjectStructure = prefs.getBoolean(KEY_ALLOW_PROJECT_STRUCTURE, true),
        allowRecentLogs = prefs.getBoolean(KEY_ALLOW_RECENT_LOGS, true),
        highRiskConfirmation = prefs.getBoolean(KEY_HIGH_RISK_CONFIRMATION, true),
    )

    fun saveSettings(settings: AiAssistantSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putInt(KEY_MAX_CONTEXT_CHARS, settings.maxContextChars)
            putInt(KEY_MAX_LOG_CHARS, settings.maxLogChars)
            putBoolean(KEY_ALLOW_FULL_FILE, settings.allowFullFileContext)
            putBoolean(KEY_ALLOW_PROJECT_STRUCTURE, settings.allowProjectStructure)
            putBoolean(KEY_ALLOW_RECENT_LOGS, settings.allowRecentLogs)
            putBoolean(KEY_HIGH_RISK_CONFIRMATION, settings.highRiskConfirmation)
        }
    }

    fun getProviders(): MutableList<AiProviderConfig> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return mutableListOf()
        return runCatching {
            gson.fromJson<List<AiProviderConfig>>(json, providerListType).toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun getDefaultProvider(): AiProviderConfig? {
        val providers = getProviders()
        val defaultId = prefs.getString(KEY_DEFAULT_PROVIDER_ID, null)
        return providers.firstOrNull { it.id == defaultId && it.enabled }
            ?: providers.firstOrNull { it.defaultProvider && it.enabled }
            ?: providers.firstOrNull { it.enabled }
    }

    fun getProvider(id: String): AiProviderConfig? = getProviders().firstOrNull { it.id == id }

    fun getApiKey(config: AiProviderConfig): String? = keyStore.read(config.apiKeyRef)

    fun upsertProvider(config: AiProviderConfig, plainApiKey: String? = null) {
        val providers = getProviders()
        val now = System.currentTimeMillis()
        if (config.id.isBlank()) {
            config.id = UUID.randomUUID().toString()
        }
        if (config.apiKeyRef.isBlank()) {
            config.apiKeyRef = "provider_api_key_${config.id}"
        }
        if (plainApiKey != null && plainApiKey.isNotBlank()) {
            keyStore.save(config.apiKeyRef, plainApiKey)
        }
        val existingIndex = providers.indexOfFirst { it.id == config.id }
        if (existingIndex >= 0) {
            config.createdAt = providers[existingIndex].createdAt
            config.updatedAt = now
            providers[existingIndex] = config
        } else {
            config.createdAt = now
            config.updatedAt = now
            providers += config
        }
        if (config.defaultProvider || providers.count { it.defaultProvider } == 0) {
            providers.forEach { it.defaultProvider = it.id == config.id }
            prefs.edit { putString(KEY_DEFAULT_PROVIDER_ID, config.id) }
        }
        saveProviders(providers)
    }

    fun duplicateProvider(id: String) {
        val source = getProvider(id) ?: return
        val duplicated = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.displayName()} Copy",
            apiKeyRef = "",
            defaultProvider = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        getApiKey(source)?.let { apiKey ->
            upsertProvider(duplicated, apiKey)
        } ?: upsertProvider(duplicated)
    }

    fun deleteProvider(id: String) {
        val providers = getProviders()
        val removed = providers.firstOrNull { it.id == id } ?: return
        providers.removeAll { it.id == id }
        keyStore.delete(removed.apiKeyRef)
        if (removed.defaultProvider || prefs.getString(KEY_DEFAULT_PROVIDER_ID, null) == id) {
            providers.firstOrNull { it.enabled }?.let { next ->
                providers.forEach { it.defaultProvider = it.id == next.id }
                prefs.edit { putString(KEY_DEFAULT_PROVIDER_ID, next.id) }
            } ?: prefs.edit { remove(KEY_DEFAULT_PROVIDER_ID) }
        }
        saveProviders(providers)
    }

    fun setDefaultProvider(id: String) {
        val providers = getProviders()
        providers.forEach { it.defaultProvider = it.id == id }
        prefs.edit { putString(KEY_DEFAULT_PROVIDER_ID, id) }
        saveProviders(providers)
    }

    fun clearConversationAndCache() {
        prefs.edit {
            remove(KEY_LAST_ERROR_MESSAGE)
            remove(KEY_LAST_ERROR_LINE)
            remove(KEY_LAST_ERROR_COLUMN)
            remove(KEY_LAST_LOG_SNIPPET)
        }
    }

    fun saveLastRunError(message: String?, line: Int, column: Int, logSnippet: String? = null) {
        prefs.edit {
            if (message.isNullOrBlank()) remove(KEY_LAST_ERROR_MESSAGE) else putString(KEY_LAST_ERROR_MESSAGE, message)
            putInt(KEY_LAST_ERROR_LINE, line)
            putInt(KEY_LAST_ERROR_COLUMN, column)
            if (logSnippet.isNullOrBlank()) remove(KEY_LAST_LOG_SNIPPET) else putString(KEY_LAST_LOG_SNIPPET, logSnippet)
        }
    }

    fun getLastRunError(): AiLastRunError? {
        val message = prefs.getString(KEY_LAST_ERROR_MESSAGE, null) ?: return null
        return AiLastRunError(
            message = message,
            line = prefs.getInt(KEY_LAST_ERROR_LINE, -1),
            column = prefs.getInt(KEY_LAST_ERROR_COLUMN, 0),
            logSnippet = prefs.getString(KEY_LAST_LOG_SNIPPET, null).orEmpty(),
        )
    }

    private fun saveProviders(providers: List<AiProviderConfig>) {
        prefs.edit { putString(KEY_PROVIDERS, gson.toJson(providers)) }
    }

    companion object {
        private const val PREF_NAME = "ai_script_assistant"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_DEFAULT_PROVIDER_ID = "default_provider_id"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MAX_CONTEXT_CHARS = "max_context_chars"
        private const val KEY_MAX_LOG_CHARS = "max_log_chars"
        private const val KEY_ALLOW_FULL_FILE = "allow_full_file"
        private const val KEY_ALLOW_PROJECT_STRUCTURE = "allow_project_structure"
        private const val KEY_ALLOW_RECENT_LOGS = "allow_recent_logs"
        private const val KEY_HIGH_RISK_CONFIRMATION = "high_risk_confirmation"
        private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
        private const val KEY_LAST_ERROR_LINE = "last_error_line"
        private const val KEY_LAST_ERROR_COLUMN = "last_error_column"
        private const val KEY_LAST_LOG_SNIPPET = "last_log_snippet"

        fun maskSecret(secret: String?): String {
            if (secret.isNullOrBlank()) return ""
            val suffix = secret.takeLast(4)
            return "****$suffix"
        }
    }
}

data class AiLastRunError(
    val message: String,
    val line: Int,
    val column: Int,
    val logSnippet: String,
)
