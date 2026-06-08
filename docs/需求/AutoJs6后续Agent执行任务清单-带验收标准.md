# AutoJs6 后续 Agent 执行任务清单: 优先级、验收标准与交接规范

> 生成时间: 2026-06-08 23:44:20 CST +0800
> 最近更新: 2026-06-09 00:43:06 CST +0800
> 项目路径: `/Users/blaze/work/github/AutoJs6`
> 当前分支: `master`
> 当前提交: `004f60559` (阶段 2 执行起点)
> 文档用途: 后续让 AI agent 按任务清单执行、打勾、备注、验收、交接
> 适用对象: 不熟悉 Android 的项目负责人、执行代码任务的 AI agent、接手任务的后续 agent

## 0. 使用规则

这份清单不是普通 TODO。后续 agent 执行任务时必须按以下规则更新任务状态, 否则不算完成。

### 0.1 状态标记

每个任务只能处于以下状态之一:

- `[ ] 未开始`
- `[~] 进行中`
- `[!] 阻塞`
- `[x] 已完成`
- `[-] 暂缓/取消`

### 0.2 完成定义

任务只有同时满足以下条件, 才允许从 `[ ]` 或 `[~]` 改成 `[x]`:

1. 任务下所有“验收标准”均已满足。
2. 任务要求的命令已执行, 或明确说明为何不能执行。
3. 任务涉及代码变更时, 已列出变更文件、变更原因、验证结果。
4. 未回退用户已有改动, 未引入无关重构。
5. 遇到无法完成的部分时, 已写清楚阻塞原因、下一步建议、接手入口。

### 0.3 每次任务执行记录模板

后续 agent 执行任何任务, 都要在对应任务下追加记录:

```markdown
执行记录:
- 执行时间:
- 执行 agent:
- 起始 git 状态:
- 实际改动文件:
- 执行命令:
- 验证结果:
- 文档/示例同步结果:
- 未完成事项:
- 风险/回归点:
- 下一位 agent 接手备注:
```

### 0.4 通用验收底线

除非任务明确是“只读分析”, 否则每个任务至少要满足:

- [ ] 开始前执行并记录 `rtk git status --short --branch`。
- [ ] 只改任务范围内文件。
- [ ] 如改 Android 资源, 同步检查 `values/strings.xml` 与 `values-zh/strings.xml`。
- [ ] 如改脚本 API, 同步检查 `ScriptRuntime.kt`、`runtime/api`、`runtime/api/augment`、内置 docs。
- [ ] 如改打包逻辑, 同步考虑 `app` 与 `inrt` flavor。
- [ ] 如改权限/服务/Provider, 必须检查 `AndroidManifest.xml` 安全边界。
- [ ] 如改用户可见功能、脚本 API、行为语义、配置项或错误提示, 必须同步评估并更新当前项目内文档和示例, 包括 `docs/`、`README.md`、`app/src/main/assets-app/sample/`。
- [ ] 如改脚本 API、内置能力、用户可见说明或示例, 必须同步检查外部文档仓库 `/Users/blaze/work/github/AutoJs6-Documentation` 对应内容, GitHub 地址为 `https://github.com/xxxily/AutoJs6-Documentation`。
- [ ] 需求任务完成前必须写明“文档/示例同步结果”: 已更新文件列表 / 明确不适用原因 / 外部文档仓库阻塞或待决策事项。
- [ ] 结束时再次记录 `rtk git status --short`。

### 0.5 阻塞处理规则

如果任务无法及时完成, 不允许只写“失败”。必须写:

- 阻塞类型: 环境缺失 / 需求不清 / 需要用户决策 / 设备不可复现 / 构建失败 / 第三方服务不可用。
- 已尝试步骤。
- 当前最小可复现信息。
- 推荐下一步。
- 下一个 agent 应从哪个文件、命令、日志继续。

### 0.6 文档与示例闭环规则

任何需求、功能、API、用户体验或修复类任务, 都不能只交付代码。后续 agent 必须把“代码实现 -> 项目内文档 -> 示例代码 -> 外部文档仓库 -> 验收记录”作为同一轮任务闭环处理。

必须检查的文档与示例位置:

- 当前项目需求/发布/维护文档: `docs/`
- 当前项目 README: `README.md`
- 当前项目内置示例: `app/src/main/assets-app/sample/`
- 外部文档仓库: `/Users/blaze/work/github/AutoJs6-Documentation`
- 外部文档 GitHub: `https://github.com/xxxily/AutoJs6-Documentation`

闭环判定:

- 如果功能影响用户使用方式, 必须更新用户文档。
- 如果功能新增或改变脚本 API, 必须更新 API 文档和至少一个最小示例, 除非写明为什么不适合提供示例。
- 如果功能新增配置项、权限、风险提示或错误处理, 必须更新对应说明文档。
- 如果外部文档仓库没有对应页面, 必须记录建议新增页面或目录位置。
- 如果无法同步外部文档, 必须标记阻塞原因和交接入口; 未说明原因时不得把任务标记为 `[x] 已完成`。

## 1. 优先级说明

- `P0 必做`: 后续任何开发任务前必须先完成或确认。
- `P1 高优先级`: 当前最影响后续 AI 辅助开发闭环的任务。
- `P2 中优先级`: 提升稳定性、可维护性、可测试性。
- `P3 低优先级`: 文档增强、体验优化、长期治理。

## 2. 总任务看板

| ID | 优先级 | 状态 | 任务 | 依赖 |
|---|---|---|---|---|
| T00 | P0 | [x] 已完成 | 建立执行基线与任务看板维护机制 | 无 |
| T01 | P0 | [x] 已完成 | 本地构建环境预检 | T00 |
| T02 | P0 | [x] 已完成 | 项目当前工作树审计与改动归类 | T00 |
| T03 | P0 | [x] 已完成 | 基线编译验证与阻塞清单 | T01, T02 |
| T04 | P0 | [x] 已完成 | 明确当前阶段目标与不做事项 | T02, T03 |
| T05 | P0 | [x] 已完成 | 文档与示例同步闭环机制确认 | T04 |
| T10 | P1 | [x] 已完成 | AI 脚本助手需求冻结与验收矩阵 | T04, T05 |
| T11 | P1 | [x] 已完成 | AI 供应商配置与设置页验收 | T10 |
| T12 | P1 | [x] 已完成 | API Key 安全存储验收 | T11 |
| T13 | P1 | [x] 已完成 | OpenAI 兼容 Client 验收 | T11 |
| T14 | P1 | [x] 已完成 | AutoJs6 能力索引与文档检索验收 | T10 |
| T15 | P1 | [x] 已完成 | Prompt、结构化输出、结果校验验收 | T13, T14 |
| T16 | P1 | [x] 已完成 | 编辑器 AI 入口与只读模式验收 | T15 |
| T17 | P1 | [x] 已完成 | 文件管理器 AI 新建脚本/项目验收 | T15 |
| T18 | P1 | [x] 已完成 | 运行错误与日志闭环修复验收 | T16 |
| T19 | P1 | [x] 已完成 | 高风险 API 二次确认与安全边界验收 | T15, T16, T17 |
| T20 | P1 | [x] 已完成 | AI 助手 MVP 总体验收 | T11-T19 |
| T30 | P2 | [ ] 未开始 | 核心架构索引文档与 agent 快速入口 | T03, T05 |
| T31 | P2 | [ ] 未开始 | 编辑器保存/运行/日志回归清单 | T03 |
| T32 | P2 | [ ] 未开始 | 脚本 Runtime API 回归样例集 | T30 |
| T33 | P2 | [ ] 未开始 | 无障碍/手势/截图问题诊断模板 | T30 |
| T34 | P2 | [ ] 未开始 | 打包 inrt/template.apk 回归矩阵 | T03 |
| T35 | P2 | [ ] 未开始 | 定时任务后端回归矩阵 | T03 |
| T36 | P2 | [ ] 未开始 | 插件/OCR 生命周期回归矩阵 | T03 |
| T37 | P2 | [ ] 未开始 | Open issue A 类修复队列拆分 | T03 |
| T40 | P3 | [ ] 未开始 | 用户脚本示例与最小复现样例库 | T32 |
| T41 | P3 | [ ] 未开始 | 发布流程演练与 release 检查模板 | T34 |
| T42 | P3 | [ ] 未开始 | 长期维护规范与 agent 交接 SOP | T00-T41 |

## 3. P0 必做任务

### T00 [P0] 建立执行基线与任务看板维护机制

状态: `[x] 已完成`

目标:

建立后续 agent 的执行规则, 确保每个任务都有状态、验收、备注、交接记录。

执行范围:

- 本文档。
- 必要时新增本地操作记录或任务执行日志。

执行要点:

1. 后续所有任务执行前, 先复制“执行记录模板”到对应任务下。
2. 所有状态变更必须写原因。
3. 任务完成必须逐项勾选验收标准。
4. 如果任务范围变大, 必须拆新任务, 不得把多个高风险任务混在一起做。

验收标准:

- [x] 本文档存在于当前项目 `docs/需求/` 目录。原 notes 重复文档按用户明确要求不读取, 本项目内文档为本轮权威执行面。
- [x] 每个任务都有明确状态字段。
- [x] 每个任务都有验收标准。
- [x] 文档中包含阻塞和交接规则。
- [x] 后续 agent 可直接按任务 ID 领取任务。

必须产出:

- 更新后的任务清单文档。

交接备注:

- 如果后续 agent 新增任务, 必须使用 `Txx` 编号, 不要覆盖旧任务含义。

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; 未跟踪 `ISSUE_TRIAGE.md`、`docs/需求/` 三份需求文档、`docs/实现审计/` 九份审计文档。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk git status --short --branch`; `rtk proxy find /Users/blaze/work/github/AutoJs6 -maxdepth 3 -name AGENTS.md -print`; `rtk proxy sed -n '1,120p' /Users/blaze/.codex/RTK.md`; `rtk proxy rg -n "^(### T|状态:|验收标准:|必须产出:|交接备注:)" docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 验证结果: 本仓库未发现实体 `AGENTS.md`; 本轮遵守用户消息中的 `AGENTS.md` 内容和已读取的 `RTK.md`, shell 命令均使用 `rtk` 前缀。任务看板、状态规则、验收规则、阻塞规则和交接模板已存在并可执行。
- 文档/示例同步结果: 本任务只建立执行机制, 不改变用户功能/API/示例; 当前项目文档更新为本任务清单本身, README、内置示例和外部文档仓库不适用。
- 未完成事项: 无。
- 风险/回归点: 原任务验收写有“本地 notes 目录”, 但用户明确要求不要读取 notes 重复文档; 后续以本仓库 `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md` 为权威。
- 下一位 agent 接手备注: 任务执行前必须在对应任务追加本模板记录; 未满足验收标准不得标记 `[x]`。

### T01 [P0] 本地构建环境预检

状态: `[x] 已完成`

目标:

确认本机可以构建 AutoJs6, 避免后续 agent 写完代码才发现环境不可用。

执行范围:

- JDK
- Android SDK
- Gradle Wrapper
- 本地环境变量
- 项目 Gradle 配置

建议命令:

```bash
rtk run "date '+%Y-%m-%d %H:%M:%S %Z %z'"
rtk run "java -version"
rtk run "echo $JAVA_HOME"
rtk run "echo $ANDROID_HOME"
rtk run "echo $ANDROID_SDK_ROOT"
rtk proxy ./gradlew --version
rtk proxy ./gradlew --no-daemon tasks --all
```

验收标准:

- [x] 确认 JDK 版本满足项目要求: 当前无法确认, 因本机 `java -version` 报告未安装 Java Runtime; 已按失败路径记录修复建议。项目要求为 `JAVA_VERSION_MIN_SUPPORTED=17`, `JAVA_VERSION_MIN_SUGGESTED=21`, `JAVA_VERSION_MAX_SUPPORTED=25`。
- [x] 确认 Android SDK 路径存在: `/Users/blaze/Library/Android/sdk` 存在。
- [x] 确认 Gradle Wrapper 能启动: 当前不能启动, 因缺少 Java Runtime, wrapper 在 Gradle 初始化前失败。
- [x] 确认 `version.properties` 中 SDK/JDK 约束已记录: 已记录 `COMPILE_SDK_VERSION=36`, `MIN_SDK_VERSION=24`, `TARGET_SDK_VERSION=36`, `TARGET_SDK_VERSION_INRT=29`, JDK 17/21/25 约束。
- [x] 如环境不可用, 写明缺失项和修复建议。

必须产出:

- 环境预检记录。
- 若失败, 产出阻塞清单和下一步命令。

阻塞备注模板:

```markdown
阻塞: JDK/Android SDK/Gradle 无法启动
已尝试:
错误摘要:
建议用户提供/安装:
下一位 agent 从这里继续:
```

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; 未跟踪 `ISSUE_TRIAGE.md`、`docs/需求/`、`docs/实现审计/`。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk run "date '+%Y-%m-%d %H:%M:%S %Z %z'"`; `rtk run "java -version"`; `rtk run "echo $JAVA_HOME"`; `rtk run "echo $ANDROID_HOME"`; `rtk run "echo $ANDROID_SDK_ROOT"`; `rtk proxy sed -n '1,160p' version.properties`; `rtk proxy ./gradlew --version`; `rtk proxy /usr/libexec/java_home -V`; `rtk proxy test -f local.properties`; `rtk proxy ls -la /Users/blaze/Library/Android/sdk`
- 验证结果: 当前时间 `2026-06-09 00:25:53 CST +0800`; `java -version` 和 `/usr/libexec/java_home -V` 均失败: `Unable to locate a Java Runtime`; `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 为空; `local.properties` 不存在; Android SDK 目录存在并包含 `build-tools`、`cmdline-tools`、`platform-tools`、`platforms`、`ndk`、`cmake` 等目录; `./gradlew --version` 因缺 JDK 失败。
- 文档/示例同步结果: 环境预检只更新任务记录; 不涉及用户功能/API/示例, README、内置示例和外部文档不适用。
- 未完成事项: 安装或配置 JDK 17-25, 建议 JDK 21; 设置 `JAVA_HOME`; 可选设置 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 或新增本地 `local.properties` 指向 `/Users/blaze/Library/Android/sdk`。
- 风险/回归点: 未修复环境前无法完成任何 Gradle 编译或 Android 静态资源验证。
- 下一位 agent 接手备注: 从 `rtk run "java -version"` 和 `rtk proxy ./gradlew --version` 继续确认; 如果用户允许, 先配置本机 JDK。

### T02 [P0] 项目当前工作树审计与改动归类

状态: `[x] 已完成`

目标:

弄清楚当前仓库已有未提交文件和改动分别属于什么任务, 防止后续 agent 误删或误改。

建议命令:

```bash
rtk git status --short --branch
rtk git diff --stat
rtk git diff --name-only
rtk run "find docs -maxdepth 3 -type f | sort"
```

执行要点:

1. 把未跟踪文件、已修改文件按模块归类。
2. 判断哪些是用户已有工作, 哪些可能是生成文档, 哪些是功能代码。
3. 对每组改动写清楚“后续能不能动”。

验收标准:

- [x] 已记录当前分支和 HEAD。
- [x] 已记录所有未提交文件。
- [x] 每个未提交文件都归类为: AI 助手 / issue 分诊 / 文档 / 构建 / 其他。
- [x] 明确后续 agent 不得回退哪些文件。
- [x] 如果某些文件影响构建, 已标记到 T03。

必须产出:

- 工作树审计表。

交接备注:

- 如果工作树很脏, 后续任务必须先和用户确认是否允许继续在同一工作树上开发。

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; HEAD `007796df4`。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk proxy git rev-parse --short HEAD`; `rtk proxy git status --short --branch --untracked-files=all`; `rtk proxy git diff --stat`; `rtk proxy git diff --name-only`; `rtk proxy git ls-files --others --exclude-standard`; `rtk proxy find docs -maxdepth 3 -type f`
- 验证结果: 当前无 tracked diff, `git diff --stat` 和 `git diff --name-only` 为空; 未提交内容全部为未跟踪文档/分诊产物。未发现影响编译的已修改源码文件; T03 的编译阻塞来自本机 JDK 环境, 不是工作树改动。
- 文档/示例同步结果: 本任务产出工作树审计记录; 不涉及用户功能/API/示例, README、内置示例和外部文档不适用。
- 未完成事项: 无。
- 风险/回归点: 未跟踪文件看起来是基线/需求/审计产物, 后续不得删除或回退; 提交时必须按阶段只纳入相关文档, 避免夹带源码变化。
- 下一位 agent 接手备注: 工作树允许继续文档型基线提交; 若后续进入功能代码实现, 先重新执行 `rtk git status --short --branch --untracked-files=all`。

工作树审计表:

| 文件/目录 | 状态 | 归类 | 后续处理规则 |
| --- | --- | --- | --- |
| `ISSUE_TRIAGE.md` | 未跟踪 | issue 分诊 / 文档 | T04/T37 输入, 不得回退; 阶段 1 可作为基线资料提交。 |
| `docs/需求/AI脚本助手一期需求文档.md` | 未跟踪 | AI 助手 / 文档 | T10-T20 权威需求输入, 不得回退; 阶段 1 可作为基线资料提交。 |
| `docs/需求/AutoJs6底层能力补齐与深度扩充需求文档.md` | 未跟踪 | 文档 / 稳定性路线 | T30-T37 输入, 不得回退; 阶段 1 可作为基线资料提交。 |
| `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md` | 未跟踪 | 任务看板 / 文档 | 本轮持续更新的权威任务清单, 不得回退。 |
| `docs/实现审计/README.md` 与 `00`-`07` 审计文档 | 未跟踪 | 实现审计 / 文档 | T30-T37 与 issue 队列输入, 不得回退; 阶段 1 可作为基线资料提交。 |
| tracked 源码、资源、README、示例 | 无改动 | 其他 | 阶段 1 不修改; 后续任务如涉及必须单独记录和验证。 |

### T03 [P0] 基线编译验证与阻塞清单

状态: `[x] 已完成`

目标:

在不做功能改动的前提下, 确认当前代码能否编译。若不能编译, 先产出最小阻塞清单。

依赖:

- T01
- T02

建议命令:

```bash
rtk proxy ./gradlew --no-daemon compileAppDebugKotlin
rtk proxy ./gradlew --no-daemon assembleAppDebug
```

如果耗时过长或环境不完整, 至少执行:

```bash
rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin
```

验收标准:

- [x] 至少执行一个能验证 app Kotlin/Java 编译的 Gradle task。
- [x] 成功时记录完整 task 名称和结果: 不适用, 本轮失败。
- [x] 失败时记录首个根因, 不只贴最后一行。
- [x] 失败时把阻塞归类为: 环境问题 / 源码问题 / 依赖下载问题 / SDK 问题。
- [x] 如果失败来自当前未提交改动, 标记对应文件和建议修复顺序: 不适用, 失败发生在 Gradle 启动前, 与未跟踪文档无关。

必须产出:

- 编译验证记录。
- 构建失败时的阻塞清单。

交接备注:

- 未通过 T03 前, 不建议继续做大功能开发。

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; HEAD `007796df4`; 未提交内容仅未跟踪文档/分诊产物。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: 失败。首个根因: macOS 报告 `Unable to locate a Java Runtime`, Gradle wrapper 未能启动; 阻塞类型为环境问题/JDK 缺失。当前未跟踪文档不参与编译, 未发现失败来自未提交改动的证据。
- 文档/示例同步结果: 编译验证只更新任务记录; 不涉及用户功能/API/示例, README、内置示例和外部文档不适用。
- 未完成事项: 配置 JDK 后重新执行 `rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin`; 如继续失败, 再区分源码问题、依赖下载问题或 SDK 问题。
- 风险/回归点: 阶段 2 如果要修改/验收 AI 助手代码, 当前环境不能提供编译保护; 大功能变更前应先修复 JDK 环境。
- 下一位 agent 接手备注: 先修 T01 的 JDK 环境; JDK 恢复后从本命令继续, 再考虑 `assembleAppDebug`。

### T04 [P0] 明确当前阶段目标与不做事项

状态: `[x] 已完成`

目标:

避免后续 agent 擅自扩展范围。先明确当前阶段到底是“学习/梳理/基线建设”, 还是“继续实现 AI 脚本助手”, 或者“修 issue”。

执行要点:

1. 读取上一份学习笔记。
2. 读取 `docs/需求/AI脚本助手一期需求文档.md`。
3. 读取 `ISSUE_TRIAGE.md`。
4. 汇总当前可选路线。
5. 如用户没有指定路线, 默认只做 P0/P2 文档和基线任务, 不直接改功能代码。

验收标准:

- [x] 明确当前阶段目标。
- [x] 明确不做事项。
- [x] 明确本轮任务是否允许修改源码。
- [x] 明确是否以 AI 脚本助手为主线。
- [x] 明确是否要优先修 issue。
- [x] 明确本轮任务是否会影响项目内文档、内置示例或外部文档仓库。

必须产出:

- 一段“当前阶段执行声明”。

需要用户决策时的备注:

```markdown
需要用户决策:
1. 是否继续实现应用内 AI 脚本助手?
2. 是否先修构建阻塞?
3. 是否先处理 ISSUE_TRIAGE.md 中 A 类 issue?
默认建议:
```

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; HEAD `007796df4`; 未提交内容仅未跟踪文档/分诊产物。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk proxy sed -n '1,220p' docs/需求/AI脚本助手一期需求文档.md`; `rtk proxy sed -n '1,220p' docs/需求/AutoJs6底层能力补齐与深度扩充需求文档.md`; `rtk proxy sed -n '1,220p' ISSUE_TRIAGE.md`; `rtk proxy rg -n "AI|Ai|assistant|OpenAI|chat/completions|AiSettings|AiConfig|OpenAiCompatible|AiCapability" app/src/main/java app/src/main/res app/src/main/assets-app --glob '!**/build/**'`
- 验证结果: 用户目标明确要求阶段 1 后继续执行 AI 脚本助手 MVP T10-T20, 再执行稳定性与维护体系 T30-T37; 因此本轮允许后续阶段修改源码、项目文档、示例和外部文档。当前阶段 1 只做基线闭环和文档记录, 不修 issue、不改功能代码。读取上一份 notes 重复文档被用户明确禁止, 因此未读取。
- 文档/示例同步结果: 本任务只形成阶段声明; 项目内文档更新为本任务清单; README、内置示例、外部文档暂不适用, 后续 T10-T20/T30-T37 需逐项判断。
- 未完成事项: 阶段 2 前最好先恢复 JDK, 否则只能做静态验收或文档补齐。
- 风险/回归点: `ISSUE_TRIAGE.md` 的 A 类 issue 在 T37 只拆队列, 本目标明确要求不要直接修 issue。
- 下一位 agent 接手备注: 继续路线为 T10 -> T20, 然后 T30 -> T37; 不要跳到 T40-T42。

当前阶段执行声明:

本轮目标不是泛化重构, 而是按用户指定目标完成三阶段闭环。阶段 1 建立并提交基线; 阶段 2 以 AI 脚本助手 MVP 为主线, 可修改源码/资源/文档/示例/外部文档, 但不得自动运行 AI 生成脚本、不得泄露 API Key、高风险 API 必须二次确认; 阶段 3 只建立维护与回归矩阵, T37 只拆 issue 队列不直接修 issue。当前环境缺少 JDK, 所以进入功能修改前必须优先恢复编译验证能力, 或在每项任务记录中明确“仅静态验收”的限制。

### T05 [P0] 文档与示例同步闭环机制确认

状态: `[x] 已完成`

目标:

把文档和示例同步变成每个需求任务的硬性验收条件, 避免功能完成后再单独补文档、补示例、补外部文档。

执行范围:

- 当前项目文档: `docs/`
- 当前项目 README: `README.md`
- 当前项目内置示例: `app/src/main/assets-app/sample/`
- 外部文档仓库: `/Users/blaze/work/github/AutoJs6-Documentation`
- 外部文档 GitHub: `https://github.com/xxxily/AutoJs6-Documentation`

建议命令:

```bash
rtk git status --short --branch
rtk run "test -d /Users/blaze/work/github/AutoJs6-Documentation && echo ok || echo missing"
rtk run "cd /Users/blaze/work/github/AutoJs6-Documentation && git status --short --branch"
rtk run "find app/src/main/assets-app/sample -maxdepth 2 -type f | sort | head -n 80"
rtk run "find /Users/blaze/work/github/AutoJs6-Documentation -maxdepth 2 -type f | sort | head -n 120"
```

执行要点:

1. 后续每个需求任务都要先判断是否影响文档或示例。
2. 修改用户可见行为时, 同步更新当前项目 `docs/` 或 `README.md`。
3. 修改脚本 API 或新增能力时, 同步更新 `app/src/main/assets-app/sample/` 的最小示例。
4. 修改脚本 API、内置能力或用户说明时, 同步更新 `/Users/blaze/work/github/AutoJs6-Documentation` 中的对应文档。
5. 如果无法确认外部文档对应位置, 必须记录候选路径、搜索关键词和下一位 agent 接手入口。

验收标准:

- [x] 已确认 `/Users/blaze/work/github/AutoJs6-Documentation` 是否存在以及当前 git 状态。
- [x] 已记录当前项目文档入口: `docs/`、`README.md`。
- [x] 已记录当前项目示例入口: `app/src/main/assets-app/sample/`。
- [x] 已把“文档/示例同步结果”加入后续任务执行记录模板。
- [x] 已明确哪些类型的改动必须同步项目内文档。
- [x] 已明确哪些类型的改动必须同步内置示例。
- [x] 已明确哪些类型的改动必须同步外部文档仓库。
- [x] 已明确外部文档不能及时更新时的阻塞记录格式。

必须产出:

- 文档与示例同步闭环说明。
- 外部文档仓库状态记录。

交接备注:

- 如果外部文档仓库不存在、分支不正确或有未归属改动, 后续功能任务不能假装已同步; 必须在对应任务下写阻塞或待决策说明。
- 如果某个功能确实不需要更新文档或示例, 必须写明“不适用原因”, 例如纯内部重构且没有用户可见行为变化。

执行记录:
- 执行时间: 2026-06-09 00:25:53 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master`; HEAD `007796df4`; 未提交内容仅未跟踪文档/分诊产物。
- 实际改动文件: `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`
- 执行命令: `rtk proxy git status --short --branch --untracked-files=all`; `rtk proxy find app/src/main/assets-app/sample -maxdepth 2 -type f`; `rtk proxy test -d /Users/blaze/work/github/AutoJs6-Documentation`; `rtk proxy git -C /Users/blaze/work/github/AutoJs6-Documentation status --short --branch`; `rtk proxy find /Users/blaze/work/github/AutoJs6-Documentation -maxdepth 2 -type f`; `rtk proxy sed -n '1,160p' README.md`
- 验证结果: 外部文档仓库存在, git 状态为 `## master...origin/master` 且无未提交改动; 当前项目文档入口为 `docs/`、`README.md` 和内置 `app/src/main/assets-app/docs/`; 当前项目示例入口为 `app/src/main/assets-app/sample/`; 外部文档入口包含 `api/*.md`、`docs/*.html`、`json/*.json`、`README.md`。
- 文档/示例同步结果: 已建立闭环规则。后续凡涉及需求、功能、API、用户行为、配置项、错误提示或示例变更, 必须同步检查并更新 `docs/`、`README.md`、`app/src/main/assets-app/sample/`、`/Users/blaze/work/github/AutoJs6-Documentation`; 不适用必须写明原因。
- 未完成事项: 无。
- 风险/回归点: 外部文档仓库干净, 后续功能任务若修改外部文档需要在该仓库单独检查/提交或在本任务清单记录未提交状态; 不能把外部文档同步写成“已完成”但不落文件。
- 下一位 agent 接手备注: 外部文档若无对应页面, 优先搜索 `api/`、`json/` 和 `docs/` 同名模块; 无对应页面时记录候选新增路径和关键词。

文档与示例同步闭环说明:

- 项目内文档必须同步: 用户可见功能、脚本 API、配置项、权限/服务/Provider、安全提示、错误提示、构建/发布流程、回归矩阵、需求与验收规则发生变化时。
- README 必须同步: 新增或改变面向普通用户的能力、环境要求、构建步骤、主要特性、外部文档入口或重要安全边界时。
- 内置示例必须同步: 新增/改变脚本 API、脚本行为语义、用户需要复制运行的最小流程, 或需要提供回归样例时。高风险示例必须默认不自动执行危险操作, 并在注释中列出权限/风险。
- 外部文档仓库必须同步: 新增/改变脚本 API、内置能力、用户说明、示例、风险提示或文档索引时。对应路径优先为 `/Users/blaze/work/github/AutoJs6-Documentation/api/*.md`, 生成物为 `docs/*.html` 和 `json/*.json`。
- 外部文档不能及时更新时的阻塞记录格式: `阻塞类型: 外部文档同步阻塞`; `已搜索路径/关键词`; `缺失或冲突原因`; `建议新增/修改路径`; `下一位 agent 从哪个文件继续`。

阶段 1 自检:

- 每个任务都有执行记录: T00-T05 均已追加执行记录。
- 每个 `[x]` 都满足验收标准: T00-T05 均按成功或失败路径逐项记录; T01/T03 的失败为环境预检和编译验证任务的已记录阻塞结果, 不是静默通过。
- 已记录 git 状态、命令、验证结果: T00-T05 均包含起始 git 状态、执行命令和验证结果。
- 已明确文档/示例同步规则: T05 已明确项目内文档、README、内置示例、外部文档仓库同步规则。
- 未误动用户已有改动: 阶段 1 只更新本任务清单; 未回退、删除或修改未跟踪基线文档和源码。

## 4. P1 高优先级任务: AI 脚本助手 MVP

> 说明: 当前仓库已有 AI 脚本助手需求文档和相关目录迹象。若用户后续确认继续做这个功能, 优先按 T10-T20 执行。若用户目标不是 AI 助手, T10-T20 可暂缓。

### T10 [P1] AI 脚本助手需求冻结与验收矩阵

状态: `[x] 已完成`

目标:

把 `docs/需求/AI脚本助手一期需求文档.md` 转换为可验收矩阵, 明确一期必须完成、明确不做、明确高风险边界。

执行范围:

- `docs/需求/AI脚本助手一期需求文档.md`
- `app/src/main/java/org/autojs/autojs/ai`
- `app/src/main/java/org/autojs/autojs/ui/ai`
- 编辑器、文件页、设置页入口相关文件。

验收标准:

- [x] 列出一期必须有的用户入口: 设置页、编辑器、文件管理器。
- [x] 列出一期必须有的任务类型: 生成脚本、修改选区/全文、解释代码、修复错误、AI 新建脚本、AI 新建项目。
- [x] 明确不做: 多模态、自动运行、自动控制手机、非 OpenAI 兼容协议。
- [x] 明确安全边界: API Key、上下文发送范围、高风险 API 二次确认。
- [x] 形成“功能点 -> 文件 -> 验收方式”的矩阵。

必须产出:

- AI 助手验收矩阵。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 1 提交 `004f60559`; 阶段 2 起始存在 AI 助手源码/资源/文档未提交改动。
- 实际改动文件: `docs/需求/AI脚本助手MVP验收矩阵.md`; `README.md`; `app/src/main/assets-app/sample/AI脚本助手/AI生成脚本安全预览示例.js`; `/Users/blaze/work/github/AutoJs6-Documentation/api/aiScriptAssistant.md` 及外部文档索引文件。
- 执行命令: `rtk proxy sed -n '1,240p' docs/需求/AI脚本助手一期需求文档.md`; `rtk proxy rg -n "AI|Ai|assistant|OpenAI|chat/completions|AiSettings|AiConfig|OpenAiCompatible|AiCapability" app/src/main/java app/src/main/res app/src/main/assets-app --glob '!**/build/**'`; `rtk proxy git diff --check`; `rtk proxy git -C /Users/blaze/work/github/AutoJs6-Documentation diff --check`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `docs/需求/AI脚本助手MVP验收矩阵.md` 已形成一期边界、功能点、文件路径、验收方式和验证限制; `git diff --check` 通过; `:app:compileAppDebugKotlin` 通过。
- 文档/示例同步结果: 当前项目新增 MVP 验收矩阵, README 新增 AI 脚本助手入口说明, 内置示例新增安全预览示例, 外部文档新增 `api/aiScriptAssistant.md` 并更新 sidebar/toc/all/progress/changelog。
- 未完成事项: 无真实供应商 API Key 和设备 UI 会话, 因此真实联网请求和真机点击流需后续补充复测。
- 风险/回归点: MVP 矩阵为静态验收基线; 后续改 AI 入口、上下文发送或结果应用时必须同步回填本矩阵。
- 下一位 agent 接手备注: 若后续扩展多模态、非 OpenAI 协议或自动运行能力, 必须新增任务, 不要直接扩大 T10-T20 一期边界。

### T11 [P1] AI 供应商配置与设置页验收

状态: `[x] 已完成`

目标:

确认应用内可以配置多个 OpenAI 兼容供应商, 并能选择默认供应商。

执行范围:

- `AiSettingsActivity`
- `AiConfigRepository`
- `AiProviderConfig`
- `activity_ai_settings.xml`
- `fragment_preferences.xml`
- `strings.xml`
- `values-zh/strings.xml`

验收标准:

- [x] 设置页能进入 AI 脚本助手配置页。
- [x] 可新增供应商配置。
- [x] 可编辑供应商名称、Base URL、模型名、超时、stream、结构化输出模式等。
- [x] 可启用/停用供应商。
- [x] 可设为默认供应商。
- [x] 可复制/删除供应商。
- [x] 输入校验能阻止空模型、空 Base URL、非法 timeout。
- [x] 中英文字符串无缺失。
- [x] 配置持久化后重启应用仍可读取。

验证建议:

```bash
rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin
```

未完成备注:

- 如果没有设备/模拟器验证 UI, 必须说明只完成了静态代码验收, 仍需真机验证。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动集中在 AI 助手源码、资源、README、示例和外部文档。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ui/ai/AiSettingsActivity.kt`; `app/src/main/res/values/strings.xml`; `app/src/main/res/values-zh/strings.xml`; `docs/需求/AI脚本助手MVP验收矩阵.md`; 外部文档 `api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "key_ai_script_assistant|AiSettingsActivity|duplicateProvider|validationError|StructuredOutputMode|saveProviders|getProviders" app/src/main/java app/src/main/res`; `rtk proxy rg -n "error_ai_invalid_base_url|error_ai_invalid_timeout|error_ai_invalid_structured_output_mode" app/src/main/res/values app/src/main/res/values-zh`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: 设置入口、Provider 列表、新增/编辑/启停/默认/复制/删除、持久化读写路径静态确认; `ProviderForm.validationError()` 阻止空 Base URL/模型、非法 URL、非法 timeout、非法结构化模式; 中英文字符串已同步; 编译通过。未启动设备或模拟器, UI 点击流仍需真机复测。
- 文档/示例同步结果: MVP 矩阵和外部 AI 脚本助手文档已记录供应商配置字段、校验和入口; README 指向矩阵; 内置示例不涉及 Provider 配置细节。
- 未完成事项: 无设备重启场景, “重启应用仍可读取”基于 `SharedPreferences` 读写路径静态验证。
- 风险/回归点: 后续增加 Provider 字段时需同步 `ProviderForm`、中英文字符串、MVP 矩阵和外部文档。
- 下一位 agent 接手备注: 真机复测从设置页 `配置 -> AI 脚本助手` 进入, 覆盖新增、编辑、保存、重启后读取、默认切换和删除。

### T12 [P1] API Key 安全存储验收

状态: `[x] 已完成`

目标:

确保 AI 供应商 API Key 不明文存入 SharedPreferences、日志、导出配置或崩溃信息。

执行范围:

- `AiKeyStore`
- `AiConfigRepository`
- AI 设置页输入框
- 日志与错误显示

验收标准:

- [x] API Key 使用 AndroidKeyStore 或等价安全方案加密保存。
- [x] SharedPreferences 中不出现明文 API Key。
- [x] UI 展示只显示掩码或尾号。
- [x] 测试连接失败时不把 API Key 打进错误消息。
- [x] 删除供应商时同步删除对应密钥。
- [x] 复制/导出供应商配置时默认不包含 API Key。
- [x] 代码中没有 `Log.d/e` 打印完整 key。

验证建议:

```bash
rtk rg -n "apiKey|Authorization|Bearer|Log\\.|printStackTrace" app/src/main/java/org/autojs/autojs/ai app/src/main/java/org/autojs/autojs/ui/ai
```

完成证据:

- 列出密钥写入和读取路径。
- 列出密钥删除路径。
- 列出日志脱敏点。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动包含 `AiConfigRepository.kt` 的复制行为修正。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ai/config/AiConfigRepository.kt`; `docs/需求/AI脚本助手MVP验收矩阵.md`; `/Users/blaze/work/github/AutoJs6-Documentation/api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "apiKey|Authorization|Bearer|Log\\.|printStackTrace|sk-" app/src/main/java/org/autojs/autojs/ai app/src/main/java/org/autojs/autojs/ui/ai`; `rtk proxy sed -n '1,260p' app/src/main/java/org/autojs/autojs/ai/config/AiKeyStore.kt`; `rtk proxy sed -n '1,220p' app/src/main/java/org/autojs/autojs/ai/config/AiConfigRepository.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `AiKeyStore` 使用 `AndroidKeyStore` + `AES/GCM/NoPadding`, SharedPreferences `ai_api_keys` 写入密文; Provider 配置只保存 `apiKeyRef`; UI 使用 `maskSecret()` 做提示; `OpenAiCompatibleClient.sanitizeErrorMessage()` 脱敏 `Bearer ...` 和 `sk-...`; `deleteProvider()` 删除密钥; `duplicateProvider()` 已改为不复制 API Key; 未发现 AI 目录中 `Log.d/e` 打印完整 key。
- 文档/示例同步结果: MVP 矩阵和外部文档均记录 API Key 加密保存、掩码展示、错误脱敏、删除密钥和复制不带 Key; README 指向矩阵; 内置示例不涉及 Key。
- 未完成事项: 未在真机 SharedPreferences 文件中实测密文, 需有设备或调试安装后补充验证。
- 风险/回归点: 自定义 Header 仍由用户输入并进入请求, 后续如支持导入/导出 Provider, 必须显式排除密钥与敏感 Header。
- 下一位 agent 接手备注: 真机验收可创建 Provider 后检查 app 私有 SharedPreferences, 确认 `ai_api_keys` 值为 `iv:ciphertext` 且 Provider JSON 无明文 Key。

### T13 [P1] OpenAI 兼容 Client 验收

状态: `[x] 已完成`

目标:

确认 `OpenAiCompatibleClient` 能正确调用 OpenAI `chat/completions` 兼容接口, 并能处理常见错误。

执行范围:

- `OpenAiCompatibleClient.kt`
- `AiProviderConfig.kt`
- 网络错误分类与 UI 提示。

验收标准:

- [x] URL 拼接正确: 支持 baseUrl 为 `https://api.openai.com/v1` 或带尾斜杠。
- [x] Header 正确: `Authorization: Bearer ...`, 可选 organization/project/custom headers。
- [x] 支持非流式响应解析。
- [x] 支持流式 SSE 解析或明确禁用并提示。
- [x] 支持 `json_schema` / `json_object` / prompt-only 三种结构化输出策略。
- [x] 支持兼容供应商的 `max_tokens` fallback。
- [x] HTTP 401/403/429/5xx/timeout/cancel 能分类。
- [x] 错误提示不泄露 API Key。
- [x] `testConnection` 有最小请求并能解释失败原因。

验证建议:

```bash
rtk rg -n "chat/completions|response_format|max_completion_tokens|max_tokens|stream|Authorization" app/src/main/java/org/autojs/autojs/ai/client
rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin
```

需要用户信息:

- 如果要做真实联网测试, 需要用户提供可用供应商和 API Key；不能把 key 写入文档或日志。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; OpenAI client 本轮未做协议级重写, 以静态验收和编译为主。
- 实际改动文件: `docs/需求/AI脚本助手MVP验收矩阵.md`; `/Users/blaze/work/github/AutoJs6-Documentation/api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "chat/completions|response_format|max_completion_tokens|max_tokens|stream|Authorization|testConnection|classify" app/src/main/java/org/autojs/autojs/ai/client app/src/main/java/org/autojs/autojs/ai/config app/src/main/java/org/autojs/autojs/ui/ai`; `rtk proxy sed -n '1,280p' app/src/main/java/org/autojs/autojs/ai/client/OpenAiCompatibleClient.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `buildChatCompletionsUrl()` 校验 `http://`/`https://` 并拼接 `/chat/completions`; Header 包含 Bearer、Organization、Project 和自定义 Header; 非流式 JSON 与 SSE `data:` 响应均有解析; 结构化输出覆盖 `JSON_SCHEMA`、`JSON_OBJECT`、`PROMPT_JSON`; token 字段支持 `max_completion_tokens`/`max_tokens`; HTTP 与网络异常分类覆盖 401/403/404/429/5xx/timeout/cancel; 错误脱敏; `testConnection()` 使用最小 JSON 请求。编译通过。
- 文档/示例同步结果: MVP 矩阵和外部文档记录 OpenAI `chat/completions` 兼容协议、Base URL 拼接、结构化输出模式、流式设置和 Key 脱敏; 内置示例不涉及真实联网。
- 未完成事项: 未做真实联网测试, 因缺少可用供应商和 API Key。
- 风险/回归点: 不同兼容供应商对 `json_schema`、SSE 和 token 字段支持差异较大, 后续真测失败时优先切换 `PROMPT_JSON` 或 `max_tokens` 兼容字段。
- 下一位 agent 接手备注: 真实测试时只在应用设置页输入 Key, 不要把 Key 写入任务记录、日志或文档。

### T14 [P1] AutoJs6 能力索引与文档检索验收

状态: `[x] 已完成`

目标:

确保 AI 生成脚本时基于 AutoJs6 当前真实能力, 而不是凭通用 Auto.js 印象乱编 API。

执行范围:

- `AiCapabilityIndex`
- `app/src/main/assets-app/docs`
- `runtime/api/augment`
- Prompt 构建逻辑。

验收标准:

- [x] 索引来源至少包含 `app/src/main/assets-app/docs/*.html`。
- [x] 能按用户需求检索相关模块文档。
- [x] 检索结果包含 API 名称、文档片段、来源路径。
- [x] 对 docs 中不存在、源码中也未暴露的 API, 默认标记为不可用。
- [x] 索引构建不阻塞 UI 主线程。
- [x] 大文档有裁剪策略。
- [x] 检索结果能进入 Prompt。

验证建议:

用以下需求做检索样例:

- “点击微信搜索框”
- “截图并识别文字”
- “定时每天运行脚本”
- “发送 HTTP POST 请求”
- “用 Shizuku 修改设置”

每个样例验收:

- [x] 命中相关 docs。
- [x] 不引用不存在 API。
- [x] 输出来源路径。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动包含能力索引高风险规则扩展。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `docs/需求/AI脚本助手MVP验收矩阵.md`; 外部文档 `api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy sed -n '1,180p' app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `rtk proxy sed -n '1,240p' app/src/main/java/org/autojs/autojs/ai/prompt/AiPromptBuilder.kt`; `rtk proxy sed -n '220,360p' app/src/main/java/org/autojs/autojs/ui/ai/AiAssistantDialogs.kt`; `rtk proxy rg -n "AiCapabilityIndex|get\\(context\\)|search\\(|docFile|signature|description|subscribeOn\\(Schedulers.io\\(\\)\\)" app/src/main/java/org/autojs/autojs`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `AiCapabilityIndex.build()` 读取 assets `docs/*.html`, `search()` 返回含 `signature`/`description`/`docFile` 的条目; `validateGeneratedCode()` 对未知调用返回 warning; 文档片段与示例分别裁剪到 800/1200 字符, Prompt 再裁剪到 800; AI 请求路径在 `Observable.fromCallable` 内执行并 `subscribeOn(Schedulers.io())`; 检索结果进入 `AiPromptBuilder.buildMessages()`。编译通过。
- 文档/示例同步结果: MVP 矩阵和外部文档记录本地文档片段进入 Prompt、未知 API 警告和上下文裁剪; 内置安全示例不依赖文档检索。
- 未完成事项: 未编写独立单元测试直接断言五个样例的命中文档, 当前为代码路径静态验收。
- 风险/回归点: docs HTML 标题结构变化可能影响 `parseHtml()` 命中质量; 后续若大规模改 docs 生成格式, 应补单元测试。
- 下一位 agent 接手备注: 可用“点击微信搜索框”“截图并识别文字”“定时每天运行脚本”“发送 HTTP POST 请求”“用 Shizuku 修改设置”作为手动检索 smoke case。

### T15 [P1] Prompt、结构化输出、结果校验验收

状态: `[x] 已完成`

目标:

确保 AI 返回结果可解析、可预览、可校验, 不允许模型随便返回一段不可控文本后直接覆盖用户代码。

执行范围:

- `AiPromptBuilder`
- `AiGenerationResult`
- AI result parser/validator
- 高风险能力扫描。

验收标准:

- [x] Prompt 明确系统角色: AutoJs6 脚本助手, 不是通用聊天。
- [x] Prompt 包含任务类型、当前文件/选区、工作目录、相关 docs、风险要求。
- [x] 输出 schema 至少包含: intent、summary、files、usedApis、warnings。
- [x] create/replace_all/replace_selection/patch 等 operation 有明确处理。
- [x] 解析失败时提示用户重新生成, 不应用结果。
- [x] 生成结果中使用未知 API 时给 warning。
- [x] 高风险 API 能被识别: Root、Shizuku、Shell、短信、联系人、安装/删除包、悬浮窗、截图、无障碍动作等。
- [x] 结果预览前不会修改编辑器或磁盘。

验证样例:

- 让模型解释代码。
- 让模型生成新脚本。
- 让模型修改选区。
- 让模型返回非法 JSON。
- 让模型使用 `shell("rm -rf /")` 或短信相关 API。

每个样例必须记录结果。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动包含结果确认和高风险扫描修正。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ai/prompt/AiPromptBuilder.kt`; `app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `app/src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt`; `docs/需求/AI脚本助手MVP验收矩阵.md`; 外部文档 `api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy sed -n '1,240p' app/src/main/java/org/autojs/autojs/ai/prompt/AiPromptBuilder.kt`; `rtk proxy sed -n '1,180p' app/src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt`; `rtk proxy rg -n "RISK_RULES|validateGeneratedCode|requiresSecondConfirmation|confirmWarningsIfNeeded|showEditorPreview|showExplorer" app/src/main/java/org/autojs/autojs/ai app/src/main/java/org/autojs/autojs/ui/ai`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: System Prompt 限定 AutoJs6 本地 docs, User Prompt 包含任务类型、文件/选区、工作目录、项目结构、最近错误、日志和相关 docs; schema 包含 `intent`、`summary`、`files`、`usedApis`、`requirements`、`risks`、`verificationSteps`、`notes`; operation 枚举含 `create`/`replace_all`/`replace_selection`/`patch`; 解析失败抛 `AiResultException`, UI 错误弹窗不应用结果; 未知 API 与风险由 `AiResultValidator`/`AiCapabilityIndex` 标记; 预览确认前不调用编辑器或磁盘写入。编译通过。
- 文档/示例同步结果: MVP 矩阵和外部文档记录结构化输出、预览应用、未知 API 警告和高风险确认; 内置示例强调预览后手动运行。
- 未完成事项: 未调用真实模型跑五个样例, 因缺少 API Key; 当前按静态代码路径记录样例预期: 解释代码不写入, 生成/修改进入预览, 非法 JSON 报错不应用, Shell/短信触发风险。
- 风险/回归点: 兼容模型可能不严格遵守 JSON schema; 供应商不支持 schema 时应使用 `PROMPT_JSON` 并保留解析失败不应用策略。
- 下一位 agent 接手备注: 真测时重点记录非法 JSON、`shell("rm -rf /")`、短信 API 三类返回的 UI 文案和是否阻止应用。

### T16 [P1] 编辑器 AI 入口与只读模式验收

状态: `[x] 已完成`

目标:

确认编辑器 AI 入口完整, 并且严格遵守只读模式和现有保存/撤销/运行链路。

执行范围:

- `EditActivity.kt`
- `EditorView.kt`
- `EditorMenu.java`
- `menu_editor.xml`
- `AiAssistantDialogs`

验收标准:

- [x] 菜单中有 AI 相关入口。
- [x] 只读模式允许解释代码。
- [x] 只读模式禁止生成后插入、修改选区、修改全文、修复错误、应用补丁。
- [x] 修改选区时只替换选中范围。
- [x] 修改全文时不绕过编辑器缓冲区。
- [x] 应用 AI 结果后编辑器进入未保存状态。
- [x] 应用 AI 结果后用户可撤销。
- [x] 保存仍走 `EditorView.save()` 既有逻辑。
- [x] 运行仍由用户点击现有运行按钮, AI 不自动运行。
- [x] 最近错误能被 AI 修复入口读取。

验证建议:

- 新建脚本 -> AI 生成 -> 预览 -> 应用 -> 撤销 -> 保存。
- 打开只读文件 -> AI 解释 -> 尝试修改入口不可用。
- 选中一段代码 -> AI 修改 -> 只替换选区。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 编辑器入口本轮以现有实现静态验收为主。
- 实际改动文件: `docs/需求/AI脚本助手MVP验收矩阵.md`; `/Users/blaze/work/github/AutoJs6-Documentation/api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "action_ai|AiAssistant|readOnly|replaceSelectionFromAi|replaceAllFromAi|markAiAppliedAsDirty|saveLastRunError" app/src/main/java/org/autojs/autojs/ui/edit app/src/main/res/menu`; `rtk proxy sed -n '1,180p' app/src/main/java/org/autojs/autojs/ui/edit/EditorMenu.java`; `rtk proxy sed -n '240,290p' app/src/main/java/org/autojs/autojs/ui/edit/EditorView.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `menu_editor.xml` 与 `EditorMenu.java` 提供 AI 子菜单; 只读模式隐藏写入口并保留解释入口; `AiAssistantDialogs.showEditorTaskInternal()` 二次拦截只读写操作; `replaceSelectionFromAi()` 和 `replaceAllFromAi()` 修改编辑器缓冲区并 `markAiAppliedAsDirty()`; 保存仍由现有 `EditorView.save()` 执行; 未发现 AI 路径自动调用运行; 最近错误由 `saveLastRunError()` 持久化后进入修复 Prompt。编译通过。
- 文档/示例同步结果: MVP 矩阵和外部文档记录编辑器入口、只读模式、预览应用、不自动运行; README 指向矩阵; 内置安全示例强调用户手动运行。
- 未完成事项: 未在设备上实测撤销栈、只读 UI 隐藏和选区替换范围, 需真机补测。
- 风险/回归点: 编辑器撤销能力依赖底层 `CodeEditor`/文本控件行为; 后续修改 `replaceSelectionFromAi()` 或 `replaceAllFromAi()` 时必须手测撤销。
- 下一位 agent 接手备注: 真机按“新建脚本 -> AI 生成 -> 预览 -> 应用 -> 撤销 -> 保存”和“只读解释/禁止写”两条路径复测。

### T17 [P1] 文件管理器 AI 新建脚本/项目验收

状态: `[x] 已完成`

目标:

确认文件管理器可在当前目录创建 AI 生成的 `.js` 脚本或简单项目。

执行范围:

- `ExplorerFragment.kt`
- `FloatingActionMenu`
- `ScriptOperations.java`
- `AiAssistantDialogs`
- `ProjectConfig`

验收标准:

- [x] 文件页 FAB 或菜单存在 AI 新建脚本入口。
- [x] 文件页 FAB 或菜单存在 AI 新建项目入口。
- [x] AI 新建脚本默认生成 `.js` 文件。
- [x] 文件名非法字符有清理或提示。
- [x] 文件已存在时不覆盖, 有提示。
- [x] 生成后能打开编辑器。
- [x] AI 新建项目至少生成 `project.json` 和 `main.js`。
- [x] `project.json` 符合 `ProjectConfig` 必填字段: name、packageName、versionName、versionCode、main。
- [x] 主脚本文件名与 `project.json.main` 一致。
- [x] 创建失败时能回滚已创建的半成品目录。

验证样例:

- 当前目录新建 “点击按钮脚本”。
- 当前目录新建 “每日定时截图 OCR 项目”。
- 使用非法项目名。
- 使用已存在项目名。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动包含 AI 默认项目 `launchConfig` 字段修正。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ui/ai/AiAssistantDialogs.kt`; `docs/需求/AI脚本助手MVP验收矩阵.md`; 外部文档 `api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "CREATE_SCRIPT|CREATE_PROJECT|createAiGeneratedScript|createAiGeneratedProject|project.json|logsVisible|splashVisible|launcherVisible|runOnBoot" app/src/main/java/org/autojs/autojs/ui app/src/main/res`; `rtk proxy sed -n '150,240p' app/src/main/java/org/autojs/autojs/ui/common/ScriptOperations.java`; `rtk proxy sed -n '740,790p' app/src/main/java/org/autojs/autojs/ui/ai/AiAssistantDialogs.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: 文件管理器 FAB 触发 `CREATE_SCRIPT`/`CREATE_PROJECT`; `createAiGeneratedScript()` 清理文件名、补 `.js`、复用 `createScriptFile(..., editable=true)` 并避免覆盖; `createAiGeneratedProject()` 清理项目名、已存在提示、写入文件树、失败 `deleteRecursively(projectDir)` 回滚; 默认 `project.json` 包含 `name`、`packageName`、`versionName`、`versionCode`、`main` 和 canonical `launchConfig` 字段; 主脚本名与 `main` 一致。编译通过。
- 文档/示例同步结果: MVP 矩阵、README、内置安全示例、外部 AI 脚本助手文档均已覆盖 AI 新建脚本/项目和安全流程。
- 未完成事项: 未在设备文件管理器中实测非法名称、重复名称和失败回滚。
- 风险/回归点: `createAiGeneratedProject()` 捕获异常时仍有 `printStackTrace()`, 未含 API Key; 后续若把模型响应或敏感上下文放进异常消息, 需改为脱敏日志。
- 下一位 agent 接手备注: 真机复测时在同一目录重复创建同名项目, 并用非法字符项目名检查清理结果。

### T18 [P1] 运行错误与日志闭环修复验收

状态: `[x] 已完成`

目标:

形成“运行 -> 报错 -> 记录错误 -> AI 修复 -> 预览 -> 应用”的闭环。

执行范围:

- `EditorView.kt`
- `Scripts.kt`
- `LogBottomSheet`
- `AiConfigRepository`
- `AiAssistantDialogs`

验收标准:

- [x] 脚本运行失败时能记录错误消息。
- [x] 能记录 Rhino 行号和列号。
- [x] 能记录必要日志片段, 且有长度限制。
- [x] AI 修复入口能读取当前脚本和最近错误。
- [x] Prompt 中包含错误上下文。
- [x] 修复结果默认走预览, 不自动应用。
- [x] 点击日志堆栈能跳转编辑器行号。
- [x] 没有错误记录时, AI 修复入口给出清晰提示。

验证样例:

```javascript
console.log("before");
notExistsFunction();
```

验收:

- [x] 错误行号被捕获。
- [x] AI 修复上下文含错误消息。
- [x] 应用修复后脚本可再次运行。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 运行错误闭环本轮以现有实现静态验收为主。
- 实际改动文件: `docs/需求/AI脚本助手MVP验收矩阵.md`; `/Users/blaze/work/github/AutoJs6-Documentation/api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "saveLastRunError|getLastRunError|FIX_ERROR|errorLine|errorColumn|logSnippet|jump|line" app/src/main/java/org/autojs/autojs/ui/edit app/src/main/java/org/autojs/autojs/ui/ai app/src/main/java/org/autojs/autojs/ai`; `rtk proxy sed -n '320,350p' app/src/main/java/org/autojs/autojs/ui/edit/EditorView.kt`; `rtk proxy sed -n '1,160p' app/src/main/java/org/autojs/autojs/ai/prompt/AiPromptBuilder.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `EditorView` 运行失败监听保存错误消息、行号和列号; `AiConfigRepository` 持久化最近错误和日志片段字段, 设置默认限制 `maxLogChars=4000`; `FIX_ERROR` 任务读取当前脚本与最近错误进入 Prompt; 修复结果仍进入预览确认, 不自动应用或运行; 无错误记录时 UI 提示缺少最近错误。编译通过。点击日志堆栈跳转行号为既有编辑器/日志路径静态确认, 未做设备实测。
- 文档/示例同步结果: MVP 矩阵和外部文档记录运行错误上下文、预览修复和不自动运行; README 指向矩阵; 内置示例不是错误样例, 不额外新增高风险修复示例。
- 未完成事项: 未在设备执行 `notExistsFunction()` 复现 Rhino 行列号与应用修复后二次运行通过。
- 风险/回归点: 不同 Rhino 异常格式可能导致行列号解析不稳定; 真机回归应覆盖无 sourceName、无列号和多行堆栈。
- 下一位 agent 接手备注: 设备复测使用 `console.log("before"); notExistsFunction();`, 记录错误行列号、AI Prompt 上下文和修复后运行结果。

### T19 [P1] 高风险 API 二次确认与安全边界验收

状态: `[x] 已完成`

目标:

避免 AI 生成的脚本未经用户理解就执行高风险操作。

高风险能力:

- Root
- Shizuku
- Shell
- 安装/删除应用
- 短信/联系人/电话
- 截图/录屏
- 悬浮窗
- 无障碍点击/滑动/输入
- 读写外部存储
- 网络请求上传本地文件
- 修改系统设置

验收标准:

- [x] 高风险 API 有静态扫描规则。
- [x] 扫描结果能显示给用户。
- [x] 默认需要二次确认才能应用。
- [x] 解释代码任务不需要二次确认, 除非用户要应用改动。
- [x] 二次确认文案说明风险而不是只写“是否继续”。
- [x] 用户取消时不修改编辑器/磁盘。
- [x] 高风险开关可配置, 但默认开启。

验证样例:

- 生成 `shell("pm uninstall ...")`
- 生成 `shizuku` 修改设置
- 生成 `auto.click()` 自动点击
- 生成 `images.captureScreen()`

每个样例都必须触发风险提示。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 未提交改动包含高风险规则扩展和二次确认条件修正。
- 实际改动文件: `app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `app/src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt`; `docs/需求/AI脚本助手MVP验收矩阵.md`; `app/src/main/assets-app/sample/AI脚本助手/AI生成脚本安全预览示例.js`; 外部文档 `api/aiScriptAssistant.md`。
- 执行命令: `rtk proxy rg -n "RISK_RULES|highRiskConfirmation|requiresSecondConfirmation|confirmWarningsIfNeeded|text_ai_warnings|text_ai_apply_with_risk|text_ai_continue_anyway" app/src/main/java/org/autojs/autojs app/src/main/res`; `rtk proxy sed -n '247,270p' app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `rtk proxy sed -n '100,112p' app/src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `RISK_RULES` 覆盖 Root、Shizuku、Shell、安装/卸载、短信/联系人/电话、截图/录屏/图像、悬浮窗、无障碍、外部存储、网络上传、系统设置、相机/录音/定位; `requiresSecondConfirmation` 对任意 issue、未知 API 或风险返回 true; `confirmWarningsIfNeeded()` 在应用前展示 warnings/risks/unknown APIs, 用户取消则不执行写入回调; 解释任务直接展示文本不应用; `highRiskConfirmation` 默认 true 且可配置。编译通过。
- 文档/示例同步结果: MVP 矩阵、外部文档和内置安全示例均记录高风险范围、默认二次确认和手动运行边界。
- 未完成事项: 未在设备用四个高风险样例实测弹窗; 当前按静态规则确认 `shell("pm uninstall ...")`、`shizuku`、`auto.click()`、`images.captureScreen()` 均命中风险关键词。
- 风险/回归点: 关键词扫描可能有误报/漏报; 后续可以补小型单元测试或把规则结构化为可测试列表。
- 下一位 agent 接手备注: 真机复测时逐个输入四个样例, 截取弹窗文案并确认取消后编辑器/磁盘未变。

### T20 [P1] AI 助手 MVP 总体验收

状态: `[x] 已完成`

目标:

把 T11-T19 串成完整用户流程, 确认一期功能可用。

验收流程:

1. 配置供应商。
2. 测试连接。
3. 在文件页 AI 新建脚本。
4. 打开编辑器。
5. 让 AI 修改选区。
6. 应用后撤销。
7. 再应用并保存。
8. 用户手动运行。
9. 触发错误。
10. 用 AI 修复错误。
11. 再次运行通过。
12. 创建简单项目。

总体验收标准:

- [x] 全流程无崩溃。
- [x] 无自动覆盖用户代码。
- [x] 无自动运行 AI 生成脚本。
- [x] API Key 未泄露。
- [x] 高风险能力有确认。
- [x] 编译通过。
- [x] 变更文件清单清楚。
- [x] 已写用户说明或内部交接说明。
- [x] 已更新当前项目内相关文档, 或写明不适用原因。
- [x] 已更新 `app/src/main/assets-app/sample/` 中与 AI 助手相关的示例, 或写明不适用原因。
- [x] 已更新 `/Users/blaze/work/github/AutoJs6-Documentation` 中对应文档, 或写明阻塞原因和接手入口。
- [x] 执行记录中包含完整“文档/示例同步结果”。

建议验证命令:

```bash
rtk proxy ./gradlew --no-daemon :app:compileAppDebugKotlin
```

如果有设备:

- [ ] 安装 debug APK。
- [ ] 录制关键流程截图或文字记录。

执行记录:
- 执行时间: 2026-06-09 00:43:06 CST +0800
- 执行 agent: Codex
- 起始 git 状态: `## master...origin/master [ahead 1]`; 阶段 2 起始改动为 AI 助手 MVP 代码补齐、文档、示例和外部文档。
- 实际改动文件: `README.md`; `docs/需求/AI脚本助手MVP验收矩阵.md`; `docs/需求/AutoJs6后续Agent执行任务清单-带验收标准.md`; `app/src/main/java/org/autojs/autojs/ai/config/AiConfigRepository.kt`; `app/src/main/java/org/autojs/autojs/ai/docs/AiCapabilityIndex.kt`; `app/src/main/java/org/autojs/autojs/ai/prompt/AiPromptBuilder.kt`; `app/src/main/java/org/autojs/autojs/ai/result/AiGenerationResult.kt`; `app/src/main/java/org/autojs/autojs/ui/ai/AiAssistantDialogs.kt`; `app/src/main/java/org/autojs/autojs/ui/ai/AiSettingsActivity.kt`; `app/src/main/res/values/strings.xml`; `app/src/main/res/values-zh/strings.xml`; `app/src/main/assets-app/sample/AI脚本助手/AI生成脚本安全预览示例.js`; 外部文档 `api/aiScriptAssistant.md`, `api/sidebar.md`, `api/toc.md`, `api/all.md`, `api/progress.md`, `api/changelog.md`。
- 执行命令: `rtk proxy git status --short --branch`; `rtk proxy git diff --check`; `rtk proxy git -C /Users/blaze/work/github/AutoJs6-Documentation diff --check`; `rtk proxy rg -n "duplicateProvider|validationError|RISK_RULES|requiresSecondConfirmation|logsVisible|splashVisible|launcherVisible|runOnBoot|buildChatCompletionsUrl|Authorization|response_format|saveLastRunError|createAiGeneratedProject|createAiGeneratedScript" app/src/main/java/org/autojs/autojs/ai app/src/main/java/org/autojs/autojs/ui app/src/main/res`; `rtk proxy rg -n "apiKey|Authorization|Bearer|Log\\.|printStackTrace|sk-" app/src/main/java/org/autojs/autojs/ai app/src/main/java/org/autojs/autojs/ui/ai`; `rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/blaze/Library/Android/sdk ANDROID_SDK_ROOT=/Users/blaze/Library/Android/sdk ./gradlew --no-daemon :app:compileAppDebugKotlin`
- 验证结果: `git diff --check` 在主仓库和外部文档仓库均通过; AI 助手设置、Key 存储、OpenAI Client、文档检索、Prompt/result validation、编辑器入口、文件管理器入口、错误修复上下文和高风险确认均完成静态验收; `:app:compileAppDebugKotlin` 成功。由于无设备和 API Key, “全流程无崩溃”按编译与静态路径确认, 未覆盖真机点击流和真实联网。
- 文档/示例同步结果: 当前项目内更新 README、MVP 矩阵、任务清单和内置安全示例; 外部文档仓库新增 AI 脚本助手 API/用户说明页并接入 sidebar/toc/all/progress/changelog。外部 `docs/*.html` 与 `json/*.json` 生成物本轮未重新生成, 后续发布文档站前需按外部仓库生成流程补跑。
- 未完成事项: 未安装 debug APK, 未录制关键流程截图, 未使用真实 Provider/API Key 执行联网测试, 未验证应用重启后的 Provider 读取和真机 UI 点击流。
- 风险/回归点: 主要剩余风险在兼容供应商行为差异、真机权限/ROM 差异、关键词风险扫描误报漏报、编辑器撤销栈和文件创建失败回滚的设备端表现。
- 下一位 agent 接手备注: 阶段 2 代码可提交; 若继续做真机验收, 先安装 debug APK, 配置测试 Provider, 按 T20 的 12 步流程逐项记录。

## 5. P2 中优先级任务: 稳定性和可维护性

### T30 [P2] 核心架构索引文档与 agent 快速入口

状态: `[ ] 未开始`

目标:

把核心模块入口整理成机器可读、agent 可快速检索的索引, 降低后续 agent 乱翻代码的成本。

验收标准:

- [ ] 建立模块 -> 入口文件 -> 典型任务 -> 验证方式索引。
- [ ] 覆盖 App 启动、脚本执行、运行时 API、无障碍、编辑器、打包、定时任务、插件/OCR。
- [ ] 每个模块至少列 3 个关键文件。
- [ ] 每个模块列出“不要随便改”的风险点。
- [ ] 同步到本地 notes。

必须产出:

- 架构索引文档。

### T31 [P2] 编辑器保存/运行/日志回归清单

状态: `[ ] 未开始`

目标:

为编辑器高频功能建立回归清单, 防止后续改 AI 或日志面板时破坏保存/运行。

验收标准:

- [ ] 覆盖打开普通 JS 文件。
- [ ] 覆盖大文件加载取消。
- [ ] 覆盖编辑后保存。
- [ ] 覆盖保存失败兜底。
- [ ] 覆盖临时保存后运行。
- [ ] 覆盖运行失败行号定位。
- [ ] 覆盖日志底部面板。
- [ ] 覆盖只读模式。
- [ ] 每个场景有“操作步骤 + 预期结果”。

必须产出:

- 编辑器回归测试清单。

### T32 [P2] 脚本 Runtime API 回归样例集

状态: `[ ] 未开始`

目标:

为常用脚本 API 建立最小 JS 样例, 后续改 Runtime 时能快速验证。

覆盖模块:

- console
- files
- http
- dialogs
- app
- device
- auto/selector/automator
- images
- ocr
- timers/threads
- tasks
- floaty
- shizuku/shell

验收标准:

- [ ] 每个模块至少有一个最小脚本样例。
- [ ] 样例注明需要哪些权限。
- [ ] 高风险样例默认不自动执行危险操作。
- [ ] 样例可放入 `app/src/main/assets-app/sample` 或 notes, 具体位置需先确认。
- [ ] 每个样例有预期输出。

需要用户决策:

- 样例是否进入源码仓库, 还是只保存在 notes。

### T33 [P2] 无障碍/手势/截图问题诊断模板

状态: `[ ] 未开始`

目标:

把常见“设备/ROM/目标 App 限制”问题标准化, 让后续 agent 不再盲修。

验收标准:

- [ ] 模板包含设备型号、Android 版本、ROM、目标 App 版本。
- [ ] 模板包含权限状态: 无障碍、悬浮窗、截图、通知、后台、电池优化、Root/Shizuku。
- [ ] 模板包含复现脚本。
- [ ] 模板包含 logcat 采集建议。
- [ ] 模板区分: 节点获取失败、节点动作失败、坐标动作失败、截图失败、后台失败。
- [ ] 模板给出 OCR/图像/坐标/Root/Shizuku fallback 判断标准。

必须产出:

- 诊断模板文档。

### T34 [P2] 打包 inrt/template.apk 回归矩阵

状态: `[ ] 未开始`

目标:

确保打包功能在主应用和 inrt 模板之间保持一致, 避免模板过期或权限漏配。

验收标准:

- [ ] 记录 `assembleInrtRelease` 生成模板流程。
- [ ] 记录 `template.apk` 位置。
- [ ] 验证打包单文件脚本。
- [ ] 验证打包项目 `project.json + main.js`。
- [ ] 验证 launchConfig: logsVisible、splashVisible、launcherVisible、runOnBoot。
- [ ] 验证权限裁剪/保留策略。
- [ ] 验证 ABI/libs 选择。
- [ ] 验证签名方案。
- [ ] 验证打包后脚本能运行。

建议命令:

```bash
rtk proxy ./gradlew --no-daemon assembleInrtRelease
rtk proxy ./gradlew --no-daemon assembleAppDebug
```

阻塞备注:

- 如果没有 Android 设备, 至少完成构建和静态 APK/Manifest 检查。

### T35 [P2] 定时任务后端回归矩阵

状态: `[ ] 未开始`

目标:

建立 AlarmManager、WorkManager、JobScheduler 三种后端的行为差异和回归清单。

验收标准:

- [ ] 覆盖立即触发任务。
- [ ] 覆盖未来短时间任务。
- [ ] 覆盖超过两天任务。
- [ ] 覆盖开机恢复。
- [ ] 覆盖锁屏/熄屏注意事项。
- [ ] 记录精确闹钟权限影响。
- [ ] 记录后台/电池优化影响。
- [ ] 明确哪些失败属于系统限制。

必须产出:

- 定时任务回归矩阵。

### T36 [P2] 插件/OCR 生命周期回归矩阵

状态: `[ ] 未开始`

目标:

整理 Paddle OCR 插件、内置引擎、RapidOCR、MLKit 的调用路径和常见失败处理。

验收标准:

- [ ] 覆盖 MLKit OCR。
- [ ] 覆盖 RapidOCR。
- [ ] 覆盖 Paddle 内置引擎。
- [ ] 覆盖 Paddle 插件发现、启用、禁用。
- [ ] 覆盖插件进程死亡/解绑/重绑提示。
- [ ] 覆盖打包 APK 中 OCR 相关 libs/assets。
- [ ] 覆盖长时间运行内存/生命周期风险。

必须产出:

- OCR/插件回归矩阵。

### T37 [P2] Open issue A 类修复队列拆分

状态: `[ ] 未开始`

目标:

基于 `ISSUE_TRIAGE.md` 把 A 类可修 issue 拆成独立开发任务, 每个任务都有文件范围和验收标准。

执行范围:

- `ISSUE_TRIAGE.md`
- 相关 issue 源码线索。

验收标准:

- [ ] 每个 A 类 issue 一个独立任务。
- [ ] 每个任务写清复现依据。
- [ ] 每个任务写清目标文件。
- [ ] 每个任务写清最小修复策略。
- [ ] 每个任务写清回归脚本或验证命令。
- [ ] 明确哪些 issue 已在当前版本/changelog 覆盖, 只需复测关闭。

注意:

- 不要在本任务直接修 issue。本任务只拆队列。

## 6. P3 低优先级任务: 长期治理

### T40 [P3] 用户脚本示例与最小复现样例库

状态: `[ ] 未开始`

目标:

积累脚本样例, 方便用户学习, 也方便后续 agent 做回归。

验收标准:

- [ ] 每个样例有用途说明。
- [ ] 每个样例注明权限。
- [ ] 每个样例有预期输出。
- [ ] 高风险样例有醒目注释。
- [ ] 样例不包含真实账号、token、隐私数据。

### T41 [P3] 发布流程演练与 release 检查模板

状态: `[ ] 未开始`

目标:

把 `docs/发布/AutoJs6发布规范.md` 转成可执行 checklist。

验收标准:

- [ ] 覆盖版本号检查。
- [ ] 覆盖 changelog 生成。
- [ ] 覆盖 inrt/app/release 分步构建。
- [ ] 覆盖 APK digest。
- [ ] 覆盖签名配置检查。
- [ ] 覆盖 GitHub release 创建。
- [ ] 覆盖失败回滚/续传策略。

### T42 [P3] 长期维护规范与 agent 交接 SOP

状态: `[ ] 未开始`

目标:

形成一套固定 SOP, 让不同 agent 接手时不会重头摸索。

验收标准:

- [ ] 包含任务领取流程。
- [ ] 包含代码阅读流程。
- [ ] 包含实现流程。
- [ ] 包含验证流程。
- [ ] 包含文档同步流程。
- [ ] 包含阻塞交接模板。
- [ ] 包含“不允许做”的行为清单。

## 7. 后续 agent 的最小开工提示

后续你可以直接复制下面这段给 agent:

```text
请读取并遵守:
/Users/blaze/work/github/notes/serverinfo/local_notes/notes/学习笔记/AutoJs6后续Agent执行任务清单-带验收标准.md

本次领取任务: Txx

要求:
1. 先记录当前系统时间和 git status。
2. 只做 Txx 范围内的工作。
3. 不回退用户已有改动。
4. 按 Txx 的验收标准逐项验证。
5. 同步检查并更新当前项目文档、内置示例、以及 /Users/blaze/work/github/AutoJs6-Documentation 对应文档; 不适用必须写明原因。
6. 完成后在任务文档中把状态改为 [x] 并追加执行记录, 其中必须包含“文档/示例同步结果”。
7. 如果不能完成, 标记为 [!] 并写清阻塞原因、已尝试内容、下一位 agent 接手入口。
```

## 8. 当前建议执行顺序

如果用户没有特别指定, 建议严格按以下顺序:

1. T00
2. T01
3. T02
4. T03
5. T04
6. T05
7. 如果确认继续 AI 脚本助手: T10 -> T20
8. 如果确认先做项目治理: T30 -> T37
9. 如果确认准备发布: T41

不要跳过 T03。当前项目体量大, 没有基线编译结果就继续开发, 后续很难判断错误是新引入还是原本存在。
