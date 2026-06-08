# 脚本 Runtime API 回归样例集

> 日期: 2026-06-09
> 位置选择: 本轮先放入 `docs/实现审计/`, 不新增内置用户示例。原因是这些脚本用于维护回归, 其中包含高风险能力的门控样例; 后续若要面向用户发布, 再拆分到 `app/src/main/assets-app/sample/` 并同步外部文档。

## 运行约定

- 每个样例独立运行, 不要一次性复制全部脚本运行。
- 高风险能力默认用 `ENABLE_RISKY = false` 或显式 `return` 保护。
- 记录设备、Android 版本、权限状态、AutoJs6 build、预期输出和实际输出。
- 修改 Runtime API、`runtime/api/augment`、内置 docs 或示例时, 至少复跑相关模块样例。

## 样例索引

| 模块 | 权限/风险 | 最小样例 | 预期输出 |
| --- | --- | --- | --- |
| console | 无 | 打印不同级别日志 | 控制台出现 info/warn/error |
| files | 存储/工作目录 | 创建、读取、删除临时文件 | 输出写入内容并清理文件 |
| http | 网络 | GET `https://httpbin.org/get` 或本地 mock | 状态码 200 或明确网络失败 |
| dialogs | 前台 UI | `confirm`/`multiChoice` 返回类型 | 返回 boolean/数组语义正确 |
| app | 包管理查询 | 查询当前应用包名或已安装应用 | 输出 packageName 或 false |
| device | 设备信息 | 输出宽高、SDK、品牌 | 控制台打印非空字段 |
| auto/selector/automator | 无障碍, 高风险 | 查询当前包名/点击前置门控 | 未授权时提示; 授权后节点查询可用 |
| images | 截图/存储, 高风险 | 读取内置图片或截图门控 | 图片尺寸或截图授权结果 |
| ocr | OCR 模型/截图, 高风险 | 识别内置 OCR 测试图 | 输出文字块或明确引擎不可用 |
| timers/threads | 无 | 定时器和线程通信 | 按顺序输出 timer/thread |
| tasks | 定时任务/后台限制 | 创建短延迟一次性任务前置门控 | 默认不创建; 开启后任务可在列表出现 |
| floaty | 悬浮窗, 高风险 | 悬浮窗门控 | 未开启时不显示; 开启且授权后显示并关闭 |
| shizuku/shell | Shizuku/Root/Shell, 高风险 | 输出 `id`/`settings get` 前置门控 | 默认不执行; 开启后输出命令结果或权限错误 |

## console

```javascript
"use strict";

console.show();
console.info("console regression: info");
console.warn("console regression: warn");
console.error("console regression: error");
```

## files

```javascript
"use strict";

var path = files.join(files.cwd(), "runtime-regression.tmp.txt");
files.write(path, "AutoJs6 runtime regression\n");
var text = files.read(path);
console.log("files text:", text.trim());
files.remove(path);
console.log("files exists after remove:", files.exists(path));
```

## http

```javascript
"use strict";

try {
  var res = http.get("https://httpbin.org/get", { timeout: 10000 });
  console.log("http status:", res.statusCode);
  console.log("http body starts:", String(res.body.string()).slice(0, 40));
} catch (e) {
  console.warn("http regression skipped or failed:", e);
}
```

## dialogs

```javascript
"use strict";

var confirmed = dialogs.confirm("Runtime regression", "Confirm dialog bridge works?");
console.log("confirm type:", typeof confirmed, "value:", confirmed);
var picked = dialogs.multiChoice("Pick", ["a", "b"], [0]);
console.log("multiChoice is array:", Array.isArray(picked), "value:", picked);
```

## app

```javascript
"use strict";

var selfPackage = context.getPackageName();
console.log("self package:", selfPackage);
console.log("self installed:", app.isInstalled(selfPackage));
console.log("wechat installed:", app.isInstalled("com.tencent.mm"));
```

## device

```javascript
"use strict";

console.log("screen:", device.width + "x" + device.height);
console.log("sdk:", device.sdkInt);
console.log("brand/model:", device.brand, device.model);
```

## auto / selector / automator

```javascript
"use strict";

var ENABLE_RISKY = false;

if (!auto.service) {
  console.warn("accessibility service is not enabled");
} else {
  console.log("current package:", currentPackage());
  console.log("text node sample:", textMatches(/.*/).findOnce(1000));
}

if (ENABLE_RISKY) {
  // High risk: this moves the device UI. Enable only on a test device.
  click(Math.floor(device.width / 2), Math.floor(device.height / 2));
}
```

## images

```javascript
"use strict";

var ENABLE_SCREEN_CAPTURE = false;

if (ENABLE_SCREEN_CAPTURE) {
  if (!requestScreenCapture(false)) {
    throw new Error("screen capture permission denied");
  }
  var img = captureScreen();
  console.log("capture:", img.getWidth(), img.getHeight());
  img.recycle();
} else {
  console.log("screen capture sample skipped by default");
}
```

## ocr

```javascript
"use strict";

var ENABLE_OCR = false;

if (!ENABLE_OCR) {
  console.log("OCR sample skipped by default; enable on a device with an available OCR engine");
} else {
  var img = images.read(files.path("./OCR/test.png"));
  var result = ocr.detect(img);
  console.log("ocr blocks:", result.length);
  img.recycle();
}
```

## timers / threads

```javascript
"use strict";

var done = false;
threads.start(function () {
  sleep(200);
  console.log("thread done");
  done = true;
});

setTimeout(function () {
  console.log("timer sees done:", done);
}, 500);
sleep(800);
```

## tasks

```javascript
"use strict";

var ENABLE_TASK_CREATE = false;

if (!ENABLE_TASK_CREATE) {
  console.log("task creation skipped by default");
} else {
  var task = tasks.addDisposableTask({
    path: files.path("./runtime-task-smoke.js"),
    date: new Date(Date.now() + 60 * 1000),
  });
  console.log("created task:", task && task.id);
}
```

## floaty

```javascript
"use strict";

var ENABLE_FLOATY = false;

if (!ENABLE_FLOATY) {
  console.log("floaty sample skipped by default");
} else {
  var w = floaty.window(<frame><text text="Runtime regression" /></frame>);
  sleep(1000);
  w.close();
}
```

## shizuku / shell

```javascript
"use strict";

var ENABLE_SHELL = false;
var ENABLE_SHIZUKU = false;

if (ENABLE_SHELL) {
  console.log("shell id:", shell("id", true));
} else {
  console.log("shell sample skipped by default");
}

if (ENABLE_SHIZUKU) {
  console.log("shizuku available:", shizuku.isAvailable && shizuku.isAvailable());
} else {
  console.log("shizuku sample skipped by default");
}
```

## 维护要求

- 如果某个样例因 API 变更需要修改, 同步检查 `ScriptRuntime.kt`、`runtime/api`、`runtime/api/augment`、`app/src/main/assets-app/docs/*.html` 和外部文档仓库。
- 高风险样例进入内置 sample 前必须拆成独立文件, 文件头写明权限、风险和预期输出。
- 如果真实设备不具备权限, 不要把样例失败直接判为代码 bug; 先按诊断模板记录权限状态和系统限制。
