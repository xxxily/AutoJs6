package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.autojs.autojs.runtime.api.Images as ApiImages

@Suppress("unused")
class ScreenCaptureSessionNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val session: ApiImages.ScreenCaptureSession,
) : NativeObject(), StringReadable {

    private val mFunctionNames = arrayOf(
        ::latest.name,
        ::nextFrame.name,
        ::metrics.name,
        ::lastError.name,
        ::close.name,
        ::isClosed.name,
        ::toString.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineFunctionProperties(mFunctionNames, javaClass, PERMANENT)
        defineProperty(StringReadable.KEY, newBaseFunction(StringReadable.KEY, { toStringReadable() }, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
        defineProperty("runtime", scriptRuntime, READONLY or DONTENUM or PERMANENT)
    }

    override fun toStringReadable(): String = "ScreenCaptureSession ${metricsMap().toNativeObject()}"

    private fun metricsMap(): Map<String, Any?> = session.metrics()

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun latest(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).session.latest()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun nextFrame(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val timeout = argList.getOrNull(0)?.let { coerceLongNumber(it, 0L) } ?: 0L
            (thisObj as ScreenCaptureSessionNativeObject).session.nextFrame(timeout)
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun metrics(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): NativeObject = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).session.metrics().toNativeObject()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun lastError(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).session.lastError()?.toNativeObject()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun close(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).session.close()
            UNDEFINED
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun isClosed(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).session.isClosed
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            (thisObj as ScreenCaptureSessionNativeObject).toStringReadable()
        }
    }
}
