# 打包 inrt/template.apk 回归矩阵

> 日期: 2026-06-09
> 范围: `app` 与 `inrt` flavor、`template.apk`、项目打包、权限、ABI/libs、签名和运行验证。

## 关键路径

- 生成 inrt 模板: `rtk proxy ./gradlew --no-daemon assembleInrtRelease`
- 模板 APK 源路径: `app/src/main/assets-app/template.apk`
- inrt release 输出: `app/build/outputs/apk/inrt/release/*.apk`
- app debug 编译: `rtk proxy ./gradlew --no-daemon assembleAppDebug`
- 发布分步要求: 先 `assembleInrtRelease`, 再 `assembleAppRelease`, 不要合并到同一 Gradle invocation。

## 静态文件入口

| 领域 | 文件 |
| --- | --- |
| 打包器 | `app/src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt`; `ApkPackager.java`; `ManifestEditor.java`; `TinySign.java` |
| 项目配置 | `app/src/main/java/org/autojs/autojs/project/ProjectConfig.java`; `app/src/main/java/org/autojs/autojs/project/LaunchConfig.kt`; `app/src/main/assets-inrt/project/project.json` |
| inrt 运行 | `app/src/main/java/org/autojs/autojs/inrt/**`; `app/src/main/res/xml/fragment_preferences_inrt.xml`; `app/src/main/res/xml-v25/inrt_app_shortcuts.xml` |
| Manifest/权限 | `app/src/main/AndroidManifest.xml`; `app/src/main/res/values*/strings.xml`; `app/src/main/res/xml/fragment_preferences_inrt.xml` |
| 构建脚本 | `app/build.gradle.kts`; `docs/发布/AutoJs6发布规范.md`; `README.md` |

## 回归矩阵

| 场景 | 操作/命令 | 预期结果 | 额外检查 |
| --- | --- | --- | --- |
| 生成 inrt 模板 | `rtk proxy ./gradlew --no-daemon assembleInrtRelease` | 构建成功; inrt release APK 生成; `app/src/main/assets-app/template.apk` 可更新 | 不要夹带无关构建产物; 如果 template 变动, 记录原因和大小 |
| app debug 构建 | `rtk proxy ./gradlew --no-daemon assembleAppDebug` | 主 app 构建成功, 可安装 | 如果只改文档可跳过 APK 构建, 但至少跑 `compileAppDebugKotlin` |
| 打包单文件脚本 | 文件管理器选择单个 `.js` 打包 | APK 可生成; 运行后执行脚本主入口 | Manifest 包名、图标、名称、权限符合 UI 选择 |
| 打包项目 | 项目含 `project.json + main.js` | `project.json.main` 对应文件存在; APK 启动后执行主脚本 | `assets` 目录、相对路径、中文路径和空目录处理 |
| launchConfig | 设置 `logsVisible/splashVisible/launcherVisible/runOnBoot` 的四种组合 | inrt 首次启动、日志入口、桌面图标、开机启动与配置一致 | `InrtShortcuts.syncToExplicitIntents()`; `Pref.syncLaunchConfigWithBuild()` |
| 权限裁剪/保留 | 打包需要截图、悬浮窗、通知、存储、Root/Shizuku 的项目 | Manifest 和 inrt 设置页权限提示一致; 不额外保留无关危险权限 | Android 13 通知、Android 14 FGS/MediaProjection、所有文件访问 |
| ABI/libs | 打包使用 OCR/native/libs 的项目 | 必要 SO/assets 被包含; 不包含与所选 ABI 无关或缺失的库 | `librapidocr`, Paddle plugin/embedded, imagequant |
| 签名方案 | 默认签名、新建 keystore、自选 keystore | APK 可安装; 升级签名一致时可覆盖安装 | 不记录明文 keystore 密码; 失败时记录 key alias/算法 |
| 运行验证 | 安装打包 APK, 手动启动 | 脚本可运行; 日志/设置/权限入口可用; 崩溃可导出日志 | 主 app 与 inrt 行为差异必须记录 |

## APK/Manifest 检查命令

```bash
rtk proxy ./gradlew --no-daemon assembleInrtRelease
rtk proxy ./gradlew --no-daemon assembleAppDebug
rtk proxy ls -lh app/src/main/assets-app/template.apk app/build/outputs/apk/inrt/release/*.apk
rtk proxy aapt dump permissions app/build/outputs/apk/inrt/release/*.apk
rtk proxy aapt dump badging app/build/outputs/apk/inrt/release/*.apk
```

如果本机没有 `aapt`, 可用 Android SDK build-tools 下的完整路径。

## 失败记录模板

```markdown
### 打包/inrt 回归失败

- 场景:
- 命令/操作:
- 输入项目结构:
- project.json:
- 期望:
- 实际:
- 构建日志首个根因:
- APK 路径/大小:
- Manifest/权限差异:
- 设备运行日志:
- 下一步建议:
```

## 阻塞说明

- 没有 Android 设备时, 至少完成构建、APK 文件存在性和 Manifest 静态检查。
- 没有签名材料时, 不测试用户私有 keystore; 使用默认 debug/测试签名并记录限制。
- 不要把 `template.apk` 失败简单归因于打包器; 先确认是否已重新运行 `assembleInrtRelease`。
