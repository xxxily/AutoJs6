# Agent 核心架构索引

> 日期: 2026-06-09
> 用途: 给后续 agent 的快速入口。先按模块定位入口文件、典型任务、验证方式和风险点, 再进入具体实现。

## 使用方式

1. 先用本表确定模块边界。
2. 再用 `rg` 定位调用链, 不要跨模块重构。
3. 只改当前任务要求的入口文件和直接依赖。
4. 涉及用户可见行为、脚本 API、配置项、示例或外部文档时, 同步更新 `docs/`、`README.md`、`app/src/main/assets-app/sample/` 和外部文档仓库。

## 模块索引

| 模块 | 入口文件 | 典型任务 | 验证方式 | 不要随便改 |
| --- | --- | --- | --- | --- |
| App 启动与全局初始化 | `app/src/main/java/org/autojs/autojs/App.kt`; `app/src/main/java/org/autojs/autojs/AutoJs.kt`; `app/src/main/AndroidManifest.xml`; `app/src/main/java/org/autojs/autojs/AbstractAutoJs.kt` | 启动初始化、服务注册、inrt 配置同步、全局运行时创建 | `rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin`; 冷启动 smoke; 检查 manifest exported/permission | 不要在 `Application.onCreate` 做阻塞 I/O 或无保护的 WorkManager/插件初始化; 不要放宽 exported 入口 |
| 脚本执行链路 | `app/src/main/java/org/autojs/autojs/execution/ScriptExecuteActivity.kt`; `app/src/main/java/org/autojs/autojs/execution/LoopedBasedJavaScriptExecution.java`; `app/src/main/java/org/autojs/autojs/engine/JavaScriptEngine.kt`; `app/src/main/java/org/autojs/autojs/script/ScriptSource.kt` | 启动脚本、外部 Intent、临时脚本、错误上报、停止脚本 | 新建脚本运行/停止; 外部打开脚本; `:app:compileAppDebugKotlin` | 不要绕过用户确认自动运行外部脚本; 不要丢失 source path/URI; 不要吞掉 Rhino 行列号 |
| Runtime API 注入 | `app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt`; `app/src/main/java/org/autojs/autojs/runtime/api/**`; `app/src/main/java/org/autojs/autojs/runtime/api/augment/**`; `app/src/main/assets-app/docs/*.html` | 新增/修复脚本 API、兼容旧 Auto.js 行为、同步 docs/补全/AI 索引 | Runtime API 回归样例集; `rg` 检查 docs 与 augment; 编译 | 不要只改 Kotlin/Java API 不改 augment/docs; 不要改变旧脚本语义却不写迁移说明 |
| 无障碍与手势 | `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt`; `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityBridge.java`; `app/src/main/java/org/autojs/autojs/core/automator/GlobalActionAutomator.kt`; `app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/Automator.kt` | 节点查询、点击/滑动/输入、手势失败诊断、ROM fallback | 无障碍诊断模板; 设备矩阵; `click/swipe/gesture` smoke | 不要把 ROM/目标 App 限制当成通用 bug 修; 不要改变坐标映射而不测旋转/分辨率 |
| 截图、图像、OCR | `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCaptureRequester.java`; `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java`; `app/src/main/java/org/autojs/autojs/runtime/api/augment/images/Images.kt`; `app/src/main/java/org/autojs/autojs/runtime/api/augment/ocr/Ocr.kt` | 截图授权、连续截图、图像识别、OCR 后端选择和生命周期 | OCR/插件矩阵; 截图 sample; heap/长跑记录 | 不要在没有释放策略时增加 Bitmap/native 持有; 不要忽略 Android 14+ FGS/MediaProjection 权限 |
| 编辑器、文件管理器、日志 | `app/src/main/java/org/autojs/autojs/ui/edit/EditorView.kt`; `app/src/main/java/org/autojs/autojs/ui/edit/EditorMenu.java`; `app/src/main/java/org/autojs/autojs/ui/log/LogBottomSheet.kt`; `app/src/main/java/org/autojs/autojs/ui/common/ScriptOperations.java` | 打开/保存/运行脚本、错误定位、日志底部面板、AI 入口、新建项目 | 编辑器回归清单; `:app:compileAppDebugKotlin`; 真机保存/撤销/运行 | 不要绕过 `EditorView.save()`; 不要让 AI 或日志路径自动覆盖/运行用户代码 |
| 打包与 inrt | `app/src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt`; `app/src/main/java/org/autojs/autojs/project/ProjectConfig.java`; `app/src/main/java/org/autojs/autojs/project/LaunchConfig.kt`; `app/src/main/java/org/autojs/autojs/inrt/**`; `app/src/main/assets-app/template.apk` | 生成 template.apk、打包单文件/项目、权限裁剪、launchConfig、签名 | `assembleInrtRelease`; `assembleAppDebug`; 打包矩阵; APK manifest 检查 | 不要只测 app flavor; inrt 权限、快捷方式、启动页和日志入口必须同步 |
| 定时任务 | `app/src/main/java/org/autojs/autojs/timing/TimedTaskScheduler.kt`; `app/src/main/java/org/autojs/autojs/timing/AlarmTimedTaskScheduler.kt`; `app/src/main/java/org/autojs/autojs/timing/WorkTimedTaskScheduler.kt`; `app/src/main/java/org/autojs/autojs/timing/JobTimedTaskScheduler.kt`; `app/src/main/java/org/autojs/autojs/timing/BootCompletedReceiver.kt` | Alarm/Work/Job 后端差异、开机恢复、精确闹钟、后台限制 | 定时任务矩阵; 短时/两天以上/开机恢复设备测试 | 不要承诺系统策略无法保证的准时性; 不要把一个后端的行为写死到公共 API |
| 插件与 OCR 插件 | `app/src/main/java/org/autojs/autojs/core/plugin/center/**`; `app/src/main/java/org/autojs/autojs/core/plugin/ocr/PaddleOcrPluginHost.kt`; `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt`; `app/src/main/java/org/autojs/autojs/runtime/api/augment/plugins/Plugins.kt` | 插件发现、安装、签名/信任、授权、Paddle OCR bind/rebind | OCR/插件矩阵; 插件安装/启停; 进程死亡复测 | 不要默认信任外部插件; 插件进程死亡必须提示或重绑, 不能静默失败 |
| 构建、发布、供应链 | `settings.gradle.kts`; `build.gradle.kts`; `app/build.gradle.kts`; `docs/发布/AutoJs6发布规范.md`; `gradle/libs.versions.toml` | JDK/SDK/NDK 约束、release 分步构建、依赖升级、模板生成 | `./gradlew --version`; `assembleInrtRelease`; `assembleAppRelease`; digest 检查 | 不要合并 inrt release 与 app release 到同一 Gradle invocation; 不要提交签名口令 |

## 快速检索命令

```bash
rtk proxy rg -n "ScriptRuntime|class .*Runtime|augment" app/src/main/java/org/autojs/autojs/runtime
rtk proxy rg -n "Accessibility|GlobalActionAutomator|dispatchGesture|ScreenMetrics" app/src/main/java/org/autojs/autojs/core app/src/main/java/org/autojs/autojs/runtime
rtk proxy rg -n "template.apk|LaunchConfig|InrtShortcuts|assembleInrtRelease" app README.md docs
rtk proxy rg -n "TimedTask|AlarmTimedTask|WorkTimedTask|JobTimedTask|BootCompleted" app/src/main/java/org/autojs/autojs/timing
rtk proxy rg -n "Paddle|Rapid|MLKit|Ocr|PluginHost" app/src/main/java libs plugin-api
```

## Notes 同步口径

T30 原始验收要求同步到本地 notes。用户本轮明确要求不要读取 notes 下的重复任务文档, 因此本轮不写 notes, 以本仓库 `docs/实现审计/08-Agent核心架构索引.md`、`docs/实现审计/README.md` 和任务清单记录作为权威同步面。
