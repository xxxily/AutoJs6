package org.autojs.autojs.capability

import java.util.Locale

enum class CapabilityStatus(val wireName: String) {
    AVAILABLE("available"),
    REQUESTABLE("requestable"),
    MISSING("missing"),
    BLOCKED("blocked"),
    UNSUPPORTED("unsupported"),
}

data class CapabilityCoreDefinition(
    val id: String,
    val name: String,
    val relatedApis: List<String>,
    val permissions: List<String>,
    val services: List<String> = emptyList(),
    val dangerous: Boolean = false,
    val minSdk: Int = 1,
    val inrtSupported: Boolean = true,
    val description: String,
    val requestHint: String,
    val patterns: List<Regex> = emptyList(),
)

data class CapabilityDetection(
    val status: CapabilityStatus,
    val missing: List<String> = emptyList(),
    val blocked: List<String> = emptyList(),
    val unsupported: List<String> = emptyList(),
)

data class CapabilityCheck(
    val id: String,
    val name: String,
    val status: CapabilityStatus,
    val available: Boolean,
    val requestable: Boolean,
    val missing: List<String>,
    val blocked: List<String>,
    val unsupported: List<String>,
    val dangerous: Boolean,
    val permissions: List<String>,
    val services: List<String>,
    val relatedApis: List<String>,
    val description: String,
    val requestHint: String,
    val minSdk: Int,
    val inrtSupported: Boolean,
)

object CapabilityCore {

    const val ACCESSIBILITY = "accessibility"
    const val SCREEN_CAPTURE = "screen_capture"
    const val OVERLAY = "overlay"
    const val NOTIFICATIONS = "notifications"
    const val STORAGE = "storage"
    const val NETWORK = "network"
    const val ROOT = "root"
    const val SHIZUKU = "shizuku"
    const val SHELL = "shell"
    const val USAGE_STATS = "usage_stats"
    const val WRITE_SETTINGS = "write_settings"
    const val WRITE_SECURE_SETTINGS = "write_secure_settings"
    const val EXACT_ALARM = "exact_alarm"
    const val BACKGROUND_RUN = "background_run"
    const val BATTERY_OPTIMIZATION = "battery_optimization"
    const val BOOT_COMPLETED = "boot_completed"
    const val INSTALL_APK = "install_apk"
    const val UNINSTALL_APK = "uninstall_apk"
    const val SMS = "sms"
    const val CONTACTS = "contacts"
    const val PHONE = "phone"
    const val CAMERA = "camera"
    const val RECORD_AUDIO = "record_audio"
    const val LOCATION = "location"

    val definitions: List<CapabilityCoreDefinition> = listOf(
        definition(
            id = ACCESSIBILITY,
            name = "无障碍服务",
            relatedApis = listOf(
                "auto",
                "auto.waitFor",
                "auto.waitUntil",
                "auto.retry",
                "auto.stableClick",
                "auto.stableSetText",
                "auto.findWithScroll",
                "vision.targets",
                "vision.findText",
                "vision.findButton",
                "vision.observe",
                "vision.waitForScene",
                "automator.click",
                "click",
                "swipe",
                "gesture",
                "selector",
            ),
            services = listOf("AccessibilityService"),
            dangerous = true,
            description = "点击、滑动、查找控件和读取 UI 树需要无障碍服务处于可用状态。",
            requestHint = "打开系统无障碍设置并启用 AutoJs6。",
            patterns = patterns("auto\\s*\\(", "auto\\.", "vision\\.", "\\b(click|longClick|press|swipe|gesture|gestures|setText|input)\\s*\\(", "\\b(text|id|desc|className|packageName)\\s*\\("),
        ),
        definition(
            id = SCREEN_CAPTURE,
            name = "截图/录屏",
            relatedApis = listOf("images.requestScreenCapture", "requestScreenCapture", "images.captureScreen", "captureScreen", "images.openCaptureSession", "images.findImage", "images.findColor", "ocr.detect", "vision.targets", "vision.findText", "vision.findButton", "vision.observe", "vision.waitForScene"),
            permissions = listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            services = listOf("MediaProjection"),
            dangerous = true,
            description = "截图、找图、找色和部分 OCR 流程需要屏幕采集授权。",
            requestHint = "脚本运行时调用 requestScreenCapture() 触发系统截图授权。",
            patterns = patterns("requestScreenCapture", "captureScreen", "openCaptureSession", "findImage", "findColor", "images\\.", "ocr\\.detect", "ocr\\.recognizeText", "vision\\."),
        ),
        definition(
            id = OVERLAY,
            name = "悬浮窗",
            relatedApis = listOf("floaty", "floaty.window", "floaty.rawWindow", "floaty.requestPermission"),
            permissions = listOf("android.permission.SYSTEM_ALERT_WINDOW"),
            dangerous = true,
            description = "悬浮窗会覆盖其他应用, 需要系统悬浮窗授权。",
            requestHint = "打开悬浮窗权限设置并允许 AutoJs6 显示在其他应用上层。",
            patterns = patterns("floaty", "rawWindow", "window\\s*\\("),
        ),
        definition(
            id = NOTIFICATIONS,
            name = "通知",
            relatedApis = listOf("notice", "notice.post", "events.observeNotification", "observeNotification"),
            permissions = listOf("android.permission.POST_NOTIFICATIONS"),
            services = listOf("NotificationManager"),
            description = "前台服务、脚本运行通知和通知监听相关能力依赖通知权限或通知开关。",
            requestHint = "允许通知权限或在系统设置中打开 AutoJs6 通知。",
            patterns = patterns("notice\\.", "observeNotification", "Notification"),
        ),
        definition(
            id = STORAGE,
            name = "存储",
            relatedApis = listOf("files", "files.read", "files.write", "files.remove", "open"),
            permissions = listOf(
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
            ),
            dangerous = true,
            description = "外部存储读写、批量删除或工程资源访问可能需要文件访问授权。",
            requestHint = "授予所有文件访问权限或旧版读写外部存储权限。",
            patterns = patterns("files\\.", "open\\s*\\(", "/sdcard", "externalStorage"),
        ),
        definition(
            id = NETWORK,
            name = "网络",
            relatedApis = listOf("http", "http.get", "http.post", "http.request", "web.newWebSocket"),
            permissions = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            description = "HTTP、WebSocket 和网络上传依赖网络权限和当前网络连接。",
            requestHint = "确认 Manifest 包含网络权限并检查设备网络连接。",
            patterns = patterns("http\\.", "websocket", "upload", "postMultipart"),
        ),
        definition(
            id = ROOT,
            name = "Root",
            relatedApis = listOf("root", "rootShell", "RootAutomator", "shell(cmd, true)"),
            dangerous = true,
            description = "Root 命令和 RootAutomator 依赖设备具备 su/root 环境。",
            requestHint = "在已 Root 设备上运行, 或在设置中调整 Root 模式。",
            patterns = patterns("root", "RootAutomator", "su\\s+-c", "withRoot\\s*[:=]\\s*true"),
        ),
        definition(
            id = SHIZUKU,
            name = "Shizuku",
            relatedApis = listOf(
                "shizuku",
                "shizuku.execCommand",
                "shizuku.kill",
                "shizuku.app.forceStop",
                "shizuku.app.clearData",
                "shizuku.app.grantPermission",
                "shizuku.settings.put",
                "shizuku.package.permissionState",
                "shizuku.input.injectTap",
                "shizuku.process.kill",
                "shizuku.users.runAsUser",
            ),
            permissions = listOf("moe.shizuku.manager.permission.API_V23"),
            services = listOf("Shizuku"),
            dangerous = true,
            description = "Shizuku 特权命令依赖 Shizuku 应用、运行中的服务和授权。",
            requestHint = "启动 Shizuku 服务并授予 AutoJs6 访问权限。",
            patterns = patterns("shizuku"),
        ),
        definition(
            id = SHELL,
            name = "Shell",
            relatedApis = listOf("shell", "Shell", "ProcessShell"),
            dangerous = true,
            description = "Shell 命令可执行系统命令; 非 Root shell 默认可用, 特权命令还需要 Root 或 Shizuku。",
            requestHint = "普通 shell 无需授权; 特权命令请检查 Root 或 Shizuku。",
            patterns = patterns("\\bshell\\s*\\(", "\\\$shell", "ProcessShell", "execCommand"),
        ),
        definition(
            id = USAGE_STATS,
            name = "UsageStats",
            relatedApis = listOf("auto.setFlags", "currentPackage", "currentActivity"),
            permissions = listOf("android.permission.PACKAGE_USAGE_STATS"),
            description = "读取前台应用和使用情况统计需要 UsageStats 授权。",
            requestHint = "打开使用情况访问权限并允许 AutoJs6。",
            patterns = patterns("useUsageStats", "currentPackage", "currentActivity"),
        ),
        definition(
            id = WRITE_SETTINGS,
            name = "WriteSettings",
            relatedApis = listOf("autojs.canWriteSettings", "device.setMusicVolume", "Settings.System"),
            permissions = listOf("android.permission.WRITE_SETTINGS"),
            dangerous = true,
            description = "修改系统设置需要 WRITE_SETTINGS 授权。",
            requestHint = "打开修改系统设置权限并允许 AutoJs6。",
            patterns = patterns("WRITE_SETTINGS", "Settings\\.System", "canWriteSettings", "setMusicVolume"),
        ),
        definition(
            id = WRITE_SECURE_SETTINGS,
            name = "WriteSecureSettings",
            relatedApis = listOf("autojs.canWriteSecureSettings", "settings put secure"),
            permissions = listOf("android.permission.WRITE_SECURE_SETTINGS"),
            dangerous = true,
            description = "修改 Secure Settings 需要 ADB/Root/Shizuku 授权, 普通运行时权限无法直接授予。",
            requestHint = "通过 ADB、Root 或 Shizuku 授予 WRITE_SECURE_SETTINGS。",
            patterns = patterns("WRITE_SECURE_SETTINGS", "Settings\\.Secure", "settings\\s+put\\s+secure", "canWriteSecureSettings"),
        ),
        definition(
            id = EXACT_ALARM,
            name = "精确闹钟",
            relatedApis = listOf("tasks.addDisposableTask", "tasks.addDailyTask", "tasks.addWeeklyTask"),
            permissions = listOf("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM"),
            description = "精确定时任务在 Android 12+ 可能需要闹钟和提醒权限。",
            requestHint = "打开闹钟和提醒权限以提升定时任务准点率。",
            patterns = patterns("addDailyTask", "addWeeklyTask", "addDisposableTask", "TimedTask"),
        ),
        definition(
            id = BACKGROUND_RUN,
            name = "后台运行",
            relatedApis = listOf("tasks", "engines.execScriptFile", "foreground service"),
            permissions = listOf("android.permission.WAKE_LOCK", "android.permission.FOREGROUND_SERVICE"),
            description = "后台启动和后台保活受 ROM 策略影响, 定时任务和长期运行脚本会受影响。",
            requestHint = "在系统/厂商设置中允许后台启动和后台运行。",
            patterns = patterns("tasks\\.", "engines\\.exec", "setInterval", "foreground"),
        ),
        definition(
            id = BATTERY_OPTIMIZATION,
            name = "电池优化",
            relatedApis = listOf("tasks", "engines", "foreground service"),
            permissions = listOf("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"),
            description = "电池优化可能导致定时任务、前台服务和长时间脚本被系统停止。",
            requestHint = "请求忽略电池优化或在系统设置中关闭 AutoJs6 电池限制。",
            patterns = patterns("tasks\\.", "setInterval", "long running", "foreground"),
        ),
        definition(
            id = BOOT_COMPLETED,
            name = "开机启动",
            relatedApis = listOf("runOnBoot", "tasks.addIntentTask", "BOOT_COMPLETED"),
            permissions = listOf("android.permission.RECEIVE_BOOT_COMPLETED"),
            description = "开机广播任务需要 Manifest 声明 RECEIVE_BOOT_COMPLETED。",
            requestHint = "确认应用或打包 APK Manifest 声明开机广播权限。",
            patterns = patterns("runOnBoot", "BOOT_COMPLETED", "RECEIVE_BOOT_COMPLETED"),
        ),
        definition(
            id = INSTALL_APK,
            name = "安装 APK",
            relatedApis = listOf("app.install", "installPackage", "pm install"),
            permissions = listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
            dangerous = true,
            minSdk = 26,
            description = "安装外部 APK 需要未知来源安装授权或用户确认安装。",
            requestHint = "打开允许安装未知应用权限。",
            patterns = patterns("installPackage", "pm\\s+install", "app\\.install"),
        ),
        definition(
            id = UNINSTALL_APK,
            name = "卸载 APK",
            relatedApis = listOf("app.uninstall", "uninstallPackage", "pm uninstall"),
            permissions = listOf("android.permission.REQUEST_DELETE_PACKAGES"),
            dangerous = true,
            description = "卸载应用通常需要系统确认; 静默卸载需要更高特权。",
            requestHint = "使用系统卸载确认流程, 静默卸载请检查 Root/Shizuku。",
            patterns = patterns("uninstallPackage", "pm\\s+uninstall", "app\\.uninstall"),
        ),
        runtimePermission(SMS, "短信", listOf("sms", "sendMessage", "READ_SMS", "SEND_SMS"), listOf("android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS"), "读取、发送或接收短信需要短信运行时权限。", "请求短信相关运行时权限。", patterns("\\bsms\\b", "sendMessage", "READ_SMS", "SEND_SMS")),
        runtimePermission(CONTACTS, "联系人", listOf("contacts", "READ_CONTACTS", "WRITE_CONTACTS"), listOf("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS"), "读取或修改联系人需要联系人运行时权限。", "请求联系人相关运行时权限。", patterns("contacts?", "READ_CONTACTS", "WRITE_CONTACTS")),
        runtimePermission(PHONE, "电话", listOf("phone", "call", "ACTION_CALL", "tel:", "app.startActivity"), listOf("android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE", "android.permission.READ_CALL_LOG"), "拨打电话、读取电话状态或访问通话记录需要电话相关运行时权限。", "请求电话相关运行时权限; 只打开拨号界面时优先使用 ACTION_DIAL。", patterns("ACTION_CALL", "\\btel:", "CALL_PHONE", "READ_PHONE_STATE", "READ_CALL_LOG")),
        runtimePermission(CAMERA, "相机", listOf("camera", "barcode", "qrcode"), listOf("android.permission.CAMERA"), "调用相机、扫码或拍摄能力需要相机权限。", "请求相机运行时权限。", patterns("\\bcamera\\b", "barcode", "qrcode", "scan")),
        runtimePermission(RECORD_AUDIO, "录音", listOf("media.record", "recorder", "RECORD_AUDIO"), listOf("android.permission.RECORD_AUDIO"), "录音或访问麦克风需要 RECORD_AUDIO 权限。", "请求录音运行时权限。", patterns("recordAudio", "RECORD_AUDIO", "microphone", "recorder")),
        runtimePermission(LOCATION, "定位", listOf("location", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"), listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"), "读取设备定位需要位置运行时权限。", "请求精确或粗略定位权限。", patterns("location", "gps", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")),
    )

    private val definitionsById = definitions.associateBy { it.id.lowercase(Locale.ROOT) }
    private val definitionsByApi = definitions
        .flatMap { definition -> definition.relatedApis.map { normalize(it) to definition } }
        .toMap()

    fun allDefinitions(): List<CapabilityCoreDefinition> = definitions

    fun definitionFor(idOrApi: String): CapabilityCoreDefinition? {
        val normalized = normalize(idOrApi)
        definitionsById[normalized]?.let { return it }
        definitionsByApi[normalized]?.let { return it }
        return definitions.firstOrNull { definition ->
            definition.relatedApis.any { api ->
                normalized == normalize(api) || normalized.startsWith("${normalize(api)}.")
            }
        }
    }

    fun manifestPermissionsForCapabilityIds(idsOrApis: Collection<String>): List<String> {
        return idsOrApis
            .mapNotNull(::definitionFor)
            .flatMap { it.permissions }
            .distinct()
    }

    fun inferCapabilitiesFromScript(script: String): List<CapabilityCoreDefinition> {
        val searchable = stripStringsAndComments(script)
        return definitions.filter { definition ->
            definition.patterns.any { it.containsMatchIn(searchable) }
        }
    }

    fun inferCapabilityIdsFromScript(script: String): List<String> {
        return inferCapabilitiesFromScript(script).map { it.id }
    }

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun stripStringsAndComments(source: String): String {
        val withoutBlockComments = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        val withoutLineComments = withoutBlockComments.replace(Regex("//.*"), " ")
        val withoutStrings = withoutLineComments
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")
            .replace(Regex("'(?:\\\\.|[^'\\\\])*'"), "''")
            .replace(Regex("`(?:\\\\.|[^`\\\\])*`", RegexOption.DOT_MATCHES_ALL), "``")
        return withoutStrings
    }

    private fun runtimePermission(
        id: String,
        name: String,
        relatedApis: List<String>,
        permissions: List<String>,
        description: String,
        requestHint: String,
        patterns: List<Regex>,
    ) = definition(
        id = id,
        name = name,
        relatedApis = relatedApis,
        permissions = permissions,
        dangerous = true,
        description = description,
        requestHint = requestHint,
        patterns = patterns,
    )

    private fun definition(
        id: String,
        name: String,
        relatedApis: List<String>,
        permissions: List<String> = emptyList(),
        services: List<String> = emptyList(),
        dangerous: Boolean = false,
        minSdk: Int = 1,
        inrtSupported: Boolean = true,
        description: String,
        requestHint: String,
        patterns: List<Regex>,
    ) = CapabilityCoreDefinition(
        id = id,
        name = name,
        relatedApis = relatedApis,
        permissions = permissions,
        services = services,
        dangerous = dangerous,
        minSdk = minSdk,
        inrtSupported = inrtSupported,
        description = description,
        requestHint = requestHint,
        patterns = patterns,
    )

    private fun patterns(vararg expressions: String): List<Regex> {
        return expressions.map { Regex(it, RegexOption.IGNORE_CASE) }
    }
}
