package org.autojs.autojs.runtime.api.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPerceptionPipelineTest {

    @Test
    fun fusesAccessibilityAndOcrTargetsWithCurrentAppMetadata() {
        val sample = ScreenPerceptionPipeline.fuse(
            VisionInput(
                packageName = "com.example",
                activity = "LoginActivity",
                a11yNodes = listOf(node(text = "确定", clickable = true)),
                ocrBlocks = listOf(VisionOcrBlock("确定", 0.92, bounds())),
            ),
        )

        val target = sample.targets.first()
        assertEquals("a11y+ocr", target.source)
        assertEquals(listOf("a11y", "ocr"), target.sources)
        assertEquals("确定", target.text)
        assertEquals("click", target.suggestedAction.type)
        assertEquals("com.example", target.packageName)
        assertEquals("LoginActivity", target.activity)
        assertTrue(target.confidence > 0.9)
        assertTrue(target.explanation.any { it.contains("fused sources") })
    }

    @Test
    fun ocrOnlyHitUsesBoundsFallback() {
        val sample = ScreenPerceptionPipeline.fuse(
            VisionInput(
                packageName = "com.example",
                activity = "MainActivity",
                ocrBlocks = listOf(VisionOcrBlock("继续", 0.87, bounds(left = 20, top = 40, right = 120, bottom = 90))),
            ),
            VisionPipelineOptions(sources = setOf("ocr")),
        )

        val target = sample.targets.first()
        assertEquals("ocr", target.source)
        assertEquals("tap", target.suggestedAction.type)
        assertEquals(70, target.suggestedAction.x)
        assertEquals(65, target.suggestedAction.y)
        assertTrue(target.explanation.contains("ocr bounds fallback"))
        assertEquals(target, ScreenPerceptionPipeline.findText(sample.targets, "继续"))
    }

    @Test
    fun imageMatchCanSupplyLabelForTextlessAccessibilityNode() {
        val sample = ScreenPerceptionPipeline.fuse(
            VisionInput(
                packageName = "com.example",
                activity = "LoginActivity",
                a11yNodes = listOf(
                    node(
                        text = "",
                        desc = "",
                        className = "android.widget.ImageButton",
                        clickable = true,
                    ),
                ),
                imageMatches = listOf(VisionImageMatch("登录", 0.84, bounds(), template = "login_button.png")),
            ),
            VisionPipelineOptions(sources = setOf("a11y", "image")),
        )

        val target = sample.targets.first()
        assertEquals("a11y+image", target.source)
        assertEquals("登录", target.label)
        assertEquals("click", target.suggestedAction.type)
        assertTrue(target.explanation.any { it.contains("image match supplied label") })
        assertEquals(target, ScreenPerceptionPipeline.findButton(sample.targets, "登录"))
    }

    @Test
    fun sourceSwitchesAndRegionFilterConstrainTargets() {
        val sample = ScreenPerceptionPipeline.fuse(
            VisionInput(
                a11yNodes = listOf(node(text = "设置", bounds = bounds(left = 0, top = 0, right = 80, bottom = 50))),
                ocrBlocks = listOf(VisionOcrBlock("设置", 0.9, bounds(left = 300, top = 300, right = 420, bottom = 360))),
            ),
            VisionPipelineOptions(
                sources = setOf("a11y"),
                region = bounds(left = 0, top = 0, right = 100, bottom = 100),
            ),
        )

        assertEquals(1, sample.targets.size)
        assertEquals("a11y", sample.targets.first().source)
        assertEquals("设置", sample.targets.first().text)
    }

    @Test
    fun sceneDefinitionMatchesTextAndButtonRequirements() {
        val targets = ScreenPerceptionPipeline.fuse(
            VisionInput(
                a11yNodes = listOf(
                    node(text = "账号登录", clickable = false),
                    node(text = "登录", clickable = true, bounds = bounds(top = 80, bottom = 140)),
                ),
            ),
        ).targets

        val scene = VisionSceneDefinition(
            name = "login",
            requirements = listOf(
                VisionSceneRequirement(type = "text", text = "账号登录"),
                VisionSceneRequirement(type = "button", text = "登录"),
            ),
        )
        val match = scene.match(targets)

        assertTrue(match.ok)
        assertEquals("login", match.name)
        assertEquals(2, match.matched.size)
        assertTrue(match.missing.isEmpty())
    }

    @Test
    fun normalizeSourcesAcceptsCombinedSyntax() {
        assertEquals(setOf("ocr", "a11y"), ScreenPerceptionPipeline.normalizeSources(listOf("ocr+a11y")))
        assertEquals(setOf("a11y", "image"), ScreenPerceptionPipeline.normalizeSources(listOf("accessibility,cv")))
        assertEquals(setOf("a11y", "ocr", "image", "color"), ScreenPerceptionPipeline.normalizeSources(listOf("all")))
    }

    private companion object {

        private fun node(
            text: String = "确定",
            desc: String = "",
            className: String = "android.widget.Button",
            clickable: Boolean = true,
            bounds: VisionBounds = bounds(),
        ): Map<String, Any?> = mapOf(
            "key" to "w0:0",
            "path" to "w0/0",
            "text" to text,
            "desc" to desc,
            "resourceName" to "com.example:id/${text.ifBlank { "icon" }}",
            "className" to className,
            "bounds" to bounds.toMap(),
            "clickable" to clickable,
            "enabled" to true,
            "visibleToUser" to true,
            "editable" to false,
            "scrollable" to false,
            "selector" to mapOf("best" to "text(\"$text\")", "score" to 86, "matchCount" to 1),
        )

        private fun bounds(left: Int = 10, top: Int = 10, right: Int = 110, bottom: Int = 60) =
            VisionBounds(left, top, right, bottom)
    }
}
