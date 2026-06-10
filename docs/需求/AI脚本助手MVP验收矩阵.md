# AutoJs6 AI 脚本助手 MVP 验收矩阵

> 日期: 2026-06-09
> 来源: `docs/需求/AI脚本助手一期需求文档.md`
> 范围: T10-T20, OpenAI `chat/completions` 兼容供应商, 编辑器与文件管理器内 AI 辅助脚本流程

## 一期边界

必须包含:

- 用户入口: 设置页、编辑器菜单、文件管理器浮动按钮。
- 任务类型: 生成脚本、修改选区、修改全文、解释代码、修复运行错误、AI 新建脚本、AI 新建项目。
- 供应商协议: OpenAI 兼容 `POST /v1/chat/completions`。
- 结果流程: 生成 -> 预览/差异摘要 -> 用户确认 -> 应用到编辑器或创建文件。
- 安全边界: API Key 加密保存, 错误与日志脱敏, 高风险代码应用前二次确认。

明确不做:

- 不做多模态输入、截图理解或屏幕状态自动感知。
- 不做模型主动控制手机, 不自动运行 AI 生成脚本。
- 不做 Anthropic/Gemini/Ollama 原生协议。
- 不做云端账号体系、账单聚合、插件市场分发或模型微调。

## 二期 Copilot 增强边界

T58 / A12 在一期基础上补齐应用前校验、最小补丁、能力图谱和隐私上下文:

- 能力预检复用 `AiCapabilityIndex` 与 `CapabilityRegistry`, AI 结果出现未知 API 时阻断应用, 高风险能力未写入 `requirements` 或 `risks` 时阻断应用。
- 修改当前文件优先要求 `replace_selection` 或 unified diff `patch`, `AiPatchApplicator` 在应用前校验编辑器内容与发送请求时的快照一致, 不再允许漂移后继续强行应用。
- Copilot 闭环模型覆盖 `generate -> static_check -> pre_run_check -> user_confirm -> run -> capture_failure -> fix`, 运行失败修复上下文会带入异常、日志和能力状态摘要。
- UI 快照、截图摘要、OCR 摘要和剪贴板作为可选上下文源建模, 设置页默认关闭; 发送前摘要会列出包含、排除和需要确认的上下文源。
- `AiPrivacyRedactor` 统一脱敏 API Key、Bearer token、token/query 参数和请求错误。

## 功能验收矩阵

| 功能点 | 主要文件 | 验收方式 | 当前证据 |
| --- | --- | --- | --- |
| 设置页入口 | `app/src/main/res/xml/fragment_preferences.xml`, `app/src/main/java/org/autojs/autojs/ui/settings/PreferencesFragment.kt`, `AiSettingsActivity.kt` | 设置页可进入 AI 脚本助手配置页 | `key_ai_script_assistant` Preference 启动 `AiSettingsActivity` |
| 多供应商配置 | `AiProviderConfig.kt`, `AiConfigRepository.kt`, `AiSettingsActivity.kt`, `activity_ai_settings.xml` | 新增/编辑/启停/默认/复制/删除供应商 | Provider 列表持久化于 `ai_script_assistant`; 新增、编辑、删除、默认、复制均有 UI 动作 |
| 配置输入校验 | `AiSettingsActivity.kt`, `strings.xml`, `values-zh/strings.xml` | 空 Base URL、空模型、非法 URL、非法 timeout、非法结构化模式被阻止 | 保存前执行 `ProviderForm.validationError()` |
| API Key 安全存储 | `AiKeyStore.kt`, `AiConfigRepository.kt`, `AiSettingsActivity.kt` | Key 使用 AndroidKeyStore AES/GCM; UI 掩码; 删除供应商删除 key; 复制供应商不复制 key | `AiKeyStore` 使用 `AndroidKeyStore` + `AES/GCM/NoPadding`; `duplicateProvider()` 不传递 key |
| OpenAI 兼容请求 | `OpenAiCompatibleClient.kt` | URL、Header、请求体、`response_format`、`max_completion_tokens`/`max_tokens`、错误分类正确 | `buildChatCompletionsUrl()`, `applyHeaders()`, `buildRequestBody()`, `classifyHttpError()` |
| 流式输出 | `OpenAiCompatibleClient.kt`, `AiProviderConfig.kt` | SSE `data:` 响应可解析或禁用说明 | `parseStream()` 解析 SSE; 当前 UI 等待完整响应后展示 |
| 文档检索 | `AiCapabilityIndex.kt`, `app/src/main/assets-app/indices/all.json`, `app/src/main/assets-app/docs/*.html` | 需求检索返回 API 名称、片段、来源路径, 且能力索引 URL 可校验 | `AiCapabilityIndex.get()` 合并 `indices/all.json` 元数据和 assets docs HTML, `search()` 返回 `docFile`/`signature`/`description`; `AiCapabilityIndexConsistencyTest` 校验空 URL、坏 docs 文件、坏 anchor、重复 key、旧 `widgets-based-automation.html` 和核心运行时注入对象覆盖 |
| Prompt 约束 | `AiPromptBuilder.kt` | Prompt 包含任务类型、文件/选区、工作目录、项目结构、最近错误、相关 docs 和输出规则 | `buildMessages()` 组装 system/user prompt, 输出 schema 包含 `intent/summary/files/usedApis/requirements/risks/warnings` |
| 结构化解析 | `AiGenerationResult.kt` | 非 JSON 或缺失文件时不应用结果 | `AiResultParser.parse()` 与 `AiResultValidator.validate()` 先解析校验, UI 只在预览确认后应用 |
| Copilot 能力预检 | `AiCapabilityIndex.kt`, `CapabilityRegistry.kt`, `AiCopilotPreflight.kt`, `AiGenerationResult.kt` | 生成结果使用不存在 API 时阻断应用; 高风险动作未声明时阻断应用 | `AiResultValidator.blocksApply`, `AiCopilotPreflight.analyze()` 合并 API 索引、权限、风险和能力状态 |
| 高风险二次确认 | `AiCapabilityIndex.kt`, `AiGenerationResult.kt`, `AiAssistantDialogs.kt` | Root/Shizuku/Shell/安装卸载/短信联系人电话/截图录屏/悬浮窗/无障碍/外部存储/网络上传/系统设置触发确认 | `RISK_RULES` 与 `CapabilityRegistry` 覆盖高风险项; Shizuku 强停/授权/设置修改优先识别 `shizuku.app.*` / `shizuku.settings.*` 结构化 API; 阻断项只允许关闭, 非阻断风险仍走二次确认 |
| 编辑器入口 | `menu_editor.xml`, `EditorMenu.java`, `AiAssistantDialogs.kt` | 编辑器菜单提供 AI 分组和任务入口 | `action_ai_script_assistant` 子菜单包含生成/修改/修复/注释/重构/解释 |
| 只读模式 | `EditorMenu.java`, `AiAssistantDialogs.kt` | 只读文件允许解释, 禁止写操作 | `prepareOptionsMenu()` 隐藏写入口, `showEditorTaskInternal()` 二次拦截 |
| 编辑器应用 | `EditorView.kt`, `AiAssistantDialogs.kt`, `AiPatchApplicator.kt` | 修改选区只替换选区; unified diff 应用前校验上下文未漂移; 修改全文进入未保存状态; 保存仍走 `EditorView.save()` | `AiPatchApplicator.apply()` 支持 `replace_selection`、`patch`、`replace_all`, 漂移或 hunk 不匹配则不改编辑器 |
| 不自动运行 | `AiAssistantDialogs.kt`, `EditorView.kt` | AI 结果应用后不调用运行入口 | 预览动作只有应用/复制/重新生成/继续修改; 运行仍由现有运行按钮触发 |
| 运行错误闭环 | `EditorView.kt`, `AiConfigRepository.kt`, `AiPromptBuilder.kt`, `AiCopilotLoop.kt` | 运行失败记录错误消息/行列号并带入修复 prompt; 修复上下文包含相关能力状态 | `saveLastRunError()` 保存错误; `FIX_ERROR` task 带入 error line/column/log snippet 和 `CapabilityRegistry.check()` 摘要 |
| 隐私上下文策略 | `AiCopilotPrivacy.kt`, `AiSettingsActivity.kt`, `activity_ai_settings.xml` | 发送前摘要可见; 文件/日志/剪贴板/截图/UI 快照/OCR 可排除; 敏感可选上下文默认关闭且需确认 | 设置项持久化 `allowClipboardContext`, `allowUiSnapshotContext`, `allowScreenCaptureContext`, `allowOcrContext`; `AiPrivacyRedactor` 脱敏 |
| 文件管理器入口 | `FloatingActionMenu.java`, `ExplorerFragment.kt`, `ScriptOperations.java`, `AiAssistantDialogs.kt` | 文件页可 AI 新建脚本和项目 | FAB pos 4/5 分别触发 `CREATE_SCRIPT`/`CREATE_PROJECT` |
| AI 新建脚本 | `ScriptOperations.java`, `AiAssistantDialogs.kt` | 默认 `.js`, 文件名清理, 不覆盖已存在文件, 创建后打开编辑器 | `createAiGeneratedScript()` 清理名称并复用 `createScriptFile(..., editable=true)` |
| AI 新建项目 | `ScriptOperations.java`, `AiAssistantDialogs.kt` | 生成 `project.json` + `main.js`; 主脚本一致; 失败回滚 | `createAiGeneratedProject()` 写入文件树, 失败时 `deleteRecursively(projectDir)`; 默认 `project.json` 含必填字段 |
| 用户文档与示例 | `README.md`, `docs/需求/AI脚本助手MVP验收矩阵.md`, `app/src/main/assets-app/sample/AI脚本助手/AI生成脚本安全预览示例.js`, 外部文档仓库 | 用户可找到入口、安全边界和示例 | 本阶段同步更新 |

## 验收样例

| 样例 | 预期 |
| --- | --- |
| 解释代码 | 只展示解释文本, 不显示“应用”写入动作 |
| 生成新脚本 | 返回 `.js` 文件, 进入预览, 用户确认后才写入 |
| 修改选区 | `operation=replace_selection`, 只替换选中范围 |
| 非法 JSON | 显示 AI 请求失败或结构化解析错误, 不改编辑器/磁盘 |
| `shell("pm uninstall ...")` | 预览中显示 Shell/安装卸载风险, 应用前二次确认 |
| `shizuku.settings.secure.put(...)` 修改设置 | 显示 Shizuku/系统设置风险, 应用前二次确认; AI 应优先生成结构化 API 而非 `settings put` 字符串 |
| `auto.click()` | 显示无障碍操作风险, 应用前二次确认 |
| `images.captureScreen()` | 显示截图/图像识别风险, 应用前二次确认 |
| `missingApi()` | 作为未知 API 阻断应用, 只能关闭预览或重新生成 |
| AI 返回 `operation=patch` 且用户发送后手动编辑文件 | 上下文漂移, 补丁不应用 |
| 剪贴板/截图/OCR/UI 快照上下文 | 默认不发送; 开启后发送前摘要列出并由用户确认 |

## 当前验证限制

本阶段已使用 Homebrew JDK 17 和本机 Android SDK 执行 `:app:compileAppDebugKotlin` 并通过。由于没有可用的真实供应商 API Key, 也没有启动真机或模拟器 UI 会话, 本矩阵中的 UI 流程和联网请求结论以静态源码检查与编译验证为主; 真机点击流、持久化重启验证和真实 `chat/completions` 请求仍需在有设备与 Key 时补充复测。
