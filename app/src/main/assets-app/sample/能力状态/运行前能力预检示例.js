/*
 * Capabilities - 运行前能力预检示例
 *
 * 本示例不会自动运行高风险操作, 只展示如何检查能力状态。
 */

let required = ["accessibility", "screen_capture", "shizuku"];
let checks = capabilities.check(required);

checks.forEach((item) => {
    log(item.name + " [" + item.id + "]: " + item.status);
    if (!item.available) {
        log("  " + item.requestHint);
    }
});

let captureInfo = capabilities.explain("images.captureScreen");
log("API 说明: " + captureInfo.name + " - " + captureInfo.description);

let inferred = capabilities.scan("requestScreenCapture(); auto.waitFor(); click('OK');");
log("静态扫描能力: " + inferred.map((item) => item.id).join(", "));
