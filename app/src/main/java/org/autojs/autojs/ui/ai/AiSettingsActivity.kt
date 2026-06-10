package org.autojs.autojs.ui.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import org.autojs.autojs.ai.client.AiClientException
import org.autojs.autojs.ai.client.OpenAiCompatibleClient
import org.autojs.autojs.ai.config.AiAssistantSettings
import org.autojs.autojs.ai.config.AiConfigRepository
import org.autojs.autojs.ai.config.AiHeader
import org.autojs.autojs.ai.config.AiProviderConfig
import org.autojs.autojs.ai.config.StructuredOutputMode
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAiSettingsBinding
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class AiSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private lateinit var repository: AiConfigRepository
    private var testDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AiConfigRepository(this)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(R.string.text_ai_script_assistant)
        binding.scrollView.excludePaddingClippableViewFromBottomNavigationBar()
        bindActions()
        loadSettings()
        refreshProviderSummary()
    }

    override fun onDestroy() {
        testDisposable?.dispose()
        super.onDestroy()
    }

    private fun bindActions() {
        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
            showToast(R.string.text_done)
        }
        binding.buttonManageProviders.setOnClickListener {
            showProviderListDialog()
        }
        binding.buttonTestProvider.setOnClickListener {
            testDefaultProvider()
        }
        binding.buttonClearAiCache.setOnClickListener {
            repository.clearConversationAndCache()
            showToast(R.string.text_done)
        }
    }

    private fun loadSettings() {
        val settings = repository.getSettings()
        binding.switchEnabled.isChecked = settings.enabled
        binding.checkFullFile.isChecked = settings.allowFullFileContext
        binding.checkProjectStructure.isChecked = settings.allowProjectStructure
        binding.checkRecentLogs.isChecked = settings.allowRecentLogs
        binding.checkClipboardContext.isChecked = settings.allowClipboardContext
        binding.checkUiSnapshotContext.isChecked = settings.allowUiSnapshotContext
        binding.checkScreenCaptureContext.isChecked = settings.allowScreenCaptureContext
        binding.checkOcrContext.isChecked = settings.allowOcrContext
        binding.checkHighRisk.isChecked = settings.highRiskConfirmation
        binding.inputMaxContext.setText(settings.maxContextChars.toString())
        binding.inputMaxLogs.setText(settings.maxLogChars.toString())
    }

    private fun saveSettings() {
        repository.saveSettings(
            AiAssistantSettings(
                enabled = binding.switchEnabled.isChecked,
                maxContextChars = binding.inputMaxContext.intValueOrDefault(24_000).coerceIn(2_000, 200_000),
                maxLogChars = binding.inputMaxLogs.intValueOrDefault(4_000).coerceIn(0, 50_000),
                allowFullFileContext = binding.checkFullFile.isChecked,
                allowProjectStructure = binding.checkProjectStructure.isChecked,
                allowRecentLogs = binding.checkRecentLogs.isChecked,
                allowClipboardContext = binding.checkClipboardContext.isChecked,
                allowUiSnapshotContext = binding.checkUiSnapshotContext.isChecked,
                allowScreenCaptureContext = binding.checkScreenCaptureContext.isChecked,
                allowOcrContext = binding.checkOcrContext.isChecked,
                highRiskConfirmation = binding.checkHighRisk.isChecked,
            )
        )
    }

    private fun refreshProviderSummary() {
        val providers = repository.getProviders()
        val defaultProvider = repository.getDefaultProvider()
        val text = if (providers.isEmpty()) {
            getString(R.string.text_ai_no_provider_configured)
        } else {
            getString(
                R.string.text_ai_provider_summary,
                providers.size,
                defaultProvider?.displayName() ?: getString(R.string.text_none),
            )
        }
        binding.textProviderSummary.text = text
    }

    private fun showProviderListDialog() {
        val providers = repository.getProviders()
        val labels = providers.map { provider ->
            buildString {
                append(provider.displayName())
                append(" / ")
                append(provider.model.ifBlank { getString(R.string.text_ai_model_not_set) })
                if (provider.defaultProvider) append(" [${getString(R.string.text_default)}]")
                if (!provider.enabled) append(" [${getString(R.string.text_disabled)}]")
            }
        }.toMutableList()
        labels += getString(R.string.text_ai_add_provider)

        MaterialDialog.Builder(this)
            .title(R.string.text_ai_manage_providers)
            .items(labels)
            .itemsCallback { _, _, which, _ ->
                if (which == providers.size) {
                    showProviderEditorDialog(AiProviderConfig(id = "", name = "OpenAI", model = "gpt-4.1-mini"))
                } else {
                    showProviderActionDialog(providers[which])
                }
            }
            .positiveText(R.string.dialog_button_dismiss)
            .show()
    }

    private fun showProviderActionDialog(provider: AiProviderConfig) {
        val actions = arrayOf(
            getString(R.string.text_edit),
            getString(R.string.text_default),
            getString(R.string.text_duplicate),
            getString(R.string.text_delete),
        )
        MaterialDialog.Builder(this)
            .title(provider.displayName())
            .content(providerSummary(provider))
            .items(actions.toList())
            .itemsCallback { _, _, which, _ ->
                when (which) {
                    0 -> showProviderEditorDialog(provider)
                    1 -> {
                        repository.setDefaultProvider(provider.id)
                        refreshProviderSummary()
                    }
                    2 -> {
                        repository.duplicateProvider(provider.id)
                        refreshProviderSummary()
                    }
                    3 -> confirmDeleteProvider(provider)
                }
            }
            .positiveText(R.string.dialog_button_dismiss)
            .show()
    }

    private fun confirmDeleteProvider(provider: AiProviderConfig) {
        MaterialDialog.Builder(this)
            .title(R.string.text_confirm_to_delete)
            .content(provider.displayName())
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                repository.deleteProvider(provider.id)
                refreshProviderSummary()
            }
            .show()
    }

    private fun showProviderEditorDialog(source: AiProviderConfig) {
        val provider = source.copy(customHeaders = source.customHeaders.toMutableList())
        val apiKeyMask = AiConfigRepository.maskSecret(repository.getApiKey(source))
        val form = ProviderForm(this, provider, apiKeyMask)
        MaterialDialog.Builder(this)
            .title(if (source.id.isBlank()) R.string.text_ai_add_provider else R.string.text_ai_edit_provider)
            .customView(form.root, true)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .onPositive { dialog, _ ->
                form.validationError()?.let { errorRes ->
                    showToast(errorRes)
                    return@onPositive
                }
                val edited = form.toConfig(provider.id, provider.apiKeyRef, provider.createdAt)
                repository.upsertProvider(edited, form.apiKey())
                saveSettings()
                refreshProviderSummary()
                dialog.dismiss()
            }
            .show()
    }

    private fun testDefaultProvider() {
        saveSettings()
        val provider = repository.getDefaultProvider()
        if (provider == null) {
            showToast(R.string.error_ai_no_provider)
            return
        }
        val apiKey = repository.getApiKey(provider)
        if (apiKey.isNullOrBlank()) {
            showToast(R.string.error_ai_missing_api_key)
            return
        }

        val callRef = AtomicReference<Call?>()
        val progress = MaterialDialog.Builder(this)
            .title(R.string.text_ai_testing_provider)
            .content(provider.displayName())
            .progress(true, 0)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_caution)
            .onNegative { _, _ ->
                callRef.get()?.cancel()
                testDisposable?.dispose()
            }
            .show()

        testDisposable = Observable.fromCallable {
            OpenAiCompatibleClient().testConnection(provider, apiKey) { callRef.set(it) }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { progress.dismiss() }
            .subscribe({
                MaterialDialog.Builder(this)
                    .title(R.string.text_verify_success)
                    .content(it.content)
                    .positiveText(R.string.dialog_button_dismiss)
                    .show()
            }, { error ->
                val message = if (error is AiClientException) {
                    "${error.code}: ${error.message}"
                } else {
                    error.message ?: error.toString()
                }
                MaterialDialog.Builder(this)
                    .title(R.string.text_verify_failed)
                    .content(message)
                    .positiveText(R.string.dialog_button_dismiss)
                    .show()
            })
    }

    private fun providerSummary(provider: AiProviderConfig): String = buildString {
        appendLine(provider.baseUrl)
        appendLine("Model: ${provider.model}")
        appendLine("Structured output: ${provider.structuredOutputMode.name.lowercase(Locale.ROOT)}")
        appendLine("Stream: ${provider.streamEnabled}")
        appendLine("Max output tokens: ${provider.maxOutputTokens}")
        if (provider.organizationId.isNotBlank()) appendLine("Organization: ${provider.organizationId}")
        if (provider.projectId.isNotBlank()) appendLine("Project: ${provider.projectId}")
        if (provider.customHeaders.isNotEmpty()) appendLine("Headers: ${provider.customHeaders.size}")
    }.trim()

    private fun showToast(resId: Int) {
        ViewUtils.showToast(this, resId)
    }

    private fun EditText.intValueOrDefault(defaultValue: Int): Int {
        return text?.toString()?.trim()?.toIntOrNull() ?: defaultValue
    }

    private class ProviderForm(
        context: Context,
        provider: AiProviderConfig,
        apiKeyMask: String,
    ) {
        val root: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
        }
        private val name = root.textInput(context, R.string.text_name, provider.name)
        private val baseUrl = root.textInput(context, R.string.text_ai_base_url, provider.baseUrl)
        private val model = root.textInput(context, R.string.text_ai_model, provider.model)
        private val key = root.textInput(context, R.string.text_ai_api_key, "", secret = true).apply {
            if (apiKeyMask.isNotBlank()) {
                hint = context.getString(R.string.text_ai_api_key_keep_existing, apiKeyMask)
            }
        }
        private val organization = root.textInput(context, R.string.text_ai_organization_id, provider.organizationId)
        private val project = root.textInput(context, R.string.text_ai_project_id, provider.projectId)
        private val timeout = root.textInput(context, R.string.text_ai_timeout_millis, provider.timeoutMillis.toString(), numeric = true)
        private val maxTokens = root.textInput(context, R.string.text_ai_max_output_tokens, provider.maxOutputTokens.toString(), numeric = true)
        private val temperature = root.textInput(context, R.string.text_ai_temperature, provider.temperature.toString(), decimal = true)
        private val mode = root.textInput(context, R.string.text_ai_structured_output_mode, provider.structuredOutputMode.name)
        private val headers = root.textInput(context, R.string.text_ai_custom_headers, provider.customHeaders.toHeaderText(), multiline = true)
        private val enabled = root.checkInput(context, R.string.text_enabled, provider.enabled)
        private val defaultProvider = root.checkInput(context, R.string.text_default, provider.defaultProvider)
        private val stream = root.checkInput(context, R.string.text_ai_stream_output, provider.streamEnabled)
        private val compatibility = root.checkInput(context, R.string.text_ai_compatibility_max_tokens, provider.useCompatibilityFallback)

        fun apiKey(): String = key.text?.toString()?.trim().orEmpty()

        fun validationError(): Int? {
            if (baseUrl.value().isBlank() || model.value().isBlank()) {
                return R.string.error_ai_provider_required_fields
            }
            if (runCatching { OpenAiCompatibleClient.buildChatCompletionsUrl(baseUrl.value()) }.isFailure) {
                return R.string.error_ai_invalid_base_url
            }
            val timeoutMillis = timeout.value().toLongOrNull()
            if (timeoutMillis == null || timeoutMillis !in 5_000L..300_000L) {
                return R.string.error_ai_invalid_timeout
            }
            if (StructuredOutputMode.values().none { it.name.equals(mode.value(), true) }) {
                return R.string.error_ai_invalid_structured_output_mode
            }
            return null
        }

        fun toConfig(id: String, apiKeyRef: String, createdAt: Long): AiProviderConfig {
            val customHeaders = headers.text?.toString().orEmpty()
                .lineSequence()
                .mapNotNull { line ->
                    val split = line.split(":", limit = 2)
                    if (split.size != 2) return@mapNotNull null
                    AiHeader(split[0].trim(), split[1].trim(), false)
                }
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .toMutableList()
            return AiProviderConfig(
                id = id,
                name = name.value(),
                baseUrl = baseUrl.value(),
                model = model.value(),
                apiKeyRef = apiKeyRef,
                organizationId = organization.value(),
                projectId = project.value(),
                enabled = enabled.isChecked,
                defaultProvider = defaultProvider.isChecked,
                streamEnabled = stream.isChecked,
                structuredOutputMode = mode.value().toStructuredMode(),
                timeoutMillis = timeout.value().toLongOrNull()?.coerceIn(5_000L, 300_000L) ?: 60_000L,
                temperature = temperature.value().toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.2,
                maxOutputTokens = maxTokens.value().toIntOrNull()?.coerceIn(256, 32_000) ?: 2048,
                customHeaders = customHeaders,
                useCompatibilityFallback = compatibility.isChecked,
                createdAt = createdAt,
            )
        }

        private fun EditText.value(): String = text?.toString()?.trim().orEmpty()

        private fun String.toStructuredMode(): StructuredOutputMode {
            return StructuredOutputMode.values().firstOrNull { it.name.equals(this, true) }
                ?: StructuredOutputMode.JSON_SCHEMA
        }

        private fun List<AiHeader>.toHeaderText(): String = joinToString("\n") {
            "${it.key}: ${if (it.isEffectivelySensitive()) "" else it.value}"
        }

        private fun LinearLayout.textInput(
            context: Context,
            hintRes: Int,
            value: String,
            secret: Boolean = false,
            numeric: Boolean = false,
            decimal: Boolean = false,
            multiline: Boolean = false,
        ): EditText {
            val editText = EditText(context).apply {
                hint = context.getString(hintRes)
                setText(value)
                inputType = when {
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    numeric -> InputType.TYPE_CLASS_NUMBER
                    decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (multiline) minLines = 3
            }
            addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return editText
        }

        private fun LinearLayout.checkInput(context: Context, textRes: Int, checked: Boolean): CheckBox {
            val checkBox = CheckBox(context).apply {
                text = context.getString(textRes)
                isChecked = checked
            }
            addView(checkBox, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return checkBox
        }
    }

    companion object {
        @JvmStatic
        fun launch(context: Context) {
            Intent(context, AiSettingsActivity::class.java).startSafely(context)
        }
    }
}
