package org.autojs.autojs.runtime.api.augment.ipc

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

@Suppress("unused", "UNUSED_PARAMETER")
class Ipc(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::publish.name,
        ::subscribe.name,
        ::unsubscribe.name,
        ::messages.name,
        ::clear.name,
        ::request.name,
        ::reply.name,
    )

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun publish(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (topic, payload, options) = argList
            val opt = normalizeOptions(options)
            scriptRuntime.ipc.publish(
                topic = coerceString(topic),
                payload = payload ?: UNDEFINED,
                replyTo = opt.prop("replyTo").takeUnless { it.isJsNullish() }?.let { coerceString(it) },
                correlationId = opt.prop("correlationId").takeUnless { it.isJsNullish() }?.let { coerceString(it) },
            ).toNativeObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun request(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (topic, payload, options) = argList
            val opt = normalizeOptions(options)
            scriptRuntime.ipc.request(
                topic = coerceString(topic),
                payload = payload ?: UNDEFINED,
                replyTo = opt.prop("replyTo").takeUnless { it.isJsNullish() }?.let { coerceString(it) } ?: "ipc.reply.${java.util.UUID.randomUUID()}",
                correlationId = opt.prop("correlationId").takeUnless { it.isJsNullish() }?.let { coerceString(it) },
            ).toNativeObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun reply(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (source, payload) = argList
            scriptRuntime.ipc.reply(source, payload ?: UNDEFINED).toNativeObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun subscribe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLength(args, 2) { argList ->
            val (topic, callback) = argList
            require(callback.isJsFunction()) { "Argument callback ${callback.jsBrief()} for ipc.subscribe() must be a JavaScript Function" }
            scriptRuntime.ipc.subscribe(coerceString(topic), callback as BaseFunction)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun unsubscribe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.ipc.unsubscribe(coerceString(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun messages(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 2) { argList ->
            val (topic, options) = argList
            val opt = normalizeOptions(options)
            val topicFilter = topic.takeUnless { it.isJsNullish() }?.let { coerceString(it) }
            val limit = opt.inquire("limit", ::coerceIntNumber, 50)
            val clear = opt.inquire("clear", ::coerceBoolean, false)
            scriptRuntime.ipc.messages(topicFilter, limit, clear)
                .map { it.toNativeObject() }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clear(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsAtMost(args, 1) { argList ->
            val (topic) = argList
            scriptRuntime.ipc.clear(topic.takeUnless { it.isJsNullish() }?.let { coerceString(it) })
        }

        private fun normalizeOptions(options: Any?): NativeObject {
            if (options.isJsNullish()) return newNativeObject()
            if (options is NativeObject) return options
            throw WrappedIllegalArgumentException("Argument \"options\" ${options.jsBrief()} must be a JavaScript Object")
        }
    }
}
