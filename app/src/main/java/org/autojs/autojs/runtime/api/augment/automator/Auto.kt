package org.autojs.autojs.runtime.api.augment.automator

import android.graphics.Rect
import android.os.SystemClock
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.UiSnapshotTools
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.function.Supplier

@Suppress("unused", "UNUSED_PARAMETER")
class Auto(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::start.name,
        ::stop.name,
        ::enable.name,
        ::disable.name,
        ::hasInstance.name,
        ::hasService.name,
        ::exists.name,
        ::isRunning.name,
        ::isOperational.name,
        ::stateListener.name,
        ::registerEvent.name,
        ::registerEvents.name,
        ::removeEvent.name,
        ::removeEvents.name,
        ::waitFor.name,
        ::setMode.name,
        ::setFlags.name,
        ::setWindowFilter.name,
        ::launchSettings.name,
        ::clearCache.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
        ::snapshot.name,
        ::diffSnapshot.name,
        ::waitUntil.name,
        ::retry.name,
        ::stableClick.name,
        ::stableSetText.name,
        ::findWithScroll.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "service" to Supplier {
            scriptRuntime.accessibilityBridge.service
        },
        "services" to Supplier {
            accessibilityTool.getServices().toNativeArray()
        },
        "windows" to Supplier {
            scriptRuntime.accessibilityBridge.service?.run { Java.toJsArrayRhino(windows, true) } ?: newNativeArray()
        },
        "root" to Supplier {
            scriptRuntime.accessibilityBridge.getRootInCurrentWindow()?.let { UiObject.createRoot(it) }
        },
        "rootInActiveWindow" to Supplier {
            scriptRuntime.accessibilityBridge.getRootInActiveWindow()?.let { UiObject.createRoot(it) }
        },
        "windowRoots" to Supplier {
            scriptRuntime.accessibilityBridge.windowRoots().map { UiObject.createRoot(it) }.toNativeArray()
        },
        "state" to Supplier {
            newNativeObject().also { o ->
                o.defineProp("hasInstance", accessibilityTool.hasInstance())
                o.defineProp("hasService", accessibilityTool.hasService())
                o.defineProp("isRunning", accessibilityTool.isRunning())
                o.defineProp("isOperational", accessibilityTool.isOperational())
            }
        }
    )

    override fun invoke(vararg args: Any?): Any = ensureArgumentsAtMost(args, 2) {
        when {
            it.isEmpty() -> invoke(null)
            it.size == 1 -> {
                val (o) = it
                when {
                    o.isJsNullish() -> invoke(false)
                    o is Boolean -> ensureA11yServiceStarted(scriptRuntime, o)
                    o is String -> setModeRhinoWithRuntime(scriptRuntime, o)
                    else -> throw WrappedIllegalArgumentException("Invalid argument ${Context.toString(o)} for auto()")
                }
            }
            else -> {
                val (mode, isForcibleRestart) = it
                setModeRhinoWithRuntime(scriptRuntime, mode)
                ensureA11yServiceStarted(scriptRuntime, isForcibleRestart)
            }
        }
        return@ensureArgumentsAtMost UNDEFINED
    }

    companion object : ArgumentGuards() {

        val accessibilityTool by lazy { AccessibilityTool(globalContext) }

        private val modes = hashMapOf(
            "normal" to AccessibilityBridge.MODE_NORMAL,
            "fast" to AccessibilityBridge.MODE_FAST,
        )

        private val flags = hashMapOf(
            "findOnUiThread" to AccessibilityBridge.FLAG_FIND_ON_UI_THREAD,
            "useUsageStats" to AccessibilityBridge.FLAG_USE_USAGE_STATS,
            "useShell" to AccessibilityBridge.FLAG_USE_SHELL,
        )

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun start(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.startService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun enable(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            start(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stop(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.stopService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun disable(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            stop(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hasInstance(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasInstance()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hasService(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun exists(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRunning(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.isRunning()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isOperational(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.isOperational()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stateListener(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) {
            val (listener) = it
            scriptRuntime.accessibilityBridge.setAccessibilityListener(
                when {
                    listener.isJsNullish() -> null
                    listener is AccessibilityServiceCallback -> listener
                    listener is ScriptableObject -> {
                        val adapter = NativeJavaObject.createInterfaceAdapter(
                            AccessibilityServiceCallback::class.java, listener
                        ) as AccessibilityServiceCallback

                        object : AccessibilityServiceCallback {
                            override fun onConnected() = adapter.onConnected()
                            override fun onDisconnected() = adapter.onDisconnected()
                        }
                    }
                    else -> throw WrappedIllegalArgumentException("Argument listener ($listener) is invalid for auto.setWindowFilter")
                })
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
            val (name, listener) = it
            require(!name.isJsNullish()) { "Argument \"name\" for auto.registerEvent cannot be nullish" }
            scriptRuntime.automator.registerEvent(
                Context.toString(name), when {
                    listener.isJsNullish() -> null
                    listener is AccessibilityEventCallback -> listener
                    listener is ScriptableObject -> {
                        val adapter = NativeJavaObject.createInterfaceAdapter(
                            AccessibilityEventCallback::class.java, listener
                        ) as AccessibilityEventCallback
                        object : AccessibilityEventCallback {
                            override fun onAccessibilityEvent(event: AccessibilityEventWrapper) = adapter.onAccessibilityEvent(event)
                        }
                    }
                    else -> throw WrappedIllegalArgumentException("Argument listener ($listener) is invalid for auto.registerEvent")
                })
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerEvents(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return registerEvent(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            require(!it.isJsNullish()) { "Argument \"name\" for auto.removeEvent cannot be nullish" }
            scriptRuntime.automator.removeEvent(Context.toString(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeEvents(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return removeEvent(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitFor(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (timeout) = it
            waitForRhino(timeout)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun waitForRhino(timeout: Any?) {
            Automator.waitForServiceRhino(timeout)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setMode(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            setModeRhinoWithRuntime(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setModeRhinoWithRuntime(scriptRuntime: ScriptRuntime, mode: Any?) {
            if (mode !is String) throw WrappedIllegalArgumentException("Argument mode must be of type String for auto.setMode")
            modes[mode.lowercase()]?.let {
                scriptRuntime.accessibilityBridge.setMode(it)
            } ?: throw WrappedIllegalArgumentException("Unknown mode ($mode) for auto.setMode")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setFlags(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            var flagsInt = 0
            when (it) {
                is NativeArray -> it
                is String -> listOf(it)
                else -> throw WrappedIllegalArgumentException("Unknown flags ($it) for auto.setFlags")
            }.forEach { s ->
                val flag = flags[s] ?: throw WrappedIllegalArgumentException("Unknown flag ($s) for auto.setFlags")
                flagsInt = flagsInt or flag
            }
            scriptRuntime.accessibilityBridge.setFlags(flagsInt)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setWindowFilter(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (filter) = it
            val accessibilityBridge = scriptRuntime.accessibilityBridge
            when {
                filter.isJsNullish() -> accessibilityBridge.setWindowFilter { true }
                filter is Boolean -> accessibilityBridge.setWindowFilter { filter }
                filter is WindowFilter -> accessibilityBridge.setWindowFilter(filter)
                filter is BaseFunction -> accessibilityBridge.setWindowFilter { info ->
                    Context.toBoolean(callFunction(scriptRuntime, filter, null, scriptRuntime.topLevelScope, arrayOf(info)))
                }
                else -> throw WrappedIllegalArgumentException("Argument filter ($filter) is invalid for auto.setWindowFilter")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.launchSettings()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearCache(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            accessibilityTool.clearCache()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            scriptRuntime.info.latestPackage
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            scriptRuntime.info.latestActivity
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            val latestPackage = scriptRuntime.info.latestPackage.takeUnless { it.isEmpty() } ?: return@ensureArgumentsIsEmpty ""
            val latestActivity = scriptRuntime.info.latestActivity.takeUnless { it.isEmpty() } ?: return@ensureArgumentsIsEmpty ""
            "$latestPackage/$latestActivity"
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun snapshot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = options as? NativeObject ?: newNativeObject()
            val captureOptions = UiSnapshotTools.CaptureOptions(
                includeWindows = opt.inquire("includeWindows", ::coerceBoolean, true),
                maxDepth = opt.inquire("maxDepth", ::coerceIntNumber, 50).coerceAtLeast(0),
                maxNodes = opt.inquire("maxNodes", ::coerceIntNumber, 2_000).coerceIn(1, 10_000),
                redact = opt.inquire("redact", ::coerceBoolean, false),
                redactInputs = opt.inquire("redactInputs", ::coerceBoolean, true),
                redactPatterns = opt.inquire("redactPatterns", ::coerceBoolean, true),
            )
            val snapshot = UiSnapshotTools.capture(
                globalContext,
                scriptRuntime.accessibilityBridge,
                scriptRuntime.info.latestPackage,
                scriptRuntime.info.latestActivity,
                captureOptions,
            )
            val format = opt.inquire("format", ::coerceString, "object").lowercase()
            when (format) {
                "json", "string" -> JSONObject(snapshot).toString(opt.inquire("indent", ::coerceIntNumber, 0).coerceIn(0, 8))
                else -> snapshot.toRhino()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun diffSnapshot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (before, after, options) = argList
            val opt = options as? NativeObject ?: newNativeObject()
            val diff = UiSnapshotTools.diff(before.toSnapshotMap(), after.toSnapshotMap())
            val format = opt.inquire("format", ::coerceString, "object").lowercase()
            when (format) {
                "json", "string" -> JSONObject(diff).toString(opt.inquire("indent", ::coerceIntNumber, 0).coerceIn(0, 8))
                else -> diff.toRhino()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitUntil(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (condition, options) = argList
            val opt = ReliableAutomationOptions.from(options, "auto.waitUntil", stableCountDefault = 1, retriesDefault = 0)
            val started = SystemClock.uptimeMillis()
            var attempts = 0
            var stableHits = 0
            var lastSignature: String? = null
            var lastEvaluation = AutomationEvaluation(false, null, null, "condition_not_met")

            while (!isTimedOut(started, opt.timeoutMs)) {
                attempts += 1
                val evaluation = evaluateCondition(scriptRuntime, condition)
                lastEvaluation = evaluation

                if (evaluation.ok) {
                    val signature = evaluation.node?.stableSignature() ?: Context.toString(evaluation.value)
                    stableHits = if (signature == lastSignature) stableHits + 1 else 1
                    lastSignature = signature
                    if (stableHits >= opt.stableCount) {
                        return@ensureArgumentsLengthInRange automationResult(
                            scriptRuntime = scriptRuntime,
                            options = opt,
                            started = started,
                            action = "waitUntil",
                            ok = true,
                            attempts = attempts,
                            selector = describeTarget(condition),
                            node = evaluation.node,
                            reason = null,
                            strategy = listOf("wait", "stable:${opt.stableCount}"),
                            extra = mapOf("value" to evaluation.value),
                        )
                    }
                } else {
                    stableHits = 0
                    lastSignature = null
                }

                sleepUntilNextPoll(scriptRuntime, started, opt)
            }

            automationResult(
                scriptRuntime = scriptRuntime,
                options = opt,
                started = started,
                action = "waitUntil",
                ok = false,
                attempts = attempts,
                selector = describeTarget(condition),
                node = lastEvaluation.node,
                reason = lastEvaluation.reason ?: "timeout",
                strategy = listOf("wait", "timeout"),
                extra = mapOf("value" to lastEvaluation.value),
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun retry(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (action, options) = argList
            require(action is BaseFunction) { "Argument action for auto.retry must be a JavaScript function" }
            val opt = ReliableAutomationOptions.from(options, "auto.retry", stableCountDefault = 1, retriesDefault = 2)
            val started = SystemClock.uptimeMillis()
            var attempts = 0
            var interval = opt.intervalMs
            var lastValue: Any? = null
            var lastError: Throwable? = null

            while (attempts <= opt.retries && !isTimedOut(started, opt.timeoutMs)) {
                attempts += 1
                try {
                    lastValue = callFunction(scriptRuntime, action, scriptRuntime.topLevelScope, arrayOf(attempts))
                    lastError = null
                    if (isSuccessfulAutomationValue(lastValue)) {
                        return@ensureArgumentsLengthInRange automationResult(
                            scriptRuntime = scriptRuntime,
                            options = opt,
                            started = started,
                            action = "retry",
                            ok = true,
                            attempts = attempts,
                            selector = "function",
                            node = nodeFromAutomationValue(lastValue),
                            reason = null,
                            strategy = listOf("retry", "attempt:$attempts"),
                            extra = mapOf("value" to lastValue),
                        )
                    }
                } catch (e: Throwable) {
                    lastValue = null
                    lastError = e
                }

                if (attempts <= opt.retries) {
                    sleepForRetry(scriptRuntime, started, opt, interval)
                    interval = nextRetryInterval(interval, opt.backoff)
                }
            }

            automationResult(
                scriptRuntime = scriptRuntime,
                options = opt,
                started = started,
                action = "retry",
                ok = false,
                attempts = attempts,
                selector = "function",
                node = nodeFromAutomationValue(lastValue),
                reason = lastError?.message ?: "retry_exhausted",
                strategy = listOf("retry", "give_up"),
                extra = mapOf("value" to lastValue, "error" to lastError?.javaClass?.name),
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stableClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (targetArg, options) = argList
            val opt = ReliableAutomationOptions.from(options, "auto.stableClick", stableCountDefault = 2, retriesDefault = 2)
            val started = SystemClock.uptimeMillis()
            val target = ReliableTarget.from(scriptRuntime, targetArg, "auto.stableClick")
            val strategies = mutableListOf("wait_stable:${opt.stableCount}", "pre_click_validate")
            var attempts = 0
            var lastNode: UiObject? = null
            var reason = "not_started"

            while (attempts <= opt.retries && !isTimedOut(started, opt.timeoutMs)) {
                attempts += 1
                val stable = resolveStableNode(scriptRuntime, target, opt, started, scrollOnMiss = opt.scrollFind)
                lastNode = stable.node
                reason = stable.reason ?: "not_found"
                if (stable.scrolled) strategies += "scroll_find"

                val node = stable.node
                if (node != null) {
                    validateActionNode(node, opt)?.let {
                        reason = it
                    } ?: run {
                        if (node.click()) {
                            strategies += "node_click"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableClick", true, attempts, target.label, node, null, strategies,
                            )
                        }
                        reason = "node_click_failed"

                        if (opt.parentFallback && clickParent(node)) {
                            strategies += "parent_fallback"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableClick", true, attempts, target.label, node, null, strategies,
                            )
                        }

                        if (opt.coordinateFallback && node.clickBounds()) {
                            strategies += "coordinate_fallback"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableClick", true, attempts, target.label, node, null, strategies,
                            )
                        }
                    }
                }

                if (attempts <= opt.retries) {
                    sleepForRetry(scriptRuntime, started, opt, opt.intervalMs)
                }
            }

            if (opt.ocrFallback) {
                strategies += "ocrFallback:not_available"
                reason = "$reason; ocrFallback not available in reliable automation DSL"
            }

            automationResult(
                scriptRuntime, opt, started, "stableClick", false, attempts, target.label, lastNode, reason, strategies,
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stableSetText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (targetArg, textArg, options) = argList
            val opt = ReliableAutomationOptions.from(options, "auto.stableSetText", stableCountDefault = 2, retriesDefault = 2)
            val text = Context.toString(textArg)
            val started = SystemClock.uptimeMillis()
            val target = ReliableTarget.from(scriptRuntime, targetArg, "auto.stableSetText")
            val strategies = mutableListOf("wait_stable:${opt.stableCount}", "pre_set_text_validate")
            var attempts = 0
            var lastNode: UiObject? = null
            var reason = "not_started"

            while (attempts <= opt.retries && !isTimedOut(started, opt.timeoutMs)) {
                attempts += 1
                val stable = resolveStableNode(scriptRuntime, target, opt, started, scrollOnMiss = opt.scrollFind)
                lastNode = stable.node
                reason = stable.reason ?: "not_found"
                if (stable.scrolled) strategies += "scroll_find"

                val node = stable.node
                if (node != null) {
                    validateActionNode(node, opt)?.let {
                        reason = it
                    } ?: run {
                        if (node.setText(text)) {
                            strategies += "node_set_text"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableSetText", true, attempts, target.label, node, null, strategies,
                            )
                        }
                        reason = "node_set_text_failed"

                        if (opt.parentFallback && node.parent()?.setText(text) == true) {
                            strategies += "parent_fallback"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableSetText", true, attempts, target.label, node, null, strategies,
                            )
                        }

                        if (opt.coordinateFallback && node.clickBounds() && scriptRuntime.automator.setText(scriptRuntime.automator.editable(-1), text)) {
                            strategies += "coordinate_focus_fallback"
                            return@ensureArgumentsLengthInRange automationResult(
                                scriptRuntime, opt, started, "stableSetText", true, attempts, target.label, node, null, strategies,
                            )
                        }
                    }
                }

                if (attempts <= opt.retries) {
                    sleepForRetry(scriptRuntime, started, opt, opt.intervalMs)
                }
            }

            if (opt.ocrFallback) {
                strategies += "ocrFallback:not_available"
                reason = "$reason; ocrFallback not available in reliable automation DSL"
            }

            automationResult(
                scriptRuntime, opt, started, "stableSetText", false, attempts, target.label, lastNode, reason, strategies,
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findWithScroll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val targetArg = argList[0]
            val (scrollContainerArg, optionsArg) = splitFindWithScrollArgs(argList)
            val opt = ReliableAutomationOptions.from(optionsArg, "auto.findWithScroll", stableCountDefault = 1, retriesDefault = 0)
            val started = SystemClock.uptimeMillis()
            val target = ReliableTarget.from(scriptRuntime, targetArg, "auto.findWithScroll")
            val scrollContainer = scrollContainerArg?.let { ReliableTarget.from(scriptRuntime, it, "auto.findWithScroll") }
            val strategies = mutableListOf("find", "scroll")
            var attempts = 0
            var scrolls = 0
            var scrolled = false
            var lastNode: UiObject? = null
            var reason = "not_found"

            while (!isTimedOut(started, opt.timeoutMs)) {
                attempts += 1
                target.findOnce()?.let { found ->
                    val stable = waitStableExistingNode(scriptRuntime, found, opt, started)
                    lastNode = stable.node ?: found
                    reason = stable.reason ?: "found"
                    if (stable.node != null) {
                        return@ensureArgumentsLengthInRange automationResult(
                            scriptRuntime, opt, started, "findWithScroll", true, attempts, target.label, stable.node, null, strategies,
                            extra = mapOf("scrolls" to scrolls),
                        )
                    }
                }

                if (scrolls >= opt.maxScrolls) {
                    reason = "max_scrolls_reached"
                    break
                }

                val scrollOk = scrollContainer?.findOnce()?.let { scrollNode ->
                    scrollNode.scrollForward() || scrollNode.scrollDown()
                } ?: scriptRuntime.automator.scrollForward(0)

                scrolls += 1
                scrolled = scrolled || scrollOk
                if (!scrollOk) {
                    reason = "scroll_failed"
                    break
                }
                scriptRuntime.sleep(opt.scrollIntervalMs)
            }

            if (scrolled) strategies += "scroll_find"
            automationResult(
                scriptRuntime, opt, started, "findWithScroll", false, attempts, target.label, lastNode, reason, strategies,
                extra = mapOf("scrolls" to scrolls),
            )
        }

        private fun ensureA11yServiceStarted(scriptRuntime: ScriptRuntime, isForcibleRestart: Any?) {
            if (isForcibleRestart !is Boolean) throw WrappedIllegalArgumentException("Argument isForcibleRestart must be of type Boolean")
            scriptRuntime.accessibilityBridge.ensureServiceStarted(isForcibleRestart)
        }

        private data class ReliableAutomationOptions(
            val timeoutMs: Long,
            val intervalMs: Long,
            val scrollIntervalMs: Long,
            val stableCount: Int,
            val retries: Int,
            val backoff: Double,
            val maxScrolls: Int,
            val captureSnapshot: Boolean,
            val captureOnFailure: Boolean,
            val redact: Boolean,
            val coordinateFallback: Boolean,
            val parentFallback: Boolean,
            val scrollFind: Boolean,
            val ocrFallback: Boolean,
            val requireVisible: Boolean,
            val requireEnabled: Boolean,
            val snapshotMaxNodes: Int,
        ) {
            companion object {
                fun from(source: Any?, functionName: String, stableCountDefault: Int, retriesDefault: Int): ReliableAutomationOptions {
                    val opt = when {
                        source.isJsNullish() -> newNativeObject()
                        source is NativeObject -> source
                        else -> throw WrappedIllegalArgumentException("Argument options for $functionName must be an object")
                    }
                    val intervalMs = opt.inquire("interval", ::coerceLongNumber, 200L).coerceAtLeast(0L)
                    val scrollIntervalMs = opt.inquire("scrollInterval", ::coerceLongNumber, intervalMs).coerceAtLeast(0L)
                    val retries = when {
                        opt.has("retries", opt) -> opt.inquire("retries", ::coerceIntNumber, retriesDefault)
                        else -> opt.inquire("maxRetries", ::coerceIntNumber, retriesDefault)
                    }.coerceIn(0, 20)
                    return ReliableAutomationOptions(
                        timeoutMs = opt.inquire("timeout", ::coerceLongNumber, 10_000L).coerceAtLeast(1L),
                        intervalMs = intervalMs,
                        scrollIntervalMs = scrollIntervalMs,
                        stableCount = opt.inquire("stableCount", ::coerceIntNumber, stableCountDefault).coerceIn(1, 10),
                        retries = retries,
                        backoff = opt.inquire("backoff", ::coerceNumber, 1.0).coerceIn(1.0, 10.0),
                        maxScrolls = opt.inquire("maxScrolls", ::coerceIntNumber, 8).coerceIn(0, 100),
                        captureSnapshot = opt.inquire("captureSnapshot", ::coerceBoolean, false),
                        captureOnFailure = opt.inquire("captureOnFailure", ::coerceBoolean, true),
                        redact = opt.inquire("redact", ::coerceBoolean, true),
                        coordinateFallback = opt.inquire("fallbackToBounds", ::coerceBoolean, true),
                        parentFallback = opt.inquire("fallbackToParent", ::coerceBoolean, true),
                        scrollFind = opt.inquire("scrollFind", ::coerceBoolean, false),
                        ocrFallback = opt.inquire("ocrFallback", ::coerceBoolean, false),
                        requireVisible = opt.inquire("requireVisible", ::coerceBoolean, true),
                        requireEnabled = opt.inquire("requireEnabled", ::coerceBoolean, true),
                        snapshotMaxNodes = opt.inquire("snapshotMaxNodes", ::coerceIntNumber, 500).coerceIn(1, 10_000),
                    )
                }
            }
        }

        private data class ReliableTarget(val selector: UiSelector?, val node: UiObject?, val label: String) {
            fun findOnce(): UiObject? = node ?: selector?.findOnce()

            companion object {
                fun from(scriptRuntime: ScriptRuntime, raw: Any?, functionName: String): ReliableTarget {
                    val target = (raw as? NativeJavaObject)?.unwrap() ?: raw
                    return when (target) {
                        is UiObject -> ReliableTarget(null, target, "UiObject(${target.shortDescription()})")
                        is UiSelector -> ReliableTarget(target, null, target.toStringReadable())
                        is CharSequence -> Context.toString(target).let { text ->
                            ReliableTarget(UiSelector(scriptRuntime.accessibilityBridge).text(text), null, "text(\"$text\")")
                        }
                        else -> throw WrappedIllegalArgumentException(
                            "Argument target for $functionName must be a UiSelector, UiObject, or string",
                        )
                    }
                }
            }
        }

        private data class AutomationEvaluation(
            val ok: Boolean,
            val node: UiObject?,
            val value: Any?,
            val reason: String?,
        )

        private data class StableNodeResult(
            val node: UiObject?,
            val reason: String?,
            val scrolled: Boolean = false,
        )

        private fun evaluateCondition(scriptRuntime: ScriptRuntime, condition: Any?): AutomationEvaluation {
            val value = when (val target = (condition as? NativeJavaObject)?.unwrap() ?: condition) {
                is BaseFunction -> callFunction(scriptRuntime, target, scriptRuntime.topLevelScope, emptyArray<Any?>())
                is UiObject -> target
                is UiSelector -> target.findOnce()
                is CharSequence -> UiSelector(scriptRuntime.accessibilityBridge).text(Context.toString(target)).findOnce()
                else -> target
            }
            val node = nodeFromAutomationValue(value)
            val ok = isSuccessfulAutomationValue(value)
            return AutomationEvaluation(ok, node, value, if (ok) null else "condition_not_met")
        }

        private fun resolveStableNode(
            scriptRuntime: ScriptRuntime,
            target: ReliableTarget,
            options: ReliableAutomationOptions,
            started: Long,
            scrollOnMiss: Boolean,
        ): StableNodeResult {
            var stableHits = 0
            var lastSignature: String? = null
            var lastNode: UiObject? = null
            var scrolled = false
            var scrolls = 0

            while (!isTimedOut(started, options.timeoutMs)) {
                val node = target.findOnce()
                if (node != null) {
                    lastNode = node
                    val signature = node.stableSignature()
                    stableHits = if (signature == lastSignature) stableHits + 1 else 1
                    lastSignature = signature
                    if (stableHits >= options.stableCount) {
                        return StableNodeResult(node, null, scrolled)
                    }
                } else {
                    stableHits = 0
                    lastSignature = null
                    if (scrollOnMiss && scrolls < options.maxScrolls) {
                        scrolls += 1
                        scrolled = scriptRuntime.automator.scrollForward(0) || scrolled
                    } else if (scrollOnMiss && scrolls >= options.maxScrolls) {
                        return StableNodeResult(lastNode, "max_scrolls_reached", scrolled)
                    }
                }
                sleepUntilNextPoll(scriptRuntime, started, options)
            }

            return StableNodeResult(lastNode, if (lastNode == null) "not_found" else "not_stable", scrolled)
        }

        private fun waitStableExistingNode(
            scriptRuntime: ScriptRuntime,
            node: UiObject,
            options: ReliableAutomationOptions,
            started: Long,
        ): StableNodeResult {
            if (options.stableCount <= 1) return StableNodeResult(node, null)
            var stableHits = 0
            var lastSignature: String? = null
            while (!isTimedOut(started, options.timeoutMs)) {
                val signature = node.stableSignature()
                stableHits = if (signature == lastSignature) stableHits + 1 else 1
                lastSignature = signature
                if (stableHits >= options.stableCount) return StableNodeResult(node, null)
                sleepUntilNextPoll(scriptRuntime, started, options)
            }
            return StableNodeResult(node, "not_stable")
        }

        private fun validateActionNode(node: UiObject, options: ReliableAutomationOptions): String? {
            val bounds = node.bounds()
            return when {
                options.requireVisible && !node.visibleToUser() -> "matched_not_visible"
                options.requireEnabled && !node.enabled() -> "matched_disabled"
                bounds.width() <= 0 || bounds.height() <= 0 -> "matched_empty_bounds"
                else -> null
            }
        }

        private fun clickParent(node: UiObject): Boolean {
            val parent = node.parent() ?: return false
            if (!parent.visibleToUser() || !parent.enabled()) return false
            return parent.click() || parent.clickBounds()
        }

        private fun splitFindWithScrollArgs(args: Array<Any?>): Pair<Any?, Any?> = when (args.size) {
            1 -> null to null
            2 -> {
                val second = args[1]
                when (second) {
                    is NativeObject -> null to second
                    else -> second to null
                }
            }
            else -> args[1] to args[2]
        }

        private fun automationResult(
            scriptRuntime: ScriptRuntime,
            options: ReliableAutomationOptions,
            started: Long,
            action: String,
            ok: Boolean,
            attempts: Int,
            selector: String?,
            node: UiObject?,
            reason: String?,
            strategy: List<String>,
            extra: Map<String, Any?> = emptyMap(),
        ): NativeObject {
            val duration = SystemClock.uptimeMillis() - started
            val snapshot = snapshotReference(scriptRuntime, options, ok)
            return linkedMapOf<String, Any?>(
                "ok" to ok,
                "success" to ok,
                "action" to action,
                "duration" to duration,
                "elapsed" to duration,
                "attempts" to attempts,
                "selector" to selector,
                "matched" to node?.summaryMap(),
                "uiObject" to node,
                "reason" to reason,
                "strategy" to strategy,
                "snapshotRef" to snapshot.first,
                "snapshot" to snapshot.second,
            ).also { it.putAll(extra) }.toRhino()
        }

        private fun snapshotReference(
            scriptRuntime: ScriptRuntime,
            options: ReliableAutomationOptions,
            ok: Boolean,
        ): Pair<Map<String, Any?>, Map<String, Any?>?> {
            val shouldCapture = options.captureSnapshot || (!ok && options.captureOnFailure)
            if (!shouldCapture) {
                return mapOf(
                    "captured" to false,
                    "reason" to "captureSnapshot disabled",
                ) to null
            }
            val snapshot = UiSnapshotTools.capture(
                globalContext,
                scriptRuntime.accessibilityBridge,
                scriptRuntime.info.latestPackage,
                scriptRuntime.info.latestActivity,
                UiSnapshotTools.CaptureOptions(
                    includeWindows = true,
                    maxDepth = 50,
                    maxNodes = options.snapshotMaxNodes,
                    redact = options.redact,
                    redactInputs = true,
                    redactPatterns = true,
                ),
            )
            return mapOf(
                "captured" to true,
                "timestamp" to snapshot["timestamp"],
                "packageName" to snapshot["packageName"],
                "activity" to snapshot["activity"],
                "nodeCount" to snapshot["nodeCount"],
                "truncated" to snapshot["truncated"],
            ) to snapshot
        }

        private fun nodeFromAutomationValue(value: Any?): UiObject? = when (val unwrapped = (value as? NativeJavaObject)?.unwrap() ?: value) {
            is UiObject -> unwrapped
            is NativeObject -> {
                val uiObject = unwrapped.get("uiObject", unwrapped).takeUnless { it == ScriptableObject.NOT_FOUND }
                    ?: unwrapped.get("node", unwrapped).takeUnless { it == ScriptableObject.NOT_FOUND }
                (uiObject as? NativeJavaObject)?.unwrap() as? UiObject ?: uiObject as? UiObject
            }
            else -> null
        }

        private fun isSuccessfulAutomationValue(value: Any?): Boolean {
            if (value.isJsNullish()) return false
            if (value is Boolean) return value
            if (value is Double && value.isNaN()) return false
            if (value is NativeObject) {
                val ok = value.get("ok", value)
                if (ok != ScriptableObject.NOT_FOUND) return Context.toBoolean(ok)
                val success = value.get("success", value)
                if (success != ScriptableObject.NOT_FOUND) return Context.toBoolean(success)
            }
            return Context.toBoolean(value)
        }

        private fun sleepUntilNextPoll(scriptRuntime: ScriptRuntime, started: Long, options: ReliableAutomationOptions) {
            val remaining = options.timeoutMs - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0L) return
            val sleep = options.intervalMs.coerceAtMost(remaining)
            if (sleep > 0L) scriptRuntime.sleep(sleep)
        }

        private fun sleepForRetry(scriptRuntime: ScriptRuntime, started: Long, options: ReliableAutomationOptions, interval: Long) {
            val remaining = options.timeoutMs - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0L) return
            val sleep = interval.coerceAtLeast(0L).coerceAtMost(remaining)
            if (sleep > 0L) scriptRuntime.sleep(sleep)
        }

        private fun nextRetryInterval(interval: Long, backoff: Double): Long =
            (interval * backoff).toLong().coerceAtLeast(interval).coerceAtMost(60_000L)

        private fun isTimedOut(started: Long, timeoutMs: Long): Boolean =
            timeoutMs > 0L && SystemClock.uptimeMillis() - started >= timeoutMs

        private fun describeTarget(target: Any?): String = when (val unwrapped = (target as? NativeJavaObject)?.unwrap() ?: target) {
            is UiObject -> "UiObject(${unwrapped.shortDescription()})"
            is UiSelector -> unwrapped.toStringReadable()
            is BaseFunction -> "function"
            is CharSequence -> "text(\"${Context.toString(unwrapped)}\")"
            else -> Context.toString(target)
        }

        private fun UiObject.shortDescription(): String =
            listOfNotNull(id()?.let { "id=$it" }, text().takeIf { it.isNotEmpty() }?.let { "text=$it" }, desc()?.let { "desc=$it" })
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: className().orEmpty()

        private fun UiObject.stableSignature(): String {
            val bounds = bounds()
            return listOf(
                id().orEmpty(),
                text(),
                desc().orEmpty(),
                className().orEmpty(),
                bounds.flatten(),
                clickable().toString(),
                enabled().toString(),
                visibleToUser().toString(),
            ).joinToString("|")
        }

        private fun UiObject.summaryMap(): Map<String, Any?> = mapOf(
            "text" to text(),
            "desc" to desc(),
            "id" to id(),
            "resourceName" to id(),
            "className" to className(),
            "packageName" to packageName(),
            "bounds" to bounds().toSummaryMap(),
            "clickable" to clickable(),
            "enabled" to enabled(),
            "visibleToUser" to visibleToUser(),
            "editable" to editable(),
        )

        private fun Rect.toSummaryMap(): Map<String, Any?> = mapOf(
            "left" to left,
            "top" to top,
            "right" to right,
            "bottom" to bottom,
            "width" to width(),
            "height" to height(),
            "centerX" to centerX(),
            "centerY" to centerY(),
        )

        private fun Rect.flatten(): String = "$left,$top,$right,$bottom"

        private fun Map<*, *>.toRhino(): NativeObject = newNativeObject().also { obj ->
            forEach { (key, value) ->
                obj.put(Context.toString(key), obj, value.toRhinoValue())
            }
        }

        private fun List<*>.toRhinoArray(): NativeArray = newNativeArray(map { it.toRhinoValue() }.toTypedArray())

        private fun Any?.toRhinoValue(): Any? = when (this) {
            is Map<*, *> -> toRhino()
            is List<*> -> toRhinoArray()
            else -> this
        }

        @Suppress("UNCHECKED_CAST")
        private fun Any?.toSnapshotMap(): Map<String, Any?> = when (this) {
            is NativeObject -> nativeToKotlin(this) as? Map<String, Any?> ?: emptyMap()
            is Map<*, *> -> this.entries.associate { Context.toString(it.key) to it.value }
            is String -> jsonToKotlin(JSONTokener(this).nextValue()) as? Map<String, Any?> ?: emptyMap()
            else -> throw WrappedIllegalArgumentException("Snapshot must be an object or JSON string")
        }

        private fun nativeToKotlin(value: Any?): Any? = when (value) {
            is NativeArray -> (0 until value.length.toInt()).map { index -> nativeToKotlin(value.get(index, value)) }
            is NativeObject -> value.ids.associate { id ->
                val key = Context.toString(id)
                key to nativeToKotlin(value.get(key, value))
            }.toMutableMap()
            is Undefined -> null
            else -> value
        }

        private fun jsonToKotlin(value: Any?): Any? = when (value) {
            JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWith { key -> jsonToKotlin(value.get(key)) }.toMutableMap()
            is JSONArray -> (0 until value.length()).map { index -> jsonToKotlin(value.get(index)) }
            else -> value
        }

    }

}
