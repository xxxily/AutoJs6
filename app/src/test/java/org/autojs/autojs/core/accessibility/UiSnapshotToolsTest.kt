package org.autojs.autojs.core.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSnapshotToolsTest {

    @Test
    fun scoreSelectorsPrefersUniqueResourceNameAndMarksFragileFallbacks() {
        val target = node(
            path = "w0/0",
            text = "Pay 2026-06-09",
            desc = "Submit payment",
            id = "submit",
            resourceName = "com.example:id/submit",
            className = "android.widget.Button",
            index = 0,
        )
        val duplicateClass = node(path = "w0/1", className = "android.widget.Button", index = 1)
        UiSnapshotTools.scoreSelectors(snapshot(target, duplicateClass))

        val selector = selector(target)
        assertEquals("id(\"com.example:id/submit\")", selector["best"])
        assertEquals(95, selector["score"])

        val candidates = selector["candidates"] as List<*>
        assertTrue(candidates.anyCandidate("text(\"Pay 2026-06-09\")", "volatile-text"))
        assertTrue(candidates.anyCandidate("indexInParent(0)", "index-fragile"))
        assertTrue(candidates.anyCandidate("bounds(0, 0, 100, 50)", "bounds-fragile"))
    }

    @Test
    fun diffReportsChangedTextIdResourceNameAndBoundsOnSameStructuralNode() {
        val before = snapshot(
            node(
                path = "w0/0",
                text = "Pay",
                id = "old_id",
                resourceName = "com.example:id/old",
                bounds = bounds(right = 100),
            ),
        )
        val after = snapshot(
            node(
                path = "w0/0",
                text = "Paid",
                id = "new_id",
                resourceName = "com.example:id/new",
                bounds = bounds(right = 120),
            ),
        )
        UiSnapshotTools.scoreSelectors(before)
        UiSnapshotTools.scoreSelectors(after)

        val diff = UiSnapshotTools.diff(before, after)
        assertEquals(0, diff["addedCount"])
        assertEquals(0, diff["removedCount"])
        assertEquals(1, diff["changedCount"])

        val fields = changedFields(diff)
        assertTrue("text should be reported as changed", "text" in fields)
        assertTrue("id should be reported as changed", "id" in fields)
        assertTrue("resourceName should be reported as changed", "resourceName" in fields)
        assertTrue("bounds should be reported as changed", "bounds" in fields)
    }

    @Test
    fun diffPromotesStableSelectorsAcrossSnapshots() {
        val before = snapshot(
            node(
                path = "w0/0",
                text = "Submit",
                id = "submit",
                resourceName = "com.example:id/submit",
            ),
        )
        val after = snapshot(
            node(
                path = "w0/0",
                text = "Submit",
                id = "submit",
                resourceName = "com.example:id/submit",
            ),
        )
        UiSnapshotTools.scoreSelectors(before)
        UiSnapshotTools.scoreSelectors(after)

        val stableSelectors = UiSnapshotTools.diff(before, after)["stableSelectors"] as List<*>
        val stable = stableSelectors.filterIsInstance<Map<*, *>>().first()
        assertEquals("id(\"com.example:id/submit\")", stable["selector"])
        assertEquals(100, stable["score"])
        assertTrue((stable["reasons"] as List<*>).contains("stable-across-snapshots"))
    }

    private companion object {

        private fun snapshot(vararg children: MutableMap<String, Any?>): MutableMap<String, Any?> {
            val root = node(path = "w0", className = "android.widget.FrameLayout").also {
                it["children"] = children.toList()
            }
            return mutableMapOf(
                "version" to 1,
                "timestamp" to 1_000L,
                "packageName" to "com.example",
                "activity" to "MainActivity",
                "windows" to listOf(
                    mutableMapOf(
                        "index" to 0,
                        "type" to "application",
                        "root" to root,
                    ),
                ),
            )
        }

        private fun node(
            path: String,
            text: String = "",
            desc: String = "",
            id: String = "",
            resourceName: String = "",
            className: String = "android.widget.TextView",
            bounds: Map<String, Int> = bounds(),
            index: Int = 0,
        ): MutableMap<String, Any?> = mutableMapOf(
            "key" to "0:$path",
            "path" to path,
            "windowIndex" to 0,
            "text" to text,
            "desc" to desc,
            "id" to id,
            "resourceName" to resourceName,
            "className" to className,
            "bounds" to bounds,
            "clickable" to false,
            "enabled" to true,
            "visibleToUser" to true,
            "index" to index,
            "children" to emptyList<MutableMap<String, Any?>>(),
        )

        private fun bounds(
            left: Int = 0,
            top: Int = 0,
            right: Int = 100,
            bottom: Int = 50,
        ) = linkedMapOf(
            "left" to left,
            "top" to top,
            "right" to right,
            "bottom" to bottom,
            "width" to (right - left),
            "height" to (bottom - top),
            "centerX" to ((left + right) / 2),
            "centerY" to ((top + bottom) / 2),
        )

        @Suppress("UNCHECKED_CAST")
        private fun selector(node: Map<String, Any?>): Map<String, Any?> {
            return node["selector"] as Map<String, Any?>
        }

        private fun List<*>.anyCandidate(selector: String, reason: String): Boolean {
            return filterIsInstance<Map<*, *>>().any { candidate ->
                candidate["selector"] == selector && (candidate["reasons"] as List<*>).contains(reason)
            }
        }

        private fun changedFields(diff: Map<String, Any?>): Set<String> {
            val changed = diff["changed"] as List<*>
            val first = changed.filterIsInstance<Map<*, *>>().first()
            val fields = first["fields"] as List<*>
            return fields.filterIsInstance<Map<*, *>>().map { it["field"].toString() }.toSet()
        }
    }

}
