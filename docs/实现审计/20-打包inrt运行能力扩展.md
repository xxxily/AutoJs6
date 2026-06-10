# 打包 inrt 运行能力扩展

> 日期: 2026-06-10
> 对应任务: T65 / A14
> 范围: 打包预检、能力到 APK/inrt 的映射、构建诊断资产、inrt 首启/失败诊断、AI 新建项目默认清单

## 实现范围

- 新增 `ProjectBuildPreflight`, 在打包前静态检查 `sourcePath`、主脚本、项目 JS 文件、常见相对资源引用、`project.json assets`、能力声明、插件依赖和高风险 Android permission。
- `BuildActivity` 在 APK 输出备份前执行预检: error 阻断打包, warning 通过对话框确认后继续。
- `ApkBuilder` 支持接收预检报告, 构建时写入 `assets/project/build-diagnostics.json`, 并把单文件推断能力与能力映射权限写入 bundled `project.json`。
- `ProjectConfig` 新增 `pluginDependencies`, 兼容 `plugins`、`pluginDeps`、`requiredPlugins`, 并在 `setCapabilitySecurityFrom()` 中随能力策略复制。
- `InrtDiagnostics` 读取 bundled `project.json` 和 `project/build-diagnostics.json`, 导出包含 app、project、requiredInrtSettings、capabilityChecks、preflight、exception 的最小诊断 JSON。
- inrt 首次启动会检查 missing/requestable 能力, Toast 提示并打开设置页; 设置页显示构建诊断摘要, 日志页可复制完整诊断 JSON。
- AI 新建项目默认 `project.json` 包含 `capabilities`, `pluginDependencies` 和 `riskPolicy` 字段。

## 预检覆盖

- 主脚本: `sourcePath` 为空、不存在或主脚本缺失会生成 error。
- 资源引用: 扫描 `files.read/readBytes/open/exists`, `images.read`, `open`, `require` 中的 `./` / `../` 静态相对路径, 缺失为 error。
- `assets`: `project.json assets` 中声明但不存在的文件为 error。
- 能力声明: 脚本静态推断出的危险能力若未在项目 `capabilities` 声明, 项目打包为 error; 单文件打包为 warning 并自动补入 bundled 配置。
- 插件依赖: `plugins.load("...")` 或 `plugins("...")` 未在 `pluginDependencies` 声明会阻断。
- 权限提示: 能力映射出的 Android permissions 会写入配置; 未由能力清单解释的高风险 permission 会提示 warning。

## inrt 行为

- `build-diagnostics.json` 记录 `schemaVersion`、source/main、scriptFiles、resourceReferences、pluginCalls、declared/inferred/missing capabilities、mappedPermissions、foregroundServiceTypes、requiredInrtSettings、errors、warnings 和 project 摘要。
- inrt 设置页增加 “Build diagnostics” 偏好项, summary 展示项目名、包名、版本、buildId、buildNumber、capabilities、required settings 和 missing capabilities。
- inrt 日志页菜单增加复制诊断, 脚本启动失败时全局控制台输出 `[INRT_DIAGNOSTICS]` JSON。
- 首启能力预检只做提示和设置引导, 不硬阻断脚本运行, 保持旧打包应用兼容性。

## 用户可见入口

- 内置文档: `capabilities.html`, `plugins.html`, `all.html`
- 内置索引: `app/src/main/assets-app/indices/all.json`
- 示例: `app/src/main/assets-app/sample/能力状态/打包预检与 inrt 诊断 project.json 示例 [v6.7.3+].js`
- README: 脚本开发辅助章节增加打包预检和 inrt 诊断摘要
- 外部文档仓库: `api/capabilities.md`, `api/plugins.md`, `api/permissionCapabilityMatrix.md`, `json/runtimeApiIndexData.json`

## 验收记录

- JVM 单测: `ProjectBuildPreflightTest`, `ProjectConfigTest`
- 编译: `:app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac`
- 静态校验: 主仓 `indices/all.json`, 外部文档 `runtimeApiIndexData.json`, 主仓/外部文档 `git diff --check`
- 设备限制: 当前无连接 Android 设备, 未执行真实 APK 安装、首启授权引导和 inrt 日志复制验证。

## 风险与后续

- 资源扫描是静态正则最小实现, 覆盖常见字符串字面量相对路径, 不覆盖动态拼接路径。
- `pluginDependencies` 当前检查“脚本调用已声明”, 不检查目标插件是否已安装或可绑定; 安装态仍由运行时插件中心/插件宿主诊断负责。
- `foregroundServiceTypes` 进入预检诊断资产和设置引导; 模板 manifest 仍依赖现有 inrt service 声明, 本任务未新增二进制 manifest 改写器。
- 无设备时不能证明 ROM 授权页跳转、MediaProjection 授权弹窗、Android 13+ 通知授权和日志页复制在所有系统版本真实可用。
