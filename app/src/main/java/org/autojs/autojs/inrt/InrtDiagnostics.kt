package org.autojs.autojs.inrt

import android.content.Context
import android.content.Intent
import android.util.Log
import org.autojs.autojs.apkbuilder.ProjectBuildPreflight
import org.autojs.autojs.capability.CapabilityCheck
import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject

object InrtDiagnostics {

    private const val TAG = "InrtDiagnostics"

    @JvmStatic
    fun projectConfig(context: Context): ProjectConfig? {
        return ProjectConfig.fromAssets(context, ProjectConfig.configFileOfDir("project"))
    }

    @JvmStatic
    fun preflightAsset(context: Context): JSONObject? {
        return runCatching {
            context.assets.open(ProjectBuildPreflight.DIAGNOSTICS_ASSET_PATH).use { input ->
                JSONObject(input.bufferedReader().readText())
            }
        }.onFailure {
            Log.d(TAG, "No bundled build diagnostics found: ${it.message}")
        }.getOrNull()
    }

    @JvmStatic
    fun capabilityChecks(context: Context, config: ProjectConfig? = projectConfig(context)): List<CapabilityCheck> {
        val capabilities = config?.capabilities.orEmpty()
        if (capabilities.isEmpty()) return emptyList()
        return CapabilityRegistry.check(context, capabilities)
    }

    @JvmStatic
    fun missingOrRequestableChecks(context: Context, config: ProjectConfig? = projectConfig(context)): List<CapabilityCheck> {
        return capabilityChecks(context, config).filterNot { it.available }
    }

    @JvmStatic
    fun requiredSettings(context: Context, config: ProjectConfig? = projectConfig(context)): List<String> {
        val fromAsset = preflightAsset(context)
            ?.optJSONArray("requiredInrtSettings")
            ?.toStringList()
            .orEmpty()
        if (fromAsset.isNotEmpty()) return fromAsset
        return ProjectBuildPreflight.requiredInrtSettingsForCapabilities(config?.capabilities.orEmpty())
    }

    @JvmStatic
    fun export(context: Context, throwable: Throwable? = null): String {
        val config = projectConfig(context)
        val checks = capabilityChecks(context, config)
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("app", JSONObject()
                .put("packageName", context.packageName)
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("isInrt", BuildConfig.isInrt))
            .put("project", config?.let { ProjectBuildPreflight.projectSummaryJson(it) } ?: JSONObject.NULL)
            .put("requiredInrtSettings", requiredSettings(context, config).toJsonArray())
            .put("capabilityChecks", checks.map { it.toJson() }.toJsonArray())
            .put("preflight", preflightAsset(context) ?: JSONObject.NULL)
        throwable?.let {
            root.put("exception", JSONObject()
                .put("class", it.javaClass.name)
                .put("message", it.message ?: JSONObject.NULL)
                .put("stackTrace", Log.getStackTraceString(it)))
        }
        return root.toString(2)
    }

    @JvmStatic
    fun summaryForSettings(context: Context): String {
        val config = projectConfig(context) ?: return context.getString(R.string.text_inrt_build_diagnostics_unavailable)
        val missing = missingOrRequestableChecks(context, config)
        val requiredSettings = requiredSettings(context, config)
        return buildString {
            appendLine("${context.getString(R.string.text_project)}: ${config.name}")
            appendLine("${context.getString(R.string.text_package_name)}: ${config.packageName}")
            appendLine("${context.getString(R.string.text_version)}: ${config.versionName} (${config.versionCode})")
            appendLine("${context.getString(R.string.text_build_id)}: ${config.buildInfo?.buildId.orEmpty().ifBlank { "-" }}")
            appendLine("${context.getString(R.string.text_build_number)}: ${config.buildInfo?.buildNumber ?: 0}")
            appendLine("${context.getString(R.string.text_capabilities)}: ${config.capabilities.joinToString().ifBlank { "-" }}")
            appendLine("${context.getString(R.string.text_inrt_required_settings)}: ${requiredSettings.joinToString().ifBlank { "-" }}")
            append("${context.getString(R.string.text_inrt_missing_capabilities)}: ")
            append(missing.joinToString { "${it.name}(${it.status.wireName})" }.ifBlank { "-" })
        }
    }

    @JvmStatic
    fun copyToClipboard(context: Context, throwable: Throwable? = null) {
        ClipboardUtils.setClip(context, export(context, throwable))
        ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
    }

    @JvmStatic
    fun openSettingsIfNeeded(context: Context, checks: List<CapabilityCheck>): Boolean {
        if (checks.isEmpty()) return false
        Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let(context::startActivity)
        return true
    }

    private fun CapabilityCheck.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("status", status.wireName)
        .put("available", available)
        .put("requestable", requestable)
        .put("missing", missing.toJsonArray())
        .put("blocked", blocked.toJsonArray())
        .put("unsupported", unsupported.toJsonArray())
        .put("dangerous", dangerous)
        .put("permissions", permissions.toJsonArray())
        .put("services", services.toJsonArray())
        .put("requestHint", requestHint)
        .put("inrtSupported", inrtSupported)

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> optString(index) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Collection<*>.toJsonArray(): JSONArray {
        val arr = JSONArray()
        forEach { arr.put(it) }
        return arr
    }
}
