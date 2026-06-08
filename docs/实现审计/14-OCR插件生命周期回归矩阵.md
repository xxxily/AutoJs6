# OCR 与插件生命周期回归矩阵

> 日期: 2026-06-09
> 范围: MLKit OCR、RapidOCR、Paddle 内置引擎、Paddle OCR 插件、插件中心、打包 APK 中 OCR 相关资产和长时间运行风险。

## 核心入口

| 领域 | 文件 |
| --- | --- |
| OCR 统一 API | `app/src/main/java/org/autojs/autojs/runtime/api/Ocr.kt`; `runtime/api/augment/ocr/Ocr.kt`; `OcrResult.kt` |
| MLKit OCR | `app/src/main/java/org/autojs/autojs/runtime/api/OcrMLKit.kt`; `runtime/api/augment/ocr/OcrMLKit.kt` |
| RapidOCR | `app/src/main/java/org/autojs/autojs/runtime/api/OcrRapid.kt`; `runtime/api/augment/ocr/OcrRapid.kt`; `libs/rapidocr/**` |
| Paddle 内置 | `runtime/api/augment/ocr/PaddleOcrEmbeddedEngine.kt`; `plugin-api/paddle-ocr-engine/**` |
| Paddle 插件 | `app/src/main/java/org/autojs/autojs/core/plugin/ocr/PaddleOcrPluginHost.kt`; `runtime/api/augment/ocr/OcrPaddle.kt` |
| 插件中心 | `app/src/main/java/org/autojs/autojs/core/plugin/center/**`; `app/src/main/java/org/autojs/autojs/runtime/api/Plugins.kt` |
| 示例/资产 | `app/src/main/assets-app/sample/OCR/*`; `app/src/main/res/drawable/ic_plugin_paddle_ocr.png`; `app/src/main/assets-app/docs/ocr.html` |

## 引擎矩阵

| 引擎 | 前置 | Smoke case | 预期 | 失败记录 |
| --- | --- | --- | --- | --- |
| MLKit OCR | 设备具备 MLKit 依赖; 截图或图片输入 | 识别 `sample/OCR/test.png` | 返回文字块或明确无结果, 不崩溃 | 模型下载状态、异常、耗时、图片尺寸 |
| RapidOCR | native libs/assets 可加载 | 识别同一测试图; 连续运行 20 次 | 初始化一次或可复用; 无 native 崩溃; 结果稳定 | 初始化耗时、每次耗时、内存增长 |
| Paddle 内置 | `plugin-api/paddle-ocr-engine` libs/assets 可用 | 识别测试图; init/destroy 重复 | `destroy()` 后可重新 init; 不 use-after-free | so 加载错误、模型路径、线程并发 |
| Paddle 插件 | 已安装插件、授权、启用 | 绑定插件后识别测试图 | 绑定成功; 识别成功; 失败有提示 | 插件版本、授权状态、bind/binder died 日志 |

## 生命周期场景

| 场景 | 操作步骤 | 预期 |
| --- | --- | --- |
| 插件发现 | 打开插件中心, 刷新索引, 安装/识别 Paddle OCR 插件 | 插件列表展示名称、版本、签名/信任状态和启用状态 |
| 插件启用/禁用 | 启用后运行 OCR; 禁用后再次运行 | 启用时可调用; 禁用后给出明确错误, 不静默 fallback 到错误引擎 |
| 插件进程死亡 | 运行中强停插件进程或系统回收 | 捕获 binding died/app stopped; 清理连接池; 下次调用尝试重绑或提示 |
| 解绑/重绑 | 连续调用 OCR, 中间切换插件启用状态 | 不保留失效 binder; 新状态生效 |
| 长时间运行 | 每 5 秒识别一次, 连续 30 分钟 | 内存无持续线性增长; native/Bitmap 资源释放; 失败可恢复 |
| 并发调用 | 两个脚本同时 OCR | 结果互不串扰; 不崩溃; 不破坏全局 engine 状态 |
| 打包 APK | 打包含 OCR 的项目并运行 | 必要 libs/assets/权限存在; inrt 中错误提示可读 |

## 打包检查点

- `template.apk` 重新生成后再打包 OCR 项目。
- 检查 APK 内是否包含相关 SO/assets。
- 运行时截图权限、存储权限、前台服务权限和插件依赖提示要与主 app 一致。
- 插件 OCR 依赖外部插件时, 打包前应提示用户安装/授权插件, 不应假装内置。

## 长跑记录模板

```markdown
### OCR 长跑记录

- 引擎:
- 设备/Android/ROM:
- AutoJs6 build:
- 图片来源: 文件/截图/相机
- 循环次数/间隔:
- 起始内存:
- 结束内存:
- 平均耗时:
- 最大耗时:
- 错误次数:
- 插件 bind/rebind 日志:
- 是否可恢复:
- 判断:
```

## 最小测试脚本

```javascript
"use strict";

var ENABLE_OCR = false;

if (!ENABLE_OCR) {
  console.log("OCR regression is disabled by default");
} else {
  var img = images.read(files.path("./OCR/test.png"));
  var start = Date.now();
  var result = ocr.detect(img);
  console.log("ocr count:", result.length, "cost:", Date.now() - start);
  img.recycle();
}
```

## 风险边界

- Native 崩溃、use-after-free、模型读取失败继续初始化属于代码问题。
- OCR 准确率低需要保留输入图片、语言、引擎、缩放和阈值, 不能只写“识别错误”。
- 插件被系统停止属于系统/后台限制和插件生命周期共同问题, 需要记录插件进程、授权和后台策略。
- 修改 OCR API 时同步检查 `app/src/main/assets-app/docs/ocr.html`、内置 OCR 示例和外部文档仓库。
