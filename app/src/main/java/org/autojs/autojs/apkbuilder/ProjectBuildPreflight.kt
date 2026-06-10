package org.autojs.autojs.apkbuilder

import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.project.ProjectConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ProjectBuildPreflightIssue(
    val code: String,
    val message: String,
    val path: String? = null,
    val detail: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("code", code)
        .put("message", message)
        .put("path", path ?: JSONObject.NULL)
        .put("detail", detail ?: JSONObject.NULL)
}

data class ProjectBuildPreflightReport(
    val sourcePath: String,
    val projectRoot: String?,
    val mainScript: String,
    val mainScriptPath: String?,
    val singleFileBuild: Boolean,
    val scriptFiles: List<String>,
    val resourceReferences: List<String>,
    val pluginCalls: List<String>,
    val declaredPluginDependencies: List<String>,
    val declaredCapabilities: List<String>,
    val inferredCapabilities: List<String>,
    val missingCapabilities: List<String>,
    val mappedPermissions: List<String>,
    val foregroundServiceTypes: List<String>,
    val requiredInrtSettings: List<String>,
    val highRiskPermissions: List<String>,
    val errors: List<ProjectBuildPreflightIssue>,
    val warnings: List<ProjectBuildPreflightIssue>,
) {
    fun hasErrors(): Boolean = errors.isNotEmpty()

    fun hasWarnings(): Boolean = warnings.isNotEmpty()

    fun toDisplayString(): String {
        val lines = mutableListOf<String>()
        if (errors.isNotEmpty()) {
            lines += "Errors:"
            errors.forEach { issue ->
                lines += "- ${issue.message}${issue.path?.let { " [$it]" }.orEmpty()}"
            }
        }
        if (warnings.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += ""
            lines += "Warnings:"
            warnings.forEach { issue ->
                lines += "- ${issue.message}${issue.path?.let { " [$it]" }.orEmpty()}"
            }
        }
        if (mappedPermissions.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += ""
            lines += "Mapped Android permissions:"
            mappedPermissions.forEach { lines += "- $it" }
        }
        if (foregroundServiceTypes.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += ""
            lines += "Foreground service types:"
            foregroundServiceTypes.forEach { lines += "- $it" }
        }
        if (requiredInrtSettings.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += ""
            lines += "Required inrt settings:"
            requiredInrtSettings.forEach { lines += "- $it" }
        }
        return lines.joinToString("\n").ifBlank { "No build preflight issues." }
    }

    fun toJson(config: ProjectConfig? = null): JSONObject = JSONObject()
        .put("schemaVersion", ProjectBuildPreflight.SCHEMA_VERSION)
        .put("sourcePath", sourcePath)
        .put("projectRoot", projectRoot ?: JSONObject.NULL)
        .put("mainScript", mainScript)
        .put("mainScriptPath", mainScriptPath ?: JSONObject.NULL)
        .put("singleFileBuild", singleFileBuild)
        .put("scriptFiles", scriptFiles.toJsonArray())
        .put("resourceReferences", resourceReferences.toJsonArray())
        .put("pluginCalls", pluginCalls.toJsonArray())
        .put("declaredPluginDependencies", declaredPluginDependencies.toJsonArray())
        .put("declaredCapabilities", declaredCapabilities.toJsonArray())
        .put("inferredCapabilities", inferredCapabilities.toJsonArray())
        .put("missingCapabilities", missingCapabilities.toJsonArray())
        .put("mappedPermissions", mappedPermissions.toJsonArray())
        .put("foregroundServiceTypes", foregroundServiceTypes.toJsonArray())
        .put("requiredInrtSettings", requiredInrtSettings.toJsonArray())
        .put("highRiskPermissions", highRiskPermissions.toJsonArray())
        .put("errors", errors.map { it.toJson() }.toJsonArray())
        .put("warnings", warnings.map { it.toJson() }.toJsonArray())
        .also { root ->
            config?.let { root.put("project", ProjectBuildPreflight.projectSummaryJson(it)) }
        }
}

object ProjectBuildPreflight {

    const val SCHEMA_VERSION = 1
    const val DIAGNOSTICS_ASSET_PATH = "project/build-diagnostics.json"

    private val JS_EXTENSIONS = setOf("js", "mjs", "cjs")

    private val RESOURCE_REFERENCE_PATTERNS = listOf(
        Regex("""\bfiles\s*\.\s*(?:read|readBytes|open|exists)\s*\(\s*["'](\.{1,2}/[^"']+)["']"""),
        Regex("""\bimages\s*\.\s*read\s*\(\s*["'](\.{1,2}/[^"']+)["']"""),
        Regex("""\bopen\s*\(\s*["'](\.{1,2}/[^"']+)["']"""),
        Regex("""\brequire\s*\(\s*["'](\.{1,2}/[^"']+)["']"""),
    )

    private val PLUGIN_CALL_PATTERNS = listOf(
        Regex("""\bplugins\s*\.\s*load\s*\(\s*["']([^"']+)["']"""),
        Regex("""\bplugins\s*\(\s*["']([^"']+)["']"""),
    )

    private val HIGH_RISK_PERMISSIONS = setOf(
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.REQUEST_DELETE_PACKAGES",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.CALL_PHONE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_CALL_LOG",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
    )

    @JvmStatic
    fun run(config: ProjectConfig): ProjectBuildPreflightReport {
        val errors = mutableListOf<ProjectBuildPreflightIssue>()
        val warnings = mutableListOf<ProjectBuildPreflightIssue>()

        val sourcePath = config.sourcePath.orEmpty().trim()
        val source = File(sourcePath)
        val singleFileBuild = source.isFile
        val projectRoot = source.takeIf { it.isDirectory }?.absoluteFile
        val mainScript = config.mainScriptFileName.ifBlank { ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME }
        val mainScriptFile = when {
            sourcePath.isBlank() -> null
            source.isDirectory -> File(source, mainScript)
            else -> source
        }

        if (sourcePath.isBlank()) {
            errors += ProjectBuildPreflightIssue("source_missing", "Source path is empty.")
        } else if (!source.exists()) {
            errors += ProjectBuildPreflightIssue("source_missing", "Source path does not exist.", sourcePath)
        }

        if (mainScriptFile == null || !mainScriptFile.isFile) {
            errors += ProjectBuildPreflightIssue(
                code = "main_script_missing",
                message = "Main script does not exist: $mainScript",
                path = mainScriptFile?.path ?: mainScript,
            )
        }

        val scriptFiles = collectScriptFiles(source, config)
        val scriptTexts = scriptFiles.mapNotNull { file ->
            runCatching { file to file.readText(StandardCharsets.UTF_8) }
                .onFailure {
                    warnings += ProjectBuildPreflightIssue(
                        code = "script_read_failed",
                        message = "Failed to read script for preflight: ${file.name}",
                        path = file.path,
                        detail = it.message,
                    )
                }
                .getOrNull()
        }

        val inferredCapabilities = scriptTexts
            .flatMap { (_, text) -> CapabilityRegistry.inferCapabilityIdsFromScript(text) }
            .distinct()

        val declaredCapabilities = normalizeList(config.capabilities)
        val missingCapabilities = inferredCapabilities.filterNot { inferred ->
            declaredCapabilities.any { it.equals(inferred, ignoreCase = true) }
        }

        missingCapabilities.forEach { capability ->
            val definition = CapabilityRegistry.explain(capability)
            val issue = ProjectBuildPreflightIssue(
                code = "capability_missing",
                message = "Capability \"$capability\" is used by scripts but is not declared in project.json capabilities.",
                detail = definition?.description,
            )
            if (!singleFileBuild && definition?.dangerous == true) {
                errors += issue
            } else {
                warnings += issue
            }
        }

        val resourceReferences = collectResourceReferences(scriptTexts, projectRoot)
        resourceReferences.forEach { reference ->
            val root = projectRoot ?: mainScriptFile?.parentFile
            val file = root?.let { File(it, reference).canonicalFile }
            if (file == null || !file.exists()) {
                errors += ProjectBuildPreflightIssue(
                    code = "resource_missing",
                    message = "Referenced resource does not exist: $reference",
                    path = reference,
                )
            }
        }

        config.assets.orEmpty().filter { it.isNotBlank() }.forEach { asset ->
            val file = projectRoot?.let { File(it, asset).canonicalFile }
            if (file == null || !file.exists()) {
                errors += ProjectBuildPreflightIssue(
                    code = "asset_missing",
                    message = "project.json asset does not exist: $asset",
                    path = asset,
                )
            }
        }

        val pluginCalls = collectPluginCalls(scriptTexts)
        val declaredPluginDependencies = normalizeList(config.pluginDependencies)
        val missingPluginDependencies = pluginCalls.filterNot { call ->
            declaredPluginDependencies.any { it.equals(call, ignoreCase = true) }
        }
        missingPluginDependencies.forEach { plugin ->
            errors += ProjectBuildPreflightIssue(
                code = "plugin_dependency_missing",
                message = "Plugin dependency \"$plugin\" is used by plugins.load() but is not declared in project.json pluginDependencies.",
                detail = "Add \"$plugin\" to pluginDependencies, or remove the plugin call before packaging.",
            )
        }

        val effectiveCapabilities = (declaredCapabilities + if (singleFileBuild) missingCapabilities else emptyList()).distinct()
        val mappedPermissions = manifestPermissionsForCapabilities(effectiveCapabilities)
        val foregroundServiceTypes = foregroundServiceTypesForCapabilities(effectiveCapabilities)
        val requiredInrtSettings = requiredInrtSettingsForCapabilities(effectiveCapabilities)
        val highRiskPermissions = normalizeList(config.permissions)
            .filter { permission -> HIGH_RISK_PERMISSIONS.any { it.equals(permission, ignoreCase = true) } }
            .filterNot { permission -> mappedPermissions.any { it.equals(permission, ignoreCase = true) } }
            .distinct()

        highRiskPermissions.forEach { permission ->
            warnings += ProjectBuildPreflightIssue(
                code = "high_risk_permission",
                message = "High-risk Android permission is selected but not explained by declared capabilities: $permission",
            )
        }

        val missingMappedPermissions = mappedPermissions.filterNot { permission ->
            config.permissions.any { it.equals(permission, ignoreCase = true) }
        }
        missingMappedPermissions.forEach { permission ->
            warnings += ProjectBuildPreflightIssue(
                code = "permission_auto_mapped",
                message = "Capability manifest mapping will add Android permission: $permission",
            )
        }

        return ProjectBuildPreflightReport(
            sourcePath = sourcePath,
            projectRoot = projectRoot?.path,
            mainScript = mainScript,
            mainScriptPath = mainScriptFile?.path,
            singleFileBuild = singleFileBuild,
            scriptFiles = scriptFiles.map { it.path },
            resourceReferences = resourceReferences,
            pluginCalls = pluginCalls,
            declaredPluginDependencies = declaredPluginDependencies,
            declaredCapabilities = declaredCapabilities,
            inferredCapabilities = inferredCapabilities,
            missingCapabilities = missingCapabilities,
            mappedPermissions = mappedPermissions,
            foregroundServiceTypes = foregroundServiceTypes,
            requiredInrtSettings = requiredInrtSettings,
            highRiskPermissions = highRiskPermissions,
            errors = errors.distinctBy { listOf(it.code, it.message, it.path, it.detail).joinToString("|") },
            warnings = warnings.distinctBy { listOf(it.code, it.message, it.path, it.detail).joinToString("|") },
        )
    }

    @JvmStatic
    fun manifestPermissionsForCapabilities(capabilities: Collection<String>): List<String> {
        return CapabilityRegistry.manifestPermissionsForCapabilityIds(capabilities)
            .filter { it.isNotBlank() }
            .distinct()
    }

    @JvmStatic
    fun applyReportToConfig(config: ProjectConfig, report: ProjectBuildPreflightReport): ProjectConfig {
        if (report.singleFileBuild && report.missingCapabilities.isNotEmpty()) {
            config.capabilities = (config.capabilities + report.missingCapabilities).distinct()
        }
        if (report.mappedPermissions.isNotEmpty()) {
            config.permissions = (config.permissions + report.mappedPermissions).distinct()
        }
        return config
    }

    @JvmStatic
    fun foregroundServiceTypesForCapabilities(capabilities: Collection<String>): List<String> {
        val normalized = capabilities.map { it.lowercase(Locale.ROOT) }.toSet()
        return buildList {
            if (CapabilityRegistry.SCREEN_CAPTURE in normalized) add("mediaProjection")
            if (CapabilityRegistry.BACKGROUND_RUN in normalized || CapabilityRegistry.NOTIFICATIONS in normalized) add("specialUse")
        }.distinct()
    }

    @JvmStatic
    fun requiredInrtSettingsForCapabilities(capabilities: Collection<String>): List<String> {
        val normalized = capabilities.map { it.lowercase(Locale.ROOT) }.toSet()
        return buildList {
            if (CapabilityRegistry.BACKGROUND_RUN in normalized || CapabilityRegistry.NOTIFICATIONS in normalized) add("foreground_service")
            if (CapabilityRegistry.NOTIFICATIONS in normalized) add("post_notifications")
            if (CapabilityRegistry.OVERLAY in normalized) add("display_over_other_apps")
            if (CapabilityRegistry.STORAGE in normalized) add("all_files_access")
            if (CapabilityRegistry.BOOT_COMPLETED in normalized) add("run_on_boot")
            if (CapabilityRegistry.ACCESSIBILITY in normalized) add("accessibility_service")
            if (CapabilityRegistry.SCREEN_CAPTURE in normalized) add("screen_capture_request")
        }.distinct()
    }

    @JvmStatic
    fun projectSummaryJson(config: ProjectConfig): JSONObject = JSONObject()
        .put("name", config.name)
        .put("packageName", config.packageName)
        .put("versionName", config.versionName)
        .put("versionCode", config.versionCode)
        .put("main", config.mainScriptFileName)
        .put("build", JSONObject()
            .put("buildId", config.buildInfo?.buildId ?: JSONObject.NULL)
            .put("buildNumber", config.buildInfo?.buildNumber ?: JSONObject.NULL)
            .put("buildTime", config.buildInfo?.buildTime ?: JSONObject.NULL))
        .put("launchConfig", JSONObject()
            .put("logsVisible", config.launchConfig?.isLogsVisible ?: JSONObject.NULL)
            .put("splashVisible", config.launchConfig?.isSplashVisible ?: JSONObject.NULL)
            .put("launcherVisible", config.launchConfig?.isLauncherVisible ?: JSONObject.NULL)
            .put("runOnBoot", config.launchConfig?.isRunOnBoot ?: JSONObject.NULL)
            .put("slug", config.launchConfig?.slug ?: JSONObject.NULL))
        .put("permissions", normalizeList(config.permissions).toJsonArray())
        .put("capabilities", normalizeList(config.capabilities).toJsonArray())
        .put("pluginDependencies", normalizeList(config.pluginDependencies).toJsonArray())

    private fun collectScriptFiles(source: File, config: ProjectConfig): List<File> {
        if (!source.exists()) return emptyList()
        if (source.isFile) return listOf(source).filter { it.isScriptFile() }
        val excluded = runCatching { config.excludedDirs.map { it.canonicalFile }.toSet() }.getOrDefault(emptySet())
        return source.walkTopDown()
            .onEnter { dir ->
                val canonical = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
                dir.name != ".git" && canonical !in excluded
            }
            .filter { it.isFile && it.isScriptFile() }
            .toList()
    }

    private fun File.isScriptFile(): Boolean = extension.lowercase(Locale.ROOT) in JS_EXTENSIONS

    private fun collectResourceReferences(scriptTexts: List<Pair<File, String>>, projectRoot: File?): List<String> {
        return scriptTexts.flatMap { (scriptFile, text) ->
            RESOURCE_REFERENCE_PATTERNS.flatMap { regex ->
                regex.findAll(text).mapNotNull { match ->
                    val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
                    if (raw.isBlank()) return@mapNotNull null
                    val base = projectRoot ?: scriptFile.parentFile ?: return@mapNotNull raw
                    runCatching {
                        val canonical = File(base, raw).canonicalFile
                        val root = (projectRoot ?: scriptFile.parentFile)?.canonicalFile
                        if (root != null && canonical.path.startsWith(root.path)) {
                            canonical.relativeTo(root).path
                        } else {
                            raw
                        }
                    }.getOrDefault(raw)
                }
            }
        }.distinct()
    }

    private fun collectPluginCalls(scriptTexts: List<Pair<File, String>>): List<String> {
        return scriptTexts.flatMap { (_, text) ->
            PLUGIN_CALL_PATTERNS.flatMap { regex ->
                regex.findAll(text).mapNotNull { match ->
                    match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                }
            }
        }.distinct()
    }

    private fun normalizeList(values: Collection<String>?): List<String> {
        return values.orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
    }
}

private fun Collection<*>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(it) }
    return arr
}
