package org.autojs.autojs.ui.ai

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import org.autojs.autojs.ai.client.AiClientException
import org.autojs.autojs.ai.client.OpenAiCompatibleClient
import org.autojs.autojs.ai.config.AiConfigRepository
import org.autojs.autojs.ai.copilot.AiContextSource
import org.autojs.autojs.ai.copilot.AiCopilotContextBuilder
import org.autojs.autojs.ai.copilot.AiCopilotContextExtras
import org.autojs.autojs.ai.copilot.AiCopilotPreflight
import org.autojs.autojs.ai.copilot.AiCopilotPrivacyPolicy
import org.autojs.autojs.ai.copilot.AiPatchApplicator
import org.autojs.autojs.ai.docs.AiCapabilityEntry
import org.autojs.autojs.ai.docs.AiCapabilityIndex
import org.autojs.autojs.ai.prompt.AiPromptBuilder
import org.autojs.autojs.ai.prompt.AiScriptContext
import org.autojs.autojs.ai.prompt.AiTaskType
import org.autojs.autojs.ai.result.AiDiffUtils
import org.autojs.autojs.ai.result.AiGeneratedFile
import org.autojs.autojs.ai.result.AiResultParser
import org.autojs.autojs.ai.result.AiResultValidator
import org.autojs.autojs.ai.result.AiValidatedResult
import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object AiAssistantDialogs {

    @JvmStatic
    fun showEditorTask(editorView: EditorView, taskType: AiTaskType, readOnly: Boolean) {
        showEditorTaskInternal(editorView, taskType, readOnly, null)
    }

    @JvmStatic
    fun showEditorTaskWithPrefill(editorView: EditorView, taskType: AiTaskType, readOnly: Boolean, prefillOverride: String) {
        showEditorTaskInternal(editorView, taskType, readOnly, prefillOverride)
    }

    private fun showEditorTaskInternal(
        editorView: EditorView,
        taskType: AiTaskType,
        readOnly: Boolean,
        prefillOverride: String?,
    ) {
        if (readOnly && taskType != AiTaskType.EXPLAIN) {
            showMessage(editorView.context, R.string.text_ai_read_only_write_blocked)
            return
        }
        val context = editorView.context
        val title = titleFor(context, taskType)
        val prefill = when {
            prefillOverride != null -> prefillOverride
            taskType == AiTaskType.FIX_ERROR -> editorView.getLastRunErrorForAi()?.message.orEmpty()
            else -> ""
        }
        askText(
            context = context,
            title = title,
            hint = hintFor(context, taskType),
            prefill = prefill,
        ) { userRequest ->
            val effectiveRequest = userRequest.ifBlank {
                if (taskType == AiTaskType.EXPLAIN) context.getString(R.string.text_ai_explain_code) else ""
            }
            if (taskType != AiTaskType.EXPLAIN && effectiveRequest.isBlank()) {
                showMessage(context, R.string.error_ai_empty_request)
                return@askText
            }
            val snapshotText = editorView.getTextForAi()
            val selectedText = editorView.getSelectedTextForAi()
            val repo = AiConfigRepository(context)
            val settings = repo.getSettings()
            val lastError = editorView.getLastRunErrorForAi()
            val inferredCapabilityIds = if (taskType == AiTaskType.FIX_ERROR) {
                CapabilityRegistry.inferCapabilityIdsFromScript(snapshotText)
            } else {
                emptyList()
            }
            val capabilityStateSummary = if (inferredCapabilityIds.isNotEmpty()) {
                AiCopilotPreflight.summarizeCapabilityChecks(CapabilityRegistry.check(context, inferredCapabilityIds))
            } else {
                ""
            }
            val croppedText = when {
                selectedText.isNotBlank() -> cropAroundSelection(
                    snapshotText,
                    editorView.getSelectionStartForAi(),
                    editorView.getSelectionEndForAi(),
                    settings.maxContextChars,
                )
                settings.allowFullFileContext -> crop(snapshotText, settings.maxContextChars)
                else -> cropAroundSelection(
                    snapshotText,
                    editorView.getSelectionStartForAi(),
                    editorView.getSelectionEndForAi(),
                    settings.maxContextChars,
                )
            }
            val scriptContext = AiScriptContext(
                taskType = taskType,
                userRequest = effectiveRequest,
                filePath = editorView.getFilePathForAi(),
                workingDirectory = editorView.getWorkingDirectoryForAi(),
                currentText = croppedText,
                selectedText = selectedText,
                selectionStart = editorView.getSelectionStartForAi(),
                selectionEnd = editorView.getSelectionEndForAi(),
                projectSummary = projectSummary(editorView.getWorkingDirectoryForAi(), settings.allowProjectStructure),
                errorMessage = if (taskType == AiTaskType.FIX_ERROR) lastError?.message.orEmpty() else "",
                errorLine = lastError?.line ?: -1,
                errorColumn = lastError?.column ?: 0,
                logSnippet = if (settings.allowRecentLogs) crop(lastError?.logSnippet.orEmpty(), settings.maxLogChars) else "",
                capabilityStateSummary = capabilityStateSummary,
            )
            val contextExtras = AiCopilotContextExtras(
                clipboardText = if (settings.allowClipboardContext) ClipboardUtils.getClipOrEmpty(context).toString() else "",
            )
            runAiTask(
                context = context,
                scriptContext = scriptContext,
                contextSummary = buildContextSummary(context, scriptContext, selectedText.isNotBlank(), contextExtras),
                contextExtras = contextExtras,
                onResult = { validated, docs ->
                    showEditorPreview(
                        editorView = editorView,
                        taskType = taskType,
                        snapshotText = snapshotText,
                        scriptContext = scriptContext,
                        hasSelection = selectedText.isNotBlank(),
                        validated = validated,
                        docs = docs,
                        readOnly = readOnly,
                    )
                },
            )
        }
    }

    @JvmStatic
    fun showExplorerTask(context: Context, operations: ScriptOperations, taskType: AiTaskType) {
        when (taskType) {
            AiTaskType.CREATE_SCRIPT -> showExplorerScriptTask(context, operations)
            AiTaskType.CREATE_PROJECT -> showExplorerProjectTask(context, operations)
            else -> Unit
        }
    }

    private fun showExplorerScriptTask(context: Context, operations: ScriptOperations) {
        askText(
            context = context,
            title = context.getString(R.string.text_ai_new_script),
            hint = context.getString(R.string.hint_ai_script_request),
            prefill = "",
        ) { request ->
            if (request.isBlank()) {
                showMessage(context, R.string.error_ai_empty_request)
                return@askText
            }
            val fileName = defaultScriptName(request)
            val scriptContext = AiScriptContext(
                taskType = AiTaskType.CREATE_SCRIPT,
                userRequest = request,
                filePath = fileName,
                workingDirectory = operations.getCurrentDirectoryPathForAi(),
                currentText = "",
                selectedText = "",
                selectionStart = 0,
                selectionEnd = 0,
                projectSummary = projectSummary(operations.getCurrentDirectoryPathForAi(), true),
            )
            runAiTask(
                context = context,
                scriptContext = scriptContext,
                contextSummary = buildContextSummary(context, scriptContext, false),
                onResult = { validated, docs ->
                    showExplorerScriptPreview(context, operations, fileName, scriptContext, validated, docs)
                },
            )
        }
    }

    private fun showExplorerProjectTask(context: Context, operations: ScriptOperations) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
        }
        val projectName = EditText(context).apply {
            hint = context.getString(R.string.text_project)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val packageName = EditText(context).apply {
            hint = "org.autojs.generated"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val mainScript = EditText(context).apply {
            hint = "main.js"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val description = EditText(context).apply {
            hint = context.getString(R.string.hint_ai_project_request)
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        listOf(projectName, packageName, mainScript, description).forEach(container::addView)
        MaterialDialog.Builder(context)
            .title(R.string.text_ai_new_project)
            .customView(container, false)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                val name = projectName.text?.toString()?.trim().orEmpty().ifBlank { "AiProject" }
                val main = mainScript.text?.toString()?.trim().orEmpty().ifBlank { "main.js" }
                val pkg = packageName.text?.toString()?.trim().orEmpty().ifBlank { "org.autojs.generated.${name.sanitizeIdentifier()}" }
                val request = description.text?.toString()?.trim().orEmpty()
                if (request.isBlank()) {
                    showMessage(context, R.string.error_ai_empty_request)
                    return@onPositive
                }
                val scriptContext = AiScriptContext(
                    taskType = AiTaskType.CREATE_PROJECT,
                    userRequest = "Project name: $name\nPackage name: $pkg\nMain script: $main\n$request",
                    filePath = name,
                    workingDirectory = operations.getCurrentDirectoryPathForAi(),
                    currentText = "",
                    selectedText = "",
                    selectionStart = 0,
                    selectionEnd = 0,
                    projectSummary = projectSummary(operations.getCurrentDirectoryPathForAi(), true),
                )
                runAiTask(
                    context = context,
                    scriptContext = scriptContext,
                    contextSummary = buildContextSummary(context, scriptContext, false),
                    onResult = { validated, docs ->
                        showExplorerProjectPreview(context, operations, name, main, pkg, scriptContext, validated, docs)
                    },
                )
            }
            .show()
    }

    private fun runAiTask(
        context: Context,
        scriptContext: AiScriptContext,
        contextSummary: String,
        contextExtras: AiCopilotContextExtras = AiCopilotContextExtras(),
        onResult: (AiValidatedResult, List<AiCapabilityEntry>) -> Unit,
    ) {
        val repo = AiConfigRepository(context)
        val settings = repo.getSettings()
        if (!settings.enabled) {
            showOpenSettingsPrompt(context, context.getString(R.string.error_ai_disabled))
            return
        }
        val provider = repo.getDefaultProvider()
        if (provider == null) {
            showOpenSettingsPrompt(context, context.getString(R.string.error_ai_no_provider))
            return
        }
        val apiKey = repo.getApiKey(provider)
        if (apiKey.isNullOrBlank()) {
            showOpenSettingsPrompt(context, context.getString(R.string.error_ai_missing_api_key))
            return
        }
        val privacyPolicy = AiCopilotPrivacyPolicy.fromSettings(settings)
        val privacySummary = AiCopilotContextBuilder.summarize(scriptContext, contextExtras, privacyPolicy)

        MaterialDialog.Builder(context)
            .title(R.string.text_ai_context_summary)
            .content(contextSummary)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_ai_send)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                val confirmedPolicy = privacyPolicy.copy(
                    confirmedSources = privacyPolicy.confirmedSources + privacySummary.confirmationRequiredSources,
                )
                val prepared = AiCopilotContextBuilder.prepare(scriptContext, contextExtras, confirmedPolicy)
                executeAiRequest(context, provider.displayName(), prepared.scriptContext, provider, apiKey, onResult)
            }
            .show()
    }

    private fun executeAiRequest(
        context: Context,
        providerName: String,
        scriptContext: AiScriptContext,
        provider: org.autojs.autojs.ai.config.AiProviderConfig,
        apiKey: String,
        onResult: (AiValidatedResult, List<AiCapabilityEntry>) -> Unit,
    ) {
        val callRef = AtomicReference<Call?>()
        val disposableRef = AtomicReference<Disposable?>()
        val progress = MaterialDialog.Builder(context)
            .title(R.string.text_ai_generating)
            .content(context.getString(R.string.text_ai_using_provider, providerName))
            .progress(true, 0)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_caution)
            .onNegative { _, _ ->
                callRef.get()?.cancel()
                disposableRef.get()?.dispose()
            }
            .cancelable(false)
            .show()

        val disposable = Observable.fromCallable {
            val index = AiCapabilityIndex.get(context)
            val docs = index.search(
                "${scriptContext.userRequest}\n${scriptContext.currentText}\n${scriptContext.errorMessage}",
            )
            val messages = AiPromptBuilder.buildMessages(scriptContext, docs)
            val response = OpenAiCompatibleClient().complete(
                provider = provider,
                apiKey = apiKey,
                messages = messages,
                responseSchema = AiPromptBuilder.buildResponseSchema(),
                callConsumer = { callRef.set(it) },
            )
            val result = AiResultParser.parse(response.content)
            val validated = AiResultValidator.validate(result, scriptContext.taskType, index)
            val generatedCode = result.files.joinToString("\n") { it.content }
            val capabilityIds = CapabilityRegistry.inferCapabilityIdsFromScript(generatedCode)
            val capabilityChecks = if (capabilityIds.isEmpty()) emptyList() else CapabilityRegistry.check(context, capabilityIds)
            val preflight = AiCopilotPreflight.analyze(result, index, capabilityChecks)
            validated.copy(issues = (validated.issues + preflight.issues).distinct()) to docs
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { progress.dismiss() }
            .subscribe({ (validated, docs) ->
                onResult(validated, docs)
            }, { error ->
                val message = when (error) {
                    is AiClientException -> "${error.code}: ${error.message}"
                    else -> error.message ?: error.toString()
                }
                MaterialDialog.Builder(context)
                    .title(R.string.text_ai_failed)
                    .content(message)
                    .positiveText(R.string.dialog_button_dismiss)
                    .show()
            })
        disposableRef.set(disposable)
    }

    private fun showEditorPreview(
        editorView: EditorView,
        taskType: AiTaskType,
        snapshotText: String,
        scriptContext: AiScriptContext,
        hasSelection: Boolean,
        validated: AiValidatedResult,
        docs: List<AiCapabilityEntry>,
        readOnly: Boolean,
    ) {
        val context = editorView.context
        if (taskType == AiTaskType.EXPLAIN) {
            showTextResult(context, validated)
            return
        }
        val file = validated.result.files.firstOrNull()
        if (file == null) {
            showTextResult(context, validated)
            return
        }
        val diff = when (file.operation) {
            "replace_selection" -> AiDiffUtils.summarize(editorView.getSelectedTextForAi(), file.content)
            "create" -> context.getString(R.string.text_ai_generated_chars, file.content.length)
            else -> AiDiffUtils.summarize(snapshotText, file.content)
        }
        val content = previewText(validated, docs, diff, file)
        val actions = mutableListOf<PreviewAction>()
        if (!readOnly) {
            actions += PreviewAction(R.string.text_apply) {
                confirmWarningsIfNeeded(context, validated) {
                    applyToEditor(editorView, snapshotText, taskType, file)
                }
            }
        }
        actions += PreviewAction(R.string.text_copy) {
            ClipboardUtils.setClip(context, file.content)
            ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
        }
        actions += PreviewAction(R.string.text_ai_regenerate) {
            rerunEditorTask(editorView, taskType, snapshotText, scriptContext, hasSelection, readOnly)
        }
        actions += PreviewAction(R.string.text_ai_continue_modify) {
            askFollowUp(context, scriptContext, validated) { nextContext ->
                runAiTask(
                    context = context,
                    scriptContext = nextContext,
                    contextSummary = buildContextSummary(context, nextContext, hasSelection),
                    onResult = { nextValidated, nextDocs ->
                        showEditorPreview(editorView, taskType, snapshotText, nextContext, hasSelection, nextValidated, nextDocs, readOnly)
                    },
                )
            }
        }
        showPreviewActionsDialog(context, R.string.text_ai_result_preview, content, actions)
    }

    private fun rerunEditorTask(
        editorView: EditorView,
        taskType: AiTaskType,
        snapshotText: String,
        scriptContext: AiScriptContext,
        hasSelection: Boolean,
        readOnly: Boolean,
    ) {
        val context = editorView.context
        runAiTask(
            context = context,
            scriptContext = scriptContext,
            contextSummary = buildContextSummary(context, scriptContext, hasSelection),
            onResult = { nextValidated, nextDocs ->
                showEditorPreview(editorView, taskType, snapshotText, scriptContext, hasSelection, nextValidated, nextDocs, readOnly)
            },
        )
    }

    private fun showPreviewActionsDialog(
        context: Context,
        @StringRes title: Int,
        content: String,
        actions: List<PreviewAction>,
    ) {
        MaterialDialog.Builder(context)
            .title(title)
            .content(content)
            .items(actions.map { context.getString(it.labelRes) })
            .itemsCallback { dialog, _, which, _ ->
                dialog.dismiss()
                actions.getOrNull(which)?.action?.invoke()
            }
            .negativeText(R.string.dialog_button_cancel)
            .show()
    }

    private fun askFollowUp(
        context: Context,
        scriptContext: AiScriptContext,
        validated: AiValidatedResult,
        onFollowUp: (AiScriptContext) -> Unit,
    ) {
        askText(
            context = context,
            title = context.getString(R.string.text_ai_continue_modify),
            hint = context.getString(R.string.hint_ai_continue_modify),
            prefill = "",
        ) { request ->
            if (request.isBlank()) {
                showMessage(context, R.string.error_ai_empty_request)
                return@askText
            }
            onFollowUp(scriptContext.copy(userRequest = followUpRequest(scriptContext.userRequest, validated, request)))
        }
    }

    private fun followUpRequest(originalRequest: String, validated: AiValidatedResult, followUp: String): String = buildString {
        appendLine(originalRequest)
        appendLine()
        appendLine("Previous AI result summary:")
        appendLine(validated.result.summary)
        validated.result.files.take(4).forEach { file ->
            appendLine()
            appendLine("Previous generated file: ${file.path}")
            appendLine("```javascript")
            appendLine(crop(file.content, 12_000))
            appendLine("```")
        }
        appendLine()
        appendLine("Follow-up request:")
        appendLine(followUp)
    }

    private data class PreviewAction(
        @param:StringRes val labelRes: Int,
        val action: () -> Unit,
    )

    private fun applyToEditor(editorView: EditorView, snapshotText: String, taskType: AiTaskType, file: AiGeneratedFile) {
        val context = editorView.context
        val patchResult = AiPatchApplicator.apply(
            currentText = editorView.getTextForAi(),
            snapshotText = snapshotText,
            selectionStart = editorView.getSelectionStartForAi(),
            selectionEnd = editorView.getSelectionEndForAi(),
            file = file,
        )
        if (!patchResult.applied) {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(patchResult.message.ifBlank { context.getString(R.string.prompt_ai_editor_changed_before_apply) })
                .positiveText(R.string.dialog_button_dismiss)
                .show()
            return
        }
        applyToEditorUnchecked(editorView, taskType, file, patchResult.newText)
    }

    private fun applyToEditorUnchecked(editorView: EditorView, taskType: AiTaskType, file: AiGeneratedFile, appliedText: String) {
        when {
            taskType == AiTaskType.CREATE_SCRIPT && editorView.getTextForAi().isNotBlank() && file.operation != "patch" -> editorView.insertFromAi(file.content)
            else -> editorView.replaceAllFromAi(appliedText)
        }
    }

    private fun showExplorerScriptPreview(
        context: Context,
        operations: ScriptOperations,
        fallbackFileName: String,
        scriptContext: AiScriptContext,
        validated: AiValidatedResult,
        docs: List<AiCapabilityEntry>,
    ) {
        val file = validated.result.files.firstOrNull()
        if (file == null) {
            showTextResult(context, validated)
            return
        }
        val fileName = file.path.ifBlank { fallbackFileName }.ensureJsSuffix()
        val actions = listOf(
            PreviewAction(R.string.text_create) {
                confirmWarningsIfNeeded(context, validated) {
                    operations.createAiGeneratedScript(fileName, file.content)
                }
            },
            PreviewAction(R.string.text_copy) {
                ClipboardUtils.setClip(context, file.content)
                ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
            },
            PreviewAction(R.string.text_ai_regenerate) {
                runAiTask(
                    context = context,
                    scriptContext = scriptContext,
                    contextSummary = buildContextSummary(context, scriptContext, false),
                    onResult = { nextValidated, nextDocs ->
                        showExplorerScriptPreview(context, operations, fallbackFileName, scriptContext, nextValidated, nextDocs)
                    },
                )
            },
            PreviewAction(R.string.text_ai_continue_modify) {
                askFollowUp(context, scriptContext, validated) { nextContext ->
                    runAiTask(
                        context = context,
                        scriptContext = nextContext,
                        contextSummary = buildContextSummary(context, nextContext, false),
                        onResult = { nextValidated, nextDocs ->
                            showExplorerScriptPreview(context, operations, fallbackFileName, nextContext, nextValidated, nextDocs)
                        },
                    )
                }
            },
        )
        showPreviewActionsDialog(
            context,
            R.string.text_ai_result_preview,
            previewText(validated, docs, context.getString(R.string.text_ai_generated_chars, file.content.length), file),
            actions,
        )
    }

    private fun showExplorerProjectPreview(
        context: Context,
        operations: ScriptOperations,
        projectName: String,
        mainScript: String,
        packageName: String,
        scriptContext: AiScriptContext,
        validated: AiValidatedResult,
        docs: List<AiCapabilityEntry>,
    ) {
        val generated = validated.result.files
        val files = linkedMapOf<String, String>()
        generated.forEach { file ->
            if (file.path.isNotBlank() && file.content.isNotBlank()) {
                files[file.path] = file.content
            }
        }
        val main = mainScript.ensureJsSuffix()
        if (files.keys.none { it == main || it.endsWith("/$main") }) {
            files[main] = generated.firstOrNull { it.language.equals("javascript", true) }?.content.orEmpty()
        }
        if (files.keys.none { it == "project.json" || it.endsWith("/project.json") }) {
            files["project.json"] = defaultProjectJson(projectName, packageName, main)
        }
        val tree = files.keys.joinToString("\n") { "- $it" }
        val preview = previewText(validated, docs, tree, generated.firstOrNull())
        val actions = listOf(
            PreviewAction(R.string.text_create) {
                confirmWarningsIfNeeded(context, validated) {
                    operations.createAiGeneratedProject(projectName, files, main)
                }
            },
            PreviewAction(R.string.text_ai_regenerate) {
                runAiTask(
                    context = context,
                    scriptContext = scriptContext,
                    contextSummary = buildContextSummary(context, scriptContext, false),
                    onResult = { nextValidated, nextDocs ->
                        showExplorerProjectPreview(context, operations, projectName, mainScript, packageName, scriptContext, nextValidated, nextDocs)
                    },
                )
            },
            PreviewAction(R.string.text_ai_continue_modify) {
                askFollowUp(context, scriptContext, validated) { nextContext ->
                    runAiTask(
                        context = context,
                        scriptContext = nextContext,
                        contextSummary = buildContextSummary(context, nextContext, false),
                        onResult = { nextValidated, nextDocs ->
                            showExplorerProjectPreview(context, operations, projectName, mainScript, packageName, nextContext, nextValidated, nextDocs)
                        },
                    )
                }
            },
        )
        showPreviewActionsDialog(context, R.string.text_ai_result_preview, preview, actions)
    }

    private fun showTextResult(context: Context, validated: AiValidatedResult) {
        MaterialDialog.Builder(context)
            .title(R.string.text_ai_result)
            .content(validationText(validated))
            .positiveText(R.string.dialog_button_dismiss)
            .neutralText(R.string.text_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> ClipboardUtils.setClip(context, validationText(validated)) }
            .show()
    }

    private fun confirmWarningsIfNeeded(context: Context, validated: AiValidatedResult, action: () -> Unit) {
        if (validated.blocksApply) {
            MaterialDialog.Builder(context)
                .title(R.string.text_ai_risk_confirmation)
                .content(validationText(validated))
                .positiveText(R.string.dialog_button_dismiss)
                .show()
            return
        }
        if (!AiConfigRepository(context).getSettings().highRiskConfirmation || !validated.requiresSecondConfirmation) {
            action()
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_ai_risk_confirmation)
            .content(validationText(validated))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_continue)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ -> action() }
            .show()
    }

    private fun previewText(
        validated: AiValidatedResult,
        docs: List<AiCapabilityEntry>,
        changeSummary: String,
        file: AiGeneratedFile?,
    ): String = buildString {
        appendLine(validated.result.summary)
        appendLine()
        appendLine(changeSummary)
        file?.let {
            appendLine("File: ${it.path}")
            appendLine("Operation: ${it.operation}")
        }
        appendLine()
        appendLine(validationText(validated))
        appendLine()
        appendLine("Docs:")
        docs.take(5).forEach { appendLine("- ${it.signature} (${it.docFile})") }
    }.trim()

    private fun validationText(validated: AiValidatedResult): String = buildString {
        val result = validated.result
        if (result.requirements.isNotEmpty()) {
            appendLine("Requirements:")
            result.requirements.forEach { appendLine("- $it") }
        }
        if (result.risks.isNotEmpty() || validated.codeValidation.risks.isNotEmpty()) {
            appendLine("Risks:")
            result.risks.forEach { appendLine("- $it") }
            validated.codeValidation.risks.forEach { appendLine("- ${it.name}: ${it.description}") }
        }
        if (result.usedApis.isNotEmpty()) {
            appendLine("Used APIs:")
            result.usedApis.forEach { appendLine("- ${it.name} (${it.doc}): ${it.reason}") }
        }
        if (validated.issues.isNotEmpty()) {
            appendLine("Validation warnings:")
            validated.issues.forEach { appendLine("- $it") }
        }
        if (validated.codeValidation.unknownApis.isNotEmpty()) {
            appendLine("Unknown API candidates:")
            validated.codeValidation.unknownApis.forEach { appendLine("- $it") }
        }
        if (result.verificationSteps.isNotEmpty()) {
            appendLine("Verification:")
            result.verificationSteps.forEach { appendLine("- $it") }
        }
        if (result.notes.isNotBlank()) {
            appendLine("Notes:")
            appendLine(result.notes)
        }
    }.ifBlank { validated.result.summary }

    private fun askText(
        context: Context,
        title: String,
        hint: String,
        prefill: String,
        callback: (String) -> Unit,
    ) {
        MaterialDialog.Builder(context)
            .title(title)
            .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            .input(hint, prefill, false) { _, input -> callback(input.toString()) }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun showOpenSettingsPrompt(context: Context, message: String) {
        MaterialDialog.Builder(context)
            .title(R.string.text_ai_script_assistant)
            .content(message)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_settings)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> context.startActivity(Intent(context, AiSettingsActivity::class.java)) }
            .show()
    }

    private fun buildContextSummary(
        context: Context,
        scriptContext: AiScriptContext,
        hasSelection: Boolean,
        contextExtras: AiCopilotContextExtras = AiCopilotContextExtras(),
    ): String = buildString {
        val settings = AiConfigRepository(context).getSettings()
        val policy = AiCopilotPrivacyPolicy.fromSettings(settings)
        val summary = AiCopilotContextBuilder.summarize(scriptContext, contextExtras, policy)
        appendLine(context.getString(R.string.text_ai_context_summary_provider_placeholder))
        appendLine("Task: ${scriptContext.taskType.wireName}")
        appendLine("File: ${scriptContext.filePath.ifBlank { "(unknown)" }}")
        appendLine("Includes selection: $hasSelection")
        appendLine("Includes current text chars: ${if (policy.allows(AiContextSource.FILE)) scriptContext.currentText.length else 0}")
        appendLine("Includes project structure: ${policy.allows(AiContextSource.PROJECT_STRUCTURE) && scriptContext.projectSummary.isNotBlank()}")
        appendLine("Includes recent error/logs: ${policy.allows(AiContextSource.RECENT_LOGS) && (scriptContext.errorMessage.isNotBlank() || scriptContext.logSnippet.isNotBlank())}")
        appendLine("Copilot privacy summary:")
        summary.lines.forEach { appendLine(it) }
    }

    private fun titleFor(context: Context, taskType: AiTaskType): String = when (taskType) {
        AiTaskType.CREATE_SCRIPT -> context.getString(R.string.text_ai_generate_script)
        AiTaskType.MODIFY_SELECTION -> context.getString(R.string.text_ai_modify_selection)
        AiTaskType.MODIFY_FILE -> context.getString(R.string.text_ai_modify_file)
        AiTaskType.FIX_ERROR -> context.getString(R.string.text_ai_fix_error)
        AiTaskType.EXPLAIN -> context.getString(R.string.text_ai_explain_code)
        AiTaskType.CREATE_PROJECT -> context.getString(R.string.text_ai_new_project)
    }

    private fun hintFor(context: Context, taskType: AiTaskType): String = when (taskType) {
        AiTaskType.CREATE_SCRIPT -> context.getString(R.string.hint_ai_script_request)
        AiTaskType.MODIFY_SELECTION, AiTaskType.MODIFY_FILE -> context.getString(R.string.hint_ai_modify_request)
        AiTaskType.FIX_ERROR -> context.getString(R.string.hint_ai_error_request)
        AiTaskType.EXPLAIN -> context.getString(R.string.hint_ai_explain_request)
        AiTaskType.CREATE_PROJECT -> context.getString(R.string.hint_ai_project_request)
    }

    private fun crop(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val half = maxChars / 2
        return text.take(half) + "\n/* ... cropped ... */\n" + text.takeLast(maxChars - half)
    }

    private fun cropAroundSelection(text: String, start: Int, end: Int, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        val selected = text.substring(safeStart, safeEnd)
        val side = ((maxChars - selected.length).coerceAtLeast(0)) / 2
        val from = (safeStart - side).coerceAtLeast(0)
        val to = (safeEnd + side).coerceAtMost(text.length)
        return text.substring(from, to)
    }

    private fun projectSummary(directory: String, allowed: Boolean): String {
        if (!allowed || directory.isBlank()) return ""
        val dir = File(directory)
        val files = dir.listFiles()?.sortedBy { it.name }?.take(80).orEmpty()
        if (files.isEmpty()) return ""
        return files.joinToString("\n") { file ->
            (if (file.isDirectory) "[D] " else "[F] ") + file.name
        }
    }

    private fun defaultProjectJson(projectName: String, packageName: String, mainScript: String): String = """
        {
          "name": "$projectName",
          "versionName": "1.0.0",
          "versionCode": 1,
          "packageName": "$packageName",
          "main": "$mainScript",
          "assets": [],
          "capabilities": [],
          "pluginDependencies": [],
          "riskPolicy": {
            "default": "prompt",
            "undeclared": "prompt",
            "high": "prompt",
            "critical": "reject",
            "rememberAllowed": true
          },
          "launchConfig": {
            "logsVisible": true,
            "splashVisible": true,
            "launcherVisible": true,
            "runOnBoot": false
          }
        }
    """.trimIndent()

    private fun defaultScriptName(request: String): String {
        val token = request
            .replace(Regex("[^A-Za-z0-9_\\u4e00-\\u9fa5]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "ai_script" }
        return "$token.js"
    }

    private fun String.ensureJsSuffix(): String = if (endsWith(".js", true)) this else "$this.js"

    private fun String.sanitizeIdentifier(): String = lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "project" }

    private fun showMessage(context: Context, resId: Int) {
        ViewUtils.showToast(context, resId)
    }
}
