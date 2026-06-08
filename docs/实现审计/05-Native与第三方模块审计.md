# Native 与第三方模块审计

## 范围

本分册覆盖本地 native/JNI、OCR 引擎、APK 解析/签名模块、vendored 第三方 UI/工具模块和二进制依赖版本风险。主要路径为:

- `libs/rapidocr/**`
- `plugin-api/paddle-ocr-engine/**`
- `modules/apk-parser/**`
- `modules/apk-signer/**`
- `libs/imagequant/**`
- `modules/material-dialogs/**`
- `modules/expandable-layout/**`
- `modules/color-picker/**`
- `libs/**` 下 AAR/JAR/SO 包装模块

## 问题表

| ID | 文件/行号 | 问题 | 原因/风险 | 修复建议 | 严重程度 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| NATIVE-01 | `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:380`, `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:385`, `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:392` | RapidOCR asset 读取失败只返回空指针 | 失败时 `size` 可能保持 0, 调用方仍继续构造 ORT session, native 初始化可能崩溃 | 返回结构体 `{ptr,size,status}`; 失败抛 Java 异常; `AAsset_read` 校验读取长度等于 asset 长度 | `HIGH` | `P0` |
| NATIVE-02 | `libs/rapidocr/src/main/cpp/src/DbNet.cpp:34`, `libs/rapidocr/src/main/cpp/src/DbNet.cpp:37`, `libs/rapidocr/src/main/cpp/src/AngleNet.cpp:34`, `libs/rapidocr/src/main/cpp/src/CrnnNet.cpp:60` | ORT session 构造前未检查模型数据 | `dbModelData == NULL` 或长度为 0 时仍传给 `Ort::Session`, 造成崩溃或未定义行为 | 模型读取失败立即 return/error; Java 层提示模型缺失; 初始化结果必须可观测 | `HIGH` | `P0` |
| NATIVE-03 | `libs/rapidocr/src/main/cpp/src/main.cpp:6`, `libs/rapidocr/src/main/cpp/src/main.cpp:26`, `libs/rapidocr/src/main/cpp/src/main.cpp:62` | RapidOCR 使用全局 `OcrLite*` 且无并发保护 | 多脚本同时 init/detect/benchmark/release 会共享 session 和中间状态, 存在竞态和崩溃 | 改为实例句柄 `jlong`; 或全局 mutex 保护并禁止 init/detect/release 并发; Java 层加状态机 | `HIGH` | `P0` |
| NATIVE-04 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:22`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:23`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:28` | Paddle native library 加载失败后污染状态 | `compareAndSet(false,true)` 在加载成功前置位, 首次失败后后续实例会跳过 `System.loadLibrary` | 加载成功后再置 true; catch 中回滚; 记录具体 so/OpenCV 加载错误 | `HIGH` | `P0` |
| NATIVE-05 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:49`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:70`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OCRPredictorNative.java:72` | Paddle `destroy()` 未与 `runImage()` 共用锁 | native pointer 可能在 forward 过程中被 release, 导致 use-after-free 或 native 崩溃 | `destroy()` 加同一把锁; pointer 访问集中封装; 使用 destroyed 状态和引用计数 | `HIGH` | `P0` |
| NATIVE-06 | `modules/apk-parser/src/main/java/net/dongliu/apk/parser/ApkFile.java:78`, `modules/apk-parser/src/main/java/net/dongliu/apk/parser/ApkFile.java:119` | APK parser 文件句柄和 ZipFile 泄漏 | `new FileInputStream(...).getChannel()` 未关闭; `close()` 未关闭 `zf`; 高频解析 APK 会耗尽 fd | try-with-resources 包裹 stream/channel; `close()` 同时关闭 `zf` 和 super; 增加 fd 泄漏测试 | `HIGH` | `P0` |
| NATIVE-07 | `modules/apk-signer/src/main/java/com/mcal/apksigner/CertCreator.kt:35`, `modules/apk-signer/src/main/java/com/mcal/apksigner/CertCreator.kt:37` | 新证书默认使用 `SHA1withRSA` | SHA-1 已不适合作为默认签名哈希, 可能触发现代平台策略和安全审计问题 | 默认改为 `SHA256withRSA` 或更强; SHA-1 仅作为显式兼容选项 | `HIGH` | `P1` |
| NATIVE-08 | `libs/rapidocr/src/main/cpp/src/BitmapUtils.cpp:15`, `libs/rapidocr/src/main/cpp/src/BitmapUtils.cpp:18`, `libs/rapidocr/src/main/cpp/src/BitmapUtils.cpp:43` | `AndroidBitmap_lockPixels` 失败前 catch 仍 unlock | lock 未成功时 catch 调用 `AndroidBitmap_unlockPixels`, 可能触发额外错误或掩盖原始异常 | 使用 `locked` 布尔或 RAII guard; 只在 lock 成功后 unlock | `MEDIUM` | `P1` |
| NATIVE-09 | `libs/rapidocr/src/main/cpp/src/OcrResultUtils.cpp:64`, `libs/rapidocr/src/main/cpp/src/OcrResultUtils.cpp:76`, `libs/rapidocr/src/main/cpp/src/OcrResultUtils.cpp:99`, `libs/rapidocr/src/main/cpp/src/OcrResultUtils.cpp:101` | JNI local refs 在循环中不释放 | 大量文本框/点位时 local reference table 可能溢出, 导致 OCR 调用崩溃 | 循环内 `DeleteLocalRef`; 检查 `NewObject/NewStringUTF` 返回; 必要时用 `PushLocalFrame/PopLocalFrame` | `MEDIUM` | `P1` |
| NATIVE-10 | `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:398`, `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:401`, `libs/rapidocr/src/main/cpp/src/OcrUtils.cpp:412` | `jstringTostring()` local refs/堆内存处理不完整 | `strencode/barr/clsstring` local refs 未释放; `malloc` 后未 free; `input` 为空或 `alen=0` 时构造 `std::string` 有风险 | 使用 `GetStringUTFChars` 或 RAII; 释放 local refs; 空值显式返回空字符串或抛异常 | `MEDIUM` | `P2` |
| NATIVE-11 | `modules/apk-parser/src/main/java/net/dongliu/apk/parser/AbstractApkFile.java:421`, `modules/apk-parser/src/main/java/net/dongliu/apk/parser/AbstractApkFile.java:426`, `modules/apk-parser/src/main/java/net/dongliu/apk/parser/AbstractApkFile.java:428` | APK Signing Block 偏移边界检查不足 | 畸形 APK 可让 `cdStart - magicStrLen`、`cdStart - blockSize - 8` 越界, 触发崩溃/DoS | 每次 `position/slice` 前检查 `cdStart/blockSize/remaining`; 对异常 APK 返回 null 而非抛出底层异常 | `MEDIUM` | `P1` |
| NATIVE-12 | `modules/apk-parser/src/main/java/net/dongliu/apk/parser/utils/Inputs.kt:8`, `modules/apk-parser/src/main/java/net/dongliu/apk/parser/ApkFile.java:66`, `modules/apk-parser/src/main/java/net/dongliu/apk/parser/ApkFile.java:71` | ZIP entry 全量读入且无限额 | 恶意 APK 大 entry 或 zip bomb 可导致 OOM; parser 没有总解压量限制 | 对 entry size/总读取字节设置上限; 尽量流式解析; 对压缩率异常的 APK 拒绝 | `MEDIUM` | `P1` |
| NATIVE-13 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Predictor.java:315`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:63`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:90` | Paddle 模型 assets 复制递归判断错误 | 对 assets 路径使用 `new File(srcSubPath).isDirectory()`, 在 APK assets 中不能判断目录, 子目录模型可能被当作文件处理 | 使用 `AssetManager.list(srcSubPath)` 判断目录; 空目录和文件区分需要显式策略 | `MEDIUM` | `P2` |
| NATIVE-14 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:75`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:93`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:94` | `copyDirectoryFromAssetsIfNeeded()` 首个已存在文件会提前返回整个目录 | 任一文件存在就 `return`, 后续模型文件可能不复制, 造成缺文件初始化失败 | 已存在文件只 `continue`; 每个文件校验大小/hash; 复制完成后标记目录版本 | `MEDIUM` | `P2` |
| NATIVE-15 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Predictor.java:318`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Predictor.java:322`, `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Predictor.java:325` | Paddle 模型缓存标记与实际文件完整性脱节 | SharedPreferences 标记为已加载后, 文件损坏/版本变化/部分复制不会被强制修复 | 缓存 key 包含模型版本和文件摘要; 启动时校验文件集合; 失败强制重拷贝 | `MEDIUM` | `P2` |
| NATIVE-16 | `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/Utils.java:105` | Paddle label/配置解析缺格式和大小防护 | OCR label 文件异常时可能解析失败或造成过大内存占用; 错误信息不明确 | 对 label 文件行数、单行长度、编码和异常格式做校验; 抛出可诊断错误 | `LOW` | `P3` |
| NATIVE-17 | `libs/imagequant/src/main/cpp/PngQuantBridge.cpp` | imagequant 需要补充尺寸/jsize 边界测试 | 当前桥接实现已有 RAII 口径, 未发现同级资源泄漏; 但 JNI 数组长度和图片尺寸仍需压力边界测试 | 添加极大图片、空数组、负尺寸、OOM 路径测试; 对 jsize 转 size_t 做显式检查 | `LOW` | `P3` |
| NATIVE-18 | `modules/material-dialogs/src/main/java/com/afollestad/materialdialogs/internal/MDButton.java`, `modules/material-dialogs/src/main/java/com/afollestad/materialdialogs/util/DialogUtils.java` | vendored material-dialogs 使用旧 Android 存储/主题假设 | 老库代码与现代 scoped storage、动态颜色、暗色主题兼容性存在长期风险 | 仅保留必要补丁; 评估升级或替换; 对文件选择/颜色解析做 Android 13-15 回归 | `LOW` | `P3` |
| NATIVE-19 | `modules/expandable-layout/src/main/java/com/github/aakira/expandablelayout/ExpandableLayout.java` | expandable 动画生命周期需补取消/cleanup 测试 | view detach、adapter 快速复用时动画对象可能持有 view 或重复回调 | detach 时 cancel animator; 增加 RecyclerView 复用场景测试 | `LOW` | `P3` |
| NATIVE-20 | `modules/color-picker/src/main/java/com/jaredrummler/android/colorpicker/ColorPickerDialog.java` | color-picker 反射/旧游标 tint 路径兼容性风险 | 旧库对内部字段或旧 API 的依赖在新 Android/Material 主题下可能失效 | 将反射路径做 try/catch 降级; 增加 Android 15 UI smoke test | `LOW` | `P3` |
| NATIVE-21 | `libs/org-opencv-4_8_0/**`, `libs/rapidocr/build.gradle.kts:47`, `plugin-api/paddle-ocr-engine/**` | 第三方 native/二进制依赖版本和来源可追溯性不足 | OpenCV、ONNX Runtime、Paddle 相关 so/AAR/JAR 未形成 SBOM、CVE 映射和固定校验 | 建立 SBOM; 为每个二进制记录版本、来源 URL、SHA-256、许可证和 CVE 状态 | `MEDIUM` | `P2` |

## 已确认未发现明确实现级问题的关键文件

- `libs/imagequant/src/main/cpp/PngQuantBridge.cpp`: 资源释放整体采用 RAII/局部封装, 本轮未发现与 RapidOCR 同级的 lock/unlock 泄漏问题。
- `plugin-api/paddle-ocr-api/src/main/aidl/**`: AIDL 数据结构较窄, 本轮未发现权限绕过; 风险集中在 engine native 生命周期。
- `plugin-api/paddle-ocr-engine/src/main/java/com/baidu/paddle/lite/ocr/OpenCLGuard.java` 与 `OpenCLProbe.java`: OpenCL 探测和 guard 未发现直接高风险问题。
- `modules/apk-signer/src/main/java/com/android/apksig/**`: 主要为 Google apksig 源码引入, 本轮未发现独立修改造成的明确问题; 证书默认算法问题记录在 `CertCreator.kt`。
- `libs/androidx-appcompat-1_0_2/**`、`libs/jackpal-androidterm-*/*` 等包装模块: 本轮按二进制/版本/调用链确认, 未逐一反编译, 供应链和版本治理归入后续专项。

## 后续验证建议

- RapidOCR/Paddle OCR 增加 native stress: 多线程 init/detect/destroy、缺模型、损坏模型、超大图片、连续 1000 次 OCR。
- APK parser 增加畸形 APK corpus: 越界 signing block、zip bomb、超大 entry、缺 EOCD、多 disk 标志。
- 为所有 AAR/JAR/SO 生成 SBOM, 同步许可证和 CVE 扫描结果。
- 对 vendored UI 模块做 Android 13-15 smoke test, 聚焦权限、存储、主题、反射路径。
