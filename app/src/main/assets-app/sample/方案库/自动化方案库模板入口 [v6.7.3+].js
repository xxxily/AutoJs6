"use strict";

/*
 * 自动化方案库模板入口
 *
 * 本示例只打印可复制模板, 不会直接执行点击、截图、定时任务或 Shizuku 操作。
 * 使用方式:
 * 1. 在控制台查看需要的方案名称。
 * 2. 复制对应 template 到自己的项目脚本。
 * 3. 按目标 App 替换变量、选择器、图片路径和 project.json 能力声明。
 */

console.show();

var SOLUTIONS = [
    {
        name: "App 启动与等待",
        capabilities: ["accessibility"],
        riskLevel: "medium",
        template: [
            '"auto";',
            'var APP_NAME = "目标应用";',
            'var READY_TEXT = "首页";',
            'if (!app.launchApp(APP_NAME)) throw new Error("无法启动应用: " + APP_NAME);',
            'var ready = auto.waitUntil(text(READY_TEXT), { timeout: 12000, stableCount: 2, captureOnFailure: true });',
            'if (!ready.ok) throw new Error("首页未就绪: " + ready.reason);'
        ]
    },
    {
        name: "列表滚动查找",
        capabilities: ["accessibility"],
        riskLevel: "medium",
        template: [
            '"auto";',
            'var KEYWORD = "目标文本";',
            'var item = auto.findWithScroll(textContains(KEYWORD), null, { timeout: 15000, maxScrolls: 12, captureOnFailure: true });',
            'if (!item.ok) throw new Error("未找到列表项: " + item.reason);',
            'var clicked = auto.stableClick(item.uiObject, { timeout: 3000 });',
            'if (!clicked.ok) throw new Error("列表项点击失败: " + clicked.reason);'
        ]
    },
    {
        name: "表单填写",
        capabilities: ["accessibility"],
        riskLevel: "medium",
        template: [
            '"auto";',
            'var account = auto.stableSetText(id("account"), "demo@example.com", { timeout: 8000 });',
            'if (!account.ok) throw new Error("账号输入失败: " + account.reason);',
            'var password = auto.stableSetText(id("password"), "change-me", { timeout: 8000 });',
            'if (!password.ok) throw new Error("密码输入失败: " + password.reason);',
            'var submit = auto.stableClick(text("登录"), { timeout: 8000, stableCount: 2 });',
            'if (!submit.ok) throw new Error("提交失败: " + submit.reason);'
        ]
    },
    {
        name: "OCR 文字点击",
        capabilities: ["accessibility", "screen_capture", "ocr"],
        riskLevel: "medium",
        template: [
            '"auto";',
            'var target = vision.findText("确定", { sources: "a11y+ocr", timeout: 6000, minConfidence: 0.35 });',
            'if (!target) throw new Error("未通过 OCR/无障碍找到目标文字");',
            'if (target.selector) {',
            '    var clicked = auto.stableClick(target.selector, { timeout: 3000 });',
            '    if (!clicked.ok) throw new Error("selector 点击失败: " + clicked.reason);',
            '} else {',
            '    var action = target.suggestedAction;',
            '    click(action.x, action.y);',
            '}'
        ]
    },
    {
        name: "截图找图",
        capabilities: ["screen_capture", "storage"],
        riskLevel: "medium",
        projectJson: {
            capabilities: ["screen_capture", "storage"],
            assets: ["assets/button.png"]
        },
        template: [
            'if (!requestScreenCapture()) throw new Error("截图权限请求失败");',
            'var template = images.read("./assets/button.png");',
            'if (!template) throw new Error("模板图片不存在");',
            'try {',
            '    var point = images.findImage(captureScreen(), template, { threshold: 0.82 });',
            '    if (!point) throw new Error("未找到模板图");',
            '    click(point.x, point.y);',
            '} finally {',
            '    template.recycle();',
            '}'
        ]
    },
    {
        name: "定时任务",
        capabilities: ["background_run", "exact_alarm"],
        riskLevel: "medium",
        projectJson: {
            capabilities: ["background_run", "exact_alarm"]
        },
        template: [
            'var task = tasks.addDailyTask({',
            '    path: files.join(files.cwd(), "daily-sync.js"),',
            '    time: "08:30",',
            '    maxRetries: 2,',
            '    retryBackoffMillis: 60 * 1000,',
            '    mutex: "daily-sync",',
            '    timeoutMillis: 5 * 60 * 1000',
            '});',
            'log(JSON.stringify(tasks.queryTimedTaskRuns({ taskId: task.id, limit: 5 })));'
        ]
    },
    {
        name: "Shizuku 应用管理",
        capabilities: ["shizuku"],
        riskLevel: "high",
        projectJson: {
            capabilities: ["shizuku"],
            privilegedPolicy: {
                shizuku: true,
                backend: "shizuku"
            },
            riskPolicy: {
                high: "prompt",
                critical: "reject",
                rememberAllowed: true
            }
        },
        template: [
            '"auto";',
            'var PACKAGE = "com.example.target";',
            'var state = shizuku.state();',
            'if (!state.available) throw new Error("Shizuku 不可用: " + state.reason);',
            'log(JSON.stringify(shizuku.package.info(PACKAGE)));',
            'log(JSON.stringify(shizuku.app.forceStop(PACKAGE)));'
        ]
    },
    {
        name: "插件 OCR",
        capabilities: ["ocr", "screen_capture"],
        riskLevel: "medium",
        projectJson: {
            capabilities: ["ocr", "screen_capture"],
            pluginDependencies: ["org.autojs.autojs.plugin.paddle.ocr"]
        },
        template: [
            'console.show();',
            'var img = images.read("./test.png");',
            'if (!img) throw new Error("图片不存在: ./test.png");',
            'try {',
            '    var results = ocr.paddle.detect(img, { useSlim: true, cpuThreadNum: 4 });',
            '    log(JSON.stringify(Array.from(results)));',
            '} finally {',
            '    img.recycle();',
            '}'
        ]
    }
];

SOLUTIONS.forEach(function (solution, index) {
    console.log("");
    console.log((index + 1) + ". " + solution.name);
    console.log("capabilities: " + solution.capabilities.join(", "));
    console.log("riskLevel: " + solution.riskLevel);
    if (solution.projectJson) {
        console.log("project.json: " + JSON.stringify(solution.projectJson, null, 2));
    }
    console.log(solution.template.join("\n"));
});

toast("自动化方案库模板已输出到控制台");
