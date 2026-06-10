package org.autojs.autojs.core.accessibility

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.core.automator.UiObject

object UiSnapshotTools {

    private val phoneRegex = Regex("""(?<!\d)(?:\+?\d[\d\-\s]{6,}\d)(?!\d)""")
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val volatileTextRegex = Regex("""\d{2,}|[￥$]\s*\d+|\d{1,2}:\d{2}|\d{4}[-/]\d{1,2}[-/]\d{1,2}""")

    data class CaptureOptions(
        val includeWindows: Boolean = true,
        val maxDepth: Int = 50,
        val maxNodes: Int = 2_000,
        val redact: Boolean = false,
        val redactInputs: Boolean = true,
        val redactPatterns: Boolean = true,
    )

    fun capture(
        context: Context,
        bridge: AccessibilityBridge,
        packageName: String,
        activityName: String,
        options: CaptureOptions = CaptureOptions(),
    ): Map<String, Any?> {
        val counter = NodeCounter(options.maxNodes)
        val service = bridge.service
        val windows = when {
            options.includeWindows && service != null -> service.windows
                .mapIndexedNotNull { index, window -> window.toSnapshotWindow(context, index, counter, options) }
            else -> emptyList()
        }.ifEmpty {
            bridge.windowRoots()
                .filterNotNull()
                .mapIndexed { index, root ->
                    mutableMapOf<String, Any?>(
                        "index" to index,
                        "type" to "root",
                        "title" to "",
                        "packageName" to packageName,
                        "active" to (index == 0),
                        "focused" to (index == 0),
                        "root" to UiObject.createRoot(root).toSnapshotNode(context, options, counter, index, "w$index", 0),
                    )
                }
        }

        val snapshot = linkedMapOf<String, Any?>(
            "version" to 1,
            "timestamp" to System.currentTimeMillis(),
            "packageName" to packageName,
            "activity" to activityName,
            "apiLevel" to Build.VERSION.SDK_INT,
            "windowCount" to windows.size,
            "nodeCount" to counter.count,
            "truncated" to counter.truncated,
            "windows" to windows,
        )
        return scoreSelectors(snapshot)
    }

    fun scoreSelectors(snapshot: MutableMap<String, Any?>): MutableMap<String, Any?> {
        val nodes = flattenNodes(snapshot)
        nodes.forEach { node ->
            node["selector"] = bestSelector(node, nodes)
        }
        return snapshot
    }

    fun diff(before: Map<String, Any?>, after: Map<String, Any?>): Map<String, Any?> {
        val beforeNodes = flattenNodes(before)
        val afterNodes = flattenNodes(after)
        val matches = matchNodes(beforeNodes, afterNodes)
        val matchedBefore = matches.map { it.before }.toSet()
        val matchedAfter = matches.map { it.after }.toSet()
        val added = afterNodes.filterNot { it in matchedAfter }
        val removed = beforeNodes.filterNot { it in matchedBefore }

        val changed = matches.mapNotNull { match ->
            val oldNode = match.before
            val newNode = match.after
            changedFields(oldNode, newNode).takeIf { it.isNotEmpty() }?.let { fields ->
                linkedMapOf(
                    "key" to match.key,
                    "before" to nodeSummary(oldNode),
                    "after" to nodeSummary(newNode),
                    "fields" to fields,
                )
            }
        }

        val stableSelectors = matches.mapNotNull { stableSelectorAcrossSnapshots(it) }
            .sortedByDescending { (it["score"] as? Number)?.toInt() ?: 0 }

        return linkedMapOf(
            "beforeTimestamp" to before["timestamp"],
            "afterTimestamp" to after["timestamp"],
            "addedCount" to added.size,
            "removedCount" to removed.size,
            "changedCount" to changed.size,
            "added" to added.map { nodeSummary(it) },
            "removed" to removed.map { nodeSummary(it) },
            "changed" to changed,
            "stableSelectors" to stableSelectors.take(50),
        )
    }

    private fun AccessibilityWindowInfo.toSnapshotWindow(
        context: Context,
        index: Int,
        counter: NodeCounter,
        options: CaptureOptions,
    ): MutableMap<String, Any?>? {
        val root = root ?: return null
        return mutableMapOf(
            "index" to index,
            "type" to typeName(type),
            "title" to (title?.toString() ?: ""),
            "packageName" to (root.packageName?.toString() ?: ""),
            "active" to isActive,
            "focused" to isFocused,
            "root" to UiObject.createRoot(root).toSnapshotNode(context, options, counter, index, "w$index", 0),
        )
    }

    private fun UiObject.toSnapshotNode(
        context: Context,
        options: CaptureOptions,
        counter: NodeCounter,
        windowIndex: Int,
        path: String,
        depth: Int,
    ): MutableMap<String, Any?> {
        counter.countNode()
        val bounds = bounds()
        val rawText = text()
        val rawDesc = desc().orEmpty()
        val textRedacted = shouldRedact(rawText, this, options)
        val descRedacted = shouldRedact(rawDesc, this, options)
        val node = mutableMapOf<String, Any?>(
            "key" to nodeKey(windowIndex, path),
            "path" to path,
            "windowIndex" to windowIndex,
            "text" to if (textRedacted) "[redacted]" else rawText,
            "desc" to if (descRedacted) "[redacted]" else rawDesc,
            "textRedacted" to textRedacted,
            "descRedacted" to descRedacted,
            "id" to simpleId().orEmpty(),
            "resourceName" to fullId().orEmpty(),
            "className" to className().orEmpty(),
            "packageName" to packageName().orEmpty(),
            "bounds" to bounds.toMap(),
            "clickable" to clickable(),
            "longClickable" to longClickable(),
            "enabled" to enabled(),
            "visibleToUser" to visibleToUser(),
            "scrollable" to scrollable(),
            "editable" to editable(),
            "checkable" to checkable(),
            "checked" to checked(),
            "selected" to selected(),
            "focusable" to focusable(),
            "focused" to focused(),
            "depth" to depth,
            "index" to indexInParent(),
            "drawingOrder" to drawingOrder(),
            "childCount" to childCount(),
        )
        val children = mutableListOf<Map<String, Any?>>()
        if (depth < options.maxDepth && !counter.truncated) {
            for (index in 0 until childCount()) {
                if (counter.truncated) break
                child(index)?.let { child ->
                    children += child.toSnapshotNode(context, options, counter, windowIndex, "$path/$index", depth + 1)
                }
            }
        }
        node["children"] = children
        return node
    }

    private fun bestSelector(node: MutableMap<String, Any?>, allNodes: List<MutableMap<String, Any?>>): Map<String, Any?> {
        val candidates = buildList {
            candidate(node, allNodes, "resourceName", "id", 95)?.let(::add)
            candidate(node, allNodes, "desc", "desc", 76)?.let(::add)
            candidate(node, allNodes, "text", "text", 68)?.let(::add)
            candidate(node, allNodes, "className", "className", 42)?.let(::add)
            indexCandidate(node, allNodes)?.let(::add)
            boundsCandidate(node)?.let(::add)
        }.sortedByDescending { it.score }

        val best = candidates.firstOrNull()
        return linkedMapOf(
            "best" to (best?.selector ?: ""),
            "score" to (best?.score ?: 0),
            "matchCount" to (best?.matchCount ?: 0),
            "reasons" to (best?.reasons ?: emptyList<String>()),
            "candidates" to candidates.map {
                linkedMapOf(
                    "selector" to it.selector,
                    "score" to it.score,
                    "matchCount" to it.matchCount,
                    "reasons" to it.reasons,
                )
            },
        )
    }

    private fun matchNodes(
        beforeNodes: List<MutableMap<String, Any?>>,
        afterNodes: List<MutableMap<String, Any?>>,
    ): List<NodeMatch> {
        val matches = mutableListOf<NodeMatch>()
        val remainingBefore = beforeNodes.toMutableSet()
        val remainingAfter = afterNodes.toMutableSet()

        fun matchBy(keyOf: (Map<String, Any?>) -> String?) {
            val beforeByKey = remainingBefore
                .mapNotNull { node -> keyOf(node)?.let { it to node } }
                .groupBy({ it.first }, { it.second })
            val afterByKey = remainingAfter
                .mapNotNull { node -> keyOf(node)?.let { it to node } }
                .groupBy({ it.first }, { it.second })
            beforeByKey.keys.intersect(afterByKey.keys)
                .filter { key -> beforeByKey.getValue(key).size == 1 && afterByKey.getValue(key).size == 1 }
                .forEach { key ->
                    val before = beforeByKey.getValue(key).first()
                    val after = afterByKey.getValue(key).first()
                    if (before in remainingBefore && after in remainingAfter) {
                        remainingBefore -= before
                        remainingAfter -= after
                        matches += NodeMatch(key, before, after)
                    }
                }
        }

        matchBy(::resourceIdentityKey)
        matchBy(::structuralIdentityKey)
        matchBy(::selectorIdentityKey)
        return matches
    }

    private fun candidate(
        node: Map<String, Any?>,
        allNodes: List<Map<String, Any?>>,
        field: String,
        method: String,
        baseScore: Int,
    ): SelectorCandidate? {
        val value = (node[field] as? String).orEmpty().takeIf { it.isNotBlank() && it != "[redacted]" } ?: return null
        val matchCount = allNodes.count { it[field] == value }
        val reasons = mutableListOf<String>()
        var score = baseScore
        when {
            matchCount == 1 -> reasons += "unique"
            else -> {
                score -= (matchCount - 1).coerceAtMost(5) * 8
                reasons += "matches=$matchCount"
            }
        }
        if (field in setOf("text", "desc") && volatileTextRegex.containsMatchIn(value)) {
            score -= 20
            reasons += "volatile-text"
        }
        if (field == "className") {
            reasons += "class-only"
        }
        return SelectorCandidate(
            selector = "$method(${quoteJs(value)})",
            score = score.coerceIn(0, 100),
            matchCount = matchCount,
            reasons = reasons,
        )
    }

    private fun indexCandidate(node: Map<String, Any?>, allNodes: List<Map<String, Any?>>): SelectorCandidate? {
        val index = (node["index"] as? Number)?.toInt()?.takeIf { it >= 0 } ?: return null
        val className = (node["className"] as? String).orEmpty()
        val matchCount = allNodes.count {
            (it["index"] as? Number)?.toInt() == index && (className.isBlank() || it["className"] == className)
        }.coerceAtLeast(1)
        return SelectorCandidate(
            selector = "indexInParent($index)",
            score = (28 - (matchCount - 1).coerceAtMost(4) * 4).coerceIn(0, 100),
            matchCount = matchCount,
            reasons = listOf("index-fragile") + if (matchCount == 1) listOf("unique") else listOf("matches=$matchCount"),
        )
    }

    private fun boundsCandidate(node: Map<String, Any?>): SelectorCandidate? {
        val bounds = node["bounds"] as? Map<*, *> ?: return null
        val left = (bounds["left"] as? Number)?.toInt() ?: return null
        val top = (bounds["top"] as? Number)?.toInt() ?: return null
        val right = (bounds["right"] as? Number)?.toInt() ?: return null
        val bottom = (bounds["bottom"] as? Number)?.toInt() ?: return null
        return SelectorCandidate(
            selector = "bounds($left, $top, $right, $bottom)",
            score = 18,
            matchCount = 1,
            reasons = listOf("bounds-fragile"),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun flattenNodes(snapshot: Map<String, Any?>): List<MutableMap<String, Any?>> {
        val result = mutableListOf<MutableMap<String, Any?>>()
        val windows = snapshot["windows"] as? List<*> ?: return result
        windows.forEach { window ->
            val root = (window as? Map<*, *>)?.get("root") as? MutableMap<String, Any?>
            if (root != null) {
                collectNode(root, result)
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectNode(node: MutableMap<String, Any?>, result: MutableList<MutableMap<String, Any?>>) {
        result += node
        (node["children"] as? List<*>)?.forEach { child ->
            (child as? MutableMap<String, Any?>)?.let { collectNode(it, result) }
        }
    }

    private fun resourceIdentityKey(node: Map<String, Any?>): String? {
        val resourceName = (node["resourceName"] as? String).orEmpty().takeIfStableValue()
        val id = (node["id"] as? String).orEmpty().takeIfStableValue()
        return when {
            resourceName != null -> "resource:$resourceName"
            id != null -> "id:$id"
            else -> null
        }
    }

    private fun structuralIdentityKey(node: Map<String, Any?>): String? {
        val path = (node["path"] as? String).orEmpty().takeIf { it.isNotBlank() } ?: return null
        val className = (node["className"] as? String).orEmpty()
        val windowIndex = (node["windowIndex"] as? Number)?.toInt() ?: 0
        return "structure:$windowIndex:$path:$className"
    }

    private fun selectorIdentityKey(node: Map<String, Any?>): String? {
        val selector = selectorSummary(node)["selector"] as? String
        return selector?.takeIf { it.isNotBlank() }?.let { "selector:$it" }
    }

    private fun stableKey(node: Map<String, Any?>): String {
        val selector = selectorSummary(node)["selector"] as? String
        return selector?.takeIf { it.isNotBlank() }
            ?: resourceIdentityKey(node)
            ?: structuralIdentityKey(node)
            ?: (node["key"] as? String).orEmpty()
    }

    private fun selectorSummary(node: Map<String, Any?>): Map<String, Any?> {
        val selector = node["selector"] as? Map<*, *>
        return linkedMapOf(
            "selector" to (selector?.get("best") as? String).orEmpty(),
            "score" to ((selector?.get("score") as? Number)?.toInt() ?: 0),
            "matchCount" to ((selector?.get("matchCount") as? Number)?.toInt() ?: 0),
            "reasons" to (selector?.get("reasons") as? List<*> ?: emptyList<Any?>()),
        )
    }

    private fun stableSelectorAcrossSnapshots(match: NodeMatch): Map<String, Any?>? {
        val beforeSelector = selectorSummary(match.before)
        val afterSelector = selectorSummary(match.after)
        val selector = afterSelector["selector"] as? String ?: return null
        if (selector.isBlank() || selector != beforeSelector["selector"]) {
            return null
        }
        val beforeScore = (beforeSelector["score"] as? Number)?.toInt() ?: 0
        val afterScore = (afterSelector["score"] as? Number)?.toInt() ?: 0
        if (beforeScore < 60 || afterScore < 60) {
            return null
        }
        val reasons = ((afterSelector["reasons"] as? List<*>) ?: emptyList<Any?>())
            .mapNotNull { it as? String }
            .plus("stable-across-snapshots")
            .distinct()
        return linkedMapOf(
            "key" to match.key,
            "selector" to selector,
            "score" to (minOf(beforeScore, afterScore) + 8).coerceAtMost(100),
            "beforeScore" to beforeScore,
            "afterScore" to afterScore,
            "matchCount" to afterSelector["matchCount"],
            "reasons" to reasons,
        )
    }

    private fun changedFields(before: Map<String, Any?>, after: Map<String, Any?>): List<Map<String, Any?>> {
        return listOf("text", "desc", "id", "resourceName", "className", "bounds", "clickable", "enabled", "visibleToUser")
            .mapNotNull { field ->
                if (before[field] == after[field]) null else linkedMapOf("field" to field, "before" to before[field], "after" to after[field])
            }
    }

    private fun nodeSummary(node: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "key" to stableKey(node),
        "path" to node["path"],
        "text" to node["text"],
        "desc" to node["desc"],
        "resourceName" to node["resourceName"],
        "className" to node["className"],
        "bounds" to node["bounds"],
        "selector" to selectorSummary(node),
    )

    private fun shouldRedact(value: String, node: UiObject, options: CaptureOptions): Boolean {
        if (!options.redact || value.isBlank()) return false
        if (options.redactInputs && (node.password() || node.editable())) return true
        return options.redactPatterns && (phoneRegex.containsMatchIn(value) || emailRegex.containsMatchIn(value))
    }

    private fun Rect.toMap(): Map<String, Int> = linkedMapOf(
        "left" to left,
        "top" to top,
        "right" to right,
        "bottom" to bottom,
        "width" to width(),
        "height" to height(),
        "centerX" to centerX(),
        "centerY" to centerY(),
    )

    private fun nodeKey(windowIndex: Int, path: String) = "w$windowIndex:$path"

    private fun String.takeIfStableValue() = takeIf { it.isNotBlank() && it != "[redacted]" }

    private fun typeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "accessibility_overlay"
        else -> "unknown:$type"
    }

    private fun quoteJs(value: String): String {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private data class SelectorCandidate(
        val selector: String,
        val score: Int,
        val matchCount: Int,
        val reasons: List<String>,
    )

    private data class NodeMatch(
        val key: String,
        val before: MutableMap<String, Any?>,
        val after: MutableMap<String, Any?>,
    )

    private class NodeCounter(private val maxNodes: Int) {
        var count = 0
            private set
        var truncated = false
            private set

        fun countNode() {
            if (count >= maxNodes) {
                truncated = true
                return
            }
            count += 1
        }
    }

}
