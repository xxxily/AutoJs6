# AutoJs6 发布规范

本文档用于指导 agent 或维护者按当前项目约定发布 AutoJs6 新版本。发布操作必须以项目现有构建脚本、changelog 生成机制和 GitHub Release 风格为准。

## 发布风格

- Git tag 使用 `v{version}`, 例如 `v6.7.1`.
- GitHub Release 标题使用 `{version} @ {YYYY/MM/DD}`, 例如 `6.7.1 @ 2026/06/07`.
- Release 正文使用简体中文 changelog 条目, 每行一个项目符号:

```markdown
* `新增` ...
* `修复` ...
* `优化` ...
* `依赖` ...
```

- APK 附件命名使用项目的 digest 任务生成结果, 格式为 `autojs6-v{version}-{abi}-{crc32}.apk`.
- 上游原始仓库风格参考 `https://github.com/SuperMonster003/AutoJs6/releases`.

## 发布前检查

1. 确认当前分支为 `master`, 且 remote 指向发布目标仓库。

```bash
rtk git branch --show-current
rtk git remote -v
rtk git status --short --branch
```

2. 确认目标版本 tag 和 release 不存在。

```bash
rtk git tag --list 'v6.7.1'
rtk gh release view v6.7.1 --repo xxxily/AutoJs6
```

3. 检查自上一个 tag 以来的变更, 生成发布说明时只写用户可感知变更和重要构建修复。

```bash
rtk git log v6.7.0..HEAD --oneline --no-merges
rtk git diff v6.7.0..HEAD --stat
```

## 版本文件

版本配置位于 `version.properties`:

- `VERSION_NAME`: 发布展示版本, 例如 `6.7.1`.
- `VERSION_BUILD`: Android `versionCode`, 每次可安装升级的正式发布必须递增。
- `BUILD_TIME`: Gradle 构建可能自动更新, release commit 前要检查实际 diff。

发布 `6.7.1` 时应确保:

```properties
VERSION_NAME=6.7.1
VERSION_BUILD=3805
```

## Changelog

changelog 的源数据在 `.changelog/lang_*.json`, 生成结果在 `app/src/main/assets-app/doc/CHANGELOG*.md`。

更新流程:

1. 在所有 `.changelog/lang_*.json` 的 `$data` 顶部插入新版本。
2. 简体中文条目决定 GitHub Release 正文风格。
3. 在 `.python` 目录运行生成脚本。

```bash
rtk python3 generate_markdown.py
```

生成脚本会同时更新:

- `app/src/main/assets-app/doc/CHANGELOG*.md`
- `.readme/README-*.md`
- `README.md`

README 中的最新三版发行历史和维护期日期属于预期生成结果。

## 签名配置

本地签名文件不提交 Git:

- `sign.properties`
- `*.jks`
- `.omx/signing/*`

GitHub Actions 需要配置以下 secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

workflow 中生成的 `sign.properties` 要使用 `storeFile=../release.jks`, 因为 `app/build.gradle.kts` 在 `app` 模块上下文解析 `storeFile`。

## 构建

本地构建前确保可用 JDK 和 Android SDK。macOS 若系统 `java` 不可用, 本次发布验证可用的 JDK 为 Homebrew OpenJDK 17.0.19:

```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

> 注: Android Studio JBR 17.0.6 在本机曾触发 HotSpot AArch64 JIT 崩溃; SonarLint 自带 JDK 21 在本项目自动 AGP/KSP 组合下曾触发 `AndroidPluginVersion.getVersion()` API 不匹配. 优先使用已验证的 OpenJDK 17.0.19 或同等稳定 JDK 17.

正式发布构建命令必须分步执行:

```bash
rtk proxy ./gradlew --no-daemon clean
rtk proxy ./gradlew --no-daemon assembleInrtRelease
rtk proxy ./gradlew --no-daemon assembleAppRelease
rtk proxy ./gradlew --no-daemon :app:appendDigestToReleasedFiles
```

不要把 `assembleInrtRelease` 和 `assembleAppRelease` 合并到同一个 Gradle invocation 中执行, 否则 `app` flavor 的 ABI split 会被关闭, 只生成 universal APK.

构建产物目录:

- 原始 app APK: `app/build/outputs/apk/app/release/*.apk`
- 摘要命名 app APK: `app/releases/*.apk`
- inrt APK: `app/build/outputs/apk/inrt/release/*.apk`

GitHub Release 只上传 `app/releases/*.apk`, 与上游 release 风格一致。

## 验证

构建后至少检查:

```bash
rtk proxy ls -lh app/releases/*.apk
rtk proxy sh -c 'find app/releases -maxdepth 1 -type f -name "*.apk" -print0 | xargs -0 -n1 shasum -a 256'
rtk git diff -- version.properties
```

如果 Gradle 构建更新了 `BUILD_TIME`, release commit 应纳入该变更。若意外改动 `VERSION_BUILD` 或 `VERSION_NAME`, 必须先修正。

## 提交与发布

推荐提交拆分:

1. 非发布前置修正或文档单独提交。
2. CI/workflow 修正单独提交。
3. release commit 只包含版本号、changelog、README 生成结果和发布规范更新。

示例:

```bash
rtk git add <files>
rtk git commit -m "chore: prepare release v6.7.1"
rtk git tag v6.7.1
rtk git push origin master
rtk git push origin v6.7.1
```

创建 GitHub Release:

```bash
rtk gh release create v6.7.1 app/releases/*.apk \
  --repo xxxily/AutoJs6 \
  --title "6.7.1 @ 2026/06/07" \
  --notes-file .omx/release/v6.7.1/release-notes.md
```

发布后验证:

```bash
rtk gh release view v6.7.1 --repo xxxily/AutoJs6 --json tagName,name,isDraft,isPrerelease,url,assets
rtk gh run list --repo xxxily/AutoJs6 --branch master --limit 5
```

## 失败处理

- 本地构建失败: 先修复代码或构建配置, 不创建 tag 和 release。
- `SDK location not found`: 设置 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT`, 或提供 `local.properties:sdk.dir`。
- Gradle daemon 崩溃并生成 `hs_err_pid*.log`: 优先更换 JDK; 本次发布从 Android Studio JBR 17.0.6 切换到 Homebrew OpenJDK 17.0.19 后通过。
- `appendDigestToReleasedFiles NO-SOURCE`: 确认已先单独执行 `assembleAppRelease`, 且任务读取 `app/build/outputs/apk/app/release` 并输出到 `app/releases`。
- tag 已推送但 release 创建失败: 修复 release 命令后继续创建 release, 不要重写 tag。
- release 已创建但附件缺失: 使用 `gh release upload v{version} app/releases/*.apk --clobber` 补传。
- tag 指向错误提交: 发布前发现可删除本地 tag；已推送后必须谨慎处理, 先和维护者确认。
