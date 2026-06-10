package org.autojs.autojs.ai.docs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class AiCapabilityIndexConsistencyTest {

    private val appDir = resolveAppDir()
    private val indexFile = appDir.resolve("src/main/assets-app/indices/all.json")
    private val docsDir = appDir.resolve("src/main/assets-app/docs")
    private val runtimeFile = appDir.resolve("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt")
    private val aiIndexFile = appDir.resolve("src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt")

    private val modules: List<JsonObject> by lazy {
        JsonParser.parseReader(indexFile.reader()).asJsonArray.map { it.asJsonObject }
    }

    @Test
    fun allIndexUrlsResolveToExistingDocsAndAnchors() {
        val invalid = indexedUrls().mapNotNull { indexedUrl ->
            val url = indexedUrl.url
            when {
                url.isBlank() -> "${indexedUrl.owner} has empty url"
                "widgets-based-automation.html" in url -> "${indexedUrl.owner} still points to legacy widgets-based-automation.html"
                else -> {
                    val docFile = url.substringBefore("#")
                    val anchor = url.substringAfter("#", "")
                    val file = docsDir.resolve(docFile)
                    when {
                        !file.isFile -> "${indexedUrl.owner} points to missing docs file: $url"
                        anchor.isNotBlank() && anchor !in htmlIds(file) -> "${indexedUrl.owner} points to missing anchor: $url"
                        else -> null
                    }
                }
            }
        }

        assertTrue(invalid.joinToString("\n"), invalid.isEmpty())
    }

    @Test
    fun allIndexHasNoDuplicateModuleOrPropertyKeys() {
        val duplicateModules = modules
            .map { it.string("name").lowercase(Locale.ROOT) }
            .duplicates()

        val duplicateProperties = modules.flatMap { module ->
            val moduleName = module.string("name")
            module.getAsJsonArray("properties")
                ?.map { property -> "$moduleName.${property.asJsonObject.string("key")}" }
                .orEmpty()
                .duplicates()
        }

        assertTrue("Duplicate modules: ${duplicateModules.joinToString()}", duplicateModules.isEmpty())
        assertTrue("Duplicate properties: ${duplicateProperties.joinToString()}", duplicateProperties.isEmpty())
    }

    @Test
    fun coreAutomationAndPerceptionModulesAreIndexed() {
        val expectedDocs = mapOf(
            "automator" to "automator.html",
            "selector" to "uiSelectorType.html",
            "images" to "image.html",
            "ocr" to "ocr.html",
            "vision" to "vision.html",
            "shizuku" to "shizuku.html",
            "tasks" to "tasks.html",
        )

        expectedDocs.forEach { (moduleName, expectedDoc) ->
            val module = module(moduleName)
            assertEquals("$moduleName docs file", expectedDoc, module.string("url"))
        }

        assertProperties("automator", "click", "longClick", "press", "swipe", "gesture", "gestures", "input", "setText", "snapshot", "diffSnapshot", "waitUntil", "retry", "stableClick", "stableSetText", "findWithScroll")
        assertProperties("selector", "id", "text", "desc", "className", "packageName", "bounds", "clickable", "scrollable")
        assertProperties("images", "requestScreenCapture", "captureScreen", "openCaptureSession", "findColor", "findMultiColors", "findImage")
        assertProperties("ocr", "mode", "recognizeText", "detect", "tap", "summary")
        assertProperties("vision", "targets", "findText", "findButton", "observe", "waitForScene", "summary")
        assertProperties(
            "shizuku",
            "execCommand",
            "getCommand",
            "kill",
            "currentPackage",
            "currentActivity",
            "currentComponent",
            "state",
            "app",
            "app.forceStop",
            "app.clearData",
            "app.grantPermission",
            "app.revokePermission",
            "settings",
            "settings.get",
            "settings.put",
            "settings.delete",
            "settings.secure.put",
            "package",
            "package.info",
            "package.apkPath",
            "package.permissionState",
            "input",
            "input.injectTap",
            "input.injectSwipe",
            "input.keyEvent",
            "process",
            "process.list",
            "process.kill",
            "process.foreground",
            "users",
            "users.listUsers",
            "users.currentUser",
            "users.runAsUser",
            "audit",
            "audit.exportJson",
            "operations",
        )
        assertProperties("tasks", "addDailyTask", "addWeeklyTask", "addDisposableTask", "addIntentTask", "queryTimedTasks", "queryIntentTasks")
    }

    @Test
    fun runtimeInjectedCoreObjectsHaveIndexCoverage() {
        val runtimeText = runtimeFile.readText()
        val indexedModules = modules.map { it.string("name") }.toSet()
        val runtimeMarkers = mapOf(
            "automator" to "val automator",
            "images" to "val images",
            "ocr" to "val ocr",
            "vision" to "Vision(this).augment",
            "shizuku" to "val shizuku",
            "selector" to "Selector(this).augment",
            "tasks" to "Tasks(this).augment",
        )

        runtimeMarkers.forEach { (moduleName, marker) ->
            assertTrue("ScriptRuntime.kt no longer exposes expected marker: $marker", marker in runtimeText)
            assertTrue("Runtime object $moduleName is missing from all.json", moduleName in indexedModules)
        }
    }

    @Test
    fun aiCapabilityIndexConsumesAllJsonMetadata() {
        val source = aiIndexFile.readText()
        assertTrue("AI index must load indices/all.json", "indices/all.json" in source)
        assertTrue("AI index must parse all.json metadata", "parseCapabilityIndexJson" in source)
        assertFalse("Unknown API warning should not be generic docs-only wording", "not found in local docs:" in appDir.resolve("src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt").readText())
    }

    private fun assertProperties(moduleName: String, vararg expectedKeys: String) {
        val keys = module(moduleName).getAsJsonArray("properties")
            ?.map { it.asJsonObject.string("key") }
            ?.toSet()
            .orEmpty()
        expectedKeys.forEach { key ->
            assertTrue("$moduleName.$key is missing from all.json", key in keys)
        }
    }

    private fun module(name: String): JsonObject {
        return modules.firstOrNull { it.string("name") == name }
            ?: error("Missing module in all.json: $name")
    }

    private fun indexedUrls(): List<IndexedUrl> {
        return modules.flatMap { module ->
            val moduleName = module.string("name")
            val moduleUrl = IndexedUrl(moduleName, module.string("url"))
            val propertyUrls = module.getAsJsonArray("properties")
                ?.map { property ->
                    val propertyObject = property.asJsonObject
                    IndexedUrl("$moduleName.${propertyObject.string("key")}", propertyObject.string("url"))
                }
                .orEmpty()
            listOf(moduleUrl) + propertyUrls
        }
    }

    private fun htmlIds(file: File): Set<String> {
        return HTML_ID_PATTERN.findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun List<String>.duplicates(): List<String> {
        return groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .toList()
    }

    private fun JsonObject.string(name: String): String {
        return get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    }

    private data class IndexedUrl(
        val owner: String,
        val url: String,
    )

    private companion object {
        private val HTML_ID_PATTERN = Regex("""id="([^"]+)"""")

        private fun resolveAppDir(): File {
            val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return listOf(workingDir, workingDir.resolve("app"))
                .firstOrNull { it.resolve("src/main/assets-app/indices/all.json").isFile }
                ?: error("Cannot resolve app module directory from ${workingDir.path}")
        }
    }
}
