package org.autojs.autojs.runtime.api.augment.vision

import android.os.SystemClock
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.UiSnapshotTools
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Images as ApiImages
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.images.Images as AugmentableImages
import org.autojs.autojs.runtime.api.augment.ocr.Ocr
import org.autojs.autojs.runtime.api.vision.ScreenPerceptionPipeline
import org.autojs.autojs.runtime.api.vision.VisionBounds
import org.autojs.autojs.runtime.api.vision.VisionColorFeature
import org.autojs.autojs.runtime.api.vision.VisionImageMatch
import org.autojs.autojs.runtime.api.vision.VisionInput
import org.autojs.autojs.runtime.api.vision.VisionOcrBlock
import org.autojs.autojs.runtime.api.vision.VisionPipelineOptions
import org.autojs.autojs.runtime.api.vision.VisionSample
import org.autojs.autojs.runtime.api.vision.VisionSceneDefinition
import org.autojs.autojs.runtime.api.vision.VisionSceneRequirement
import org.autojs.autojs.runtime.api.vision.VisionTarget
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceDoubleNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

@Suppress("unused")
class Vision(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        "toString" to AS_LITERAL_TO_STRING,
        ::summary.name,
        ::targets.name,
        ::findText.name,
        ::findButton.name,
        ::observe.name,
        ::waitForScene.name,
    )

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun targets(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            val options = parseOptions(argList.firstOrNull())
            sample(scriptRuntime, options, null).targets.toRhinoTargets()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (text, rawOptions) = argList
            val options = parseOptions(rawOptions)
            val sample = sample(scriptRuntime, options, null)
            ScreenPerceptionPipeline.findText(
                sample.targets,
                coerceString(text),
                exact = optionBoolean(rawOptions, "exact") ?: false,
                ignoreCase = optionBoolean(rawOptions, "ignoreCase") ?: true,
            )?.toRhinoObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findButton(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (text, rawOptions) = argList
            val options = parseOptions(rawOptions)
            val sample = sample(scriptRuntime, options, null)
            ScreenPerceptionPipeline.findButton(
                sample.targets,
                coerceString(text),
                exact = optionBoolean(rawOptions, "exact") ?: false,
                ignoreCase = optionBoolean(rawOptions, "ignoreCase") ?: true,
            )?.toRhinoObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun observe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): VisionObservationNativeObject = ensureArgumentsAtMost(args, 1) { argList ->
            VisionObservationNativeObject(scriptRuntime, parseOptions(argList.firstOrNull(), observer = true))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForScene(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (sceneArg, rawOptions) = argList
            val scene = parseScene(sceneArg)
            val options = parseOptions(rawOptions)
            val timeoutMillis = optionLong(rawOptions, "timeout")
                ?: optionLong(rawOptions, "timeoutMillis")
                ?: optionLong(sceneArg, "timeout")
                ?: 5_000L
            val started = SystemClock.uptimeMillis()
            var attempts = 0
            var latestSample: VisionSample? = null
            var latestMatch = scene.match(emptyList())
            do {
                attempts += 1
                val sampled = sample(scriptRuntime, options, null)
                latestSample = sampled
                latestMatch = scene.match(sampled.targets)
                if (latestMatch.ok) break
                sleepInterruptibly(options.intervalMillis)
            } while (!scriptRuntime.isExiting && SystemClock.uptimeMillis() - started < timeoutMillis)

            val duration = SystemClock.uptimeMillis() - started
            val finalSample = latestSample
            linkedMapOf<String, Any?>(
                "ok" to latestMatch.ok,
                "success" to latestMatch.ok,
                "name" to latestMatch.name,
                "scene" to latestMatch.toMap(),
                "matched" to latestMatch.matched.map { it.toMap() },
                "missing" to latestMatch.missing,
                "attempts" to attempts,
                "duration" to duration,
                "elapsed" to duration,
                "packageName" to finalSample.packageName,
                "activity" to finalSample.activity,
                "currentPackage" to finalSample.packageName,
                "currentActivity" to finalSample.activity,
                "targets" to finalSample.targets.map { it.toMap() },
                "diagnostics" to finalSample.diagnostics,
            ).toNativeObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun summary(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            """
            [ Vision summary ]
            APIs: vision.targets(options), vision.findText(text, options), vision.findButton(text, options), vision.observe(options), vision.waitForScene(scene, options)
            Default sources: a11y+ocr
            Target fields: source, confidence, bounds, suggestedAction, currentPackage, currentActivity
            """.trimIndent()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toString(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            summary(scriptRuntime, args)
        }

        internal fun sample(
            scriptRuntime: ScriptRuntime,
            options: VisionRuntimeOptions,
            session: ApiImages.ScreenCaptureSession?,
        ): VisionSample {
            val diagnostics = mutableListOf<Map<String, Any?>>()
            val packageName = scriptRuntime.info.latestPackage
            val activity = scriptRuntime.info.latestActivity
            val snapshot = if (ScreenPerceptionPipeline.SOURCE_A11Y in options.pipeline.sources) {
                runCatching {
                    UiSnapshotTools.capture(
                        globalContext,
                        scriptRuntime.accessibilityBridge,
                        packageName,
                        activity,
                        UiSnapshotTools.CaptureOptions(
                            includeWindows = options.includeWindows,
                            maxDepth = options.maxDepth,
                            maxNodes = options.maxNodes,
                            redact = options.redact,
                            redactInputs = true,
                            redactPatterns = true,
                        ),
                    )
                }.onFailure {
                    diagnostics += diagnostic("a11y", false, it)
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val nodes = if (snapshot.isEmpty()) emptyList() else UiSnapshotTools.flattenNodes(snapshot)
            if (snapshot.isNotEmpty()) {
                diagnostics += linkedMapOf(
                    "source" to "a11y",
                    "ok" to true,
                    "nodeCount" to (snapshot["nodeCount"] ?: nodes.size),
                    "truncated" to snapshot["truncated"],
                )
            }

            val ocrBlocks = if (ScreenPerceptionPipeline.SOURCE_OCR in options.pipeline.sources) {
                collectOcrBlocks(scriptRuntime, options, session, diagnostics)
            } else {
                emptyList()
            }

            val input = VisionInput(
                packageName = packageName,
                activity = activity,
                a11ySnapshot = snapshot,
                a11yNodes = nodes,
                ocrBlocks = ocrBlocks,
                imageMatches = parseImageMatches(options.rawOptions),
                colorFeatures = parseColorFeatures(options.rawOptions),
                diagnostics = diagnostics,
            )
            return ScreenPerceptionPipeline.fuse(input, options.pipeline)
        }

        private fun collectOcrBlocks(
            scriptRuntime: ScriptRuntime,
            options: VisionRuntimeOptions,
            session: ApiImages.ScreenCaptureSession?,
            diagnostics: MutableList<Map<String, Any?>>,
        ): List<VisionOcrBlock> {
            val started = SystemClock.uptimeMillis()
            var image: ImageWrapper? = null
            return runCatching {
                image = when {
                    session != null -> session.nextFrame(options.frameTimeoutMillis) ?: session.latest()
                    else -> AugmentableImages.captureScreen(scriptRuntime, emptyArray()) as? ImageWrapper
                }
                val capt = image ?: return@runCatching emptyList()
                val ocrOptions = ocrOptions(options)
                val results = Ocr.detectWith(scriptRuntime, null, arrayOf(capt, ocrOptions)).toOcrResults()
                diagnostics += linkedMapOf(
                    "source" to "ocr",
                    "ok" to true,
                    "duration" to (SystemClock.uptimeMillis() - started),
                    "blockCount" to results.size,
                )
                results.map { it.toVisionBlock() }
            }.onFailure {
                diagnostics += diagnostic("ocr", false, it)
            }.getOrDefault(emptyList()).also {
                image?.shoot()
            }
        }

        private fun ocrOptions(options: VisionRuntimeOptions): NativeObject = newNativeObject().also { obj ->
            options.pipeline.region?.let { bounds ->
                obj.defineProp("region", listOf(bounds.left, bounds.top, bounds.width, bounds.height).toNativeArray())
            }
            options.rawOptions.prop("mode")?.takeUnless { it.isJsNullish() }?.let { obj.defineProp("mode", it) }
        }

        private fun OcrResult.toVisionBlock(): VisionOcrBlock = VisionOcrBlock(
            text = text,
            confidence = confidence.toDouble(),
            bounds = VisionBounds.fromRect(bounds),
            data = linkedMapOf("label" to label),
        )

        private fun NativeArray.toOcrResults(): List<OcrResult> = (0 until length.toInt()).mapNotNull { index ->
            when (val value = get(index, this)) {
                is OcrResult -> value
                is org.mozilla.javascript.Wrapper -> value.unwrap() as? OcrResult
                else -> null
            }
        }

        internal fun parseOptions(raw: Any?, observer: Boolean = false): VisionRuntimeOptions {
            val opt = raw as? NativeObject ?: newNativeObject().also {
                require(raw.isJsNullish()) {
                    "Argument \"options\" ${raw.jsBrief()} for vision must be a JavaScript Object"
                }
            }
            val sources = parseSources(opt.prop("sources") ?: opt.prop("source"))
            val interval = opt.inquire("interval") { coerceLongNumber(it) }
                ?: opt.inquire("intervalMillis") { coerceLongNumber(it) }
                ?: if (observer) 500L else 0L
            val frameTimeout = opt.inquire("frameTimeout") { coerceLongNumber(it) }
                ?: opt.inquire("frameTimeoutMillis") { coerceLongNumber(it) }
                ?: 1_800L
            val maxTargets = opt.inquire("maxTargets") { coerceIntNumber(it) } ?: 100
            val minConfidence = opt.inquire("minConfidence") { coerceDoubleNumber(it) } ?: 0.0
            return VisionRuntimeOptions(
                rawOptions = opt,
                pipeline = VisionPipelineOptions(
                    sources = sources,
                    region = parseBounds(opt.prop("region")),
                    minConfidence = minConfidence,
                    maxTargets = maxTargets,
                    includeInvisible = opt.inquire("includeInvisible", ::coerceBoolean, false),
                ),
                intervalMillis = interval.coerceAtLeast(0L),
                frameTimeoutMillis = frameTimeout.coerceAtLeast(1L),
                includeWindows = opt.inquire("includeWindows", ::coerceBoolean, true),
                maxDepth = opt.inquire("maxDepth") { coerceIntNumber(it) }?.coerceAtLeast(0) ?: 50,
                maxNodes = opt.inquire("maxNodes") { coerceIntNumber(it) }?.coerceIn(1, 10_000) ?: 2_000,
                redact = opt.inquire("redact", ::coerceBoolean, false),
            )
        }

        private fun parseSources(raw: Any?): Set<String> {
            if (raw.isJsNullish()) return VisionPipelineOptions.DEFAULT_SOURCES
            return when (raw) {
                is NativeArray -> ScreenPerceptionPipeline.normalizeSources((0 until raw.length.toInt()).mapNotNull { index ->
                    raw.get(index, raw).takeUnless { it.isJsNullish() }?.let(::coerceString)
                })
                is Iterable<*> -> ScreenPerceptionPipeline.normalizeSources(raw.mapNotNull { it?.let(::coerceString) })
                is Array<*> -> ScreenPerceptionPipeline.normalizeSources(raw.mapNotNull { it?.let(::coerceString) })
                else -> ScreenPerceptionPipeline.normalizeSources(listOf(coerceString(raw)))
            }
        }

        private fun parseBounds(raw: Any?): VisionBounds? {
            if (raw.isJsNullish()) return null
            return when (raw) {
                is NativeArray -> {
                    val values = (0 until minOf(raw.length.toInt(), 4)).map { index -> coerceIntNumber(raw.get(index, raw)) }
                    if (values.size < 4) null else VisionBounds(values[0], values[1], values[0] + values[2], values[1] + values[3])
                }
                is Map<*, *> -> VisionBounds.fromMap(raw)
                is Scriptable -> VisionBounds.fromMap(raw.toKotlinMap())
                else -> null
            }
        }

        private fun parseImageMatches(rawOptions: NativeObject?): List<VisionImageMatch> {
            val raw = rawOptions?.prop("imageMatches") ?: rawOptions?.prop("matches") ?: return emptyList()
            return sequenceItems(raw).mapNotNull { item ->
                val map = item.toKotlinMap()
                val bounds = parseBounds(map["bounds"] ?: map["region"]) ?: return@mapNotNull null
                VisionImageMatch(
                    label = coerceString(map["label"] ?: map["text"] ?: map["name"] ?: ""),
                    confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.78,
                    bounds = bounds,
                    template = coerceString(map["template"] ?: ""),
                    data = map,
                )
            }
        }

        private fun parseColorFeatures(rawOptions: NativeObject?): List<VisionColorFeature> {
            val raw = rawOptions?.prop("colorFeatures") ?: rawOptions?.prop("colors") ?: return emptyList()
            return sequenceItems(raw).mapNotNull { item ->
                val map = item.toKotlinMap()
                val bounds = parseBounds(map["bounds"] ?: map["region"]) ?: return@mapNotNull null
                VisionColorFeature(
                    label = coerceString(map["label"] ?: map["name"] ?: ""),
                    confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.62,
                    bounds = bounds,
                    color = coerceString(map["color"] ?: ""),
                    data = map,
                )
            }
        }

        private fun parseScene(raw: Any?): VisionSceneDefinition {
            if (raw is CharSequence) {
                val text = raw.toString().trim()
                if (text.startsWith("{")) {
                    return parseScene(JSONObject(text).toMap())
                }
                if (text.contains(":") && text.lines().size > 1) {
                    return parseScene(parseYamlLikeScene(text))
                }
                return VisionSceneDefinition(name = text, requirements = listOf(VisionSceneRequirement(text = text)))
            }
            val map = raw.toKotlinMap()
            val requirements = buildList {
                addAll(sequenceItems(map["texts"] ?: map["text"]).map { VisionSceneRequirement("text", coerceString(it)) })
                addAll(sequenceItems(map["buttons"] ?: map["button"]).map { VisionSceneRequirement("button", coerceString(it)) })
                addAll(sequenceItems(map["targets"]).mapNotNull { item ->
                    val targetMap = item.toKotlinMap()
                    val text = coerceString(targetMap["text"] ?: targetMap["label"] ?: "")
                    if (text.isBlank()) return@mapNotNull null
                    VisionSceneRequirement(
                        type = coerceString(targetMap["type"] ?: "text"),
                        text = text,
                        sources = parseSources(targetMap["source"] ?: targetMap["sources"]).takeUnless { it == VisionPipelineOptions.DEFAULT_SOURCES } ?: emptySet(),
                        exact = (targetMap["exact"] as? Boolean) ?: false,
                        ignoreCase = (targetMap["ignoreCase"] as? Boolean) ?: true,
                    )
                })
            }
            return VisionSceneDefinition(
                name = coerceString(map["name"] ?: ""),
                requirements = requirements,
                all = (map["all"] as? Boolean) ?: true,
            )
        }

        private fun sequenceItems(raw: Any?): List<Any?> {
            if (raw.isJsNullish()) return emptyList()
            return when (raw) {
            is NativeArray -> (0 until raw.length.toInt()).map { raw.get(it, raw) }.filterNot { it.isJsNullish() }
            is JSONArray -> (0 until raw.length()).map { raw.get(it) }.filterNot { it == JSONObject.NULL }
            is Iterable<*> -> raw.toList()
            is Array<*> -> raw.toList()
            else -> listOf(raw)
            }
        }

        private fun Any?.toKotlinMap(): Map<String, Any?> = when (this) {
            is NativeObject -> this.ids.associate { key -> key.toString() to this.get(key.toString(), this).unwrapRhinoValue() }
            is Scriptable -> this.ids.associate { key -> key.toString() to this.get(key.toString(), this).unwrapRhinoValue() }
            is JSONObject -> this.toMap()
            is Map<*, *> -> this.entries.associate { it.key.toString() to it.value.unwrapRhinoValue() }
            else -> emptyMap()
        }

        private fun Any?.unwrapRhinoValue(): Any? = when (this) {
            is org.mozilla.javascript.Wrapper -> unwrap()
            is NativeObject -> toKotlinMap()
            is Scriptable -> toKotlinMap()
            else -> this
        }

        private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
            when (val value = get(key)) {
                is JSONObject -> value.toMap()
                is JSONArray -> (0 until value.length()).map { index -> value.get(index) }.map { item ->
                    when (item) {
                        is JSONObject -> item.toMap()
                        is JSONArray -> (0 until item.length()).map { nestedIndex -> item.get(nestedIndex) }
                        JSONObject.NULL -> null
                        else -> item
                    }
                }
                org.json.JSONObject.NULL -> null
                else -> value
            }
        }

        private fun parseYamlLikeScene(text: String): Map<String, Any?> {
            val result = linkedMapOf<String, Any?>()
            var currentListKey: String? = null
            text.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isBlank() || line.startsWith("#")) return@forEach
                if (line.startsWith("-")) {
                    val key = currentListKey ?: return@forEach
                    val values = when (val existing = result[key]) {
                        is List<*> -> existing.map { it.toString() }.toMutableList()
                        is String -> mutableListOf(existing)
                        else -> mutableListOf()
                    }
                    values += line.removePrefix("-").trim().trim('"', '\'')
                    result[key] = values
                    return@forEach
                }
                val key = line.substringBefore(":", "").trim()
                val value = line.substringAfter(":", "").trim()
                if (key.isBlank()) return@forEach
                currentListKey = key
                result[key] = when {
                    value.isBlank() -> mutableListOf<String>()
                    value.startsWith("[") && value.endsWith("]") -> value.removePrefix("[").removeSuffix("]")
                        .split(",")
                        .map { it.trim().trim('"', '\'') }
                        .filter { it.isNotBlank() }
                    value.equals("true", ignoreCase = true) -> true
                    value.equals("false", ignoreCase = true) -> false
                    else -> value.trim('"', '\'')
                }
            }
            return result
        }

        private fun List<VisionTarget>.toRhinoTargets(): NativeArray = map { it.toRhinoObject() }.toNativeArray()

        internal fun VisionTarget.toRhinoObject(): NativeObject = toMap().toNativeObject()

        private fun diagnostic(source: String, ok: Boolean, throwable: Throwable): Map<String, Any?> = linkedMapOf(
            "source" to source,
            "ok" to ok,
            "error" to (throwable.message ?: throwable.javaClass.simpleName),
            "type" to throwable.javaClass.simpleName,
        )

        private fun optionBoolean(options: Any?, key: String): Boolean? = (options as? Scriptable)?.prop(key)
            ?.takeUnless { it.isJsNullish() }
            ?.let(::coerceBoolean)

        private fun optionLong(options: Any?, key: String): Long? = (options as? Scriptable)?.prop(key)
            ?.takeUnless { it.isJsNullish() }
            ?.let { coerceLongNumber(it) }

        private fun sleepInterruptibly(intervalMillis: Long) {
            if (intervalMillis <= 0L) return
            try {
                Thread.sleep(intervalMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

data class VisionRuntimeOptions(
    val rawOptions: NativeObject,
    val pipeline: VisionPipelineOptions,
    val intervalMillis: Long,
    val frameTimeoutMillis: Long,
    val includeWindows: Boolean,
    val maxDepth: Int,
    val maxNodes: Int,
    val redact: Boolean,
)
