# 数据、网络、IPC 能力增强

> 日期: 2026-06-09
> 对应任务: T64 / A13
> 范围: HTTP 命名 client、下载任务、KV 命名空间、本地 IPC、typed Intent/Broadcast

## 实现范围

- `http.client(name, options?)`: 声明/查询命名 HTTP client, 支持 `timeout`, `maxRetries`, `headers/defaultHeaders`, `interceptors`, `pins/certificatePins`, `allowedHosts`, `followRedirects`.
- `http.request/get/post/...` 可通过 `{ client: "name" }` 或 `{ clientName: "name" }` 复用命名 client.
- `http.download(url, path, options?)`: 创建进程内下载任务, 使用 `.part` 临时文件和 `Range` 请求支持暂停/恢复; 配套 `downloadStatus`, `downloads`, `pauseDownload`, `resumeDownload`, `cancelDownload`.
- `storages.namespace(name, namespace)`: 基于现有 `LocalStorage` 提供 KV 命名空间; 配套 `namespaceNames`, `removeNamespace`, `removeNamespaceSync`.
- `ipc`: 新增本地脚本消息总线, 提供 `publish`, `subscribe`, `unsubscribe`, `messages`, `clear`, `request`, `reply`; 脚本退出时清理当前 runtime 订阅.
- `app.buildTypedIntent`, `app.sendTypedBroadcast`, `app.parseTypedIntent`: 提供结构化 Intent/Broadcast 入口, payload 使用 JSON extra 承载.

## 安全与边界

- 命名 client 支持 `allowedHosts`, 可限制请求域名; 证书 pinning 使用 OkHttp `CertificatePinner`.
- 请求拦截器当前为声明式拦截, 支持 `header`, `addHeader`, `query`, `userAgent`, `bearer`; 未暴露任意 JS 回调作为 OkHttp 线程拦截器, 避免 Rhino 上下文跨线程风险.
- 下载任务是当前进程内队列, 不提供跨进程/重启后的任务恢复调度; `.part` 文件可在同一脚本/进程后续任务中续传.
- IPC 是当前 AutoJs6 进程内消息总线, 不替代跨应用 Broadcast; 与外部系统交互使用 typed Intent/Broadcast.
- typed Broadcast 默认使用普通 Android broadcast; 需要导出组件、权限或包名约束时由脚本显式指定 `packageName`, `className`, `permission`.

## 用户可见入口

- 内置文档: `http.html`, `storages.html`, `app.html`, `ipc.html`, `all.html`, `sidebar.html`
- 内置索引: `app/src/main/assets-app/indices/all.json`
- 示例: `app/src/main/assets-app/sample/HTTP/命名客户端下载与IPC [v6.7.3+].js`
- README: 增加数据/网络/IPC 能力摘要
- 外部文档仓库: `api/http.md`, `api/storages.md`, `api/app.md`, `api/ipc.md`, `api/all.md`, `api/sidebar.md`, `api/toc.md`, `json/runtimeApiIndexData.json`

## 验证

- 已通过 `:app:compileAppDebugKotlin`.
- 已通过 `:app:testAppDebugUnitTest --tests org.autojs.autojs.runtime.api.DataNetworkIpcApiTest`.
- 后续完成任务时需再执行包含 Java 编译的组合命令、`jq`、`git diff --check` 和设备可用性检查.

## 设备验证限制

当前无连接 Android 设备, 因此未执行真实 APK 上的网络下载、Broadcast 分发、跨脚本 IPC 交互和 UI 文档查看验证。接手 agent 若有设备, 优先运行新增示例脚本并观察:

- `http.downloadStatus("demo-github-mark")` 进度和完成状态.
- `ipc.messages("demo.download.reply")` 是否收到回复.
- `app.sendTypedBroadcast()` 返回对象是否包含 `type/requestId/payload`.
