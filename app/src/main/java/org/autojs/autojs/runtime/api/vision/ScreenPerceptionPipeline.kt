package org.autojs.autojs.runtime.api.vision

import android.graphics.Rect
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

data class VisionBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    private val area: Int get() = width * height

    fun isValid(): Boolean = width > 0 && height > 0

    fun intersects(other: VisionBounds): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun intersectionOverUnion(other: VisionBounds): Double {
        val overlapLeft = max(left, other.left)
        val overlapTop = max(top, other.top)
        val overlapRight = minOf(right, other.right)
        val overlapBottom = minOf(bottom, other.bottom)
        val overlapArea = (overlapRight - overlapLeft).coerceAtLeast(0) * (overlapBottom - overlapTop).coerceAtLeast(0)
        val unionArea = area + other.area - overlapArea
        return if (unionArea <= 0) 0.0 else overlapArea.toDouble() / unionArea.toDouble()
    }

    fun union(other: VisionBounds): VisionBounds = VisionBounds(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )

    fun distanceTo(other: VisionBounds): Double = hypot(
        (centerX - other.centerX).toDouble(),
        (centerY - other.centerY).toDouble(),
    )

    fun toMap(): Map<String, Int> = linkedMapOf(
        "left" to left,
        "top" to top,
        "right" to right,
        "bottom" to bottom,
        "width" to width,
        "height" to height,
        "centerX" to centerX,
        "centerY" to centerY,
    )

    companion object {
        fun fromRect(rect: Rect): VisionBounds = VisionBounds(rect.left, rect.top, rect.right, rect.bottom)

        fun fromMap(value: Map<*, *>?): VisionBounds? {
            if (value == null) return null
            val left = value.number("left") ?: value.number("x") ?: return null
            val top = value.number("top") ?: value.number("y") ?: return null
            val right = value.number("right")
            val bottom = value.number("bottom")
            val width = value.number("width") ?: value.number("w")
            val height = value.number("height") ?: value.number("h")
            return when {
                right != null && bottom != null -> VisionBounds(left, top, right, bottom)
                width != null && height != null -> VisionBounds(left, top, left + width, top + height)
                else -> null
            }
        }

        private fun Map<*, *>.number(key: String): Int? = (this[key] as? Number)?.toInt()
    }
}

data class VisionPipelineOptions(
    val sources: Set<String> = DEFAULT_SOURCES,
    val region: VisionBounds? = null,
    val minConfidence: Double = 0.0,
    val maxTargets: Int = 100,
    val includeInvisible: Boolean = false,
) {
    companion object {
        val DEFAULT_SOURCES = linkedSetOf(ScreenPerceptionPipeline.SOURCE_A11Y, ScreenPerceptionPipeline.SOURCE_OCR)
    }
}

data class VisionOcrBlock(
    val text: String,
    val confidence: Double,
    val bounds: VisionBounds,
    val data: Map<String, Any?> = emptyMap(),
)

data class VisionImageMatch(
    val label: String,
    val confidence: Double,
    val bounds: VisionBounds,
    val template: String = "",
    val data: Map<String, Any?> = emptyMap(),
)

data class VisionColorFeature(
    val label: String,
    val confidence: Double,
    val bounds: VisionBounds,
    val color: String = "",
    val data: Map<String, Any?> = emptyMap(),
)

data class VisionInput(
    val packageName: String = "",
    val activity: String = "",
    val a11ySnapshot: Map<String, Any?> = emptyMap(),
    val a11yNodes: List<Map<String, Any?>> = emptyList(),
    val ocrBlocks: List<VisionOcrBlock> = emptyList(),
    val imageMatches: List<VisionImageMatch> = emptyList(),
    val colorFeatures: List<VisionColorFeature> = emptyList(),
    val diagnostics: List<Map<String, Any?>> = emptyList(),
)

data class VisionSample(
    val targets: List<VisionTarget>,
    val packageName: String,
    val activity: String,
    val sources: Set<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val diagnostics: List<Map<String, Any?>> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "timestamp" to timestamp,
        "packageName" to packageName,
        "activity" to activity,
        "currentPackage" to packageName,
        "currentActivity" to activity,
        "sources" to sources.toList(),
        "targetCount" to targets.size,
        "targets" to targets.map { it.toMap() },
        "diagnostics" to diagnostics,
    )
}

data class VisionTarget(
    val id: String,
    val text: String,
    val desc: String,
    val label: String,
    val source: String,
    val sources: List<String>,
    val confidence: Double,
    val bounds: VisionBounds,
    val suggestedAction: VisionSuggestedAction,
    val packageName: String,
    val activity: String,
    val className: String = "",
    val selector: Map<String, Any?> = emptyMap(),
    val explanation: List<String> = emptyList(),
    val data: Map<String, Any?> = emptyMap(),
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "text" to text,
        "desc" to desc,
        "label" to label,
        "source" to source,
        "sources" to sources,
        "confidence" to confidence,
        "bounds" to bounds.toMap(),
        "suggestedAction" to suggestedAction.toMap(),
        "action" to suggestedAction.type,
        "packageName" to packageName,
        "activity" to activity,
        "currentPackage" to packageName,
        "currentActivity" to activity,
        "className" to className,
        "selector" to selector,
        "explanation" to explanation,
        "data" to data,
    )
}

data class VisionSuggestedAction(
    val type: String,
    val x: Int,
    val y: Int,
    val api: String,
    val reason: String,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "type" to type,
        "x" to x,
        "y" to y,
        "api" to api,
        "reason" to reason,
    )
}

data class VisionSceneDefinition(
    val name: String = "",
    val requirements: List<VisionSceneRequirement> = emptyList(),
    val all: Boolean = true,
) {
    fun match(targets: List<VisionTarget>): VisionSceneMatch {
        val matched = requirements.mapNotNull { requirement ->
            targets.find { requirement.matches(it) }?.let { requirement to it }
        }
        val missing = requirements.filterNot { requirement -> matched.any { it.first == requirement } }
        val ok = when {
            requirements.isEmpty() -> targets.isNotEmpty()
            all -> missing.isEmpty()
            else -> matched.isNotEmpty()
        }
        return VisionSceneMatch(
            ok = ok,
            name = name,
            matched = matched.map { it.second },
            missing = missing.map { it.toMap() },
        )
    }
}

data class VisionSceneRequirement(
    val type: String = "text",
    val text: String,
    val sources: Set<String> = emptySet(),
    val exact: Boolean = false,
    val ignoreCase: Boolean = true,
) {
    fun matches(target: VisionTarget): Boolean {
        if (sources.isNotEmpty() && target.sources.none { it in sources }) return false
        if (type.equals("button", ignoreCase = true) && target.suggestedAction.type !in setOf("click", "tap")) return false
        val haystack = listOf(target.text, target.desc, target.label).joinToString(" ").trim()
        return textMatches(haystack, text, exact, ignoreCase)
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "type" to type,
        "text" to text,
        "sources" to sources.toList(),
        "exact" to exact,
    )
}

data class VisionSceneMatch(
    val ok: Boolean,
    val name: String,
    val matched: List<VisionTarget>,
    val missing: List<Map<String, Any?>>,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "ok" to ok,
        "name" to name,
        "matched" to matched.map { it.toMap() },
        "missing" to missing,
    )
}

object ScreenPerceptionPipeline {

    const val SOURCE_A11Y = "a11y"
    const val SOURCE_OCR = "ocr"
    const val SOURCE_IMAGE = "image"
    const val SOURCE_COLOR = "color"

    private val sourceOrder = listOf(SOURCE_A11Y, SOURCE_OCR, SOURCE_IMAGE, SOURCE_COLOR)

    fun fuse(input: VisionInput, options: VisionPipelineOptions = VisionPipelineOptions()): VisionSample {
        val signals = buildList {
            if (SOURCE_A11Y in options.sources) {
                addAll(input.a11yNodes.mapNotNull { it.toA11ySignal(input, options) })
            }
            if (SOURCE_OCR in options.sources) {
                addAll(input.ocrBlocks.mapNotNull { it.toOcrSignal(input) })
            }
            if (SOURCE_IMAGE in options.sources) {
                addAll(input.imageMatches.mapNotNull { it.toImageSignal(input) })
            }
            if (SOURCE_COLOR in options.sources) {
                addAll(input.colorFeatures.mapNotNull { it.toColorSignal(input) })
            }
        }.filter { signal ->
            signal.bounds.isValid() &&
                (options.region == null || signal.bounds.intersects(options.region)) &&
                signal.confidence >= options.minConfidence
        }

        val groups = mutableListOf<TargetBuilder>()
        signals.sortedWith(compareBy<VisionSignal> { sourceOrder.indexOf(it.source).takeIf { index -> index >= 0 } ?: 99 }
            .thenByDescending { it.confidence })
            .forEach { signal ->
                val match = groups
                    .map { it to it.matchScore(signal) }
                    .filter { it.second > 0.0 }
                    .maxByOrNull { it.second }
                if (match == null) {
                    groups += TargetBuilder(signal)
                } else {
                    match.first.merge(signal)
                }
            }

        val targets = groups
            .mapIndexed { index, builder -> builder.build("vision:$index") }
            .filter { it.confidence >= options.minConfidence }
            .sortedWith(compareByDescending<VisionTarget> { it.confidence }.thenBy { it.bounds.top }.thenBy { it.bounds.left })
            .take(options.maxTargets.coerceAtLeast(1))

        return VisionSample(
            targets = targets,
            packageName = input.packageName,
            activity = input.activity,
            sources = options.sources,
            diagnostics = input.diagnostics,
        )
    }

    fun findText(targets: List<VisionTarget>, query: String, exact: Boolean = false, ignoreCase: Boolean = true): VisionTarget? {
        return targets
            .filter { target ->
                textMatches(listOf(target.text, target.desc, target.label).joinToString(" "), query, exact, ignoreCase)
            }
            .maxWithOrNull(compareBy<VisionTarget> { it.confidence }.thenByDescending { it.sources.size })
    }

    fun findButton(targets: List<VisionTarget>, query: String, exact: Boolean = false, ignoreCase: Boolean = true): VisionTarget? {
        return targets
            .filter { target ->
                target.suggestedAction.type in setOf("click", "tap") &&
                    textMatches(listOf(target.text, target.desc, target.label).joinToString(" "), query, exact, ignoreCase)
            }
            .maxWithOrNull(compareBy<VisionTarget> { if (it.suggestedAction.type == "click") 1 else 0 }.thenBy { it.confidence })
    }

    fun normalizeSources(rawSources: Iterable<String>): Set<String> {
        if (rawSources.any { it.trim().equals("all", ignoreCase = true) }) {
            return sourceOrder.toCollection(linkedSetOf())
        }
        val normalized = rawSources
            .flatMap { it.split("+", ",", "|", " ") }
            .map { it.trim().lowercase(Locale.ROOT) }
            .mapNotNull {
                when (it) {
                    "", "all" -> null
                    "accessibility", "ui", "uiautomator" -> SOURCE_A11Y
                    "cv", "image", "template" -> SOURCE_IMAGE
                    "colour" -> SOURCE_COLOR
                    SOURCE_A11Y, SOURCE_OCR, SOURCE_COLOR -> it
                    else -> it.takeIf { source -> source in sourceOrder }
                }
            }
            .toCollection(linkedSetOf())
        return normalized.ifEmpty { VisionPipelineOptions.DEFAULT_SOURCES }
    }

    private fun Map<String, Any?>.toA11ySignal(input: VisionInput, options: VisionPipelineOptions): VisionSignal? {
        val visible = boolean("visibleToUser", true)
        if (!options.includeInvisible && !visible) return null
        val bounds = VisionBounds.fromMap(this["bounds"] as? Map<*, *>) ?: return null
        val text = string("text").takeUnlessRedacted()
        val desc = string("desc").takeUnlessRedacted()
        val resourceName = string("resourceName").ifBlank { string("id") }
        val className = string("className")
        val clickable = boolean("clickable")
        val editable = boolean("editable")
        val scrollable = boolean("scrollable")
        if (text.isBlank() && desc.isBlank() && resourceName.isBlank() && !clickable && !editable && !scrollable) {
            return null
        }
        val selector = selectorMap()
        val selectorScore = (selector["score"] as? Number)?.toDouble()?.div(100.0) ?: 0.62
        val textBonus = if (text.isNotBlank() || desc.isNotBlank()) 0.08 else 0.0
        val clickBonus = if (clickable) 0.05 else 0.0
        val disabledPenalty = if (!boolean("enabled", true)) 0.2 else 0.0
        val confidence = (selectorScore + textBonus + clickBonus - disabledPenalty).coerceIn(0.1, 0.99)
        val actionType = when {
            editable -> "setText"
            clickable || className.contains("Button", ignoreCase = true) -> "click"
            scrollable -> "scroll"
            else -> "inspect"
        }
        val label = firstNonBlank(text, desc, resourceName, className)
        return VisionSignal(
            source = SOURCE_A11Y,
            text = text,
            desc = desc,
            label = label,
            confidence = confidence,
            bounds = bounds,
            actionType = actionType,
            packageName = input.packageName,
            activity = input.activity,
            className = className,
            selector = selector,
            explanation = listOf("accessibility node"),
            data = linkedMapOf(
                "key" to this["key"],
                "path" to this["path"],
                "resourceName" to resourceName,
                "clickable" to clickable,
                "editable" to editable,
                "visibleToUser" to visible,
            ),
        )
    }

    private fun VisionOcrBlock.toOcrSignal(input: VisionInput): VisionSignal? {
        if (text.isBlank()) return null
        return VisionSignal(
            source = SOURCE_OCR,
            text = text,
            desc = "",
            label = text,
            confidence = confidence.coerceIn(0.0, 1.0),
            bounds = bounds,
            actionType = "tap",
            packageName = input.packageName,
            activity = input.activity,
            explanation = listOf("ocr text block"),
            data = data,
        )
    }

    private fun VisionImageMatch.toImageSignal(input: VisionInput): VisionSignal? {
        if (label.isBlank() && template.isBlank()) return null
        return VisionSignal(
            source = SOURCE_IMAGE,
            text = "",
            desc = "",
            label = firstNonBlank(label, template),
            confidence = confidence.coerceIn(0.0, 1.0),
            bounds = bounds,
            actionType = "tap",
            packageName = input.packageName,
            activity = input.activity,
            explanation = listOf("image template match"),
            data = data + ("template" to template),
        )
    }

    private fun VisionColorFeature.toColorSignal(input: VisionInput): VisionSignal? {
        return VisionSignal(
            source = SOURCE_COLOR,
            text = "",
            desc = "",
            label = firstNonBlank(label, color, "color_feature"),
            confidence = confidence.coerceIn(0.0, 1.0),
            bounds = bounds,
            actionType = "inspect",
            packageName = input.packageName,
            activity = input.activity,
            explanation = listOf("color or region feature"),
            data = data + ("color" to color),
        )
    }

    private fun Map<String, Any?>.selectorMap(): Map<String, Any?> {
        val selector = this["selector"] as? Map<*, *> ?: return emptyMap()
        return selector.entries.associate { it.key.toString() to it.value }
    }

    private fun Map<String, Any?>.string(key: String): String = (this[key] as? CharSequence)?.toString().orEmpty()

    private fun Map<String, Any?>.boolean(key: String, default: Boolean = false): Boolean = (this[key] as? Boolean) ?: default
}

private data class VisionSignal(
    val source: String,
    val text: String,
    val desc: String,
    val label: String,
    val confidence: Double,
    val bounds: VisionBounds,
    val actionType: String,
    val packageName: String,
    val activity: String,
    val className: String = "",
    val selector: Map<String, Any?> = emptyMap(),
    val explanation: List<String> = emptyList(),
    val data: Map<String, Any?> = emptyMap(),
)

private class TargetBuilder(signal: VisionSignal) {
    private val sources = linkedSetOf<String>()
    private val explanations = mutableListOf<String>()
    private val data = linkedMapOf<String, Any?>()
    private var text = ""
    private var desc = ""
    private var label = ""
    private var confidenceProduct = 1.0
    private var bounds = signal.bounds
    private var actionType = "inspect"
    private var packageName = signal.packageName
    private var activity = signal.activity
    private var className = ""
    private var selector: Map<String, Any?> = emptyMap()

    init {
        merge(signal)
    }

    fun matchScore(signal: VisionSignal): Double {
        val currentLabel = firstNonBlank(text, desc, label)
        val nextLabel = firstNonBlank(signal.text, signal.desc, signal.label)
        val sameSource = sources.size == 1 && signal.source in sources
        val textScore = when {
            currentLabel.isBlank() || nextLabel.isBlank() -> 0.0
            currentLabel.equals(nextLabel, ignoreCase = true) -> 0.65
            sameSource -> 0.0
            currentLabel.contains(nextLabel, ignoreCase = true) || nextLabel.contains(currentLabel, ignoreCase = true) -> 0.45
            else -> 0.0
        }
        val overlap = bounds.intersectionOverUnion(signal.bounds)
        val distanceLimit = max(max(bounds.width, bounds.height), max(signal.bounds.width, signal.bounds.height)).coerceAtLeast(1) * 1.25
        val proximity = if (!sameSource && bounds.distanceTo(signal.bounds) <= distanceLimit) 0.35 else 0.0
        return max(overlap, max(textScore, proximity)).takeIf { it >= 0.34 } ?: 0.0
    }

    fun merge(signal: VisionSignal) {
        sources += signal.source
        explanations += signal.explanation
        if (text.isBlank() || (signal.text.isNotBlank() && signal.source != ScreenPerceptionPipeline.SOURCE_A11Y)) {
            text = firstNonBlank(text, signal.text)
        }
        if (desc.isBlank()) desc = signal.desc
        if (label.isBlank() || labelLooksStructural(label)) label = firstNonBlank(signal.label, label)
        confidenceProduct *= (1.0 - signal.confidence.coerceIn(0.0, 1.0))
        bounds = bounds.union(signal.bounds)
        actionType = pickAction(actionType, signal.actionType)
        if (packageName.isBlank()) packageName = signal.packageName
        if (activity.isBlank()) activity = signal.activity
        if (className.isBlank()) className = signal.className
        if (selector.isEmpty()) selector = signal.selector
        data["by_${signal.source}"] = signal.data
    }

    fun build(id: String): VisionTarget {
        val orderedSources = sources.sortedBy { sourceOrderIndex(it) }
        val source = orderedSources.joinToString("+")
        val confidence = (1.0 - confidenceProduct + if (orderedSources.size > 1) 0.04 else 0.0).coerceIn(0.0, 0.99)
        val finalLabel = firstNonBlank(text, desc, label, source)
        val finalExplanations = explanations.distinct().toMutableList().also {
            if (ScreenPerceptionPipeline.SOURCE_OCR in sources && ScreenPerceptionPipeline.SOURCE_A11Y !in sources) {
                it += "ocr bounds fallback"
            }
            if (ScreenPerceptionPipeline.SOURCE_IMAGE in sources && ScreenPerceptionPipeline.SOURCE_A11Y in sources && text.isBlank()) {
                it += "image match supplied label for textless accessibility node"
            }
            if (orderedSources.size > 1) {
                it += "fused sources: $source"
            }
        }
        return VisionTarget(
            id = id,
            text = text,
            desc = desc,
            label = finalLabel,
            source = source,
            sources = orderedSources,
            confidence = confidence,
            bounds = bounds,
            suggestedAction = suggestedAction(actionType, bounds),
            packageName = packageName,
            activity = activity,
            className = className,
            selector = selector,
            explanation = finalExplanations,
            data = data,
        )
    }

    private fun sourceOrderIndex(source: String): Int = when (source) {
        ScreenPerceptionPipeline.SOURCE_A11Y -> 0
        ScreenPerceptionPipeline.SOURCE_OCR -> 1
        ScreenPerceptionPipeline.SOURCE_IMAGE -> 2
        ScreenPerceptionPipeline.SOURCE_COLOR -> 3
        else -> 99
    }

    private fun pickAction(current: String, next: String): String {
        val priority = listOf("setText", "click", "tap", "scroll", "inspect")
        fun rank(value: String) = priority.indexOf(value).takeIf { it >= 0 } ?: priority.size
        return if (rank(next) < rank(current)) next else current
    }

    private fun suggestedAction(type: String, bounds: VisionBounds): VisionSuggestedAction = when (type) {
        "setText" -> VisionSuggestedAction(type, bounds.centerX, bounds.centerY, "auto.stableSetText", "editable accessibility target")
        "click" -> VisionSuggestedAction(type, bounds.centerX, bounds.centerY, "auto.stableClick", "clickable accessibility target")
        "tap" -> VisionSuggestedAction(type, bounds.centerX, bounds.centerY, "click", "coordinate fallback from visual bounds")
        "scroll" -> VisionSuggestedAction(type, bounds.centerX, bounds.centerY, "swipe", "scrollable accessibility target")
        else -> VisionSuggestedAction("inspect", bounds.centerX, bounds.centerY, "none", "inspect target before acting")
    }
}

private fun textMatches(haystack: String, needle: String, exact: Boolean, ignoreCase: Boolean): Boolean {
    if (needle.isBlank()) return false
    return when {
        exact -> haystack.equals(needle, ignoreCase)
        else -> haystack.contains(needle, ignoreCase)
    }
}

private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()

private fun String.takeUnlessRedacted(): String = takeUnless { it == "[redacted]" }.orEmpty()

private fun labelLooksStructural(value: String): Boolean =
    value.startsWith("android.", ignoreCase = true) || value.contains(":id/")
