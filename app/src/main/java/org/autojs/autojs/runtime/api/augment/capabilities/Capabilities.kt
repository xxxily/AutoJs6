package org.autojs.autojs.runtime.api.augment.capabilities

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.capability.CapabilityCheck
import org.autojs.autojs.capability.CapabilityDefinition
import org.autojs.autojs.capability.ProjectCapabilityAction
import org.autojs.autojs.capability.ProjectCapabilityAuditEntry
import org.autojs.autojs.capability.ProjectCapabilityAuditLog
import org.autojs.autojs.capability.ProjectCapabilitySecurity
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

@Suppress("unused", "UNUSED_PARAMETER")
class Capabilities(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::check.name,
        ::ensure.name,
        ::explain.name,
        ::scan.name,
        ::list.name,
        ::manifest.name,
        ::remember.name,
        ::audit.name,
        ::clearAudit.name,
    )

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun check(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            scriptRuntime.capabilities.check(parseIds(argList.firstOrNull()))
                .map { it.toCheckObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensure(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 2) { argList ->
            val (ids, options) = argList
            val request = optionBoolean(options, "request") ?: false
            val timeout = optionLong(options, "timeout") ?: optionLong(options, "timeoutMillis") ?: 0L
            scriptRuntime.capabilities.ensure(parseIds(ids), request, timeout)
                .map { it.toCheckObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun explain(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) {
            val target = coerceString(it)
            val definition = scriptRuntime.capabilities.explain(target)
            definition?.toDefinitionObject() ?: newNativeObject().also { obj ->
                obj.defineProp("id", target)
                obj.defineProp("status", "unsupported")
                obj.defineProp("description", "Unknown capability or API: $target")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scan(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            scriptRuntime.capabilities.scan(coerceString(it))
                .map { it.toDefinitionObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun list(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            org.autojs.autojs.capability.CapabilityRegistry.allDefinitions()
                .map { it.toDefinitionObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun manifest(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsIsEmpty(args) {
            val config = ProjectCapabilitySecurity.projectConfigForRuntime(scriptRuntime)
            newNativeObject().also { obj ->
                obj.defineProp("available", config != null)
                obj.defineProp("name", config?.name.orEmpty())
                obj.defineProp("packageName", config?.packageName.orEmpty())
                obj.defineProp("sourcePath", config?.sourcePath.orEmpty())
                obj.defineProp("capabilities", config?.capabilities.orEmpty().toNativeArray())
                obj.defineProp("riskPolicy", newNativeObject().also { risk ->
                    risk.defineProp("default", config?.riskPolicy?.defaultAction.orEmpty())
                    risk.defineProp("undeclared", config?.riskPolicy?.undeclaredAction.orEmpty())
                    risk.defineProp("high", config?.riskPolicy?.highRiskAction.orEmpty())
                    risk.defineProp("critical", config?.riskPolicy?.criticalRiskAction.orEmpty())
                    risk.defineProp("rememberAllowed", config?.riskPolicy?.rememberAllowed ?: false)
                })
                obj.defineProp("networkPolicy", newNativeObject().also { network ->
                    network.defineProp("allowedDomains", config?.networkPolicy?.allowedDomains.orEmpty().toNativeArray())
                    network.defineProp("blockedDomains", config?.networkPolicy?.blockedDomains.orEmpty().toNativeArray())
                    network.defineProp("allowCleartext", config?.networkPolicy?.allowCleartext ?: true)
                })
                obj.defineProp("filePolicy", newNativeObject().also { file ->
                    file.defineProp("allowedPaths", config?.filePolicy?.allowedPaths.orEmpty().toNativeArray())
                    file.defineProp("writablePaths", config?.filePolicy?.writablePaths.orEmpty().toNativeArray())
                    file.defineProp("deletablePaths", config?.filePolicy?.deletablePaths.orEmpty().toNativeArray())
                })
                obj.defineProp("privilegedPolicy", newNativeObject().also { privileged ->
                    privileged.defineProp("root", config?.privilegedPolicy?.root)
                    privileged.defineProp("shizuku", config?.privilegedPolicy?.shizuku)
                    privileged.defineProp("shell", config?.privilegedPolicy?.shell)
                    privileged.defineProp("backend", config?.privilegedPolicy?.backend.orEmpty())
                    privileged.defineProp("operations", config?.privilegedPolicy?.operations.orEmpty().toNativeArray())
                })
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun remember(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (ids, actionRaw) = argList
            val action = actionRaw
                .takeUnless { it.isJsNullish() }
                ?.let { ProjectCapabilitySecurity.parseAction(coerceString(it), ProjectCapabilityAction.ALLOW) }
                ?: ProjectCapabilityAction.ALLOW
            ProjectCapabilitySecurity.remember(scriptRuntime, parseIds(ids), action)
            true
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun audit(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            ProjectCapabilityAuditLog.snapshot()
                .map { it.toAuditObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearAudit(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            ProjectCapabilityAuditLog.clear()
            true
        }

        private fun parseIds(arg: Any?): List<String> {
            if (arg.isJsNullish()) return emptyList()
            return when (arg) {
                is NativeArray -> (0 until arg.length.toInt()).mapNotNull { index ->
                    arg.get(index, arg).takeUnless { it.isJsNullish() }?.let(::coerceString)
                }
                is Iterable<*> -> arg.mapNotNull { it?.let(::coerceString) }
                is Array<*> -> arg.mapNotNull { it?.let(::coerceString) }
                else -> listOf(coerceString(arg))
            }
        }

        private fun optionBoolean(options: Any?, key: String): Boolean? {
            if (options !is Scriptable) return null
            val value = options.prop(key)
            return if (value.isJsNullish()) null else coerceBoolean(value!!)
        }

        private fun optionLong(options: Any?, key: String): Long? {
            if (options !is Scriptable) return null
            val value = options.prop(key)
            return if (value.isJsNullish()) null else coerceLongNumber(value!!, 0L)
        }

        private fun CapabilityCheck.toCheckObject(): NativeObject = newNativeObject().also { obj ->
            obj.defineProp("id", id)
            obj.defineProp("name", name)
            obj.defineProp("status", status.wireName)
            obj.defineProp("available", available)
            obj.defineProp("requestable", requestable)
            obj.defineProp("missing", missing.toNativeArray())
            obj.defineProp("blocked", blocked.toNativeArray())
            obj.defineProp("unsupported", unsupported.toNativeArray())
            obj.defineProp("dangerous", dangerous)
            obj.defineProp("permissions", permissions.toNativeArray())
            obj.defineProp("services", services.toNativeArray())
            obj.defineProp("relatedApis", relatedApis.toNativeArray())
            obj.defineProp("description", description)
            obj.defineProp("requestHint", requestHint)
            obj.defineProp("minSdk", minSdk)
            obj.defineProp("inrtSupported", inrtSupported)
        }

        private fun CapabilityDefinition.toDefinitionObject(): NativeObject = newNativeObject().also { obj ->
            obj.defineProp("id", id)
            obj.defineProp("name", name)
            obj.defineProp("relatedApis", relatedApis.toNativeArray())
            obj.defineProp("permissions", permissions.toNativeArray())
            obj.defineProp("services", services.toNativeArray())
            obj.defineProp("dangerous", dangerous)
            obj.defineProp("minSdk", minSdk)
            obj.defineProp("inrtSupported", inrtSupported)
            obj.defineProp("description", description)
            obj.defineProp("requestHint", requestHint)
        }

        private fun ProjectCapabilityAuditEntry.toAuditObject(): NativeObject = newNativeObject().also { obj ->
            obj.defineProp("id", id)
            obj.defineProp("timestamp", timestamp)
            obj.defineProp("projectKey", projectKey)
            obj.defineProp("api", api)
            obj.defineProp("capabilities", capabilities.toNativeArray())
            obj.defineProp("riskLevel", riskLevel)
            obj.defineProp("action", action)
            obj.defineProp("allowed", allowed)
            obj.defineProp("target", target)
            obj.defineProp("message", message)
        }
    }
}
