# Core 底层能力审计

## 范围

本分册覆盖无障碍、截图、图像包装、OpenCV 初始化、Root/input 事件、插件运行时和插件安装入口。主要路径为:

- `app/src/main/java/org/autojs/autojs/core/accessibility/**`
- `app/src/main/java/org/autojs/autojs/core/automator/**`
- `app/src/main/java/org/autojs/autojs/core/image/**`
- `app/src/main/java/org/autojs/autojs/core/inputevent/**`
- `app/src/main/java/org/autojs/autojs/core/opencv/**`
- `app/src/main/java/org/autojs/autojs/core/plugin/**`
- `app/src/main/java/org/autojs/autojs/core/looper/**`
- `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt`

## 问题表

| ID | 文件/行号 | 问题 | 原因/风险 | 修复建议 | 严重程度 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| CORE-01 | `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:246`, `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:268`, `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:275` | Accessibility 截屏失败路径未清空 promise | `onFailure()` 非重试分支 resolve 后未置空 `mPromiseAdapter`, 后续调用可能复用已完成 promise 或挂起 | 成功、失败、空 bitmap、异常路径统一 `finally` 清理; 每次请求使用独立 promise 或明确单飞状态机 | `HIGH` | `P1` |
| CORE-02 | `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:250`, `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:251`, `app/src/main/java/org/autojs/autojs/core/accessibility/SimpleActionAutomator.kt:262` | Accessibility `ScreenshotResult.hardwareBuffer` 释放不完整 | 代码回收的是由 `wrapHardwareBuffer()` 得到的硬件 Bitmap, 未显式关闭原始 `HardwareBuffer`; 连续截屏可能泄漏 native buffer | copy 完成后 `try/finally` 关闭 `screenshot.hardwareBuffer`; 对 `wrapHardwareBuffer()` 返回 null 的路径也关闭 buffer 并 resolve/reject | `HIGH` | `P1` |
| CORE-03 | `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCaptureRequester.java:113`, `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCaptureRequester.java:115`, `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCaptureRequester.java:131` | MediaProjection 前台服务启动/绑定缺异常兜底 | Android 14+ 前台服务限制、后台启动限制或 bind 失败会直接抛异常, 当前授权流程可能崩溃或卡住 callback | 包裹 `startService()` 与 `bindService()`; 异常回传 `onRequestError()`; bind 返回 false 时 recycle; 成功/失败均解绑 | `HIGH` | `P1` |
| CORE-04 | `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt:56`, `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt:64`, `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt:69` | 插件服务绑定后脚本退出未解绑 | 使用 application context 绑定插件服务, `Plugins.clear()` 只删缓存目录, 没有 runtime exit 解绑连接 | 增加 `Plugins.recycle()/close()` 遍历已加载插件并 `unbindService`; 在 `ScriptRuntime.onExit()` 调用; 插件对象记录绑定状态 | `HIGH` | `P1` |
| CORE-05 | `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:101`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:103`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:112` | Accessibility 回调异常未隔离 | 用户脚本 callback 或 delegate 抛异常会中断整批事件处理, 影响其他脚本或服务稳定性 | 每个 callback/delegate 单独 `runCatching`; 异常转发到对应 runtime console; 不影响同批其他监听 | `MEDIUM` | `P2` |
| CORE-06 | `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:186`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:187`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:188`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt:233` | Accessibility 静态 delegates/eventTypes 非线程安全 | service 线程读取 `TreeMap/HashSet`, 其他线程可同时 `addDelegate()`, 存在并发修改和可见性问题 | 使用锁或 `CopyOnWrite` 快照; `containsAllEventTypes` 用 volatile/Atomic; 注册后重建不可变配置 | `MEDIUM` | `P2` |
| CORE-07 | `app/src/main/java/org/autojs/autojs/core/automator/search/BFS.kt:18`, `app/src/main/java/org/autojs/autojs/core/automator/search/BFS.kt:20` | BFS limit 判断 off-by-one | 命中后判断 `result.size > limit`, `limit=1` 会返回 2 个结果; limit 语义与 DFS/调用方预期可能不一致 | 改为 `>= limit`; 明确 `limit<=0` 行为; 添加 selector 搜索 limit 测试 | `LOW` | `P3` |
| CORE-08 | `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityNodeInfoAllocator.kt:49`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityNodeInfoAllocator.kt:55`, `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityNodeInfoAllocator.kt:61` | `recycleAll()` 计数和 map 清理错误 | 成功 recycle 后递增 `notRecycledCount`, 但没有从 map 清空; 日志含义反转且长期持有节点引用 | recycle 后清空 map; 分开统计 recycled/failed; Android 13+ no-op 路径也释放引用 | `MEDIUM` | `P2` |
| CORE-09 | `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java:467`, `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java:472`, `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java:475`, `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java:490` | `ScreenCapturer.release()` 非幂等 | `VirtualDisplay/ImageReader/Image` 关闭后未置空, EventBus unregister 和 display listener 重复调用可能抛异常 | 每个资源关闭后置空; unregister 用状态位保护; 多次 release 应安全返回 | `MEDIUM` | `P2` |
| CORE-10 | `app/src/main/java/org/autojs/autojs/core/inputevent/RootAutomator.java:413` | RootAutomator 对可执行文件执行 `chmod 777` | 全局可写可执行权限扩大本地篡改面, 在 root 场景下风险更高 | 改为 `chmod 700` 或 `750`; 文件放私有目录; 校验 owner/权限/摘要后再执行 | `HIGH` | `P1` |
| CORE-11 | `app/src/main/java/org/autojs/autojs/core/inputevent/RootAutomator.java:230`, `app/src/main/java/org/autojs/autojs/core/inputevent/RootAutomator.java:235`, `app/src/main/java/org/autojs/autojs/core/inputevent/RootAutomator.java:239` | root swipe 使用忙等循环 | 循环内无 sleep/backoff, 长 duration 会占满 CPU 并大量写 input event | 按帧率计算 step, 每步 sleep 8-16ms; duration 为 0 时单步处理; 限制最大事件数 | `MEDIUM` | `P2` |
| CORE-12 | `app/src/main/java/org/autojs/autojs/core/inputevent/InputDevices.kt:41`, `app/src/main/java/org/autojs/autojs/core/inputevent/InputDevices.kt:45`, `app/src/main/java/org/autojs/autojs/core/inputevent/InputDevices.kt:50` | 在设备内执行 `adb shell cat /proc/bus/input/devices` | Android 设备内通常没有可用 adb server, root/shizuku 下也会执行错误命令, 导致触摸设备解析失败 | 设备内改为 `cat /proc/bus/input/devices`; adb 仅用于外部调试模式; 对 root/shizuku 分支分别测试 | `MEDIUM` | `P2` |
| CORE-13 | `app/src/main/java/org/autojs/autojs/core/inputevent/InputEventObserver.java:162` | input event 观察线程吞掉所有 `Throwable` | Shizuku/root 观察异常只 sleep 重试, 无日志和失败状态, 现场诊断困难 | 对非预期异常限频记录日志; 连续失败后切换状态并通知 runtime; 保留 InterruptedException 语义 | `LOW` | `P3` |
| CORE-14 | `app/src/main/java/org/autojs/autojs/core/looper/Loopers.kt:44`, `app/src/main/java/org/autojs/autojs/core/looper/Loopers.kt:46`, `app/src/main/java/org/autojs/autojs/core/looper/Loopers.kt:49` | servant looper 懒初始化存在并发竞态 | 多线程同时访问时可重复调用 `initServantThread()`, CountDownLatch 只创建一次, 第二个线程状态不可控 | 用 synchronized/AtomicReference 包裹初始化; 初始化失败要重置状态; 中断时恢复 interrupt | `MEDIUM` | `P2` |
| CORE-15 | `app/src/main/java/org/autojs/autojs/core/image/ImageWrapper.kt:163`, `app/src/main/java/org/autojs/autojs/core/image/ImageWrapper.kt:164` | 全局 image list 非线程安全 | 多脚本/多线程同时创建图片 wrapper 时, 普通 list 可能并发修改异常或遗漏回收 | 改为线程安全集合或单线程调度; 回收时清理失效 weak ref; 增加并发创建/回收测试 | `MEDIUM` | `P2` |
| CORE-16 | `app/src/main/java/org/autojs/autojs/core/opencv/OpenCVHelper.java:47`, `app/src/main/java/org/autojs/autojs/core/opencv/OpenCVHelper.java:49`, `app/src/main/java/org/autojs/autojs/core/opencv/OpenCVHelper.java:61` | OpenCV 初始化成功前即标记已初始化 | `sInitialized=true` 在 `OpenCVLoader.initDebug()` 之前设置, 首次失败后后续直接回调成功 | 只有 initDebug 成功后置 true; 失败回调错误并允许重试; callback 接收 success/error | `HIGH` | `P1` |
| CORE-17 | `app/src/main/java/org/autojs/autojs/core/plugin/center/PluginInstallActions.kt:31`, `app/src/main/java/org/autojs/autojs/core/plugin/center/PluginInstallActions.kt:36` | 手输 URL 安装插件缺 hash/HTTPS 约束 | 用户可安装任意 URL APK, 当前信任管理关注运行授权, 下载源完整性和协议安全不足 | 强制 HTTPS 或本地文件; 显示包名/签名摘要/来源; 支持输入 SHA-256 并校验后安装 | `HIGH` | `P1` |

## 已确认未发现明确实现级问题的关键文件

- `app/src/main/java/org/autojs/autojs/core/inputevent/KeyInterceptor.java`: 中断和 observer 结构清晰, 未发现直接高风险问题。
- `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturerForegroundService.java`: 前台服务本体未发现明显实现问题, 风险在 requester 启动/绑定异常路径。
- `app/src/main/java/org/autojs/autojs/core/plugin/center/PluginTrustManager.kt`: 运行授权以签名指纹为核心, 本轮未发现授权绕过; 仍需补下载源完整性。
- `app/src/main/java/org/autojs/autojs/core/plugin/center/PluginSignatureUtils.kt`: 签名指纹工具未发现明显实现级问题。
- `app/src/main/java/org/autojs/autojs/core/plugin/ocr/PaddleOcrPluginHost.kt`: host 连接池和授权门禁未发现明显问题; native engine 问题归入 05 分册。
- `app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.kt` 相关 listener 包装: 本轮未发现事件分发层面的独立问题。

## 后续验证建议

- 为 Accessibility 截屏跑 Android 11-15 设备矩阵, 覆盖成功、过快调用、服务断开和 null bitmap。
- 对 `ScreenCapturer.release()`、`Plugins.recycle()`、`Loopers.servantLooper` 增加重复调用和并发调用测试。
- 对 RootAutomator 做权限专项: 可执行文件路径、owner、mode、摘要、执行前后权限恢复。
- 对插件安装链路补端到端测试: URL 下载、签名展示、授权、运行、脚本退出解绑。
