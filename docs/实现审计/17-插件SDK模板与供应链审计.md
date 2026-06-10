# 插件 SDK 模板与供应链审计

> 日期: 2026-06-09
> 范围: T62/A10 插件 SDK、权限声明与供应链审计

## 目标

- 插件在安装前可声明类型、能力、权限、风险、最低 AutoJs6 版本、文档、示例和 OCR 引擎。
- 官方索引提供 APK SHA-256、证书指纹 pinning、release 变更说明和索引签名元数据。
- 插件中心作为测试宿主, 展示 manifest、信任状态和运行诊断; URL 安装/更新必须先完成完整性校验。

## Manifest 模板

```json
{
  "pluginType": "ocr",
  "capabilities": ["ocr", "screen_capture"],
  "permissions": ["android.permission.INTERNET"],
  "riskLevel": "medium",
  "minAutoJsVersion": "6.7.3",
  "documentationUrl": "https://example.com/plugins/paddle-ocr",
  "examples": ["https://example.com/samples/ocr.js"],
  "engines": [
    {
      "id": "paddle-ocr-pp-ocrv5",
      "engine": "paddle-ocr",
      "variant": "v5",
      "label": "PP-OCRv5"
    }
  ]
}
```

兼容键:

| 语义 | 推荐键 | 兼容键 |
| --- | --- | --- |
| 插件类型 | `pluginType` | `type` |
| 能力 | `capabilities` | `capabilityIds`, `provides` |
| Android 权限 | `permissions` | `requiredPermissions`, `androidPermissions` |
| 风险等级 | `riskLevel` | `risk` |
| 最低版本 | `minAutoJsVersion` | `minAutoJs6Version`, `minVersion` |
| 文档 URL | `documentationUrl` | `docsUrl`, `docUrl` |
| 示例 URL | `examples` | `exampleUrls`, `sampleUrls` |

## OCR 插件 SDK 模板

插件工程依赖 `plugin-api:paddle-ocr-api`, 实现 `org.autojs.plugin.paddle.ocr.api.IOcrPlugin`.

```kotlin
class OcrPluginService : Service() {
    private val binder = object : IOcrPlugin.Stub() {
        override fun getInfo(): PluginInfo {
            return PluginInfo().apply {
                name = "Paddle OCR"
                description = "PP-OCRv5 engine"
                author = "AutoJs6"
                versionName = "1.2.0"
                versionCode = 12
                id = "paddle-ocr-pp-ocrv5"
                engine = "paddle-ocr"
                variant = "v5"
                capabilities = Bundle().apply {
                    putStringArrayList("capabilities", arrayListOf("ocr", "screen_capture"))
                    putStringArrayList("permissions", arrayListOf("android.permission.INTERNET"))
                    putString("riskLevel", "medium")
                    putString("minAutoJsVersion", "6.7.3")
                    putString("documentationUrl", "https://example.com/plugins/paddle-ocr")
                    putStringArrayList("examples", arrayListOf("https://example.com/samples/ocr.js"))
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
```

Service 声明示例:

```xml
<service
    android:name=".OcrPluginService"
    android:exported="true"
    android:permission="org.autojs.plugin.paddle.ocr.permission.BIND_OCR_PLUGIN">
    <intent-filter>
        <action android:name="org.autojs.plugin.paddle.ocr.api.IOcrPlugin" />
    </intent-filter>
</service>
```

## 官方索引模板

```json
{
  "signature": {
    "algorithm": "sha256",
    "keyId": "autojs6-official-2026",
    "payloadSha256": "plugins-array-sha256",
    "signedAt": "2026-06-09T00:00:00Z",
    "signature": "detached-signature"
  },
  "plugins": [
    {
      "packageName": "org.autojs.plugin.ocr",
      "title": "Paddle OCR",
      "description": "OCR plugin",
      "manifest": {
        "pluginType": "ocr",
        "capabilities": ["ocr"],
        "permissions": ["android.permission.INTERNET"],
        "riskLevel": "medium",
        "minAutoJsVersion": "6.7.3",
        "engines": ["paddle-ocr-pp-ocrv5"]
      },
      "releases": [
        {
          "versionName": "1.2.0",
          "versionCode": 12,
          "versionDate": "2026-06-09",
          "apkUrl": "https://example.com/plugin.apk",
          "apkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "certificateSha256": [
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
          ],
          "changelogText": "Declare capabilities and update OCR engine."
        }
      ]
    }
  ]
}
```

校验规则:

- `apkSha256` 缺失时, 插件中心拒绝从 URL 安装/更新。
- 下载完成后计算 APK SHA-256, 不匹配时拒绝安装。
- `certificateSha256` 非空时, 安装前读取 APK 签名证书 SHA-256, 没有任何指纹命中 pin 时拒绝安装。
- `signature.payloadSha256` 存在时, 解析索引阶段校验插件数组 payload 摘要, 不匹配时拒绝使用该索引。

## 测试宿主清单

用 AutoJs6 插件中心作为宿主验证:

1. 安装插件 APK 后进入插件中心, 详情页能看到 OCR 引擎、能力、权限、风险等级、最低版本和信任状态。
2. 官方索引提供安装 URL 时, 安装按钮必须要求 `apkSha256`; 缺失时显示完整性错误。
3. 篡改 APK 或篡改 `apkSha256` 后, 下载完成必须被拒绝安装。
4. 提供错误证书指纹 pin 后, 安装前必须被拒绝。
5. 禁用插件、撤销授权、替换未受信签名或制造协议版本不兼容后, 插件中心最近错误能区分对应诊断码。
6. 运行 `PluginIndexRepositoryTest` 覆盖 manifest 解析、release 完整性字段、payload 摘要失败和诊断映射。

## 已接入实现点

- `PluginCapabilityManifest`: manifest/schema、OCR `PluginInfo.capabilities` 兼容解析。
- `PluginIndexRepository`: 官方索引解析、payload SHA-256 校验、release `apkSha256`/证书 pin/changelog 字段。
- `PluginInstaller`: URL 下载后 APK SHA-256 校验与 APK 签名证书 pinning。
- `PluginCenterViewModel`/`PluginInfoDialogManager`: manifest 展示摘要、安装/更新前完整性字段校验。
- `PluginErrorMapper`: 未安装、未启用、未授权、签名不可信、服务异常和版本不兼容诊断映射。
