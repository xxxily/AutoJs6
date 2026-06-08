package org.autojs.autojs.ai.docs

import android.content.Context
import android.text.Html
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
        return RISK_RULES
            .filter { rule -> rule.keywords.any { it in lower } }
            .map { AiRisk(it.name, it.description, it.level) }
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

        private fun build(context: Context): AiCapabilityIndex {
            val assets = context.assets
            val files = assets.list("docs")
                ?.filter { it.endsWith(".html", ignoreCase = true) }
                ?.sorted()
                .orEmpty()
            val entries = files.flatMap { file ->
                runCatching {
                    assets.open("docs/$file").use { input ->
                        parseHtml(file, input.bufferedReader().readText())
                    }
                }.getOrDefault(emptyList())
            }
            return AiCapabilityIndex(entries.ifEmpty { fallbackEntries() })
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
            val base = TOKEN_PATTERN.findAll(query.lowercase(Locale.ROOT))
                .map { it.value }
                .filter { it.length >= 2 }
                .toMutableSet()
            val additions = base.flatMap { SYNONYMS[it].orEmpty() }
            base += additions
            return base.toList()
        }

        private fun permissionsFor(module: String, text: String): List<String> {
            val lower = "$module $text".lowercase(Locale.ROOT)
            val permissions = mutableListOf<String>()
            if ("无障碍" in lower || "accessibility" in lower || "控件" in lower) permissions += "需要无障碍服务"
            if ("截图" in lower || "capture" in lower || "image" in lower) permissions += "可能需要截图权限"
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
        )

        private val TITLE_PATTERN = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
        private val HEADING_PATTERN = Regex("<h([12])[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val CODE_PATTERN = Regex("<pre><code[^>]*>(.*?)</code></pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}_.-]+")
        private val CALL_PATTERN = Regex("\\b([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)?)\\s*\\(")

        private val SYNONYMS = mapOf(
            "找控件" to listOf("selector", "uiselector", "text", "id", "控件", "选择器"),
            "点击" to listOf("click", "tap", "press", "无障碍"),
            "滑动" to listOf("swipe", "scroll", "gesture"),
            "截图" to listOf("images", "capturescreen", "image", "找图", "找色"),
            "微信" to listOf("app", "launchapp", "text", "click"),
            "输入" to listOf("input", "settext", "text"),
            "文件" to listOf("files", "read", "write"),
            "网络" to listOf("http", "websocket"),
            "通知" to listOf("notice", "notification"),
            "shell" to listOf("root", "shizuku", "高风险"),
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
            RiskRule("无障碍操作", "脚本会点击、滑动、输入或操作屏幕控件", "high", listOf("auto.", "automator", "click(", "swipe(", "gesture(", "press(", ".click(", "settext(", "input(")),
            RiskRule("截图/录屏/图像识别", "脚本可能请求截图、录屏或处理屏幕图像", "high", listOf("capturescreen", "requestscreen", "requestscreencapture", "screenrecord", "mediaprojection", "images.", "findimage", "findcolor", "截图", "录屏")),
            RiskRule("悬浮窗", "脚本可能显示悬浮窗或覆盖其他应用", "high", listOf("floaty", "悬浮窗", "system_alert_window")),
            RiskRule("Root", "脚本可能使用 Root 权限", "high", listOf("rootautomator", "root.", "su -c", "root")),
            RiskRule("Shizuku", "脚本可能使用 Shizuku 特权", "high", listOf("shizuku")),
            RiskRule("Shell", "脚本可能执行 Shell 命令", "high", listOf("shell(", "shell.", "exec(", "runtime.exec")),
            RiskRule("文件删除/覆盖/外部存储", "脚本可能删除、覆盖或批量读写外部存储文件", "high", listOf("files.remove", "files.write", "files.append", "deletefile", "remove(", "rmdir", "unlink", "/sdcard", "externalstorage", "外部存储")),
            RiskRule("网络上传本地文件", "脚本可能通过网络请求上传本地文件或日志", "high", listOf("postmultipart", "upload", "multipart", "http.post", "http.request", "files.read", "readbytes", "上传")),
            RiskRule("短信/联系人/电话", "脚本可能访问短信、联系人或电话能力", "high", listOf("sms", "contact", "contacts", "callphone", "sendmessage", "短信", "联系人", "电话")),
            RiskRule("相机/录音/定位", "脚本可能访问相机、麦克风或定位信息", "high", listOf("camera", "recordaudio", "microphone", "location", "gps", "相机", "录音", "定位")),
            RiskRule("应用安装/卸载", "脚本可能安装或卸载应用", "high", listOf("installpackage", "uninstall", "app.install", "app.uninstall", "pm install", "pm uninstall")),
            RiskRule("修改系统设置", "脚本可能修改系统设置或安全设置", "high", listOf("writesettings", "write_settings", "writesecuresettings", "write_secure_settings", "settings put", "系统设置", "安全设置")),
        )
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
