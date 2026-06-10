package org.autojs.autojs.observability

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.UiSnapshotTools
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

object RemoteDebugBridge {

    private val gson = Gson()
    private val secureRandom = SecureRandom()

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    @JvmStatic
    fun currentToken(context: Context): String? {
        return prefs(context).getString(KEY_TOKEN, null)
    }

    @JvmStatic
    fun enableWithNewToken(context: Context): String {
        val token = newToken()
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_TOKEN, sha256(token))
            .apply()
        return token
    }

    @JvmStatic
    fun rotateToken(context: Context): String = enableWithNewToken(context)

    @JvmStatic
    fun disable(context: Context) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_TOKEN)
            .apply()
    }

    @JvmStatic
    fun verifyToken(context: Context, token: String?): Boolean {
        if (!isEnabled(context)) return false
        val expected = currentToken(context) ?: return false
        if (token.isNullOrBlank()) return false
        return MessageDigest.isEqual(expected.toByteArray(), sha256(token).toByteArray())
    }

    @JvmStatic
    fun showLocalManager(context: Context) {
        val enabled = isEnabled(context)
        val tokenHint = if (enabled) {
            context.getString(R.string.text_remote_debug_token_saved_hint)
        } else {
            context.getString(R.string.text_remote_debug_disabled_hint)
        }
        val builder = MaterialDialog.Builder(context)
            .title(R.string.text_remote_debug_bridge)
            .content(tokenHint)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)

        if (enabled) {
            builder
                .negativeText(R.string.dialog_button_disable)
                .onNegative { dialog, _ ->
                    disable(context)
                    dialog.dismiss()
                    ViewUtils.showToast(context, R.string.text_remote_debug_disabled, true)
                }
                .positiveText(R.string.dialog_button_rotate_token)
                .onPositive { dialog, _ ->
                    val token = rotateToken(context)
                    ClipboardUtils.setClip(context, token)
                    dialog.dismiss()
                    showTokenCreatedDialog(context, token, rotated = true)
                }
        } else {
            builder
                .negativeText(R.string.dialog_button_dismiss)
                .onNegative { dialog, _ -> dialog.dismiss() }
                .positiveText(R.string.dialog_button_enable)
                .onPositive { dialog, _ ->
                    val token = enableWithNewToken(context)
                    ClipboardUtils.setClip(context, token)
                    dialog.dismiss()
                    showTokenCreatedDialog(context, token, rotated = false)
                }
        }
        builder.show()
    }

    @JvmStatic
    fun handleCommand(context: Context, data: JsonObject): JsonObject {
        val command = debugCommand(data)
        val requestId = data.stringOrNull("id") ?: data.stringOrNull("requestId")
        if (command == null) {
            return response(requestId, "", false, "debug_command_missing", "Missing debug command.")
        }
        if (command == "status") {
            return response(requestId, command, true, payload = jsonObject {
                addProperty("enabled", isEnabled(context))
                addProperty("tokenRequired", true)
                addProperty("protocolVersion", PROTOCOL_VERSION)
            })
        }
        if (command == "enable" || command == "rotateToken") {
            return response(requestId, command, false, "local_confirmation_required", "Enable and token rotation must be confirmed on the device.")
        }
        if (!isEnabled(context)) {
            return response(requestId, command, false, "remote_debug_disabled", "Remote debug bridge is disabled on this device.")
        }
        if (!verifyToken(context, data.stringOrNull("token") ?: data.stringOrNull("debugToken"))) {
            return response(requestId, command, false, "invalid_token", "Remote debug token is missing or invalid.")
        }

        return try {
            when (command) {
                "listRuns", "recentRuns" -> response(requestId, command, true, payload = jsonObject {
                    add("runs", gson.toJsonTree(ScriptObservability.recentRuns(data.intOrNull("limit") ?: 20)))
                })
                "running" -> response(requestId, command, true, payload = jsonObject {
                    add("runs", gson.toJsonTree(ScriptObservability.runningRuns()))
                })
                "snapshot" -> snapshotResponse(requestId, command, data)
                "logs" -> logsResponse(requestId, command, data)
                "exportDiagnostics" -> response(requestId, command, true, payload = jsonObject {
                    addProperty("diagnosticsJson", ScriptObservability.exportDiagnosticsJson(data.intOrNull("executionId")))
                })
                "stop" -> stopResponse(context, requestId, command, data)
                "stopAll" -> response(requestId, command, true, payload = jsonObject {
                    val stopped = AutoJs.instance.scriptEngineService.stopAll()
                    addProperty("stopped", stopped)
                })
                "restartLast", "restart" -> restartResponse(context, requestId, command, data)
                "uiSnapshot" -> uiSnapshotResponse(context, requestId, command)
                "screenshot" -> response(requestId, command, true, payload = unavailable(
                    "screen_capture_unavailable",
                    "Remote screenshot export requires an active screen-capture authorization/session; use images.requestScreenCapture() locally first."
                ))
                "disable" -> {
                    disable(context)
                    response(requestId, command, true, payload = jsonObject { addProperty("enabled", false) })
                }
                else -> response(requestId, command, false, "unsupported_debug_command", "Unsupported debug command: $command")
            }
        } catch (e: Exception) {
            response(requestId, command, false, e.javaClass.name, e.message.orEmpty())
        }
    }

    private fun snapshotResponse(requestId: String?, command: String, data: JsonObject): JsonObject {
        val executionId = data.intOrNull("executionId")
        val snapshot = executionId?.let(ScriptObservability::snapshot) ?: ScriptObservability.latest()
        return when (snapshot) {
            null -> response(requestId, command, false, "run_not_found", "No matching script run was found.")
            else -> response(requestId, command, true, payload = jsonObject { add("run", gson.toJsonTree(snapshot)) })
        }
    }

    private fun logsResponse(requestId: String?, command: String, data: JsonObject): JsonObject {
        val executionId = data.intOrNull("executionId")
        val snapshot = executionId?.let(ScriptObservability::snapshot) ?: ScriptObservability.latest()
        return when (snapshot) {
            null -> response(requestId, command, false, "run_not_found", "No matching script run was found.")
            else -> response(requestId, command, true, payload = jsonObject {
                addProperty("executionId", snapshot.executionId)
                add("logs", gson.toJsonTree(snapshot.logs.takeLast(data.intOrNull("limit") ?: 100)))
            })
        }
    }

    private fun stopResponse(context: Context, requestId: String?, command: String, data: JsonObject): JsonObject {
        val executionId = data.intOrNull("executionId")
            ?: return response(requestId, command, false, "execution_id_required", "Missing executionId.")
        val execution = AutoJs.instance.scriptEngineService.getScriptExecution(executionId)
            ?: return response(requestId, command, false, "run_not_found", "No running script execution found for $executionId.")
        execution.engine.forceStop()
        ViewUtils.showToast(context, context.getString(R.string.text_remote_debug_stop_requested, executionId), false)
        return response(requestId, command, true, payload = jsonObject {
            addProperty("executionId", executionId)
            addProperty("stopRequested", true)
        })
    }

    private fun restartResponse(context: Context, requestId: String?, command: String, data: JsonObject): JsonObject {
        val snapshot = data.intOrNull("executionId")?.let(ScriptObservability::snapshot) ?: ScriptObservability.latest()
            ?: return response(requestId, command, false, "run_not_found", "No matching script run was found.")
        val filePath = snapshot.filePath
            ?: return response(requestId, command, false, "restart_source_unavailable", "Only file-backed script runs can be restarted remotely.")
        val file = File(filePath)
        if (!file.isFile) {
            return response(requestId, command, false, "restart_file_missing", "Script file does not exist: $filePath")
        }
        val execution = Scripts.run(context, ScriptFile(filePath))
            ?: return response(requestId, command, false, "restart_failed", "Script restart request did not create a new execution.")
        return response(requestId, command, true, payload = jsonObject {
            addProperty("previousExecutionId", snapshot.executionId)
            addProperty("executionId", execution.id)
            addProperty("filePath", filePath)
        })
    }

    private fun uiSnapshotResponse(context: Context, requestId: String?, command: String): JsonObject {
        val activity = AutoJs.instance.appUtils.currentActivity
        val packageName = activity?.packageName ?: context.packageName
        val activityName = activity?.javaClass?.name.orEmpty()
        val bridge = AutoJs.instance.createAccessibilityBridge()
        if (bridge.service == null) {
            return response(requestId, command, true, payload = unavailable(
                "accessibility_service_unavailable",
                "Accessibility service is not running; UI snapshot cannot be captured."
            ))
        }
        val snapshot = UiSnapshotTools.capture(
            context = context.applicationContext,
            bridge = bridge,
            packageName = packageName,
            activityName = activityName,
            options = UiSnapshotTools.CaptureOptions(redact = true),
        )
        return response(requestId, command, true, payload = jsonObject {
            addProperty("available", true)
            add("snapshot", gson.toJsonTree(snapshot))
        })
    }

    private fun unavailable(code: String, message: String): JsonObject = jsonObject {
        addProperty("available", false)
        addProperty("code", code)
        addProperty("message", message)
    }

    private fun response(
        requestId: String?,
        command: String,
        ok: Boolean,
        code: String? = null,
        message: String? = null,
        payload: JsonObject = JsonObject(),
    ): JsonObject = jsonObject {
        addProperty("version", PROTOCOL_VERSION)
        addProperty("requestId", requestId.orEmpty())
        addProperty("command", command)
        addProperty("ok", ok)
        addProperty("timestamp", System.currentTimeMillis())
        code?.let { addProperty("code", it) }
        message?.let { addProperty("message", it) }
        add("payload", payload)
    }

    private fun debugCommand(data: JsonObject): String? {
        val raw = data.stringOrNull("command") ?: return null
        if (raw == "debug") {
            return normalizeDebugCommand(data.stringOrNull("method") ?: data.stringOrNull("debugCommand"))
        }
        return normalizeDebugCommand(raw).takeIf { raw.startsWith("debug.") }
    }

    private fun normalizeDebugCommand(raw: String?): String? {
        return raw?.removePrefix("debug.")
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        return runCatching { value.asString }.getOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        return runCatching { value.asInt }.getOrNull()
    }

    private fun jsonObject(block: JsonObject.() -> Unit): JsonObject {
        return JsonObject().apply(block)
    }

    private fun JsonObject.add(key: String, value: JsonElement?) {
        add(key, value ?: gson.toJsonTree(null))
    }

    private fun showTokenCreatedDialog(context: Context, token: String, rotated: Boolean) {
        MaterialDialog.Builder(context)
            .title(if (rotated) R.string.text_remote_debug_token_rotated else R.string.text_remote_debug_enabled)
            .content(context.getString(R.string.text_remote_debug_token_created_message, token))
            .neutralText(R.string.dialog_button_copy_token)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> ClipboardUtils.setClip(context, token) }
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val PROTOCOL_VERSION = 1
    private const val PREF_NAME = "remote-debug-bridge"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOKEN = "token_sha256"
}
