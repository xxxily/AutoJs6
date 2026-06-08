# AutoJs6 AI 脚本助手一期需求文档

> 状态: 需求细化稿
> 日期: 2026-06-07
> 范围: AutoJs6 应用内接入 OpenAI 兼容供应商, 支持自动编写与修改自动化脚本

## 1. 背景与目标

AutoJs6 当前定位是 Android 平台支持无障碍服务的 JavaScript 自动化工具, 已具备脚本 IDE、代码补全、脚本录制/回放、文件管理、项目配置、脚本运行、日志查看、打包 APK、VSCode 连接和本地/在线文档等能力。现阶段用户在应用内主要依靠手工编写脚本, 或通过 VSCode/外部传输代码进入项目。用户希望在应用内直接使用 AI 自动编写或修改自动化脚本, 并能在生成后编辑、保存、运行、查看日志、修复问题, 形成完整闭环。

本期目标不是做通用聊天机器人, 而是做一个面向 AutoJs6 脚本能力的 AI 脚本助手:

- 在编辑器和文件管理器中提供 AI 生成/修改入口。
- 仅支持 OpenAI `chat/completions` 兼容供应商。
- AI 生成代码必须基于 AutoJs6 当前实际支持的脚本 API、内置文档和项目上下文。
- 生成结果不能自动覆盖用户代码, 必须可预览、可比对、可撤销、可保存。
- 生成后能复用现有编辑器保存、运行、日志与异常定位能力, 闭环到“继续让 AI 修复”。

## 2. 本地项目事实依据

以下事实来自当前仓库, 后续实现应优先沿用这些现有路径:

- `README.md` 标明 AutoJs6 是 Android 平台 JavaScript 自动化工具, 支持 JavaScript IDE、无障碍自动化、选择器 API、录制回放、截图找色找图、E4X UI、APK 打包和 VSCode 连接。
- 编辑器由 `EditActivity` 加载, 在 `app/src/main/java/org/autojs/autojs/ui/edit/EditActivity.kt` 中创建 `EditorView` 和 `EditorMenu`, 并加载 `R.menu.menu_editor`。
- 编辑器顶部已有运行/撤销/重做/保存工具栏, 见 `app/src/main/res/layout/fragment_normal_toolbar.xml`。
- 编辑器菜单已有编辑、跳转、调试、打包、日志、更多等分组, 见 `app/src/main/res/menu/menu_editor.xml`。
- `EditorMenu` 在只读模式会隐藏编辑类能力, AI 入口也必须遵守只读状态, 见 `app/src/main/java/org/autojs/autojs/ui/edit/EditorMenu.java`。
- `EditorView` 已有“临时保存后运行”链路: `runAndSaveFileIfNeeded()` -> `saveToTmpFile()` -> `runTmpFile()` -> `run(...)`。
- `EditorView.save()` 已有事务式保存、写后校验、失败草稿和另存为兜底逻辑, AI 不应绕过该保存链路。
- 脚本执行入口在 `Scripts.run(...)` / `Scripts.runWithBroadcastSender(...)`, 会设置工作目录并广播执行结果, 见 `app/src/main/java/org/autojs/autojs/model/script/Scripts.kt`。
- 可执行脚本文件由 `ScriptFile` 识别 `.js` 与 `.auto`, 见 `app/src/main/java/org/autojs/autojs/model/script/ScriptFile.java`。
- 文件管理器可新建文件/项目, 点击可编辑文本文件, 见 `ExplorerFragment` 和 `ScriptOperations`。
- 项目运行通过 `ProjectLauncher` 读取 `project.json` 并执行主脚本, 默认主脚本为 `main.js`, 见 `ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME`。
- 项目配置 `ProjectConfig` 有 `name`、`versionName`、`versionCode`、`packageName`、`main`、`assets`、`launchConfig`、`build`、`icon` 等字段, 并有默认权限列表。
- 本地文档资源在 `app/src/main/assets-app/docs/*.html`, `DocsUtils` 会根据设置选择 `file:///android_asset/docs/` 或 `https://docs.autojs6.com`。
- 应用已有 `android.permission.INTERNET`, 且依赖中已有 OkHttp、Gson、Retrofit、RxJava/RxAndroid、Room、Preference 等基础能力。
- 当前已有 AndroidKeyStore AES/GCM 工具可参考, 见 `app/src/main/java/org/autojs/autojs/apkbuilder/keystore/AESUtils.kt`; AI API Key 存储不得使用明文 SharedPreferences。

## 3. 一期范围

### 3.1 包含

1. AI 供应商配置
   - 支持多个 OpenAI 兼容供应商配置。
   - 每个配置包含名称、Base URL、API Key、模型名、可选组织/项目 Header、自定义 Header、超时、是否启用流式输出、是否启用结构化输出、是否使用兼容降级。
   - 支持配置测试连接。
   - 支持启用/停用、设为默认、复制、删除。

2. 编辑器内 AI 能力
   - 解释当前脚本或选中代码。
   - 根据自然语言生成新脚本。
   - 修改当前脚本或选中代码。
   - 修复运行错误, 输入包括当前脚本、最近一次异常、行列号、日志片段。
   - 生成代码前允许用户选择任务类型和上下文范围。
   - 生成结果以预览/差异方式展示, 用户确认后才应用到编辑器。

3. 文件管理器内 AI 能力
   - 在当前目录创建 AI 生成的新 `.js` 脚本。
   - 在当前目录创建简单脚本项目: `project.json` + `main.js`。
   - 生成后可立即打开编辑器。

4. 能力约束与文档检索
   - 构建 AutoJs6 脚本能力索引, 来源至少包括 `app/src/main/assets-app/docs/*.html`。
   - Prompt 必须附带与需求相关的文档片段和 API 名称, 禁止让模型凭通用 Auto.js/Android 经验自由发挥。
   - 生成结果必须声明用到的 AutoJs6 API 和对应文档来源。
   - 对文档索引中不存在的 API, 默认视为不可用, 除非它来自当前项目源代码明确暴露的运行时 API。

5. 生成结果校验
   - 检查输出是否为可解析的结构化结果。
   - 检查是否包含完整代码或补丁。
   - 检查是否使用明显不存在的 AutoJs6 API。
   - 检查高风险能力, 如 Root、Shizuku、Shell、短信、联系人、安装/删除包、悬浮窗、截图、无障碍操作。
   - 高风险能力需要显式提示用户并二次确认。

6. 闭环
   - AI 生成 -> 预览差异 -> 应用到编辑器 -> 使用现有保存/运行 -> 查看日志/错误 -> 一键带错误上下文继续修复。
   - 不直接自动保存到磁盘, 除非用户在文件创建流程中确认创建。
   - 不直接自动运行 AI 生成脚本, 运行仍由用户触发现有运行按钮。

### 3.2 不包含

- 不做多模态输入、截图理解或屏幕状态自动感知。
- 不做模型主动控制手机或无确认执行自动化。
- 不做非 OpenAI 兼容协议, 如 Anthropic 原生、Gemini 原生、Ollama 原生特殊接口。
- 不做云端账号体系、团队同步、用量账单聚合。
- 不做插件市场中的 AI 插件分发。
- 不训练或微调模型。
- 不自动提交、下载、分享用户脚本。

## 4. 用户角色与典型场景

1. 新手用户
   - 输入“帮我写一个打开微信并点击搜索框的脚本”。
   - 系统检索 `app`、`auto`、`uiSelector`、`automator` 等相关文档。
   - AI 生成脚本并说明需要无障碍服务。
   - 用户预览后应用, 点击运行, 如报错可一键修复。

2. 熟练脚本作者
   - 选中一段代码, 让 AI 改成更稳定的控件选择器实现。
   - AI 只能改选中范围, 并保留未选中代码。
   - 用户查看 diff 后接受或拒绝。

3. 项目作者
   - 在文件管理器中选择“AI 新建项目”。
   - 输入项目描述, AI 生成 `project.json` 和 `main.js`。
   - 项目配置必须符合 `ProjectConfig` 支持字段, 主脚本文件名与 `main` 字段一致。

4. 调试用户
   - 运行脚本失败后, 从日志面板点击“AI 修复”。
   - 系统带入异常消息、行号、列号、脚本片段和相关 docs。
   - AI 生成最小修改, 用户确认后应用。

## 5. 入口与交互设计

### 5.1 设置入口

在设置页新增“AI 脚本助手”配置项, 建议放在 `fragment_preferences.xml` 的“配置”或“扩展性”分组附近。

配置页面需要支持:

- 开关: 启用 AI 脚本助手。
- 默认供应商。
- 供应商列表管理。
- 默认模型。
- 默认上下文策略。
- 是否默认启用流式输出。
- 是否默认要求结构化 JSON 输出。
- 最大上下文字符数。
- 最大日志片段字符数。
- 是否允许发送当前文件完整内容。
- 是否允许发送项目结构。
- 是否允许发送最近运行日志。
- 高风险 API 二次确认开关。
- 清空会话上下文和缓存。

API Key 输入框必须使用密码输入形态, 展示时只显示尾号, 复制/导出配置时默认不包含 API Key。

### 5.2 编辑器入口

在编辑器菜单中新增“AI 助手”分组或放入“更多”下:

- 生成脚本
- 修改选中代码
- 修复运行错误
- 解释代码
- 生成/更新注释
- 按需求重构
- 打开 AI 会话

只读模式下:

- 允许“解释代码”。
- 禁止“修改”“应用补丁”“生成后插入”等写操作。
- 入口显示状态应与 `EditorMenu.prepareOptionsMenu` 的只读逻辑一致。

编辑器工具栏是否新增 AI 图标属于实现阶段 UI 取舍。一期至少应在菜单中可达, 避免挤压现有运行/撤销/重做/保存按钮。

### 5.3 文件管理器入口

在文件管理器浮动按钮或目录菜单中新增:

- AI 新建脚本
- AI 新建项目

生成新文件时复用 `ScriptOperations.createScriptFile(...)` 的创建与通知机制。生成项目时必须复用或兼容 `ProjectConfigActivity`/`ProjectTemplate` 的项目结构约束。

### 5.4 结果预览

生成结果必须进入预览状态:

- 对当前文件修改: 显示原始内容和修改后内容的差异摘要, 可查看完整代码。
- 对选中代码修改: 仅替换选中范围, 明确显示替换前后。
- 对新脚本: 显示文件名、路径、完整代码。
- 对新项目: 显示将创建的文件树和每个文件内容。

用户操作:

- 应用
- 复制
- 重新生成
- 继续要求修改
- 拒绝

应用后:

- 编辑器内容变为未保存状态。
- 走现有撤销/重做栈。
- 保存仍由用户点击保存, 走现有事务式保存。

## 6. AI 供应商协议

### 6.1 兼容目标

一期以 OpenAI `POST /v1/chat/completions` 兼容协议为唯一目标。

官方 OpenAPI 显示该接口核心请求包含:

- `model`
- `messages`
- `stream`
- `response_format`
- `tools`
- `tool_choice`
- `temperature`
- `top_p`
- `max_completion_tokens` 或兼容供应商常见的 `max_tokens`

响应核心结构:

- 非流式: `choices[0].message.content`
- 工具调用: `choices[0].message.tool_calls`
- 流式: SSE chunk 中的 `choices[0].delta.content`
- 错误: JSON 错误体或 HTTP 错误码

### 6.2 最小兼容请求

所有供应商至少支持:

```json
{
  "model": "configured-model",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "..." }
  ],
  "temperature": 0.2,
  "stream": false
}
```

说明:

- 为兼容第三方, 默认使用 `system` 而不是仅使用较新的 `developer` role。
- 默认 `temperature` 低一些, 以减少脚本生成随机性。
- 一期可以不强制启用工具调用, 但内部结果应优先要求 JSON 输出。

### 6.3 结构化输出策略

优先策略:

- 供应商支持 `response_format: { "type": "json_schema", ... }` 时, 使用 JSON Schema。
- 不支持时降级为 `response_format: { "type": "json_object" }`。
- 再不支持时, 在 prompt 中强制要求返回纯 JSON, 并由客户端容错解析。

AI 结果统一解析为:

```json
{
  "intent": "create_script | modify_selection | modify_file | fix_error | explain",
  "summary": "本次改动摘要",
  "files": [
    {
      "path": "main.js",
      "language": "javascript",
      "operation": "create | replace_all | replace_selection | patch",
      "content": "..."
    }
  ],
  "usedApis": [
    {
      "name": "text",
      "doc": "uiSelectorType.html",
      "reason": "用于通过文本查找控件"
    }
  ],
  "requirements": [
    "需要无障碍服务"
  ],
  "risks": [
    "会点击屏幕控件"
  ],
  "verificationSteps": [
    "保存后点击运行",
    "若未找到控件, 打开布局分析确认文本"
  ],
  "notes": "..."
}
```

### 6.4 流式输出

流式输出用于 UI 反馈, 但最终必须拼接成完整结果并通过同一解析/校验流程。

流式状态:

- 等待连接
- 正在生成
- 正在校验
- 可预览
- 失败
- 已取消

用户必须可以取消请求。取消后不得应用部分代码。

### 6.5 错误处理

需要区分:

- 未配置供应商/API Key
- URL 非法
- 网络不可用
- TLS/证书错误
- 401/403 鉴权失败
- 404 Base URL 或模型错误
- 429 限流
- 5xx 供应商错误
- 响应不是合法 JSON
- JSON 合法但缺少 `choices`
- 模型拒绝生成
- 生成内容未通过本地校验

错误信息不得展示完整 API Key、Authorization Header 或完整请求体。

## 7. Prompt 与上下文策略

### 7.1 必须包含的系统约束

Prompt 必须明确:

- 你正在为 AutoJs6 生成 JavaScript 自动化脚本。
- 只能使用提供的 AutoJs6 文档/API 清单中存在的能力。
- 不确定 API 是否存在时必须说明不确定, 不得编造。
- 默认生成可直接在 AutoJs6 中运行的脚本。
- 如果脚本依赖无障碍、截图、悬浮窗、Root、Shizuku、Shell 等权限, 必须在结果中声明。
- 不得自动加入危险操作, 如发送短信、删除文件、安装/卸载应用、执行任意 shell、格式化存储等, 除非用户明确要求且结果中标记高风险。
- 输出必须符合客户端要求的 JSON 结构。

### 7.2 上下文组成

按任务类型动态组装:

1. 基础上下文
   - AutoJs6 版本/构建信息。
   - Rhino/JavaScript 能力摘要。
   - 当前任务类型。
   - 当前语言。

2. 文件上下文
   - 当前文件名、路径、扩展名。
   - 当前工作目录。
   - 当前文件全文或选中片段。
   - 光标位置附近片段。
   - 文件编码/换行信息可作为附加信息, 不要求传给模型。

3. 项目上下文
   - `project.json` 摘要。
   - 主脚本文件名。
   - 同目录文件树摘要。
   - 仅在用户授权时包含其他文件内容。

4. 运行上下文
   - 最近一次异常消息。
   - Rhino 行号/列号。
   - 控制台日志片段。
   - 运行入口和工作目录。

5. 文档上下文
   - 从本地 docs 检索出的相关 API 页面片段。
   - API 名称、页面文件名、方法签名、简短说明、示例。
   - 对高风险 API 附加权限/风险说明。

### 7.3 上下文限制

- 默认不发送整个项目。
- 默认只发送当前文件或选中片段。
- 大文件需要裁剪, 优先保留选中区域、光标附近、文件头部执行模式声明、相关函数定义。
- 日志默认限制在最近 N 行或最大字符数。
- 用户可在请求前看到“将发送的上下文摘要”。

## 8. AutoJs6 能力索引

### 8.1 索引来源

一期必须从本地资源构建能力索引:

- `app/src/main/assets-app/docs/*.html`
- `app/src/main/assets-app/docs/sidebar.html`
- 可选补充: `app/src/main/java/org/autojs/autojs/runtime/api/**`
- 可选补充: `app/src/main/java/org/autojs/autojs/runtime/api/augment/**`
- 可选补充: 编辑器自动补全数据, 如 `org.autojs.autojs.model.autocomplete`

索引不应依赖在线网络。在线文档只能作为可选更新源, 不作为生成时唯一依据。

### 8.2 索引内容

每个能力条目至少包含:

- API 名称, 如 `click`, `text`, `images.captureScreen`
- 所属模块, 如 `automator`, `uiSelectorType`, `images`
- 文档文件名, 如 `automator.html`
- 签名或调用形式
- 简短说明
- 示例代码
- 所需权限或运行前置条件
- 风险等级
- 废弃/兼容说明

### 8.3 检索策略

根据用户需求和当前代码:

- 先做关键词检索。
- 再按模块同义词扩展, 如“找控件”关联 `selector`/`UiSelector`/`UiObject`。
- 优先返回本地 docs 明确记录的 API。
- 对多个相似 API, prompt 中要求模型说明选择理由。

### 8.4 校验策略

生成后做轻量静态检查:

- 提取疑似全局函数、模块方法、选择器方法。
- 与能力索引比对。
- 对未命中项给出警告, 阻止一键应用或要求用户确认。
- 对 Android/Java 反射调用、`runtime.loadJar`、`shell`、`shizuku`、`root` 等能力提高风险等级。

## 9. 安全与隐私

### 9.1 密钥存储

- API Key 必须加密存储。
- 禁止明文写入默认 SharedPreferences、日志、导出文件、崩溃报告。
- 可参考现有 AndroidKeyStore AES/GCM 方案, 但 AI Key 应使用独立 alias, 避免与 APK 签名 KeyStore 存储耦合。
- 删除供应商时必须清除对应密钥。

### 9.2 数据发送控制

发送前应展示摘要:

- 供应商名称和 Base URL。
- 模型名。
- 是否包含当前文件全文。
- 是否包含项目结构。
- 是否包含日志。
- 是否包含其他文件内容。

用户可取消或调整范围。

### 9.3 日志脱敏

请求日志和错误日志不得包含:

- API Key
- Authorization Header
- 自定义敏感 Header
- 完整用户脚本, 除非用户主动导出诊断包
- 手机号、短信、联系人等明显敏感数据

### 9.4 高风险脚本

以下行为需要风险提示:

- 无障碍点击/滑动/长按。
- 截图和图像识别。
- 悬浮窗。
- Root/Shizuku/ADB 特权。
- Shell 命令。
- 文件删除/覆盖/批量移动。
- 应用安装/卸载。
- 短信、电话、联系人、日历、定位、录音、摄像头。
- 网络上传本地文件。

高风险结果默认不能自动运行, 只能由用户手动保存并运行。

## 10. 功能需求明细

### 10.1 供应商管理

需求:

- 新增供应商配置页面。
- 支持新增、编辑、删除、测试、设为默认。
- Base URL 支持:
  - `https://api.openai.com/v1`
  - 第三方兼容地址, 如 `https://example.com/v1`
- 请求路径统一拼接 `/chat/completions`, 需正确处理 Base URL 尾部 `/`。
- 自定义 Header 支持多条键值对。
- Header key/value 输入必须校验非空, 敏感 Header 展示脱敏。
- API Key 保存后不可明文回显。

验收:

- 未配置时, 编辑器 AI 入口提示先配置。
- 配置错误 API Key 时, 测试连接显示鉴权失败。
- Base URL 错误时, 显示连接/404 错误。
- 删除默认供应商后, 默认状态自动清空或切换到下一个可用配置。

### 10.2 AI 生成新脚本

入口:

- 编辑器: AI 助手 -> 生成脚本。
- 文件管理器: AI 新建脚本。

输入:

- 自然语言需求。
- 可选脚本名称。
- 可选工作目录。
- 可选目标应用包名/页面描述。
- 可选权限能力勾选。

输出:

- 一个 `.js` 文件内容。
- API 使用说明。
- 前置条件。
- 运行验证步骤。

验收:

- 生成结果不应包含 docs 中不存在的 AutoJs6 API。
- 用户确认后, 文件管理器创建文件并刷新列表。
- 编辑器入口生成时, 默认插入当前编辑器或新建临时文档, 不直接保存覆盖已有文件。

### 10.3 AI 修改当前脚本

入口:

- 编辑器选中代码后: AI 助手 -> 修改选中代码。
- 未选中时: AI 助手 -> 修改当前文件。

输入:

- 用户修改目标。
- 当前选区或全文。
- 相关 docs 片段。

输出:

- 替换选区内容或整文件内容。
- 差异摘要。
- 用到的 API。

验收:

- 有选区时只替换选区。
- 无选区改全文时必须显示完整差异预览。
- 应用后保存按钮变为可用。
- 撤销按钮可撤回 AI 应用。

### 10.4 AI 修复运行错误

入口:

- 编辑器 AI 助手 -> 修复运行错误。
- 日志面板或运行失败提示处可进入。

输入:

- 当前文件内容。
- 最近一次异常消息。
- Rhino 行号/列号。
- 相关日志。
- 出错行附近片段。

输出:

- 最小修复。
- 错误原因解释。
- 验证步骤。

验收:

- 没有运行错误时, 提示先运行或手动粘贴错误。
- 行号有效时, 默认聚焦出错区域。
- 修复结果必须进入差异预览, 不自动覆盖。

### 10.5 AI 解释代码

入口:

- 编辑器 AI 助手 -> 解释代码。

输入:

- 选中代码或当前文件摘要。

输出:

- 解释文本, 不修改编辑器。
- 风险和权限说明。
- 可能的改进建议。

验收:

- 只读文件可使用解释功能。
- 不需要显示差异。
- 不改变保存状态。

### 10.6 AI 新建项目

入口:

- 文件管理器当前目录 -> AI 新建项目。

输入:

- 项目名称。
- 包名建议。
- 需求描述。
- 主脚本文件名, 默认 `main.js`。

输出:

- 项目目录。
- `project.json`。
- `main.js`。
- 可选 `README.md` 或说明文件一期不强制。

约束:

- `project.json` 字段必须符合 `ProjectConfig` 支持结构。
- `main` 字段必须指向存在的主脚本。
- 默认权限不得超出必要范围; 若使用截图、通知、悬浮窗、Root/Shizuku 等能力, 必须声明原因。

验收:

- 创建后文件管理器能识别为项目。
- 项目运行时 `ProjectLauncher` 能找到主脚本。

## 11. 非功能需求

### 11.1 性能

- 打开编辑器不应因 AI 初始化阻塞。
- 能力索引应懒加载或后台加载。
- 本地 docs 索引首次构建可异步, 需要进度与失败兜底。
- 单次请求默认超时 60 秒, 用户可配置。
- 大文件上下文裁剪必须在线程池或后台调度执行。

### 11.2 稳定性

- 网络请求可取消。
- Activity/Fragment 销毁后不得回调已销毁 UI。
- 旋转屏幕或进入后台时, 不应丢失已生成但未应用的结果。
- 应用结果前再次检查编辑器内容是否已变化, 防止覆盖用户并发编辑。

### 11.3 可维护性

建议新增模块边界:

- `org.autojs.autojs.ai.config`: 供应商配置、密钥存储。
- `org.autojs.autojs.ai.client`: OpenAI 兼容 HTTP 客户端、SSE 解析。
- `org.autojs.autojs.ai.context`: 上下文裁剪与组装。
- `org.autojs.autojs.ai.docs`: 本地文档索引、检索、API 校验。
- `org.autojs.autojs.ai.prompt`: Prompt 模板。
- `org.autojs.autojs.ai.result`: 结构化结果、差异、风险。
- `org.autojs.autojs.ui.ai`: 配置页、请求弹窗、结果预览。

不得把供应商请求、Prompt 拼接、UI 应用代码全部塞进 `EditorMenu` 或 `EditorView`。

### 11.4 国际化

- 一期至少补齐默认英文 `values/strings.xml` 和中文 `values-zh/strings.xml`。
- AI 输出语言默认跟随应用语言。
- 用户可在请求中指定输出语言。

## 12. 数据模型建议

### 12.1 AiProviderConfig

字段:

- `id`
- `name`
- `baseUrl`
- `model`
- `apiKeyRef`
- `enabled`
- `isDefault`
- `streamEnabled`
- `structuredOutputMode`: `json_schema | json_object | prompt_json`
- `timeoutMillis`
- `temperature`
- `maxOutputTokens`
- `customHeaders`
- `compatibilityFlags`
- `createdAt`
- `updatedAt`

### 12.2 AiRequestRecord

用于本地历史, 不保存完整敏感上下文:

- `id`
- `providerId`
- `model`
- `taskType`
- `targetFilePath`
- `createdAt`
- `status`
- `promptSummary`
- `resultSummary`
- `usedApis`
- `errorCode`
- `errorMessageSanitized`

历史记录默认不保存完整代码和完整 prompt。若后续需要调试开关, 必须单独提示用户。

### 12.3 AiCapabilityEntry

字段:

- `id`
- `name`
- `qualifiedName`
- `module`
- `docFile`
- `signature`
- `description`
- `example`
- `permissions`
- `riskLevel`
- `aliases`

## 13. 验收标准

### 13.1 基础配置

- 用户能配置 OpenAI 官方或兼容供应商。
- API Key 加密保存, UI 不明文回显。
- 测试连接能区分成功、鉴权失败、模型不存在、网络失败。

### 13.2 生成闭环

- 在文件管理器中输入需求, 能生成 `.js` 文件并打开编辑器。
- 在编辑器中输入需求, 能生成代码预览并应用到编辑器。
- 应用后保存按钮可用, 点击保存走现有保存逻辑。
- 点击运行走现有脚本执行逻辑。
- 运行失败后能带错误上下文再次请求 AI 修复。

### 13.3 文档约束

- 对“使用不存在的 API”类需求, AI 结果应提示当前文档未找到对应能力, 而不是编造代码。
- 生成结果中必须列出 `usedApis`。
- 本地校验能标记至少以下不存在/高风险调用:
  - 未知全局函数。
  - 未知模块方法。
  - `shell`/`root`/`shizuku`。
  - 文件删除或覆盖。

### 13.4 权限与风险

- 生成需要无障碍服务的脚本时显示前置条件。
- 生成需要截图权限的脚本时显示前置条件。
- 生成含 Shell/Root/Shizuku 的脚本时必须二次确认。

### 13.5 兼容性

- API 24+ 不引入不兼容依赖。
- 不破坏现有编辑器保存、撤销、运行、日志、版本历史功能。
- 只读模式不允许 AI 应用修改。

## 14. 测试建议

### 14.1 单元测试

- Base URL 拼接。
- OpenAI 兼容请求 JSON 序列化。
- 非流式响应解析。
- SSE 流式 chunk 解析。
- 错误响应解析和脱敏。
- AI 结果 JSON 解析。
- docs 能力索引解析。
- 未知 API 检测。
- 高风险 API 检测。
- 上下文裁剪。

### 14.2 集成测试

- 使用 MockWebServer 模拟:
  - 成功非流式响应。
  - 成功流式响应。
  - 401/403/404/429/500。
  - 非 JSON 响应。
  - JSON 缺少 `choices`。
- 编辑器中应用 AI 修改后保存状态变化。
- 选区替换只影响选区。
- 文件管理器 AI 新建脚本后列表刷新。

### 14.3 手工验收

- 官方 OpenAI Base URL。
- 至少一个第三方 OpenAI 兼容 Base URL。
- 断网。
- API Key 错误。
- 模型名错误。
- 大文件。
- 只读文件。
- 横竖屏切换。
- 后台返回。

## 15. 实施顺序建议

1. 建立 AI 配置与密钥存储。
2. 实现 OpenAI 兼容客户端和 Mock 测试。
3. 实现本地 docs 能力索引 MVP。
4. 实现 Prompt 组装和结构化结果解析。
5. 实现编辑器入口: 解释代码、生成脚本。
6. 实现差异预览和应用到编辑器。
7. 实现修改选区/当前文件。
8. 接入运行错误上下文修复。
9. 实现文件管理器 AI 新建脚本/项目。
10. 完成风险校验、脱敏、文案、测试与手工验收。

## 16. 关键风险与决策

### 16.1 是否引入新网络 SDK

建议不引入 OpenAI 官方 SDK。当前项目已有 OkHttp/Gson/Retrofit, 且目标是第三方 OpenAI 兼容供应商, 直接实现轻量 HTTP 客户端更可控。

### 16.2 是否默认使用最新 Responses API

不建议。一期目标是 OpenAI 兼容供应商, 市面兼容面最稳定的是 `/v1/chat/completions`。可以在内部抽象中保留未来扩展 `responses` 的空间。

### 16.3 是否允许 AI 自动运行脚本

不建议。一期必须由用户确认保存和手动运行。自动化脚本可能点击屏幕、读取数据、执行 Shell 或操作文件, 自动运行风险过高。

### 16.4 docs 索引不完整怎么办

默认保守处理。索引中没有的 API 不让模型当作可用能力。后续可以通过解析 runtime API 源代码补充索引, 但仍需标记来源是源码而非用户文档。

## 17. 后续可扩展方向

- 支持截图/布局分析结果作为 AI 上下文。
- 支持基于 Layout Inspector 节点生成选择器。
- 支持本地模型或非 OpenAI 原生协议。
- 支持更完整的项目级多文件修改。
- 支持 AI 生成测试脚本或回放脚本。
- 支持文档索引增量更新与版本标记。
- 支持用户自定义 Prompt 模板。
