# AutoJs6 架构评估与优化建议

> 日期: 2026-06-16
> 视角: 架构师
> 范围: 主仓 `app`、`modules`、`libs`、`plugin-api`、`build-logic`、Gradle/CI、内置文档与现有 `docs/实现审计`、`docs/需求` 文档

## 1. 总体判断

AutoJs6 已经从早期 Auto.js 类脚本工具演进为一个 Android 自动化平台: 它同时包含 JavaScript/Rhino 运行时、无障碍自动化、截图/OCR/OpenCV、Root/Shizuku 特权能力、插件中心、APK/inrt 打包、AI 脚本助手、运行观测和内置文档/示例体系。

项目的主要矛盾不再是“能力不够”, 而是“能力面过宽后如何保持安全、可测试、可复现、可维护”。当前架构最需要治理的是高权限脚本执行边界、运行时生命周期、WebView/Rhino bridge、native/OCR 资源、插件和构建供应链、以及设备级回归保障。

本评估建议把后续 1-2 个版本的架构主线从“继续扩 API”切换为“平台治理与稳定性收敛”: 先把高风险入口和供应链门禁补齐, 再推进模块边界、测试金字塔和设备实验室, 最后再让 AI/插件/方案库在稳定基座上放大价值。

## 2. 关键事实

- 工程是 Android/Kotlin/Java 多模块项目, 根模块包含 `app`、`modules`、`libs`、`plugin-api`、`build-logic`。
- `version.properties` 显示当前版本为 `6.8.0`, `MIN_SDK_VERSION=24`, `TARGET_SDK_VERSION=36`, `TARGET_SDK_VERSION_INRT=29`, Java 支持范围为 17-25。
- `settings.gradle.kts` 通过兼容数据动态解析 AGP/Kotlin/KSP/Java 版本; 本地开发友好, 但 release 可复现性需要额外门禁。
- `app/build.gradle.kts` 依赖面很宽, 同时包含远程依赖、本地 JAR/AAR、vendored modules、native 库、Rhino snapshot、OCR、Room、Retrofit、RxJava、WorkManager、Shizuku、R8、插件 API 等。
- `AndroidManifest.xml` 声明了极宽权限面, 包括存储、安装/删除包、精确闹钟、悬浮窗、后台运行、MediaProjection、Shizuku、短信/联系人/电话/相机/录音/定位/蓝牙/NFC、`WRITE_SECURE_SETTINGS`、`QUERY_ALL_PACKAGES` 等。
- 运行时 `ScriptRuntime.kt` 注入大量脚本 API, 包括 `auto`、`automator`、`images`、`ocr`、`vision`、`shell`、`shizuku`、`plugins`、`tasks`、`ipc`、`http`、`ui`、`capabilities` 等。
- `docs/实现审计` 已记录 P0/P1 风险: 外部导出脚本执行入口、Tasker receiver、WebView/Rhino bridge、签名口令、RapidOCR/Paddle native 生命周期、远程 native 包下载、CI installer、wrapper checksum 等。
- 项目已建立能力状态中心、可靠自动化 DSL、UI 快照、屏幕感知、结构化特权 API、调度运行记录、插件治理、远程调试、AI Copilot、方案库等新平台能力; 后续重点应转为验收、边界收敛和回归体系。

## 3. 架构优势

### 3.1 能力完整度高

AutoJs6 覆盖 Android 自动化的关键能力闭环: 写脚本、运行脚本、调试日志、无障碍操作、屏幕感知、定时任务、打包 APK、插件扩展、桌面开发和内置文档。相比单点工具, 这已经具备平台形态。

### 3.2 本地优先, 可离线使用

大量文档、示例、模块和依赖被内置或 vendored, 对国内网络、上游不可用、Android 旧版本兼容有现实价值。对用户脚本生态来说, 本地可用性是重要优势。

### 3.3 新治理能力方向正确

`CapabilityRegistry`、`ProjectCapabilitySecurity`、`ScriptObservability`、`StructuredPrivilegedApi`、插件签名/授权、AI capability index 等都是正确的治理方向。它们能把“脚本随意调用高权限 API”的模式逐步改造成“能力声明、运行前预检、风险审计、失败诊断”的平台模式。

### 3.4 文档和执行任务体系强

`docs/实现审计` 和 `docs/需求` 的粒度较细, 已形成实现地图、回归矩阵、任务清单、验收记录和发布闭环。这对后续 AI agent 或多人协作非常有价值。

## 4. 主要问题

### 4.1 高权限执行边界仍是最高优先级风险

`AndroidManifest.xml` 中存在多个 `android:exported="true"` 入口, 其中 `RunIntentActivity`、Tasker `FireSettingReceiver`、文件打开/导入入口、动态 receiver、inrt 入口等与脚本执行链路相邻。结合 `shell`、`shizuku`、无障碍、文件系统、安装包等能力, 任意外部触发脚本执行是平台级风险。

建议:

- 所有外部输入脚本的入口默认进入预览/确认页, 不直接执行。
- 内部直接执行入口使用非导出组件、签名权限、一次性 token 或显式 internal intent。
- Tasker/快捷方式等集成场景建立专门的 trust policy: 来源识别、用户授权记忆、可撤销授权、审计记录。
- 建立 `ExternalExecutionGateway`, 让所有外部脚本启动都走同一个安全策略, 避免入口各自实现。

### 4.2 WebView 与 Rhino bridge 的信任边界过宽

现有审计已指出 `addJavascriptInterface`、`rhino.eval()`、`allowUniversalAccessFromFileURLs`、file URL 同时存在时会形成高风险执行面。对一个能执行自动化脚本的应用来说, WebView bridge 不能按普通内容页处理。

建议:

- 把 WebView 分为至少三类: 文档浏览、脚本 UI、可信内部工具页。每类使用独立安全配置。
- 默认禁止 `allowUniversalAccessFromFileURLs` 和 `allowFileAccessFromFileURLs`, 仅可信脚本 UI 显式启用。
- `rhino.eval()` 改为白名单命令协议, 例如只允许事件发送、受控 API 调用和结构化参数。
- 对 bridge 注入增加 origin/URL 白名单、能力分级和审计日志。

### 4.3 app 模块承担过多职责

`app` 同时承载主 UI、运行时、自动化核心、权限、调度、网络、AI、打包、插件、inrt、文档和大量平台能力。短期开发效率高, 但长期会带来编译慢、边界不清、测试难、风险传播大的问题。

建议逐步拆分逻辑模块, 不必一次性大重构:

| 目标模块 | 建议承载内容 | 拆分收益 |
| --- | --- | --- |
| `:runtime-core` | Rhino engine、ScriptRuntime、脚本 API 注册、生命周期 | 降低脚本执行核心对 UI 的依赖 |
| `:automation-core` | accessibility、selector、automator、vision、screen capture 抽象 | 形成可测试的自动化能力内核 |
| `:capability-governance` | CapabilityRegistry、项目能力清单、风险策略 | 让 AI、打包、运行前预检共享同一治理模型 |
| `:observability-core` | run record、debug bridge、diagnostic export | 让主应用和 inrt 共享观测能力 |
| `:plugin-runtime` | 插件 manifest、信任、安装、host bridge | 收敛插件供应链和运行时边界 |
| `:ai-assistant-core` | prompt、index、client、patch、privacy | 降低 AI 功能对 Activity/UI 的耦合 |

拆分原则是先抽纯 Kotlin/JVM 可测部分, 保持 Android UI 和资源暂留 `app`。每拆一个模块都要配对应单元测试, 避免“目录变了但耦合仍在”。

### 4.4 供应链和构建可复现性不足

当前构建链路有动态版本选择、宽仓库配置、JitPack/镜像仓库、本地 JAR/AAR/native 包、远程 native 下载、CI 下载 CMake installer 后 `sudo` 执行、release 签名 secret 缺失时继续构建、Gradle wrapper 未固定 `distributionSha256Sum` 等问题。

建议:

- release 构建使用固定 AGP/Kotlin/KSP/Java 版本, 动态选择仅用于本地开发。
- 启用 Gradle dependency verification, 对远程依赖和 wrapper 分发包固定 SHA-256。
- 为 native 包维护 `third_party_checksums.toml`, 下载后必须校验 SHA-256 再解压。
- 压缩包解压统一做 canonical path containment, 防 Zip Slip/7z Slip。
- CI 禁止无 checksum/GPG 的远程 installer; action pin 到 commit SHA; workflow 声明最小权限。
- release/tag 构建缺 signing secret 必须失败; 上传前执行 `apksigner verify` 和证书指纹校验。
- 产物发布同时生成 SHA-256/SHA-512 checksum, CRC32 仅作为短文件名标识。

### 4.5 测试从“存在”走向“足够”仍有距离

当前已出现一批单元测试和 smoke test, 覆盖 AI 索引、能力注册、调度策略、观测、屏幕感知、插件索引等新能力。但项目的核心风险在 Android 设备行为、无障碍、MediaProjection、Shizuku/Root、OCR/native、inrt 首启、外部 Intent/Broadcast, 这些难以仅靠 JVM 测试覆盖。

建议建立三层测试门禁:

- JVM 快速门禁: API 索引、能力识别、prompt/patch、权限策略、调度策略、manifest/gradle 静态检查。
- 模拟器门禁: Activity/Intent/Broadcast 负例、WebView bridge 配置、基础 UI/权限跳转、inrt 启动。
- 真机实验室: Android 7/8/10/12/14/15/16, 至少覆盖国产 ROM、Pixel/AOSP、低内存设备; 重点测无障碍、截图、后台调度、方向切换、OCR 长跑、Shizuku。

### 4.6 运行时生命周期和资源治理仍需平台化

脚本运行涉及 engine、线程、timer、event、shell process、plugin service、screen capture、bitmap/native buffer、OCR session、WebSocket、IPC、调度记录。单点补 `close()` 容易遗漏。

建议:

- 以 `ScriptRuntime.ownerId` 为中心建立统一 `ResourceScope`, 所有可关闭资源都注册到 scope。
- 每个脚本结束后输出资源回收摘要: timers、threads、processes、captures、plugins、sockets、native sessions。
- 对长生命周期资源建立泄漏检测和 debug dump。
- 对 shell/native/OCR 加入并发状态机: `NEW -> INITIALIZING -> READY -> RUNNING -> CLOSING -> CLOSED/FAILED`。

### 4.7 文档/补全/AI 索引需要成为强约束

AutoJs6 的 API 面太宽, 如果文档、补全、AI、运行时导出不同步, 用户会直接遭遇“AI 写了不存在 API”“补全提示过期 API”“文档跳错页面”。现有 A2 方向正确, 但应变成 CI 红线。

建议:

- 所有脚本 API 导出必须有结构化 metadata: 名称、模块、参数、权限能力、风险、inrt 支持、文档 URL、示例 URL、废弃状态。
- CI 校验运行时导出、`indices/all.json`、docs HTML、AI index、外部文档索引一致。
- 对高风险 API 建立 lint: 新增 `shell/shizuku/root/install/sms/contacts/accessibility/screen_capture` 能力时必须补能力声明和风险文案。

## 5. 建议路线图

### 阶段 0: 安全止血, 1-2 周

目标: 关闭最高风险入口, 防止平台能力被外部滥用。

- 收敛 `RunIntentActivity`、Tasker receiver、动态 receiver、WebView bridge。
- 移除本地真实签名口令, 轮换 release keystore/密码。
- release CI 缺签名直接失败, wrapper/native 包 checksum 门禁上线。
- 为外部脚本执行建立负例测试: 未授权外部 App 不能直接运行脚本。

### 阶段 1: 架构门禁, 2-4 周

目标: 让每次新增能力都能被治理系统约束。

- 建立 `ExternalExecutionGateway`、`ResourceScope`、统一 WebView 安全配置。
- 将 API metadata、能力声明、文档/补全/AI 索引纳入 CI。
- 加入 manifest exported 组件白名单检查和危险权限说明检查。
- 完成核心 JVM 测试门禁和 `git diff --check`、dependency verification。

### 阶段 2: 模块化和设备实验室, 1-2 个版本

目标: 降低长期维护成本, 建立真机可信度。

- 先拆 `capability-governance`、`ai-assistant-core`、`observability-core` 等低 UI 耦合模块。
- 建立真机 smoke suite: 无障碍、截图、OCR、调度、inrt、插件、远程调试。
- 对 native/OCR 建立长跑和并发压力测试。
- 每个 release 输出设备兼容矩阵和已知 ROM 限制。

### 阶段 3: 平台生态化, 2-3 个版本

目标: 让插件、AI、方案库成为可扩展生态, 而不是主 app 持续膨胀。

- 插件 SDK 提供模板、manifest、能力声明、签名校验、测试宿主。
- AI Copilot 从“生成代码”升级为“基于能力图谱和诊断包的自动化方案编辑器”。
- 方案库按场景沉淀: 登录/搜索/表单/列表/通知/OCR/定时/打包。
- 对外提供稳定 API 兼容策略和废弃周期。

## 6. 优先级清单

| 优先级 | 事项 | 成功标准 |
| --- | --- | --- |
| P0 | 外部脚本执行入口收敛 | 任意第三方 App 不能无确认触发脚本执行 |
| P0 | WebView bridge 安全配置 | 非可信页面不能访问 Rhino eval 或高权限 bridge |
| P0 | 签名和 release 供应链门禁 | 缺签名、缺 checksum、wrapper 校验失败时 release 中断 |
| P0 | native/OCR 生命周期修复 | init/detect/release 并发和失败路径不崩溃 |
| P1 | API/文档/补全/AI 一致性 CI | 新增脚本 API 未补 metadata/docs/index 时 CI 失败 |
| P1 | ResourceScope 生命周期治理 | 脚本退出后资源回收可审计、可测试 |
| P1 | 设备兼容 smoke suite | 每个 release 有真机矩阵和阻塞项记录 |
| P2 | 模块化拆分 | 低耦合核心能力能独立 JVM 测试 |
| P2 | 插件 SDK 治理 | 官方插件索引具备签名/hash/权限声明/兼容版本 |

## 7. 架构决策建议

1. 不再把“新增 API 数量”作为主要进度指标, 改用脚本成功率、失败可诊断率、测试覆盖面、release 可复现性和高风险入口关闭率。
2. 所有高权限能力都必须经过同一套能力模型: 声明、检测、请求、预检、审计、回收。
3. 主 app 后续只保留产品集成层, 运行时、自动化、AI、插件、观测逐步变成可测试核心模块。
4. 允许保留 vendored/本地依赖, 但必须补 provenance、checksum、license、升级策略和安全例外说明。
5. AI 功能不能绕开安全治理。AI 生成、应用补丁、运行前预检、错误修复都必须使用同一份能力图谱和风险策略。

## 8. 结论

AutoJs6 的技术资产很厚, 方向也已经从“自动化工具”进入“自动化平台”。下一阶段架构成功的关键不是再扩多少功能, 而是能否把宽能力面压进清晰边界: 外部入口有门禁, 高权限调用有审计, native 资源有生命周期, 构建产物可复现, 设备行为可验证, 文档/AI/补全与运行时同步。

如果优先完成 P0/P1 治理项, AutoJs6 后续可以更稳地承载 AI Copilot、插件生态、方案库和 inrt 分发; 如果继续只堆能力, 当前的高权限和供应链风险会随功能增长被放大。
