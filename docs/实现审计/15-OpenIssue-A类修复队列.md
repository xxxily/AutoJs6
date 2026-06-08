# Open Issue A 类修复队列拆分

> 日期: 2026-06-09
> 来源: `ISSUE_TRIAGE.md`
> 规则: 本文只拆独立开发任务, 不直接修 issue。每个任务必须小 PR、小验证、独立提交。

## 执行顺序

优先顺序沿用 `ISSUE_TRIAGE.md` 的 A 类队列。若某个 issue 已被当前版本源码或 changelog 覆盖, 只做复测和关闭建议, 不重复修。

## 独立任务表

| 任务 | Issue | 复现依据 | 目标文件 | 最小修复策略 | 回归脚本/命令 | 状态建议 |
| --- | --- | --- | --- | --- | --- | --- |
| A01 | #543 解析不了 getInstance 和 globalConsole | 当前源码曾引用不存在的 `AutoJs.getInstance()` 或颜色资源 | `app/src/main/java/org/autojs/autojs/ui/log/LogBottomSheet.kt`; `AutoJs.kt`; `colors_legacy.xml` | 修正实例入口和颜色资源引用; 不改日志架构 | `rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin`; 打开日志底部面板 | 若当前已编译通过, 复查是否已覆盖, 可标记复测关闭 |
| A02 | #550 打包后桌面长按总有“日志”快捷方式 | `launchConfig.logsVisible` 已存在, inrt 快捷方式需跟随 | `InrtShortcuts.kt`; `App.kt`; `LaunchConfig.kt`; `fragment_preferences_inrt.xml` | 按 `logsVisible` 发布/移除日志 shortcut, 清理已发布动态 shortcut | 打包 `logsVisible=false` 项目, 长按桌面图标检查 | 可直接修 |
| A03 | #545 后台运行无法获取脚本真实目录 | 外部 content URI 读入 tmp-scripts 后丢失原始来源 | `RunIntentActivity.java`; `TmpScriptFiles.java`; `ScriptSource.kt`; `Engines.kt` | 给脚本源保留 originalUri/overriddenFullPath; SAF 无真实路径时返回 URI | MacroDroid/Tasker/content URI 启动脚本, 输出 `engines.myEngine().source` | 可直接修, 但需外部启动复测 |
| A04 | #540 rootLevel 无效 | 文件日志过滤和控制台显示链路混用 | `runtime/api/augment/console/Console.kt`; `core/console/ConsoleImpl.kt`; log config | 分离文件日志 rootLevel 与控制台显示 level | JS: 设置 rootLevel 后输出 verbose/debug/info, 比对文件和控制台 | 可直接修 |
| A05 | #539 ensureDir 无效 | `PFiles.ensureDir(path)` 只创建父目录, 传目录名无分隔符失败 | `PFiles.kt`; `runtime/api/Files.kt`; docs/files | 明确目录语义: 修 `ensureDir` 创建目标目录, 如需父目录另加说明 | JS: `files.ensureDir("tmpdir"); files.exists("tmpdir")` | 可直接修, 注意兼容语义 |
| A06 | #536 setMaxRetries 找不到 | `MutableOkHttp` 有字段但旧 JS 期望 setter 方法 | `core/http/MutableOkHttp.kt`; `runtime/api/augment/http/Http.kt`; docs/http | 补兼容 setter 或 augment 包装, 保留全局副作用说明 | JS: `http.__okhttp__.setMaxRetries(3)` 或对应公开 API | 可直接修 |
| A07 | #518 dialogs.multiChoice 返回字符串 | Java array/Rhino bridge 转换为字符串 | `runtime/api/augment/dialogs/Dialogs.kt`; `runtime/api/Dialogs.java` | Java array -> NativeArray; 覆盖同步/回调/Promise | JS: `Array.isArray(dialogs.multiChoice(...))` | 与 A08 合并修 |
| A08 | #322 dialogs.multiChoice 返回不是数组 | 与 #518 同因, 老版本回归 | 同 A07 | 同 A07, 同一 PR 修两个 issue | 同 A07 | 与 A07 合并修 |
| A09 | #503 Disposable.blockedGet(timeout) 问题 | `awaitValue(timeout)` 收到 signal 后未立即检查 value | `runtime/api/augment/threads/VolatileDisposeNativeObject.kt` | `awaitNanos` 返回后若 value 非空立即退出; 补 timeout 分支 | JS/单测: delayed resolve + blockedGet(timeout) | 可直接修 |
| A10 | #501 新版本无法从 Tasker 启动 | 外部入口触发 App 初始化和 WorkManager 可能崩溃 | `App.kt`; `TimedTaskScheduler.kt`; `WorkTimedTaskScheduler.kt`; 外部 receiver/activity | 延迟/保护 WorkManager 初始化; 外部入口降级捕获 | Tasker/MacroDroid 启动脚本; `compileAppDebugKotlin` | 高风险, 需外部 app 复测 |
| A11 | #495 `app.isInstalled("com.tencent.mm")` 读不到 | JS 层先按应用名/别名解析, 包名被误判 | `runtime/api/augment/app/App.kt`; `runtime/api/AppUtils.kt`; docs/app | 形似包名时直接 PackageManager 查询; 或新增明确 API | JS: `app.isInstalled("com.tencent.mm")`; `app.isInstalled(context.getPackageName())` | 可直接修 |
| A12 | #523 远程开发插件连接经常断开 | 大 ByteString 日志 `Arrays.toString` 放大内存/OOM | `pluginclient/JsonSocket.java`; `DevPluginService.kt`; 日志路径 | 二进制日志只输出长度+hash; 禁止打印完整 byte array | 发送大日志/截图消息, 观察连接和内存 | 可直接修, 插件侧需复测 |
| A13 | #59 `events.onTouch` 坐标大于分辨率 | raw ABS 坐标未统一映射 | `core/inputevent/TouchObserver.java`; `TouchCoordinateMapper.kt`; `Events.java` | 输出增加映射或兼容开关; 文档说明 raw/mapped 行为 | JS: `events.onTouch(e => console.log(e.x,e.y))`; 多设备/旋转 | 可直接修, 兼容风险中 |
| A14 | #541 手势坐标轴交换/翻转 | `GlobalActionAutomator.scaleY` 调用 `scaleX` 是明确源码缺陷 | `core/automator/GlobalActionAutomator.kt`; `runtime/api/ScreenMetrics.kt`; `RootAutomator.java` | 先修 `scaleY`; 再做旋转/Root raw 轴回归 | JS: `gesture(...)`; 横竖屏坐标 smoke | 可直接修, 设备验证必须补 |

## 已覆盖/只需复测关闭候选

- A01 如果当前 `:app:compileAppDebugKotlin` 已通过且 `LogBottomSheet.kt` 不再引用不存在 API/资源, 建议复测日志面板后关闭 #543。
- A07/A08 若当前代码已修 NativeArray 转换, 只需跑 dialogs sample 后关闭; 否则合并一项修复。
- 任何被 changelog 明确覆盖的 issue, 先在当前 build 复测, 再写关闭依据, 不要重复改代码。

## 单任务提交模板

```markdown
### Fix Axx / #issue

- 问题:
- 复现:
- 文件范围:
- 修复:
- 验证:
- 文档/示例同步:
- 风险:
```

## 禁止事项

- 不要在一个 PR 同时修多个不相关模块。
- 不要把 B/C 类平台限制塞进 A 类修复。
- 不要在缺设备/日志时改无障碍、截图、Tasker、插件长跑问题的核心行为。
- 不要回退 AI 助手、文档矩阵或阶段 1/2 已提交记录。
