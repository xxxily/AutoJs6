package org.autojs.autojs.capability

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ezy.assist.compat.SettingsCompat
import org.autojs.autojs.app.AppOps
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.permission.Permissions
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.PostNotificationsPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.timing.ExactAlarmPermissionHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RomUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import java.util.Locale

enum class CapabilityStatus(val wireName: String) {
    AVAILABLE("available"),
    REQUESTABLE("requestable"),
    MISSING("missing"),
    BLOCKED("blocked"),
    UNSUPPORTED("unsupported"),
}

data class CapabilityDefinition(
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
    internal val detector: (Context) -> CapabilityDetection,
    internal val requester: ((Context) -> Boolean)? = null,
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

object CapabilityRegistry {

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

    val definitions: List<CapabilityDefinition> = listOf(
        CapabilityDefinition(
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
            permissions = emptyList(),
            services = listOf("AccessibilityService"),
            dangerous = true,
            description = "点击、滑动、查找控件和读取 UI 树需要无障碍服务处于可用状态。",
            requestHint = "打开系统无障碍设置并启用 AutoJs6。",
            patterns = patterns("auto\\s*\\(", "auto\\.", "vision\\.", "\\b(click|longClick|press|swipe|gesture|gestures|setText|input)\\s*\\(", "\\b(text|id|desc|className|packageName)\\s*\\("),
            detector = { context ->
                if (AccessibilityTool(context).isOperational()) available() else requestable("AccessibilityService")
            },
            requester = { context -> AccessibilityTool(context).startService() },
        ),
        CapabilityDefinition(
            id = SCREEN_CAPTURE,
            name = "截图/录屏",
            relatedApis = listOf("images.requestScreenCapture", "requestScreenCapture", "images.captureScreen", "captureScreen", "images.openCaptureSession", "images.findImage", "images.findColor", "ocr.detect", "vision.targets", "vision.findText", "vision.findButton", "vision.observe", "vision.waitForScene"),
            permissions = listOf(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION),
            services = listOf("MediaProjection"),
            dangerous = true,
            description = "截图、找图、找色和部分 OCR 流程需要屏幕采集授权。",
            requestHint = "脚本运行时调用 requestScreenCapture() 触发系统截图授权。",
            patterns = patterns("requestScreenCapture", "captureScreen", "openCaptureSession", "findImage", "findColor", "images\\.", "ocr\\.detect", "ocr\\.recognizeText", "vision\\."),
            detector = { context ->
                if (AppOps.isProjectMediaAccessGranted(context)) available() else requestable("MediaProjection")
            },
        ),
        CapabilityDefinition(
            id = OVERLAY,
            name = "悬浮窗",
            relatedApis = listOf("floaty", "floaty.window", "floaty.rawWindow", "floaty.requestPermission"),
            permissions = listOf(Manifest.permission.SYSTEM_ALERT_WINDOW),
            dangerous = true,
            description = "悬浮窗会覆盖其他应用, 需要系统悬浮窗授权。",
            requestHint = "打开悬浮窗权限设置并允许 AutoJs6 显示在其他应用上层。",
            patterns = patterns("floaty", "rawWindow", "window\\s*\\("),
            detector = { context -> if (SettingsCompat.canDrawOverlays(context)) available() else requestable(Manifest.permission.SYSTEM_ALERT_WINDOW) },
            requester = { context -> DisplayOverOtherAppsPermission(context).request() },
        ),
        CapabilityDefinition(
            id = NOTIFICATIONS,
            name = "通知",
            relatedApis = listOf("notice", "notice.post", "events.observeNotification", "observeNotification"),
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            services = listOf("NotificationManager"),
            dangerous = false,
            minSdk = 1,
            description = "前台服务、脚本运行通知和通知监听相关能力依赖通知权限或通知开关。",
            requestHint = "允许通知权限或在系统设置中打开 AutoJs6 通知。",
            patterns = patterns("notice\\.", "observeNotification", "Notification"),
            detector = { if (NotificationUtils.isEnabled()) available() else requestable(Manifest.permission.POST_NOTIFICATIONS) },
            requester = { context -> PostNotificationsPermission(context).request() },
        ),
        CapabilityDefinition(
            id = STORAGE,
            name = "存储",
            relatedApis = listOf("files", "files.read", "files.write", "files.remove", "open"),
            permissions = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "android.permission.MANAGE_EXTERNAL_STORAGE",
            ),
            dangerous = true,
            description = "外部存储读写、批量删除或工程资源访问可能需要文件访问授权。",
            requestHint = "授予所有文件访问权限或旧版读写外部存储权限。",
            patterns = patterns("files\\.", "open\\s*\\(", "/sdcard", "externalStorage"),
            detector = { context -> if (AllFilesAccessPermission(context).has()) available() else requestable("storage") },
            requester = { context -> AllFilesAccessPermission(context).request() },
        ),
        CapabilityDefinition(
            id = NETWORK,
            name = "网络",
            relatedApis = listOf("http", "http.get", "http.post", "http.request", "web.newWebSocket"),
            permissions = listOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE),
            dangerous = false,
            description = "HTTP、WebSocket 和网络上传依赖网络权限和当前网络连接。",
            requestHint = "确认 Manifest 包含网络权限并检查设备网络连接。",
            patterns = patterns("http\\.", "websocket", "upload", "postMultipart"),
            detector = { context ->
                when {
                    !hasManifestPermission(context, Manifest.permission.INTERNET) -> missing(Manifest.permission.INTERNET)
                    isNetworkConnected(context) -> available()
                    else -> missing("network connection")
                }
            },
        ),
        CapabilityDefinition(
            id = ROOT,
            name = "Root",
            relatedApis = listOf("root", "rootShell", "RootAutomator", "shell(cmd, true)"),
            permissions = emptyList(),
            dangerous = true,
            description = "Root 命令和 RootAutomator 依赖设备具备 su/root 环境。",
            requestHint = "在已 Root 设备上运行, 或在设置中调整 Root 模式。",
            patterns = patterns("root", "RootAutomator", "su\\s+-c", "withRoot\\s*[:=]\\s*true"),
            detector = { if (RootUtils.isRootAvailable()) available() else missing("su/root") },
        ),
        CapabilityDefinition(
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
            detector = { context ->
                when {
                    WrappedShizuku.isOperational() -> available()
                    !WrappedShizuku.isInstalled(context) -> missing("Shizuku app")
                    !WrappedShizuku.isRunning() -> blocked("Shizuku service is not running")
                    else -> requestable("Shizuku permission")
                }
            },
            requester = { context -> WrappedShizuku.configWithContext(context, true) != null },
        ),
        CapabilityDefinition(
            id = SHELL,
            name = "Shell",
            relatedApis = listOf("shell", "Shell", "ProcessShell"),
            permissions = emptyList(),
            dangerous = true,
            description = "Shell 命令可执行系统命令; 非 Root shell 默认可用, 特权命令还需要 Root 或 Shizuku。",
            requestHint = "普通 shell 无需授权; 特权命令请检查 Root 或 Shizuku。",
            patterns = patterns("\\bshell\\s*\\(", "\\\$shell", "ProcessShell", "execCommand"),
            detector = { available() },
        ),
        specialPermission(
            id = USAGE_STATS,
            name = "UsageStats",
            relatedApis = listOf("auto.setFlags", "currentPackage", "currentActivity"),
            permission = Manifest.permission.PACKAGE_USAGE_STATS,
            description = "读取前台应用和使用情况统计需要 UsageStats 授权。",
            requestHint = "打开使用情况访问权限并允许 AutoJs6。",
            patterns = patterns("useUsageStats", "currentPackage", "currentActivity"),
            has = { AppOps.isUsageStatsPermissionGranted(it) },
            request = { UsageStatsPermission(it).request() },
        ),
        specialPermission(
            id = WRITE_SETTINGS,
            name = "WriteSettings",
            relatedApis = listOf("autojs.canWriteSettings", "device.setMusicVolume", "Settings.System"),
            permission = Manifest.permission.WRITE_SETTINGS,
            dangerous = true,
            description = "修改系统设置需要 WRITE_SETTINGS 授权。",
            requestHint = "打开修改系统设置权限并允许 AutoJs6。",
            patterns = patterns("WRITE_SETTINGS", "Settings\\.System", "canWriteSettings", "setMusicVolume"),
            has = { Settings.System.canWrite(it) },
            request = { WriteSystemSettingsPermission(it).request() },
        ),
        CapabilityDefinition(
            id = WRITE_SECURE_SETTINGS,
            name = "WriteSecureSettings",
            relatedApis = listOf("autojs.canWriteSecureSettings", "settings put secure"),
            permissions = listOf("android.permission.WRITE_SECURE_SETTINGS"),
            dangerous = true,
            description = "修改 Secure Settings 需要 ADB/Root/Shizuku 授权, 普通运行时权限无法直接授予。",
            requestHint = "通过 ADB、Root 或 Shizuku 授予 WRITE_SECURE_SETTINGS。",
            patterns = patterns("WRITE_SECURE_SETTINGS", "Settings\\.Secure", "settings\\s+put\\s+secure", "canWriteSecureSettings"),
            detector = { context ->
                when {
                    SettingsUtils.SecureSettings.isGranted(context) -> available()
                    RootUtils.isRootAvailable() || WrappedShizuku.isOperational() -> requestable("android.permission.WRITE_SECURE_SETTINGS")
                    else -> missing("ADB/Root/Shizuku grant")
                }
            },
            requester = { context -> WriteSecureSettingsPermission(context).request() },
        ),
        CapabilityDefinition(
            id = EXACT_ALARM,
            name = "精确闹钟",
            relatedApis = listOf("tasks.addDisposableTask", "tasks.addDailyTask", "tasks.addWeeklyTask"),
            permissions = listOf("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM"),
            description = "精确定时任务在 Android 12+ 可能需要闹钟和提醒权限。",
            requestHint = "打开闹钟和提醒权限以提升定时任务准点率。",
            patterns = patterns("addDailyTask", "addWeeklyTask", "addDisposableTask", "TimedTask"),
            detector = { context ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ExactAlarmPermissionHelper.canScheduleExactAlarms(context)) {
                    available()
                } else {
                    requestable("SCHEDULE_EXACT_ALARM")
                }
            },
            requester = { context ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ExactAlarmPermissionHelper.requestExactAlarmPermission(context)
            },
        ),
        CapabilityDefinition(
            id = BACKGROUND_RUN,
            name = "后台运行",
            relatedApis = listOf("tasks", "engines.execScriptFile", "foreground service"),
            permissions = listOf(Manifest.permission.WAKE_LOCK, Manifest.permission.FOREGROUND_SERVICE),
            description = "后台启动和后台保活受 ROM 策略影响, 定时任务和长期运行脚本会受影响。",
            requestHint = "在系统/厂商设置中允许后台启动和后台运行。",
            patterns = patterns("tasks\\.", "engines\\.exec", "setInterval", "foreground"),
            detector = { context -> if (RomUtils.isBackgroundStartGranted(context)) available() else requestable("background start") },
            requester = { context -> IntentUtils.launchAppDetailsSettings(context) },
        ),
        CapabilityDefinition(
            id = BATTERY_OPTIMIZATION,
            name = "电池优化",
            relatedApis = listOf("tasks", "engines", "foreground service"),
            permissions = listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
            description = "电池优化可能导致定时任务、前台服务和长时间脚本被系统停止。",
            requestHint = "请求忽略电池优化或在系统设置中关闭 AutoJs6 电池限制。",
            patterns = patterns("tasks\\.", "setInterval", "long running", "foreground"),
            detector = { context -> if (IgnoreBatteryOptimizationsPermission(context).has()) available() else requestable(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) },
            requester = { context -> IgnoreBatteryOptimizationsPermission(context).request() },
        ),
        manifestOnly(
            id = BOOT_COMPLETED,
            name = "开机启动",
            relatedApis = listOf("runOnBoot", "tasks.addIntentTask", "BOOT_COMPLETED"),
            permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
            description = "开机广播任务需要 Manifest 声明 RECEIVE_BOOT_COMPLETED。",
            requestHint = "确认应用或打包 APK Manifest 声明开机广播权限。",
            patterns = patterns("runOnBoot", "BOOT_COMPLETED", "RECEIVE_BOOT_COMPLETED"),
        ),
        CapabilityDefinition(
            id = INSTALL_APK,
            name = "安装 APK",
            relatedApis = listOf("app.install", "installPackage", "pm install"),
            permissions = listOf(Manifest.permission.REQUEST_INSTALL_PACKAGES),
            dangerous = true,
            minSdk = Build.VERSION_CODES.O,
            description = "安装外部 APK 需要未知来源安装授权或用户确认安装。",
            requestHint = "打开允许安装未知应用权限。",
            patterns = patterns("installPackage", "pm\\s+install", "app\\.install"),
            detector = { context ->
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> available()
                    context.packageManager.canRequestPackageInstalls() -> available()
                    hasManifestPermission(context, Manifest.permission.REQUEST_INSTALL_PACKAGES) -> requestable(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                    else -> missing(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                }
            },
            requester = { context ->
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).startSafely(context)
            },
        ),
        manifestOnly(
            id = UNINSTALL_APK,
            name = "卸载 APK",
            relatedApis = listOf("app.uninstall", "uninstallPackage", "pm uninstall"),
            permission = "android.permission.REQUEST_DELETE_PACKAGES",
            dangerous = true,
            description = "卸载应用通常需要系统确认; 静默卸载需要更高特权。",
            requestHint = "使用系统卸载确认流程, 静默卸载请检查 Root/Shizuku。",
            patterns = patterns("uninstallPackage", "pm\\s+uninstall", "app\\.uninstall"),
        ),
        runtimePermissions(
            id = SMS,
            name = "短信",
            relatedApis = listOf("sms", "sendMessage", "READ_SMS", "SEND_SMS"),
            permissions = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS),
            dangerous = true,
            description = "读取、发送或接收短信需要短信运行时权限。",
            requestHint = "请求短信相关运行时权限。",
            patterns = patterns("\\bsms\\b", "sendMessage", "READ_SMS", "SEND_SMS"),
        ),
        runtimePermissions(
            id = CONTACTS,
            name = "联系人",
            relatedApis = listOf("contacts", "READ_CONTACTS", "WRITE_CONTACTS"),
            permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            dangerous = true,
            description = "读取或修改联系人需要联系人运行时权限。",
            requestHint = "请求联系人相关运行时权限。",
            patterns = patterns("contacts?", "READ_CONTACTS", "WRITE_CONTACTS"),
        ),
        runtimePermissions(
            id = PHONE,
            name = "电话",
            relatedApis = listOf("phone", "call", "ACTION_CALL", "tel:", "app.startActivity"),
            permissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG),
            dangerous = true,
            description = "拨打电话、读取电话状态或访问通话记录需要电话相关运行时权限。",
            requestHint = "请求电话相关运行时权限; 只打开拨号界面时优先使用 ACTION_DIAL。",
            patterns = patterns("ACTION_CALL", "\\btel:", "CALL_PHONE", "READ_PHONE_STATE", "READ_CALL_LOG"),
        ),
        runtimePermissions(
            id = CAMERA,
            name = "相机",
            relatedApis = listOf("camera", "barcode", "qrcode"),
            permissions = listOf(Manifest.permission.CAMERA),
            dangerous = true,
            description = "调用相机、扫码或拍摄能力需要相机权限。",
            requestHint = "请求相机运行时权限。",
            patterns = patterns("\\bcamera\\b", "barcode", "qrcode", "scan"),
        ),
        runtimePermissions(
            id = RECORD_AUDIO,
            name = "录音",
            relatedApis = listOf("media.record", "recorder", "RECORD_AUDIO"),
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            dangerous = true,
            description = "录音或访问麦克风需要 RECORD_AUDIO 权限。",
            requestHint = "请求录音运行时权限。",
            patterns = patterns("recordAudio", "RECORD_AUDIO", "microphone", "recorder"),
        ),
        runtimePermissions(
            id = LOCATION,
            name = "定位",
            relatedApis = listOf("location", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            dangerous = true,
            description = "读取设备定位需要位置运行时权限。",
            requestHint = "请求精确或粗略定位权限。",
            patterns = patterns("location", "gps", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
        ),
    )

    private val definitionsById = definitions.associateBy { it.id.lowercase(Locale.ROOT) }
    private val definitionsByApi = definitions
        .flatMap { definition -> definition.relatedApis.map { normalize(it) to definition } }
        .toMap()

    fun allDefinitions(): List<CapabilityDefinition> = definitions

    fun manifestPermissionsForCapabilityIds(idsOrApis: Collection<String>): List<String> {
        return idsOrApis
            .mapNotNull(::definitionFor)
            .flatMap { it.permissions }
            .distinct()
    }

    fun check(context: Context, idsOrApis: Collection<String> = emptyList()): List<CapabilityCheck> {
        val requested = idsOrApis.mapNotNull { it.trim().takeIf(String::isNotBlank) }
        if (requested.isEmpty()) {
            return definitions.map { definition -> definition.check(context) }
        }
        return requested.map { idOrApi ->
            definitionFor(idOrApi)?.check(context) ?: unknownCheck(idOrApi)
        }
    }

    fun explain(idOrApi: String): CapabilityDefinition? = definitionFor(idOrApi)

    fun inferCapabilitiesFromScript(script: String): List<CapabilityDefinition> {
        val searchable = stripStringsAndComments(script)
        return definitions.filter { definition ->
            definition.patterns.any { it.containsMatchIn(searchable) }
        }
    }

    fun inferCapabilityIdsFromScript(script: String): List<String> {
        return inferCapabilitiesFromScript(script).map { it.id }
    }

    fun ensure(
        context: Context,
        idsOrApis: Collection<String>,
        request: Boolean = false,
        timeoutMillis: Long = 0L,
    ): List<CapabilityCheck> {
        if (request) {
            check(context, idsOrApis)
                .filterNot { it.available }
                .forEach { check ->
                    definitionsById[check.id]?.requester?.invoke(context)
                }
        }
        if (timeoutMillis > 0L) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                val checks = check(context, idsOrApis)
                if (checks.all { it.available }) return checks
                Thread.sleep(250L)
            }
        }
        return check(context, idsOrApis)
    }

    private fun CapabilityDefinition.check(context: Context): CapabilityCheck {
        val detection = when {
            Build.VERSION.SDK_INT < minSdk -> unsupported("Requires Android API $minSdk+")
            permissions.isNotEmpty() && permissions.none { hasManifestPermission(context, it) } && id !in SPECIAL_MANIFEST_EXEMPT_IDS ->
                missing("Manifest permission: ${permissions.joinToString(" or ")}")
            else -> detector(context)
        }
        return CapabilityCheck(
            id = id,
            name = name,
            status = detection.status,
            available = detection.status == CapabilityStatus.AVAILABLE,
            requestable = detection.status == CapabilityStatus.REQUESTABLE,
            missing = detection.missing,
            blocked = detection.blocked,
            unsupported = detection.unsupported,
            dangerous = dangerous,
            permissions = permissions,
            services = services,
            relatedApis = relatedApis,
            description = description,
            requestHint = requestHint,
            minSdk = minSdk,
            inrtSupported = inrtSupported,
        )
    }

    private fun definitionFor(idOrApi: String): CapabilityDefinition? {
        val normalized = normalize(idOrApi)
        definitionsById[normalized]?.let { return it }
        definitionsByApi[normalized]?.let { return it }
        return definitions.firstOrNull { definition ->
            definition.relatedApis.any { api ->
                normalized == normalize(api) || normalized.startsWith("${normalize(api)}.")
            }
        }
    }

    private fun unknownCheck(idOrApi: String) = CapabilityCheck(
        id = idOrApi.ifBlank { "unknown" },
        name = idOrApi.ifBlank { "unknown" },
        status = CapabilityStatus.UNSUPPORTED,
        available = false,
        requestable = false,
        missing = emptyList(),
        blocked = emptyList(),
        unsupported = listOf("Unknown capability or API"),
        dangerous = false,
        permissions = emptyList(),
        services = emptyList(),
        relatedApis = emptyList(),
        description = "Unknown capability or API: $idOrApi",
        requestHint = "Check the current AutoJs6 capability index and local docs.",
        minSdk = 1,
        inrtSupported = false,
    )

    private fun runtimePermissions(
        id: String,
        name: String,
        relatedApis: List<String>,
        permissions: List<String>,
        dangerous: Boolean,
        description: String,
        requestHint: String,
        patterns: List<Regex>,
    ) = CapabilityDefinition(
        id = id,
        name = name,
        relatedApis = relatedApis,
        permissions = permissions,
        dangerous = dangerous,
        description = description,
        requestHint = requestHint,
        patterns = patterns,
        detector = { context ->
            val declared = permissions.filter { hasManifestPermission(context, it) }
            when {
                declared.isEmpty() -> missing("Manifest permission: ${permissions.joinToString(" or ")}")
                declared.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } -> available()
                else -> requestable(declared)
            }
        },
        requester = { context -> Permissions.requestPermissions(context, permissions.toTypedArray()).let { true } },
    )

    private fun specialPermission(
        id: String,
        name: String,
        relatedApis: List<String>,
        permission: String,
        dangerous: Boolean = false,
        description: String,
        requestHint: String,
        patterns: List<Regex>,
        has: (Context) -> Boolean,
        request: (Context) -> Boolean,
    ) = CapabilityDefinition(
        id = id,
        name = name,
        relatedApis = relatedApis,
        permissions = listOf(permission),
        dangerous = dangerous,
        description = description,
        requestHint = requestHint,
        patterns = patterns,
        detector = { context -> if (has(context)) available() else requestable(permission) },
        requester = request,
    )

    private fun manifestOnly(
        id: String,
        name: String,
        relatedApis: List<String>,
        permission: String,
        dangerous: Boolean = false,
        description: String,
        requestHint: String,
        patterns: List<Regex>,
    ) = CapabilityDefinition(
        id = id,
        name = name,
        relatedApis = relatedApis,
        permissions = listOf(permission),
        dangerous = dangerous,
        description = description,
        requestHint = requestHint,
        patterns = patterns,
        detector = { context -> if (hasManifestPermission(context, permission)) available() else missing(permission) },
    )

    private fun patterns(vararg patterns: String) = patterns.map {
        Regex(it, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    }

    private fun available() = CapabilityDetection(CapabilityStatus.AVAILABLE)
    private fun requestable(vararg missing: String) = CapabilityDetection(CapabilityStatus.REQUESTABLE, missing = missing.toList())
    private fun requestable(missing: List<String>) = CapabilityDetection(CapabilityStatus.REQUESTABLE, missing = missing)
    private fun missing(vararg missing: String) = CapabilityDetection(CapabilityStatus.MISSING, missing = missing.toList())
    private fun blocked(vararg blocked: String) = CapabilityDetection(CapabilityStatus.BLOCKED, blocked = blocked.toList())
    private fun unsupported(vararg unsupported: String) = CapabilityDetection(CapabilityStatus.UNSUPPORTED, unsupported = unsupported.toList())

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)

    private fun stripStringsAndComments(code: String): String {
        return code
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")
            .replace(Regex("'(?:\\\\.|[^'\\\\])*'"), "''")
            .replace(Regex("`(?:\\\\.|[^`\\\\])*`"), "``")
            .replace(Regex("//.*"), "")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
    }

    private fun hasManifestPermission(context: Context, permission: String): Boolean {
        return packageInfo(context).requestedPermissions?.contains(permission) == true
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo {
        val flags = PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun isNetworkConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.activeNetwork != null
        } else {
            manager.activeNetworkInfo?.isConnected == true
        }
    }

    private val SPECIAL_MANIFEST_EXEMPT_IDS = setOf(
        ACCESSIBILITY,
        SCREEN_CAPTURE,
        ROOT,
        SHIZUKU,
        SHELL,
        USAGE_STATS,
        WRITE_SETTINGS,
        WRITE_SECURE_SETTINGS,
        EXACT_ALARM,
        INSTALL_APK,
        UNINSTALL_APK,
    )
}
