package org.autojs.autojs.runtime.api.augment.shizuku

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.capability.ProjectCapabilitySecurity
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.privileged.PrivilegedAuditEntry
import org.autojs.autojs.runtime.api.privileged.PrivilegedAuditLog
import org.autojs.autojs.runtime.api.privileged.PrivilegedBackendPreference
import org.autojs.autojs.runtime.api.privileged.PrivilegedExecutionEnvironment
import org.autojs.autojs.runtime.api.privileged.PrivilegedExecutionResult
import org.autojs.autojs.runtime.api.privileged.PrivilegedOperationMetadata
import org.autojs.autojs.runtime.api.privileged.PrivilegedOptions
import org.autojs.autojs.runtime.api.privileged.StructuredPrivilegedCommands
import org.autojs.autojs.runtime.api.privileged.StructuredPrivilegedExecutor
import org.autojs.autojs.runtime.api.privileged.StructuredPrivilegedRequest
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RootUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import java.util.function.Supplier

@Suppress("unused", "UNUSED_PARAMETER")
class Shizuku(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::execCommand.name,
        ::getCommand.name,
        ::kill.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "state" to Supplier {
            newNativeObject().also { o ->
                o.defineProp("isInstalled", WrappedShizuku.isInstalled(globalContext))
                o.defineProp("hasService", WrappedShizuku.hasService())
                o.defineProp("isRunning", WrappedShizuku.isRunning())
                o.defineProp("hasPermission", WrappedShizuku.hasPermission())
                o.defineProp("isOperational", WrappedShizuku.isOperational())
            }
        },
        "app" to Supplier { buildAppObject() },
        "settings" to Supplier { buildSettingsObject() },
        "package" to Supplier { buildPackageObject() },
        "input" to Supplier { buildInputObject() },
        "process" to Supplier { buildProcessObject() },
        "users" to Supplier { buildUsersObject() },
        "audit" to Supplier { buildAuditObject() },
        "operations" to Supplier { metadataArray() },
    )

    override fun invoke(vararg args: Any?): AbstractShell.Result = execCommand(scriptRuntime, args)

    private fun buildAppObject() = nativeObject(
        "forceStop" to fn("forceStop") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.appForceStop(target, options))
        },
        "clearData" to fn("clearData") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.appClearData(target, options))
        },
        "enableComponent" to fn("enableComponent") { args ->
            requireArgRange("shizuku.app.enableComponent", args, 1, 2)
            execute(StructuredPrivilegedCommands.appEnableComponent(coerceString(args[0]), optionsFrom(args.getOrNull(1))))
        },
        "disableComponent" to fn("disableComponent") { args ->
            requireArgRange("shizuku.app.disableComponent", args, 1, 2)
            execute(StructuredPrivilegedCommands.appDisableComponent(coerceString(args[0]), optionsFrom(args.getOrNull(1))))
        },
        "setComponentEnabled" to fn("setComponentEnabled") { args ->
            requireArgRange("shizuku.app.setComponentEnabled", args, 2, 3)
            execute(StructuredPrivilegedCommands.appSetComponentEnabled(coerceString(args[0]), coerceBoolean(args[1], false), optionsFrom(args.getOrNull(2))))
        },
        "grantPermission" to fn("grantPermission") { args ->
            requireArgRange("shizuku.app.grantPermission", args, 2, 3)
            execute(StructuredPrivilegedCommands.appGrantPermission(packageArg(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
        },
        "revokePermission" to fn("revokePermission") { args ->
            requireArgRange("shizuku.app.revokePermission", args, 2, 3)
            execute(StructuredPrivilegedCommands.appRevokePermission(packageArg(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
        },
        "install" to fn("install") { args ->
            requireArgRange("shizuku.app.install", args, 1, 2)
            execute(StructuredPrivilegedCommands.appInstall(coerceString(args[0]), optionsFrom(args.getOrNull(1))))
        },
        "uninstall" to fn("uninstall") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.appUninstall(target, options))
        },
    )

    private fun buildSettingsObject(): NativeObject {
        val settings = nativeObject(
            "get" to fn("get") { args ->
                requireArgRange("shizuku.settings.get", args, 2, 3)
                execute(StructuredPrivilegedCommands.settingsGet(coerceString(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
            },
            "put" to fn("put") { args ->
                requireArgRange("shizuku.settings.put", args, 3, 4)
                execute(StructuredPrivilegedCommands.settingsPut(coerceString(args[0]), coerceString(args[1]), coerceString(args[2]), optionsFrom(args.getOrNull(3))))
            },
            "delete" to fn("delete") { args ->
                requireArgRange("shizuku.settings.delete", args, 2, 3)
                execute(StructuredPrivilegedCommands.settingsDelete(coerceString(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
            },
        )
        listOf("secure", "global", "system").forEach { namespace ->
            settings.defineProp(namespace, nativeObject(
                "get" to fn("$namespace.get") { args ->
                    requireArgRange("shizuku.settings.$namespace.get", args, 1, 2)
                    execute(StructuredPrivilegedCommands.settingsGet(namespace, coerceString(args[0]), optionsFrom(args.getOrNull(1))))
                },
                "put" to fn("$namespace.put") { args ->
                    requireArgRange("shizuku.settings.$namespace.put", args, 2, 3)
                    execute(StructuredPrivilegedCommands.settingsPut(namespace, coerceString(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
                },
                "delete" to fn("$namespace.delete") { args ->
                    requireArgRange("shizuku.settings.$namespace.delete", args, 1, 2)
                    execute(StructuredPrivilegedCommands.settingsDelete(namespace, coerceString(args[0]), optionsFrom(args.getOrNull(1))))
                },
            ))
        }
        return settings
    }

    private fun buildPackageObject() = nativeObject(
        "info" to fn("info") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.packageInfo(target, options))
        },
        "packageInfo" to fn("packageInfo") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.packageInfo(target, options))
        },
        "apkPath" to fn("apkPath") { args ->
            val (target, options) = packageAndOptions(args, 0, 1)
            execute(StructuredPrivilegedCommands.packageApkPath(target, options))
        },
        "permissionState" to fn("permissionState") { args ->
            requireArgRange("shizuku.package.permissionState", args, 2, 3)
            execute(StructuredPrivilegedCommands.packagePermissionState(packageArg(args[0]), coerceString(args[1]), optionsFrom(args.getOrNull(2))))
        },
    )

    private fun buildInputObject() = nativeObject(
        "injectTap" to fn("injectTap") { args ->
            requireArgRange("shizuku.input.injectTap", args, 2, 3)
            execute(StructuredPrivilegedCommands.inputTap(coerceIntNumber(args[0]), coerceIntNumber(args[1]), optionsFrom(args.getOrNull(2))))
        },
        "tap" to fn("tap") { args ->
            requireArgRange("shizuku.input.tap", args, 2, 3)
            execute(StructuredPrivilegedCommands.inputTap(coerceIntNumber(args[0]), coerceIntNumber(args[1]), optionsFrom(args.getOrNull(2))))
        },
        "injectSwipe" to fn("injectSwipe") { args ->
            requireArgRange("shizuku.input.injectSwipe", args, 4, 6)
            val duration = args.getOrNull(4).takeUnless { it is NativeObject || it.isJsNullish() }?.let { coerceIntNumber(it) }
            val optionsIndex = if (duration == null) 4 else 5
            execute(StructuredPrivilegedCommands.inputSwipe(coerceIntNumber(args[0]), coerceIntNumber(args[1]), coerceIntNumber(args[2]), coerceIntNumber(args[3]), duration, optionsFrom(args.getOrNull(optionsIndex))))
        },
        "swipe" to fn("swipe") { args ->
            requireArgRange("shizuku.input.swipe", args, 4, 6)
            val duration = args.getOrNull(4).takeUnless { it is NativeObject || it.isJsNullish() }?.let { coerceIntNumber(it) }
            val optionsIndex = if (duration == null) 4 else 5
            execute(StructuredPrivilegedCommands.inputSwipe(coerceIntNumber(args[0]), coerceIntNumber(args[1]), coerceIntNumber(args[2]), coerceIntNumber(args[3]), duration, optionsFrom(args.getOrNull(optionsIndex))))
        },
        "keyEvent" to fn("keyEvent") { args ->
            requireArgRange("shizuku.input.keyEvent", args, 1, 2)
            execute(StructuredPrivilegedCommands.inputKeyEvent(coerceString(args[0]), optionsFrom(args.getOrNull(1))))
        },
    )

    private fun buildProcessObject() = nativeObject(
        "list" to fn("list") { args ->
            requireArgRange("shizuku.process.list", args, 0, 1)
            execute(StructuredPrivilegedCommands.processList(optionsFrom(args.getOrNull(0))))
        },
        "kill" to fn("kill") { args ->
            requireArgRange("shizuku.process.kill", args, 1, 2)
            val options = optionsFrom(args.getOrNull(1))
            val request = when (val target = args[0]) {
                is Number -> StructuredPrivilegedCommands.processKill(target.toInt(), options)
                else -> StructuredPrivilegedCommands.processKillPackage(packageArg(target), options)
            }
            execute(request)
        },
        "foreground" to fn("foreground") { args ->
            requireArgRange("shizuku.process.foreground", args, 0, 1)
            execute(StructuredPrivilegedCommands.processForeground(optionsFrom(args.getOrNull(0))))
        },
        "queryForeground" to fn("queryForeground") { args ->
            requireArgRange("shizuku.process.queryForeground", args, 0, 1)
            execute(StructuredPrivilegedCommands.processForeground(optionsFrom(args.getOrNull(0))))
        },
    )

    private fun buildUsersObject() = nativeObject(
        "list" to fn("list") { args ->
            requireArgRange("shizuku.users.list", args, 0, 1)
            execute(StructuredPrivilegedCommands.usersList(optionsFrom(args.getOrNull(0))))
        },
        "listUsers" to fn("listUsers") { args ->
            requireArgRange("shizuku.users.listUsers", args, 0, 1)
            execute(StructuredPrivilegedCommands.usersList(optionsFrom(args.getOrNull(0))))
        },
        "current" to fn("current") { args ->
            requireArgRange("shizuku.users.current", args, 0, 1)
            execute(StructuredPrivilegedCommands.usersCurrent(optionsFrom(args.getOrNull(0))))
        },
        "currentUser" to fn("currentUser") { args ->
            requireArgRange("shizuku.users.currentUser", args, 0, 1)
            execute(StructuredPrivilegedCommands.usersCurrent(optionsFrom(args.getOrNull(0))))
        },
        "dualApps" to fn("dualApps") { args ->
            requireArgRange("shizuku.users.dualApps", args, 0, 2)
            val packageName = args.getOrNull(0).takeUnless { it is NativeObject || it.isJsNullish() }?.let(::packageArg)
            val optionsIndex = if (packageName == null) 0 else 1
            execute(StructuredPrivilegedCommands.usersDualApps(packageName, optionsFrom(args.getOrNull(optionsIndex))))
        },
        "runAsUser" to fn("runAsUser") { args ->
            executeRunAsUser(args)
        },
    )

    private fun buildAuditObject() = nativeObject(
        "list" to fn("list") { args ->
            requireArgRange("shizuku.audit.list", args, 0, 0)
            auditArray(PrivilegedAuditLog.snapshot())
        },
        "clear" to fn("clear") { args ->
            requireArgRange("shizuku.audit.clear", args, 0, 0)
            PrivilegedAuditLog.clear()
            true
        },
        "exportJson" to fn("exportJson") { args ->
            requireArgRange("shizuku.audit.exportJson", args, 0, 0)
            PrivilegedAuditLog.exportJson()
        },
        "export" to fn("export") { args ->
            requireArgRange("shizuku.audit.export", args, 0, 0)
            PrivilegedAuditLog.exportJson()
        },
        "metadata" to fn("metadata") { args ->
            requireArgRange("shizuku.audit.metadata", args, 0, 0)
            metadataArray()
        },
    )

    private fun executeRunAsUser(args: Array<Any?>): NativeObject {
        requireArgRange("shizuku.users.runAsUser", args, 3, 6)
        val userId = coerceIntNumber(args[0])
        val operation = coerceString(args[1]).replace(Regex("[-_]"), "").lowercase()
        val options = optionsFrom(args.lastOrNull().takeIf { it is NativeObject }, userId)
        val request = when (operation) {
            "forcestop" -> StructuredPrivilegedCommands.appForceStop(packageArg(args[2]), options)
            "cleardata" -> StructuredPrivilegedCommands.appClearData(packageArg(args[2]), options)
            "grantpermission" -> {
                requireArgRange("shizuku.users.runAsUser(grantPermission)", args, 4, 5)
                StructuredPrivilegedCommands.appGrantPermission(packageArg(args[2]), coerceString(args[3]), options)
            }
            "revokepermission" -> {
                requireArgRange("shizuku.users.runAsUser(revokePermission)", args, 4, 5)
                StructuredPrivilegedCommands.appRevokePermission(packageArg(args[2]), coerceString(args[3]), options)
            }
            "uninstall" -> StructuredPrivilegedCommands.appUninstall(packageArg(args[2]), options)
            "packageinfo" -> StructuredPrivilegedCommands.packageInfo(packageArg(args[2]), options)
            "apkpath" -> StructuredPrivilegedCommands.packageApkPath(packageArg(args[2]), options)
            "permissionstate" -> {
                requireArgRange("shizuku.users.runAsUser(permissionState)", args, 4, 5)
                StructuredPrivilegedCommands.packagePermissionState(packageArg(args[2]), coerceString(args[3]), options)
            }
            "settingsget" -> {
                requireArgRange("shizuku.users.runAsUser(settingsGet)", args, 4, 5)
                StructuredPrivilegedCommands.settingsGet(coerceString(args[2]), coerceString(args[3]), options)
            }
            "settingsput" -> {
                requireArgRange("shizuku.users.runAsUser(settingsPut)", args, 5, 6)
                StructuredPrivilegedCommands.settingsPut(coerceString(args[2]), coerceString(args[3]), coerceString(args[4]), options)
            }
            "settingsdelete" -> {
                requireArgRange("shizuku.users.runAsUser(settingsDelete)", args, 4, 5)
                StructuredPrivilegedCommands.settingsDelete(coerceString(args[2]), coerceString(args[3]), options)
            }
            else -> throw WrappedIllegalArgumentException("Unsupported runAsUser operation: ${args[1]}")
        }
        return execute(request)
    }

    private fun execute(request: StructuredPrivilegedRequest): NativeObject {
        ProjectCapabilitySecurity.guard(
            scriptRuntime = scriptRuntime,
            api = "shizuku.${request.operation}",
            capabilities = request.guardCapabilities(),
            riskLevel = request.riskLevel.wireName,
            target = request.target,
        )
        return StructuredPrivilegedExecutor.execute(
            request = request,
            env = PrivilegedExecutionEnvironment(
                isShizukuAvailable = { WrappedShizuku.isOperational() },
                isRootAvailable = { RootUtils.isRootAvailable() },
                runShizuku = { WrappedShizuku.execCommand(globalContext, it) },
                runRoot = { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, true)) },
                runShell = { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, false)) },
            ),
        ).also { result ->
            ProjectCapabilitySecurity.audit(
                scriptRuntime = scriptRuntime,
                api = "shizuku.${request.operation}",
                capabilities = request.guardCapabilities(),
                riskLevel = request.riskLevel.wireName,
                target = request.target,
                message = "backend=${result.backend.wireName}; code=${result.code}; error=${result.error.take(120)}",
            )
        }.toNativeObject()
    }

    private fun fn(name: String, block: (Array<Any?>) -> Any?) = newBaseFunction(name, { rawArgs ->
        block(rawArgs.map { RhinoUtils.unwrap(it) }.toTypedArray())
    }, NOT_CONSTRUCTABLE)

    private fun nativeObject(vararg entries: Pair<String, Any?>) = newNativeObject().also { obj ->
        entries.forEach { (key, value) -> obj.defineProp(key, value) }
    }

    private fun packageAndOptions(args: Array<Any?>, packageIndex: Int, optionsIndex: Int): Pair<String, PrivilegedOptions> {
        requireArgRange("shizuku structured package API", args, packageIndex + 1, optionsIndex + 1)
        return packageArg(args[packageIndex]) to optionsFrom(args.getOrNull(optionsIndex))
    }

    private fun packageArg(arg: Any?): String {
        return App.getPackageName(scriptRuntime, arrayOf(arg)) ?: Context.toString(arg)
    }

    private fun optionsFrom(arg: Any?, forcedUserId: Int? = null): PrivilegedOptions {
        val obj = arg as? NativeObject ?: return PrivilegedOptions(userId = forcedUserId)
        val backend = backendPreference(obj)
        val userId = forcedUserId ?: firstProp(obj, "userId", "user")?.takeUnless { it.isJsNullish() }?.let { coerceIntNumber(it) }
        val replace = firstProp(obj, "replace")?.takeUnless { it.isJsNullish() }?.let { coerceBoolean(it, true) } ?: true
        val keepData = firstProp(obj, "keepData", "keep")?.takeUnless { it.isJsNullish() }?.let { coerceBoolean(it, false) } ?: false
        return PrivilegedOptions(backend = backend, userId = userId, replace = replace, keepData = keepData)
    }

    private fun backendPreference(obj: NativeObject): PrivilegedBackendPreference {
        firstProp(obj, "root")?.takeUnless { it.isJsNullish() }?.let {
            if (coerceBoolean(it, false)) return PrivilegedBackendPreference.ROOT
        }
        firstProp(obj, "shizuku")?.takeUnless { it.isJsNullish() }?.let {
            if (coerceBoolean(it, false)) return PrivilegedBackendPreference.SHIZUKU
        }
        val raw = firstProp(obj, "backend", "by")?.takeUnless { it.isJsNullish() } ?: return PrivilegedBackendPreference.AUTO
        return when (coerceString(raw).lowercase()) {
            "shizuku" -> PrivilegedBackendPreference.SHIZUKU
            "root", "su" -> PrivilegedBackendPreference.ROOT
            "auto", "" -> PrivilegedBackendPreference.AUTO
            else -> throw WrappedIllegalArgumentException("backend must be auto, shizuku or root")
        }
    }

    private fun firstProp(obj: NativeObject, vararg keys: String): Any? {
        return keys.firstNotNullOfOrNull { key ->
            obj.prop(key).takeUnless { it.isJsNullish() }
        }
    }

    private fun requireArgRange(name: String, args: Array<Any?>, min: Int, max: Int) {
        if (args.size !in min..max) {
            val expected = if (min == max) min.toString() else "$min..$max"
            throw WrappedIllegalArgumentException("$name expects $expected arguments, got ${args.size}")
        }
    }

    private fun PrivilegedExecutionResult.toNativeObject() = newNativeObject().also { obj ->
        obj.defineProp("ok", ok)
        obj.defineProp("code", code)
        obj.defineProp("result", result)
        obj.defineProp("error", error)
        obj.defineProp("operation", operation)
        obj.defineProp("target", target)
        obj.defineProp("backend", backend.wireName)
        obj.defineProp("riskLevel", riskLevel)
        obj.defineProp("capabilities", nativeArray(capabilities))
        obj.defineProp("auditId", auditId)
        obj.defineProp("data", nativeMap(data))
    }

    private fun metadataArray() = newNativeArray(
        StructuredPrivilegedCommands.metadata.map { it.toNativeObject() }.toTypedArray()
    )

    private fun PrivilegedOperationMetadata.toNativeObject() = newNativeObject().also { obj ->
        obj.defineProp("operation", operation)
        obj.defineProp("commandKind", commandKind)
        obj.defineProp("riskLevel", riskLevel)
        obj.defineProp("capabilities", nativeArray(capabilities))
    }

    private fun auditArray(entries: List<PrivilegedAuditEntry>) = newNativeArray(
        entries.map { it.toNativeObject() }.toTypedArray()
    )

    private fun PrivilegedAuditEntry.toNativeObject() = newNativeObject().also { obj ->
        obj.defineProp("id", id)
        obj.defineProp("timestamp", timestamp)
        obj.defineProp("operation", operation)
        obj.defineProp("target", target)
        obj.defineProp("backend", backend)
        obj.defineProp("riskLevel", riskLevel)
        obj.defineProp("capabilities", nativeArray(capabilities))
        obj.defineProp("ok", ok)
        obj.defineProp("code", code)
        obj.defineProp("commandKind", commandKind)
        obj.defineProp("message", message)
    }

    private fun nativeArray(values: Iterable<Any?>) = newNativeArray(values.toList().toTypedArray())

    private fun nativeMap(values: Map<String, Any?>): NativeObject = newNativeObject().also { obj ->
        values.forEach { (key, value) ->
            obj.defineProp(key, when (value) {
                is Iterable<*> -> nativeArray(value)
                is Map<*, *> -> nativeMap(value.entries.associate { "${it.key}" to it.value })
                else -> value
            })
        }
    }

    private fun StructuredPrivilegedRequest.guardCapabilities(): List<String> {
        val base = when (preferredBackend) {
            PrivilegedBackendPreference.ROOT -> capabilities
            else -> capabilities.filterNot { it == CapabilityRegistry.ROOT }
        }
        return base.ifEmpty { listOf(CapabilityRegistry.SHIZUKU) }
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AbstractShell.Result = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val command = Shell.getCommandData(argList).command
            ProjectCapabilitySecurity.guard(
                scriptRuntime = scriptRuntime,
                api = "shizuku.execCommand",
                capabilities = listOf(CapabilityRegistry.SHIZUKU),
                riskLevel = "high",
                target = command,
            )
            scriptRuntime.shizuku.execCommand(command).also { result ->
                ProjectCapabilitySecurity.audit(
                    scriptRuntime = scriptRuntime,
                    api = "shizuku.execCommand",
                    capabilities = listOf(CapabilityRegistry.SHIZUKU),
                    riskLevel = "high",
                    target = command,
                    message = "code=${result.code}; error=${result.error.orEmpty().take(120)}",
                )
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            Shell.getCommandData(argList).command
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun kill(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { app ->
            when (val packageName = App.getPackageName(scriptRuntime, arrayOf(app))) {
                null -> false
                else -> {
                    val request = StructuredPrivilegedCommands.appForceStop(packageName)
                    ProjectCapabilitySecurity.guard(
                        scriptRuntime = scriptRuntime,
                        api = "shizuku.${request.operation}",
                        capabilities = request.capabilities.filterNot { it == CapabilityRegistry.ROOT },
                        riskLevel = request.riskLevel.wireName,
                        target = request.target,
                    )
                    StructuredPrivilegedExecutor.execute(
                        request = request,
                        env = PrivilegedExecutionEnvironment(
                            isShizukuAvailable = { WrappedShizuku.isOperational() },
                            isRootAvailable = { RootUtils.isRootAvailable() },
                            runShizuku = { WrappedShizuku.execCommand(it) },
                            runRoot = { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, true)) },
                            runShell = { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, false)) },
                        ),
                    ).also { result ->
                        ProjectCapabilitySecurity.audit(
                            scriptRuntime = scriptRuntime,
                            api = "shizuku.${request.operation}",
                            capabilities = request.capabilities.filterNot { it == CapabilityRegistry.ROOT },
                            riskLevel = request.riskLevel.wireName,
                            target = request.target,
                            message = "backend=${result.backend.wireName}; code=${result.code}; error=${result.error.take(120)}",
                        )
                    }.ok
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentPackage() ?: ""
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentActivity() ?: ""
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentComponent() ?: ""
            }
        }

    }

}
