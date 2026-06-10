package org.autojs.autojs.ai.docs

import android.content.Context
import android.text.Html
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.capability.CapabilityRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class AiCapabilityIndex private constructor(
    private val entries: List<AiCapabilityEntry>,
) {

    private val knownNames: Set<String> = entries
        .flatMap { entry -> listOf(entry.name, entry.qualifiedName) + entry.aliases }
        .map { it.lowercase(Locale.ROOT) }
        .toSet()

    fun search(query: String, limit: Int = 8): List<AiCapabilityEntry> {
        val tokens = expandTokens(query)
        if (tokens.isEmpty()) return entries.take(limit)
        return entries
            .map { it to it.score(tokens) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AiCapabilityEntry, Int>> { it.second }.thenBy { it.first.docFile })
            .take(limit)
            .map { it.first }
            .ifEmpty { entries.take(limit) }
    }

    fun validateGeneratedCode(code: String): AiCodeValidation {
        val risky = detectHighRisk(code)
        val unknown = detectUnknownCalls(code)
        return AiCodeValidation(unknownApis = unknown, risks = risky)
    }

    fun containsApi(name: String): Boolean = name.lowercase(Locale.ROOT) in knownNames

    fun resolve(name: String): AiCapabilityEntry? {
        val normalized = name.lowercase(Locale.ROOT)
        return entries.firstOrNull { entry ->
            entry.name.equals(name, ignoreCase = true) ||
                entry.qualifiedName.equals(name, ignoreCase = true) ||
                entry.aliases.any { it.equals(name, ignoreCase = true) }
        } ?: entries.firstOrNull { entry ->
            normalized == entry.qualifiedName.substringAfterLast('.').lowercase(Locale.ROOT)
        }
    }

    private fun detectUnknownCalls(code: String): List<String> {
        val candidates = CALL_PATTERN.findAll(stripStringsAndComments(code))
            .map { it.groupValues[1] }
            .filter { it !in LANGUAGE_KEYWORDS }
            .filterNot { it.startsWith("function.") }
            .map { it.trim('.') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        return candidates
            .filterNot { call ->
                val lower = call.lowercase(Locale.ROOT)
                lower in SAFE_JS_GLOBALS ||
                    lower in knownNames ||
                    lower.substringBefore('.') in SAFE_JS_GLOBALS ||
                    lower.substringAfterLast('.') in knownNames
            }
            .take(20)
    }

    private fun detectHighRisk(code: String): List<AiRisk> {
        val lower = code.lowercase(Locale.ROOT)
        val capabilityRisks = CapabilityRegistry.inferCapabilitiesFromScript(code)
            .filter { it.dangerous }
            .map { capability ->
                AiRisk(capability.name, capability.description, "high")
            }
        val keywordRisks = RISK_RULES
            .filter { rule -> rule.keywords.any { it in lower } }
            .map { AiRisk(it.name, it.description, it.level) }
        return (capabilityRisks + keywordRisks)
            .distinctBy { it.name }
    }

    private fun AiCapabilityEntry.score(tokens: List<String>): Int {
        val haystack = "$name $qualifiedName $module $docFile $signature $description ${aliases.joinToString(" ")}"
            .lowercase(Locale.ROOT)
        var score = 0
        tokens.forEach { token ->
            if (token in haystack) score += if (token == name.lowercase(Locale.ROOT)) 8 else 2
            if (docFile.lowercase(Locale.ROOT).contains(token)) score += 4
            if (signature.lowercase(Locale.ROOT).contains(token)) score += 5
        }
        if (score > 0 && module == SOLUTIONS_MODULE) score += 12
        return score
    }

    companion object {
        private val cached = AtomicReference<AiCapabilityIndex?>()

        fun get(context: Context): AiCapabilityIndex {
            cached.get()?.let { return it }
            val built = build(context.applicationContext)
            cached.compareAndSet(null, built)
            return cached.get() ?: built
        }

        internal fun fromEntriesForTesting(entries: List<AiCapabilityEntry>): AiCapabilityIndex {
            return AiCapabilityIndex(entries)
        }

        private fun build(context: Context): AiCapabilityIndex {
            val assets = context.assets
            val files = assets.list("docs")
                ?.filter { it.endsWith(".html", ignoreCase = true) }
                ?.sorted()
                .orEmpty()
            val htmlEntries = files.flatMap { file ->
                runCatching {
                    assets.open("docs/$file").use { input ->
                        parseHtml(file, input.bufferedReader().readText())
                    }
                }.getOrDefault(emptyList())
            }
            val indexEntries = runCatching {
                assets.open("indices/all.json").use { input ->
                    parseCapabilityIndexJson(input.bufferedReader().readText())
                }
            }.getOrDefault(emptyList())
            val solutionEntries = runCatching {
                assets.open(SOLUTIONS_ASSET_PATH).use { input ->
                    parseSolutionIndexJson(input.bufferedReader().readText())
                }
            }.getOrDefault(emptyList())
            val entries = (solutionEntries + indexEntries + htmlEntries).distinctBy {
                listOf(it.qualifiedName, it.docFile, it.signature)
                    .joinToString("@")
                    .lowercase(Locale.ROOT)
            }
            return AiCapabilityIndex(entries.ifEmpty { fallbackEntries() })
        }

        internal fun parseSolutionIndexJson(json: String): List<AiCapabilityEntry> {
            val root = JsonParser.parseString(json).asJsonObject
            val solutions = root.getAsJsonArray("solutions") ?: return emptyList()
            return solutions.mapNotNull { element ->
                val solution = element.asJsonObject
                val id = solution.string("id")
                val title = solution.string("title")
                if (id.isBlank() || title.isBlank()) return@mapNotNull null
                val scenario = solution.string("scenario")
                val category = solution.string("category")
                val capabilities = solution.stringList("capabilities")
                val apis = solution.stringList("apis")
                val tags = solution.stringList("tags")
                val failures = solution.stringList("commonFailures")
                val script = solution.stringList("scriptLines").joinToString("\n")
                val template = solution.stringList("templateLines").joinToString("\n")
                val projectJson = solution.get("projectJson")
                    ?.takeUnless { it.isJsonNull }
                    ?.toString()
                    .orEmpty()
                val description = buildString {
                    append("Scenario: ")
                    append(scenario)
                    if (capabilities.isNotEmpty()) append("\nCapabilities: ${capabilities.joinToString(", ")}")
                    if (apis.isNotEmpty()) append("\nPreferred APIs: ${apis.joinToString(", ")}")
                    if (failures.isNotEmpty()) append("\nCommon failures: ${failures.joinToString("; ")}")
                    if (projectJson.isNotBlank()) append("\nproject.json hints: $projectJson")
                }
                AiCapabilityEntry(
                    id = "$SOLUTIONS_ASSET_PATH#$id",
                    name = title,
                    qualifiedName = "$SOLUTIONS_MODULE.$id",
                    module = SOLUTIONS_MODULE,
                    docFile = "automation-solutions.html#$id",
                    signature = "Solution: $title",
                    description = description.take(1200),
                    example = template.ifBlank { script }.take(1600),
                    permissions = capabilities.map { "capability:$it" },
                    riskLevel = solution.string("riskLevel").ifBlank { riskLevelFor(title, description) },
                    aliases = (tags + apis + capabilities + category + id).filter { it.isNotBlank() }.distinct(),
                )
            }
        }

        private fun parseCapabilityIndexJson(json: String): List<AiCapabilityEntry> {
            return JsonParser.parseString(json).asJsonArray.flatMap { element ->
                val moduleObject = element.asJsonObject
                val module = moduleObject.string("name")
                if (module.isBlank()) return@flatMap emptyList()

                val moduleUrl = moduleObject.string("url").ifBlank { "$module.html" }
                val moduleSummary = moduleObject.string("summary")
                val moduleEntry = AiCapabilityEntry(
                    id = "indices/all.json#$module",
                    name = module,
                    qualifiedName = module,
                    module = module,
                    docFile = moduleUrl,
                    signature = module,
                    description = moduleSummary.ifBlank { "$module module" },
                    example = "",
                    permissions = permissionsFor(module, moduleSummary),
                    riskLevel = riskLevelFor(module, moduleSummary),
                    aliases = aliasesFor(module),
                )

                val properties = moduleObject.getAsJsonArray("properties")
                    ?.mapNotNull { property ->
                        val propertyObject = property.asJsonObject
                        val key = propertyObject.string("key")
                        if (key.isBlank()) return@mapNotNull null
                        val url = propertyObject.string("url").ifBlank { moduleUrl }
                        val summary = propertyObject.string("summary")
                        val qualifiedName = "$module.$key"
                        val aliases = buildList {
                            addAll(aliasesFor(key))
                            addAll(aliasesFor(qualifiedName))
                            if (propertyObject.boolean("global")) add(key)
                        }.distinct()
                        AiCapabilityEntry(
                            id = "indices/all.json#$qualifiedName",
                            name = key,
                            qualifiedName = qualifiedName,
                            module = module,
                            docFile = url,
                            signature = qualifiedName,
                            description = summary.ifBlank { qualifiedName },
                            example = "",
                            permissions = permissionsFor(module, "$key $summary"),
                            riskLevel = riskLevelFor(qualifiedName, summary),
                            aliases = aliases,
                        )
                    }
                    .orEmpty()

                listOf(moduleEntry) + properties
            }
        }

        private fun JsonObject.string(name: String): String {
            return get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        }

        private fun JsonObject.boolean(name: String): Boolean {
            return get(name)?.takeUnless { it.isJsonNull }?.asBoolean == true
        }

        private fun JsonObject.stringList(name: String): List<String> {
            return get(name)
                ?.takeUnless { it.isJsonNull }
                ?.asJsonArray
                ?.mapNotNull { item -> item.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
        }

        private fun parseHtml(file: String, html: String): List<AiCapabilityEntry> {
            val module = file.removeSuffix(".html")
            val title = TITLE_PATTERN.find(html)?.groupValues?.get(1)?.htmlToText().orEmpty()
            val headingMatches = HEADING_PATTERN.findAll(html).toList()
            if (headingMatches.isEmpty()) {
                return listOf(
                    AiCapabilityEntry(
                        id = file,
                        name = module,
                        qualifiedName = module,
                        module = module,
                        docFile = file,
                        signature = title.ifBlank { module },
                        description = html.htmlToText().take(600),
                        example = "",
                        riskLevel = riskLevelFor(module, html),
                        aliases = aliasesFor(module),
                    )
                )
            }
            return headingMatches.mapIndexed { index, match ->
                val heading = match.groupValues[2].htmlToText()
                val start = match.range.last + 1
                val end = headingMatches.getOrNull(index + 1)?.range?.first ?: html.length
                val sectionHtml = html.substring(start, min(end, html.length))
                val plainSection = sectionHtml.htmlToText()
                val code = CODE_PATTERN.find(sectionHtml)?.groupValues?.get(1)?.htmlToText().orEmpty()
                val apiName = normalizeApiName(heading, module)
                AiCapabilityEntry(
                    id = "$file#$index",
                    name = apiName,
                    qualifiedName = apiName,
                    module = module,
                    docFile = file,
                    signature = heading,
                    description = plainSection.take(800),
                    example = code.take(1200),
                    permissions = permissionsFor(module, plainSection),
                    riskLevel = riskLevelFor(apiName, plainSection),
                    aliases = aliasesFor(apiName) + aliasesFor(module),
                )
            }
        }

        private fun normalizeApiName(heading: String, module: String): String {
            val beforeBrace = heading.substringBefore("(").substringBefore(" ")
            return beforeBrace
                .replace(Regex("[#：:].*"), "")
                .trim()
                .ifBlank { module }
        }

        private fun String.htmlToText(): String {
            return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun expandTokens(query: String): List<String> {
            val normalized = query.lowercase(Locale.ROOT)
            val base = TOKEN_PATTERN.findAll(normalized)
                .map { it.value }
                .filter { it.length >= 2 }
                .toMutableSet()
            base += SYNONYMS.keys.filter { key -> key in normalized }
            val additions = base.flatMap { SYNONYMS[it].orEmpty() }
            base += additions
            return base.toList()
        }

        private fun permissionsFor(module: String, text: String): List<String> {
            val lower = "$module $text".lowercase(Locale.ROOT)
            val permissions = mutableListOf<String>()
            if ("无障碍" in lower || "accessibility" in lower || "控件" in lower) permissions += "需要无障碍服务"
            if ("截图" in lower || "capture" in lower || "image" in lower) permissions += "可能需要截图权限"
            if ("vision" in lower || "屏幕感知" in lower || "感知" in lower) permissions += listOf("需要无障碍服务", "可能需要截图权限")
            if ("root" in lower) permissions += "需要 Root 权限"
            if ("shizuku" in lower) permissions += "需要 Shizuku 授权"
            if ("悬浮窗" in lower || "floaty" in lower) permissions += "需要悬浮窗权限"
            return permissions.distinct()
        }

        private fun riskLevelFor(name: String, text: String): String {
            val lower = "$name $text".lowercase(Locale.ROOT)
            return when {
                "root" in lower || "shizuku" in lower || "shell" in lower -> "high"
                "短信" in lower || "sms" in lower || "联系人" in lower || "contact" in lower -> "high"
                "delete" in lower || "remove" in lower || "删除" in lower -> "high"
                "vision" in lower || "屏幕感知" in lower || "感知" in lower -> "medium"
                "click" in lower || "swipe" in lower || "longclick" in lower || "无障碍" in lower -> "medium"
                "capture" in lower || "截图" in lower || "floaty" in lower || "悬浮窗" in lower -> "medium"
                else -> "low"
            }
        }

        private fun aliasesFor(name: String): List<String> {
            val lower = name.lowercase(Locale.ROOT)
            return SYNONYMS.entries
                .filter { (key, values) -> key == lower || lower in values }
                .flatMap { listOf(it.key) + it.value }
                .distinct()
        }

        private fun stripStringsAndComments(code: String): String {
            return code
                .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")
                .replace(Regex("'(?:\\\\.|[^'\\\\])*'"), "''")
                .replace(Regex("`(?:\\\\.|[^`\\\\])*`"), "``")
                .replace(Regex("//.*"), "")
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        }

        private fun fallbackEntries(): List<AiCapabilityEntry> = listOf(
            AiCapabilityEntry("global", "toast", "toast", "global", "global.html", "toast(message)", "显示 toast 消息", "", listOf(), "low", listOf("提示")),
            AiCapabilityEntry("automator", "click", "click", "automator", "automator.html", "click(x, y) / click(text[, i])", "点击坐标或文本控件", "", listOf("需要无障碍服务"), "medium", listOf("点击")),
            AiCapabilityEntry("automator-auto", "auto", "auto", "automator", "automator.html", "auto([mode])", "启用或等待无障碍服务", "", listOf("需要无障碍服务"), "medium", listOf("无障碍")),
            AiCapabilityEntry("automator-wait-until", "waitUntil", "auto.waitUntil", "automator", "automator.html", "auto.waitUntil(condition, options)", "等待函数、选择器、控件或文本达到稳定状态并返回结构化结果", "", listOf("需要无障碍服务"), "medium", listOf("可靠自动化", "稳定等待")),
            AiCapabilityEntry("automator-retry", "retry", "auto.retry", "automator", "automator.html", "auto.retry(action, options)", "按重试和退避策略执行动作并返回结构化结果", "", listOf(), "low", listOf("可靠自动化", "重试")),
            AiCapabilityEntry("automator-stable-click", "stableClick", "auto.stableClick", "automator", "automator.html", "auto.stableClick(target, options)", "稳定等待控件后校验并点击, 支持父节点/坐标 fallback 和失败快照", "", listOf("需要无障碍服务"), "medium", listOf("可靠自动化", "稳定点击")),
            AiCapabilityEntry("automator-stable-set-text", "stableSetText", "auto.stableSetText", "automator", "automator.html", "auto.stableSetText(target, text, options)", "稳定等待输入控件后设置文本, 支持重试和诊断", "", listOf("需要无障碍服务"), "medium", listOf("可靠自动化", "稳定输入")),
            AiCapabilityEntry("automator-find-with-scroll", "findWithScroll", "auto.findWithScroll", "automator", "automator.html", "auto.findWithScroll(target[, scrollContainer][, options])", "查找控件并按需滚动容器, 返回匹配控件和诊断结果", "", listOf("需要无障碍服务"), "medium", listOf("可靠自动化", "滚动查找")),
            AiCapabilityEntry("images-capture-session", "openCaptureSession", "images.openCaptureSession", "images", "image.html", "images.openCaptureSession(options)", "打开可诊断的截图会话, 支持帧缓存、预设、错误分类和性能指标", "", listOf("可能需要截图权限"), "medium", listOf("截图会话", "连续截图", "性能基准")),
        )

        private val TITLE_PATTERN = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
        private val HEADING_PATTERN = Regex("<h([12])[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val CODE_PATTERN = Regex("<pre><code[^>]*>(.*?)</code></pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}_.-]+")
        private val CALL_PATTERN = Regex("\\b([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\(")

        private val SYNONYMS = mapOf(
            "找控件" to listOf("selector", "uiselector", "text", "id", "控件", "选择器"),
            "可靠自动化" to listOf("waituntil", "retry", "stableclick", "stablesettext", "findwithscroll", "稳定点击", "稳定输入", "滚动查找"),
            "点击" to listOf("click", "tap", "press", "stableclick", "无障碍"),
            "滑动" to listOf("swipe", "scroll", "gesture"),
            "截图" to listOf("images", "capturescreen", "opencapturesession", "capturesession", "image", "找图", "找色", "截图会话"),
            "屏幕感知" to listOf("vision", "targets", "findtext", "findbutton", "observe", "waitforscene", "ocr", "a11y", "场景等待"),
            "微信" to listOf("app", "launchapp", "text", "stableclick", "findwithscroll"),
            "输入" to listOf("input", "settext", "stablesettext", "text"),
            "文件" to listOf("files", "read", "write"),
            "网络" to listOf("http", "websocket"),
            "通知" to listOf("notice", "notification"),
            "shell" to listOf("root", "shizuku", "高风险"),
            "强停" to listOf("shizuku.app.forcestop", "forcestop", "force-stop", "am", "高风险"),
            "授权" to listOf("shizuku.app.grantpermission", "grantpermission", "revokepermission", "pm", "权限"),
            "系统设置" to listOf("shizuku.settings.put", "shizuku.settings.get", "writesecuresettings", "settings", "安全设置"),
            "shizuku结构化" to listOf("shizuku.app", "shizuku.settings", "shizuku.package", "shizuku.input", "shizuku.process", "shizuku.users"),
            "方案库" to listOf("solutions", "automation-solutions", "模板", "solution", "pattern", "可复制模板"),
            "模板" to listOf("solutions", "方案库", "automation-solutions", "新手模板", "pattern"),
            "列表" to listOf("findwithscroll", "scroll", "滚动查找", "list"),
            "表单" to listOf("stablesettext", "stableclick", "输入", "提交", "form"),
            "插件ocr" to listOf("plugin", "plugins.load", "paddle", "ocr", "plugindependencies"),
        )

        private val LANGUAGE_KEYWORDS = setOf(
            "if", "for", "while", "switch", "catch", "function", "return", "typeof", "new",
        )

        private val SAFE_JS_GLOBALS = setOf(
            "array", "boolean", "date", "error", "json", "math", "number", "object", "regexp", "string",
            "parseint", "parsefloat", "isnan", "isfinite", "encodeuri", "decodeuri", "require",
            "console.log", "console.error", "console.warn", "log", "print", "sleep", "settimeout", "setinterval",
        )

        private val RISK_RULES = listOf(
            RiskRule("无障碍操作", "脚本会点击、滑动、输入或操作屏幕控件", "high", listOf("auto.", "automator", "stableclick", "stablesettext", "findwithscroll", "click(", "swipe(", "gesture(", "press(", ".click(", "settext(", "input(")),
            RiskRule("截图/录屏/图像识别", "脚本可能请求截图、录屏或处理屏幕图像", "high", listOf("capturescreen", "opencapturesession", "capturesession", "requestscreen", "requestscreencapture", "screenrecord", "mediaprojection", "images.", "findimage", "findcolor", "vision.", "屏幕感知", "截图", "录屏")),
            RiskRule("悬浮窗", "脚本可能显示悬浮窗或覆盖其他应用", "high", listOf("floaty", "悬浮窗", "system_alert_window")),
            RiskRule("Root", "脚本可能使用 Root 权限", "high", listOf("rootautomator", "root.", "su -c", "root")),
            RiskRule("Shizuku", "脚本可能使用 Shizuku 特权", "high", listOf("shizuku")),
            RiskRule("Shell", "脚本可能执行 Shell 命令", "high", listOf("shell(", "shell.", "exec(", "runtime.exec")),
            RiskRule("文件删除/覆盖/外部存储", "脚本可能删除、覆盖或批量读写外部存储文件", "high", listOf("files.remove", "files.write", "files.append", "deletefile", "remove(", "rmdir", "unlink", "/sdcard", "externalstorage", "外部存储")),
            RiskRule("网络上传本地文件", "脚本可能通过网络请求上传本地文件或日志", "high", listOf("postmultipart", "upload", "multipart", "http.post", "http.request", "files.read", "readbytes", "上传")),
            RiskRule("短信/联系人/电话", "脚本可能访问短信、联系人或电话能力", "high", listOf("sms", "contact", "contacts", "callphone", "sendmessage", "短信", "联系人", "电话")),
            RiskRule("相机/录音/定位", "脚本可能访问相机、麦克风或定位信息", "high", listOf("camera", "recordaudio", "microphone", "location", "gps", "相机", "录音", "定位")),
            RiskRule("应用安装/卸载", "脚本可能安装或卸载应用", "high", listOf("installpackage", "uninstall", "app.install", "app.uninstall", "pm install", "pm uninstall")),
            RiskRule("应用强停/清数据/授权", "脚本可能强制停止应用、清除应用数据或授予/撤销权限", "high", listOf("shizuku.app.forcestop", "shizuku.app.cleardata", "shizuku.app.grantpermission", "shizuku.app.revokepermission", "force-stop", "pm grant", "pm revoke", "pm clear")),
            RiskRule("修改系统设置", "脚本可能修改系统设置或安全设置", "high", listOf("writesettings", "write_settings", "writesecuresettings", "write_secure_settings", "settings put", "shizuku.settings.put", "shizuku.settings.delete", "shizuku.settings.secure.put", "shizuku.settings.global.put", "shizuku.settings.system.put", "系统设置", "安全设置")),
        )

        private const val SOLUTIONS_MODULE = "solutions"
        private const val SOLUTIONS_ASSET_PATH = "solutions/automation-solutions.json"
    }
}

data class AiCapabilityEntry(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val module: String,
    val docFile: String,
    val signature: String,
    val description: String,
    val example: String,
    val permissions: List<String> = emptyList(),
    val riskLevel: String = "low",
    val aliases: List<String> = emptyList(),
)

data class AiCodeValidation(
    val unknownApis: List<String>,
    val risks: List<AiRisk>,
) {
    val hasBlockingWarnings: Boolean
        get() = unknownApis.isNotEmpty() || risks.any { it.level == "high" }
}

data class AiRisk(
    val name: String,
    val description: String,
    val level: String,
)

private data class RiskRule(
    val name: String,
    val description: String,
    val level: String,
    val keywords: List<String>,
)
