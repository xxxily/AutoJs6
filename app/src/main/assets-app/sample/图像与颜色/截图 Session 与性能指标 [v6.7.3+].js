/**
 * 截图 Session 与性能指标
 *
 * 演示 images.openCaptureSession(options):
 * - preset: single / ocr / color / low_power
 * - session.nextFrame(timeout)
 * - session.latest()
 * - session.lastError()
 * - session.metrics()
 * - session.close()
 */

if (!requestScreenCapture()) {
    toast("请求截图失败");
    exit();
}

function logFrame(session, timeout) {
    let img = session.nextFrame(timeout || 1500);
    if (!img) {
        log("截图失败: " + JSON.stringify(session.lastError()));
        return null;
    }
    log("截图成功: " + img.getWidth() + "x" + img.getHeight());
    return img;
}

function runSinglePreset() {
    let session = images.openCaptureSession("single");
    try {
        let img = logFrame(session, 1500);
        if (img) {
            img.recycle();
        }
        log("single metrics: " + JSON.stringify(session.metrics()));
    } finally {
        session.close();
    }
}

function runOcrPreset() {
    let session = images.openCaptureSession({
        preset: "ocr",
        cacheSize: 2,
        timeout: 1800,
        interval: 300
    });
    try {
        for (let i = 0; i < 3; i++) {
            let img = logFrame(session, 2000);
            if (img) {
                // 可在这里调用 ocr.recognizeText(img) 或 ocr.detect(img).
                img.recycle();
            }
        }
        log("ocr metrics: " + JSON.stringify(session.metrics()));
    } finally {
        session.close();
    }
}

function runColorPreset() {
    let session = images.openCaptureSession({
        preset: "color",
        cacheSize: 2,
        timeout: 1200,
        interval: 16
    });
    try {
        let img = logFrame(session, 1500);
        if (img) {
            let point = images.findColor(img, "#ffffff");
            log("白色位置: " + point);
            img.recycle();
        }
        log("color metrics: " + JSON.stringify(session.metrics()));
    } finally {
        session.close();
    }
}

function runLowPowerPreset() {
    let session = images.openCaptureSession({
        preset: "low_power",
        timeout: 2500,
        interval: 1000
    });
    try {
        for (let i = 0; i < 2; i++) {
            let img = logFrame(session, 2500);
            if (img) {
                img.recycle();
            }
        }
        let latest = session.latest();
        if (latest) {
            log("latest: " + latest.getWidth() + "x" + latest.getHeight());
            latest.recycle();
        }
        log("low_power metrics: " + JSON.stringify(session.metrics()));
    } finally {
        session.close();
    }
}

runSinglePreset();
runOcrPreset();
runColorPreset();
runLowPowerPreset();
