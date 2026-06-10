"auto";

if (!requestScreenCapture()) {
    toast("需要截图权限才能启用 OCR 感知");
    exit();
}

const options = {
    sources: "a11y+ocr",
    interval: 500,
    minConfidence: 0.3,
    region: [0, 0, device.width, device.height],
};

const scene = vision.waitForScene({
    name: "登录页",
    texts: ["账号登录"],
    buttons: ["登录"],
}, Object.assign({ timeout: 8000 }, options));

if (!scene.ok) {
    console.warn("未匹配登录页", scene.missing);
    console.log("当前目标", scene.targets);
    exit();
}

const login = vision.findButton("登录", options);
if (!login) {
    console.warn("未找到登录按钮");
    exit();
}

if (login.selector && login.selector.best) {
    auto.stableClick(login.selector.best, { timeout: 3000, captureSnapshot: true });
} else {
    const action = login.suggestedAction;
    console.log("使用 OCR/CV bounds 兜底点击", action);
    click(action.x, action.y);
}

const observer = vision.observe({
    sources: "a11y+ocr",
    interval: 700,
    maxTargets: 20,
});

try {
    const sample = observer.next();
    console.log("目标数量", sample.targetCount);
    console.log("观察指标", observer.metrics());
} finally {
    observer.close();
}
