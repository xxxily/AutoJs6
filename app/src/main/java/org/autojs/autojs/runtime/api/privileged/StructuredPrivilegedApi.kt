package org.autojs.autojs.runtime.api.privileged

import com.google.gson.GsonBuilder
import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.runtime.api.AbstractShell
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class PrivilegedBackend(val wireName: String) {
    SHIZUKU("shizuku"),
    ROOT("root"),
    SHELL("shell"),
    UNAVAILABLE("unavailable"),
}

enum class PrivilegedBackendPreference {
    AUTO,
    SHIZUKU,
    ROOT,
}

enum class PrivilegedRiskLevel(val wireName: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical"),
}

data class PrivilegedOptions(
    val backend: PrivilegedBackendPreference = PrivilegedBackendPreference.AUTO,
    val userId: Int? = null,
    val replace: Boolean = true,
    val keepData: Boolean = false,
)

data class StructuredPrivilegedRequest(
    val operation: String,
    val command: String,
    val target: String,
    val commandKind: String,
    val riskLevel: PrivilegedRiskLevel,
    val capabilities: List<String>,
    val allowShellFallback: Boolean = false,
    val preferredBackend: PrivilegedBackendPreference = PrivilegedBackendPreference.AUTO,
    val parser: ((AbstractShell.Result) -> Map<String, Any?>) = { emptyMap() },
)

data class PrivilegedExecutionResult(
    val ok: Boolean,
    val code: Int,
    val result: String,
    val error: String,
    val operation: String,
    val target: String,
    val backend: PrivilegedBackend,
    val riskLevel: String,
    val capabilities: List<String>,
    val auditId: Long,
    val data: Map<String, Any?> = emptyMap(),
)

data class PrivilegedAuditEntry(
    val id: Long,
    val timestamp: Long,
    val operation: String,
    val target: String,
    val backend: String,
    val riskLevel: String,
    val capabilities: List<String>,
    val ok: Boolean,
    val code: Int,
    val commandKind: String,
    val message: String,
)

data class PrivilegedOperationMetadata(
    val operation: String,
    val commandKind: String,
    val riskLevel: String,
    val capabilities: List<String>,
)

data class PrivilegedExecutionEnvironment(
    val isShizukuAvailable: () -> Boolean,
    val isRootAvailable: () -> Boolean,
    val runShizuku: (String) -> AbstractShell.Result,
    val runRoot: (String) -> AbstractShell.Result,
    val runShell: (String) -> AbstractShell.Result,
)

object PrivilegedAuditLog {

    private val nextId = AtomicLong(1L)
    private val entries = CopyOnWriteArrayList<PrivilegedAuditEntry>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun record(
        request: StructuredPrivilegedRequest,
        backend: PrivilegedBackend,
        shellResult: AbstractShell.Result,
        message: String = shellResult.error.orEmpty(),
    ): PrivilegedAuditEntry {
        val entry = PrivilegedAuditEntry(
            id = nextId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            operation = request.operation,
            target = request.target,
            backend = backend.wireName,
            riskLevel = request.riskLevel.wireName,
            capabilities = request.capabilities,
            ok = shellResult.code == 0,
            code = shellResult.code,
            commandKind = request.commandKind,
            message = message.take(500),
        )
        entries += entry
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        return entry
    }

    @JvmStatic
    fun snapshot(): List<PrivilegedAuditEntry> = entries.toList()

    @JvmStatic
    fun clear() {
        entries.clear()
    }

    @JvmStatic
    fun exportJson(): String = gson.toJson(snapshot())

    private const val MAX_ENTRIES = 500
}

object StructuredPrivilegedExecutor {

    fun execute(
        request: StructuredPrivilegedRequest,
        env: PrivilegedExecutionEnvironment,
    ): PrivilegedExecutionResult {
        val backend = selectBackend(request, env)
        val shellResult = if (backend == PrivilegedBackend.UNAVAILABLE) {
            AbstractShell.Result(
                1,
                "",
                "No available privileged backend for ${request.operation}; require Shizuku permission/service or Root.",
            )
        } else {
            runCatching {
                when (backend) {
                    PrivilegedBackend.SHIZUKU -> env.runShizuku(request.command)
                    PrivilegedBackend.ROOT -> env.runRoot(request.command)
                    PrivilegedBackend.SHELL -> env.runShell(request.command)
                    PrivilegedBackend.UNAVAILABLE -> error("Unavailable backend cannot execute")
                }
            }.getOrElse { e ->
                AbstractShell.Result(1, "", e.message.orEmpty())
            }
        }
        val audit = PrivilegedAuditLog.record(request, backend, shellResult)
        val parsed = if (shellResult.code == 0) {
            runCatching { request.parser(shellResult) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        return PrivilegedExecutionResult(
            ok = shellResult.code == 0,
            code = shellResult.code,
            result = shellResult.result.orEmpty(),
            error = shellResult.error.orEmpty(),
            operation = request.operation,
            target = request.target,
            backend = backend,
            riskLevel = request.riskLevel.wireName,
            capabilities = request.capabilities,
            auditId = audit.id,
            data = parsed,
        )
    }

    private fun selectBackend(
        request: StructuredPrivilegedRequest,
        env: PrivilegedExecutionEnvironment,
    ): PrivilegedBackend {
        val candidates = when (request.preferredBackend) {
            PrivilegedBackendPreference.SHIZUKU -> listOf(PrivilegedBackend.SHIZUKU, PrivilegedBackend.ROOT)
            PrivilegedBackendPreference.ROOT -> listOf(PrivilegedBackend.ROOT, PrivilegedBackend.SHIZUKU)
            PrivilegedBackendPreference.AUTO -> listOf(PrivilegedBackend.SHIZUKU, PrivilegedBackend.ROOT)
        }.let {
            if (request.allowShellFallback) it + PrivilegedBackend.SHELL else it
        }
        return candidates.firstOrNull { backend ->
            when (backend) {
                PrivilegedBackend.SHIZUKU -> env.isShizukuAvailable()
                PrivilegedBackend.ROOT -> env.isRootAvailable()
                PrivilegedBackend.SHELL -> true
                PrivilegedBackend.UNAVAILABLE -> false
            }
        } ?: PrivilegedBackend.UNAVAILABLE
    }
}

object StructuredPrivilegedCommands {

    val metadata: List<PrivilegedOperationMetadata>
        get() = listOf(
            metadata("app.forceStop", "am.force-stop", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("app.clearData", "pm.clear", PrivilegedRiskLevel.CRITICAL, baseCapabilities()),
            metadata("app.enableComponent", "pm.enable-component", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("app.disableComponent", "pm.disable-component", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("app.grantPermission", "pm.grant", PrivilegedRiskLevel.HIGH, baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS)),
            metadata("app.revokePermission", "pm.revoke", PrivilegedRiskLevel.HIGH, baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS)),
            metadata("app.install", "pm.install", PrivilegedRiskLevel.CRITICAL, baseCapabilities(CapabilityRegistry.INSTALL_APK)),
            metadata("app.uninstall", "pm.uninstall", PrivilegedRiskLevel.CRITICAL, baseCapabilities(CapabilityRegistry.UNINSTALL_APK)),
            metadata("settings.get", "settings.get", PrivilegedRiskLevel.MEDIUM, baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS)),
            metadata("settings.put", "settings.put", PrivilegedRiskLevel.HIGH, baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS)),
            metadata("settings.delete", "settings.delete", PrivilegedRiskLevel.HIGH, baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS)),
            metadata("package.info", "dumpsys.package", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("package.apkPath", "pm.path", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("package.permissionState", "cmd.package.check-permission", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("input.injectTap", "input.tap", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("input.injectSwipe", "input.swipe", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("input.keyEvent", "input.keyevent", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("process.list", "ps", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("process.kill", "kill", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("process.foreground", "dumpsys.activity", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("users.list", "pm.list-users", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("users.current", "am.current-user", PrivilegedRiskLevel.MEDIUM, baseCapabilities()),
            metadata("users.dualApps", "pm.multi-user-packages", PrivilegedRiskLevel.HIGH, baseCapabilities()),
            metadata("users.runAsUser", "structured.run-as-user", PrivilegedRiskLevel.HIGH, baseCapabilities()),
        )

    fun appForceStop(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "app.forceStop",
        commandKind = "am.force-stop",
        command = command("am", "force-stop", userArgs(options), packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun appClearData(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "app.clearData",
        commandKind = "pm.clear",
        command = command("pm", "clear", userArgs(options), packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.CRITICAL,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun appEnableComponent(componentName: String, options: PrivilegedOptions = PrivilegedOptions()) = componentState(
        operation = "app.enableComponent",
        commandKind = "pm.enable-component",
        subCommand = "enable",
        componentName = componentName,
        options = options,
    )

    fun appDisableComponent(componentName: String, options: PrivilegedOptions = PrivilegedOptions()) = componentState(
        operation = "app.disableComponent",
        commandKind = "pm.disable-component",
        subCommand = "disable",
        componentName = componentName,
        options = options,
    )

    fun appSetComponentEnabled(componentName: String, enabled: Boolean, options: PrivilegedOptions = PrivilegedOptions()) = when {
        enabled -> appEnableComponent(componentName, options)
        else -> appDisableComponent(componentName, options)
    }

    fun appGrantPermission(packageName: String, permission: String, options: PrivilegedOptions = PrivilegedOptions()) = permissionRequest(
        operation = "app.grantPermission",
        commandKind = "pm.grant",
        subCommand = "grant",
        packageName = packageName,
        permission = permission,
        options = options,
    )

    fun appRevokePermission(packageName: String, permission: String, options: PrivilegedOptions = PrivilegedOptions()) = permissionRequest(
        operation = "app.revokePermission",
        commandKind = "pm.revoke",
        subCommand = "revoke",
        packageName = packageName,
        permission = permission,
        options = options,
    )

    fun appInstall(apkPath: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "app.install",
        commandKind = "pm.install",
        command = command("pm", "install", installArgs(options), path(apkPath)),
        target = path(apkPath),
        riskLevel = PrivilegedRiskLevel.CRITICAL,
        capabilities = baseCapabilities(CapabilityRegistry.INSTALL_APK),
        options = options,
    )

    fun appUninstall(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "app.uninstall",
        commandKind = "pm.uninstall",
        command = command("pm", "uninstall", uninstallArgs(options), packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.CRITICAL,
        capabilities = baseCapabilities(CapabilityRegistry.UNINSTALL_APK),
        options = options,
    )

    fun settingsGet(namespace: String, key: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "settings.get",
        commandKind = "settings.get",
        command = command("settings", userArgs(options), "get", settingsNamespace(namespace), settingsKey(key)),
        target = "${settingsNamespace(namespace)}.${settingsKey(key)}",
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS),
        options = options,
        allowShellFallback = true,
        parser = { mapOf("value" to it.result.orEmpty().trim()) },
    )

    fun settingsPut(namespace: String, key: String, value: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "settings.put",
        commandKind = "settings.put",
        command = command("settings", userArgs(options), "put", settingsNamespace(namespace), settingsKey(key), value),
        target = "${settingsNamespace(namespace)}.${settingsKey(key)}",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS),
        options = options,
        parser = { mapOf("value" to value) },
    )

    fun settingsDelete(namespace: String, key: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "settings.delete",
        commandKind = "settings.delete",
        command = command("settings", userArgs(options), "delete", settingsNamespace(namespace), settingsKey(key)),
        target = "${settingsNamespace(namespace)}.${settingsKey(key)}",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS),
        options = options,
    )

    fun packageInfo(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "package.info",
        commandKind = "dumpsys.package",
        command = command("dumpsys", "package", packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        parser = { result ->
            mapOf(
                "packageName" to packageName(packageName),
                "firstLine" to result.result.orEmpty().lineSequence().firstOrNull().orEmpty(),
            )
        },
    )

    fun packageApkPath(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "package.apkPath",
        commandKind = "pm.path",
        command = command("pm", "path", userArgs(options), packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        allowShellFallback = true,
        parser = { result ->
            mapOf(
                "path" to result.result.orEmpty()
                    .lineSequence()
                    .firstOrNull { it.startsWith("package:") }
                    ?.removePrefix("package:")
                    .orEmpty()
            )
        },
    )

    fun packagePermissionState(packageName: String, permission: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "package.permissionState",
        commandKind = "cmd.package.check-permission",
        command = command(
            "cmd",
            "package",
            "check-permission",
            permissionName(permission),
            packageName(packageName),
            options.userId?.toString().orEmpty(),
        ),
        target = "${packageName(packageName)}:${permissionName(permission)}",
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        allowShellFallback = true,
        parser = { result ->
            val state = result.result.orEmpty().trim().lowercase(Locale.ROOT)
            mapOf("state" to state, "granted" to (state == "granted"))
        },
    )

    fun inputTap(x: Int, y: Int, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "input.injectTap",
        commandKind = "input.tap",
        command = command("input", "tap", coordinate(x), coordinate(y)),
        target = "$x,$y",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int? = null, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "input.injectSwipe",
        commandKind = "input.swipe",
        command = command("input", "swipe", coordinate(x1), coordinate(y1), coordinate(x2), coordinate(y2), duration?.let(::duration).orEmpty()),
        target = "$x1,$y1->$x2,$y2",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun inputKeyEvent(keyCode: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "input.keyEvent",
        commandKind = "input.keyevent",
        command = command("input", "keyevent", keyCode(keyCode)),
        target = keyCode(keyCode),
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun processList(options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "process.list",
        commandKind = "ps",
        command = "ps -A",
        target = "processes",
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        allowShellFallback = true,
    )

    fun processKill(pid: Int, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "process.kill",
        commandKind = "kill",
        command = command("kill", positiveInt(pid, "pid")),
        target = pid.toString(),
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun processKillPackage(packageName: String, options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "process.kill",
        commandKind = "am.force-stop",
        command = command("am", "force-stop", userArgs(options), packageName(packageName)),
        target = packageName(packageName),
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    fun processForeground(options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "process.foreground",
        commandKind = "dumpsys.activity",
        command = "dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity|topResumedActivity|Resumed:' | head -n 1",
        target = "foreground",
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        parser = { result ->
            val component = COMPONENT_PATTERN.find(result.result.orEmpty())?.value.orEmpty()
            mapOf(
                "component" to component,
                "packageName" to component.substringBefore("/", ""),
                "activity" to component.substringAfter("/", ""),
            )
        },
    )

    fun usersList(options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "users.list",
        commandKind = "pm.list-users",
        command = "pm list users",
        target = "users",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
        parser = { result ->
            mapOf("userIds" to USER_ID_PATTERN.findAll(result.result.orEmpty()).map { it.groupValues[1].toInt() }.toList())
        },
    )

    fun usersCurrent(options: PrivilegedOptions = PrivilegedOptions()) = request(
        operation = "users.current",
        commandKind = "am.current-user",
        command = "am get-current-user",
        target = "current-user",
        riskLevel = PrivilegedRiskLevel.MEDIUM,
        capabilities = baseCapabilities(),
        options = options,
        parser = { result ->
            mapOf("userId" to result.result.orEmpty().trim().toIntOrNull())
        },
    )

    fun usersDualApps(packageName: String? = null, options: PrivilegedOptions = PrivilegedOptions()): StructuredPrivilegedRequest {
        val packageFilter = packageName?.let { packageName(it) }
        val command = buildString {
            append("for u in $(pm list users | sed -n 's/.*UserInfo{\\([0-9][0-9]*\\):.*/\\1/p'); do ")
            append("echo \"# user \$u\"; ")
            append("pm list packages --user \"\$u\"")
            if (packageFilter != null) append(" ").append(shellQuote(packageFilter))
            append("; done")
        }
        return request(
            operation = "users.dualApps",
            commandKind = "pm.multi-user-packages",
            command = command,
            target = packageFilter ?: "all-packages",
            riskLevel = PrivilegedRiskLevel.HIGH,
            capabilities = baseCapabilities(),
            options = options,
        )
    }

    fun metadataFor(operation: String): PrivilegedOperationMetadata? = metadata.firstOrNull { it.operation == operation }

    internal fun shellQuote(value: String): String {
        require(value.isNotEmpty()) { "Shell argument cannot be empty" }
        if (SAFE_SHELL_TOKEN.matches(value)) return value
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    private fun componentState(
        operation: String,
        commandKind: String,
        subCommand: String,
        componentName: String,
        options: PrivilegedOptions,
    ) = request(
        operation = operation,
        commandKind = commandKind,
        command = command("pm", subCommand, userArgs(options), componentName(componentName)),
        target = componentName(componentName),
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(),
        options = options,
    )

    private fun permissionRequest(
        operation: String,
        commandKind: String,
        subCommand: String,
        packageName: String,
        permission: String,
        options: PrivilegedOptions,
    ) = request(
        operation = operation,
        commandKind = commandKind,
        command = command("pm", subCommand, userArgs(options), packageName(packageName), permissionName(permission)),
        target = "${packageName(packageName)}:${permissionName(permission)}",
        riskLevel = PrivilegedRiskLevel.HIGH,
        capabilities = baseCapabilities(CapabilityRegistry.WRITE_SECURE_SETTINGS),
        options = options,
    )

    private fun request(
        operation: String,
        command: String,
        target: String,
        commandKind: String,
        riskLevel: PrivilegedRiskLevel,
        capabilities: List<String>,
        options: PrivilegedOptions,
        allowShellFallback: Boolean = false,
        parser: ((AbstractShell.Result) -> Map<String, Any?>) = { emptyMap() },
    ) = StructuredPrivilegedRequest(
        operation = operation,
        command = command,
        target = target,
        commandKind = commandKind,
        riskLevel = riskLevel,
        capabilities = capabilities.distinct(),
        preferredBackend = options.backend,
        allowShellFallback = allowShellFallback,
        parser = parser,
    )

    private fun command(vararg parts: Any?): String = parts
        .flatMap { part ->
            when (part) {
                null -> emptyList()
                is Iterable<*> -> part.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                else -> listOf(part.toString()).filter(String::isNotBlank)
            }
        }
        .joinToString(" ") { shellQuote(it) }

    private fun userArgs(options: PrivilegedOptions): List<String> = options.userId?.let {
        listOf("--user", userId(it).toString())
    }.orEmpty()

    private fun installArgs(options: PrivilegedOptions): List<String> = buildList {
        if (options.replace) add("-r")
        options.userId?.let {
            add("--user")
            add(userId(it).toString())
        }
    }

    private fun uninstallArgs(options: PrivilegedOptions): List<String> = buildList {
        if (options.keepData) add("-k")
        options.userId?.let {
            add("--user")
            add(userId(it).toString())
        }
    }

    private fun metadata(
        operation: String,
        commandKind: String,
        riskLevel: PrivilegedRiskLevel,
        capabilities: List<String>,
    ) = PrivilegedOperationMetadata(operation, commandKind, riskLevel.wireName, capabilities.distinct())

    private fun baseCapabilities(vararg extra: String): List<String> = listOf(
        CapabilityRegistry.SHIZUKU,
        CapabilityRegistry.ROOT,
    ) + extra

    private fun packageName(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Package name cannot be empty" }
        require(!trimmed.startsWith("-")) { "Package name cannot start with '-'" }
        require(SAFE_ANDROID_NAME.matches(trimmed)) { "Invalid package name: $value" }
        return trimmed
    }

    private fun componentName(value: String): String {
        val trimmed = value.trim()
        require(trimmed.contains("/")) { "Component name must use package/class format" }
        require(SAFE_COMPONENT_NAME.matches(trimmed)) { "Invalid component name: $value" }
        return trimmed
    }

    private fun permissionName(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Permission name cannot be empty" }
        require(SAFE_ANDROID_NAME.matches(trimmed)) { "Invalid permission name: $value" }
        return trimmed
    }

    private fun settingsNamespace(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT)
        require(normalized in SETTINGS_NAMESPACES) { "Settings namespace must be one of: ${SETTINGS_NAMESPACES.joinToString()}" }
        return normalized
    }

    private fun settingsKey(value: String): String {
        val trimmed = value.trim()
        require(SAFE_SETTINGS_KEY.matches(trimmed)) { "Invalid settings key: $value" }
        return trimmed
    }

    private fun path(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Path cannot be empty" }
        require(!trimmed.contains('\u0000')) { "Path cannot contain NUL" }
        return trimmed
    }

    private fun coordinate(value: Int): String {
        require(value >= 0) { "Coordinate cannot be negative: $value" }
        return value.toString()
    }

    private fun duration(value: Int): String {
        require(value >= 0) { "Duration cannot be negative: $value" }
        return value.toString()
    }

    private fun positiveInt(value: Int, name: String): String {
        require(value > 0) { "$name must be positive: $value" }
        return value.toString()
    }

    private fun userId(value: Int): Int {
        require(value >= 0) { "userId cannot be negative: $value" }
        return value
    }

    private fun keyCode(value: String): String {
        val trimmed = value.trim().uppercase(Locale.ROOT)
        require(SAFE_KEY_CODE.matches(trimmed)) { "Invalid key code: $value" }
        return trimmed
    }

    private val SETTINGS_NAMESPACES = setOf("system", "secure", "global")
    private val SAFE_SHELL_TOKEN = Regex("""[A-Za-z0-9_@%+=:,./-]+""")
    private val SAFE_ANDROID_NAME = Regex("""[A-Za-z0-9_.$]+""")
    private val SAFE_COMPONENT_NAME = Regex("""[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+""")
    private val SAFE_SETTINGS_KEY = Regex("""[A-Za-z0-9_.:-]+""")
    private val SAFE_KEY_CODE = Regex("""[A-Z0-9_]+""")
    private val USER_ID_PATTERN = Regex("""UserInfo\{(\d+):""")
    private val COMPONENT_PATTERN = Regex("""[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+""")
}
