package org.autojs.autojs.runtime.api.augment.vision

import android.os.SystemClock
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Images as ApiImages
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.vision.ScreenPerceptionPipeline
import org.autojs.autojs.runtime.api.vision.VisionSample
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

@Suppress("unused")
class VisionObservationNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val options: VisionRuntimeOptions,
) : NativeObject(), StringReadable {

    private val session: ApiImages.ScreenCaptureSession? =
        if (ScreenPerceptionPipeline.SOURCE_OCR in options.pipeline.sources) {
            scriptRuntime.images.openCaptureSession(
                ApiImages.CaptureSessionOptions(
                    ApiImages.CaptureSessionOptions.PRESET_OCR,
                    2,
                    options.frameTimeoutMillis,
                    options.intervalMillis,
                    true,
                    true,
                ),
            )
        } else {
            null
        }
    private var closed = false
    private var latestSample: VisionSample? = null
    private var lastSampleUptime = 0L

    private val mFunctionNames = arrayOf(
        ::latest.name,
        ::next.name,
        ::targets.name,
        ::metrics.name,
        ::close.name,
        ::isClosed.name,
        ::toString.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineFunctionProperties(mFunctionNames, javaClass, PERMANENT)
        defineProperty(StringReadable.KEY, newBaseFunction(StringReadable.KEY, { toStringReadable() }, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
        defineProperty("runtime", scriptRuntime, READONLY or DONTENUM or PERMANENT)
        refresh()
    }

    override fun toStringReadable(): String = "VisionObservation ${metricsMap().toNativeObject()}"

    private fun refresh(): VisionSample {
        ensureOpen()
        waitForInterval()
        val sample = Vision.sample(scriptRuntime, options, session)
        latestSample = sample
        lastSampleUptime = SystemClock.uptimeMillis()
        put("sample", this, sample.toMap().toNativeObject())
        put("targets", this, sample.targets.map { it.toMap() }.toNativeArray())
        put("targetCount", this, sample.targets.size)
        return sample
    }

    private fun latestOrRefresh(): VisionSample = latestSample ?: refresh()

    private fun metricsMap(): Map<String, Any?> = linkedMapOf(
        "closed" to closed,
        "interval" to options.intervalMillis,
        "sources" to options.pipeline.sources.toList(),
        "targetCount" to (latestSample?.targets?.size ?: 0),
        "lastSampleUptime" to lastSampleUptime,
        "captureSession" to session?.metrics(),
    )

    private fun waitForInterval() {
        if (options.intervalMillis <= 0L || lastSampleUptime <= 0L) return
        val waitMillis = lastSampleUptime + options.intervalMillis - SystemClock.uptimeMillis()
        if (waitMillis <= 0L) return
        try {
            Thread.sleep(waitMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Vision observation has been closed" }
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun latest(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): NativeObject = ensureArgumentsIsEmpty(args) {
            (thisObj as VisionObservationNativeObject).latestOrRefresh().toMap().toNativeObject()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun next(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): NativeObject = ensureArgumentsAtMost(args, 1) { argList ->
            val observer = thisObj as VisionObservationNativeObject
            val timeout = argList.getOrNull(0)?.let { coerceLongNumber(it, 0L) } ?: 0L
            if (timeout > 0L && observer.options.intervalMillis <= 0L) {
                try {
                    Thread.sleep(timeout)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            observer.refresh().toMap().toNativeObject()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun targets(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any = ensureArgumentsIsEmpty(args) {
            (thisObj as VisionObservationNativeObject).latestOrRefresh().targets.map { it.toMap() }.toNativeArray()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun metrics(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): NativeObject = ensureArgumentsIsEmpty(args) {
            (thisObj as VisionObservationNativeObject).metricsMap().toNativeObject()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun close(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
            val observer = thisObj as VisionObservationNativeObject
            observer.session?.close()
            observer.closed = true
            UNDEFINED
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun isClosed(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
            (thisObj as VisionObservationNativeObject).closed
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            (thisObj as VisionObservationNativeObject).toStringReadable()
        }
    }
}
