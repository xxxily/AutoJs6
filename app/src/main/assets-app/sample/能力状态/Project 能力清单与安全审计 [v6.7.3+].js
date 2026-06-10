"auto";

/*
 * project.json 示例:
 * {
 *   "name": "安全能力示例",
 *   "packageName": "org.autojs.autojs6.samples.capability",
 *   "versionName": "1.0.0",
 *   "versionCode": 1,
 *   "main": "main.js",
 *   "capabilities": ["shell", "shizuku", "storage"],
 *   "pluginDependencies": [],
 *   "riskPolicy": {
 *     "undeclared": "prompt",
 *     "critical": "reject",
 *     "rememberAllowed": true
 *   },
 *   "filePolicy": {
 *     "allowedPaths": ["logs"],
 *     "writablePaths": ["logs"],
 *     "deletablePaths": ["logs/tmp"]
 *   },
 *   "privilegedPolicy": {
 *     "shell": true,
 *     "shizuku": true,
 *     "backend": "shizuku"
 *   }
 * }
 *
 * 打包项目时, AutoJs6 会在构建前检查主脚本、资源引用、capabilities 和
 * pluginDependencies, 并把能力映射到打包 APK 的 Android permissions.
 * 打包成功后 inrt 会内置 project/build-diagnostics.json, 便于设置页提示
 * 缺失能力和在日志页复制诊断 JSON.
 */

let manifest = capabilities.manifest();
log("manifest available: " + manifest.available);
log("declared capabilities: " + manifest.capabilities.join(", "));

// 用户确认后可记住当前项目授权选择; 未声明能力仍会按 riskPolicy 处理.
capabilities.remember(["shell"], "allow");

// shell / Shizuku / 文件删除或覆盖 / 安装卸载等高风险动作会写入审计.
let checks = capabilities.check(["shell", "shizuku", "storage"]);
checks.forEach((item) => log(item.id + " -> " + item.status));

capabilities.audit().forEach((entry) => {
    log(entry.api + " " + entry.action + " " + entry.target);
});
