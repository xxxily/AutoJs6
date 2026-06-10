/**
 * Shizuku 结构化特权 API 示例.
 *
 * 结构化 API 会返回:
 * ok/code/result/error/backend/riskLevel/capabilities/auditId/data
 *
 * 常见 pm/am/settings/input 场景优先使用 shizuku.app/settings/package/input/process/users,
 * 不需要手写 shell 字符串. 高风险调用会写入 shizuku.audit 审计记录.
 */

log("Shizuku state = " + JSON.stringify(shizuku.state));

let operations = Array.prototype.slice.call(shizuku.operations);
log("Structured operations = " + operations.map(it => it.operation).join(", "));

let enabledServices = shizuku.settings.secure.get("enabled_accessibility_services");
log("enabled_accessibility_services = " + enabledServices.data.value);

let currentUser = shizuku.users.currentUser();
log("current user result = " + JSON.stringify(currentUser.data));

let selfPermission = shizuku.package.permissionState(
    autojs.packageName,
    "android.permission.WRITE_SECURE_SETTINGS",
);
log("WRITE_SECURE_SETTINGS = " + JSON.stringify(selfPermission.data));

if (dialogs.confirm("是否演示高风险授权: WRITE_SECURE_SETTINGS ?")) {
    let granted = shizuku.app.grantPermission(
        autojs.packageName,
        "android.permission.WRITE_SECURE_SETTINGS",
    );
    log("grant result = " + JSON.stringify({
        ok: granted.ok,
        backend: granted.backend,
        riskLevel: granted.riskLevel,
        capabilities: granted.capabilities,
        auditId: granted.auditId,
    }));
}

// 高风险强停示例. 请替换为真实目标包名并确认后再执行.
// shizuku.app.forceStop("com.example.target");

// input 注入与无障碍 click/swipe 不同, 它走系统 input 后端.
// shizuku.input.injectTap(100, 120);

log("Audit = " + shizuku.audit.exportJson());
