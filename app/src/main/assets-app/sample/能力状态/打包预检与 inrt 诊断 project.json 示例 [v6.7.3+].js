"auto";

/*
 * 将下面内容放入项目根目录的 project.json:
 *
 * {
 *   "name": "打包预检与 inrt 诊断示例",
 *   "packageName": "org.autojs.autojs6.samples.preflight",
 *   "versionName": "1.0.0",
 *   "versionCode": 1,
 *   "main": "main.js",
 *   "capabilities": ["screen_capture", "overlay", "notifications"],
 *   "pluginDependencies": ["org.autojs.autojs.plugin.ocr"],
 *   "riskPolicy": {
 *     "undeclared": "prompt",
 *     "high": "prompt",
 *     "critical": "reject",
 *     "rememberAllowed": true
 *   },
 *   "assets": ["assets/template.png"]
 * }
 *
 * 打包预检会检查:
 * - main.js 是否存在.
 * - files.read/open/exists、images.read、open、require 中的相对资源是否存在.
 * - assets/template.png 是否存在.
 * - plugins.load(...) 是否已在 pluginDependencies 声明.
 * - capabilities 是否覆盖截图、悬浮窗、通知等危险能力.
 *
 * 打包成功后 APK 会内置 project/build-diagnostics.json.
 * inrt 首次启动会提示缺失能力, 运行失败可在日志页复制诊断 JSON.
 */

let checks = capabilities.check(["screen_capture", "overlay", "notifications"]);
checks.forEach((item) => log(item.id + ": " + item.status));

let manifest = capabilities.manifest();
log("declared capabilities: " + manifest.capabilities.join(", "));
