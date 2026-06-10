"auto";

auto.waitFor();

var APP_NAME = "示例应用";
var KEYWORD = "AutoJs6";

function assertOk(result, message) {
    if (!result.ok) {
        var snapshot = result.snapshotRef && result.snapshotRef.captured
            ? " snapshot=" + result.snapshotRef.timestamp
            : "";
        throw new Error(message + ": " + result.reason + snapshot);
    }
    return result;
}

app.launchApp(APP_NAME);

assertOk(auto.waitUntil(text("搜索"), {
    timeout: 10000,
    interval: 200,
    stableCount: 2,
}), "首页未就绪");

assertOk(auto.stableClick(text("搜索"), {
    timeout: 5000,
    stableCount: 2,
    captureOnFailure: true,
}), "无法打开搜索入口");

assertOk(auto.stableSetText(id("search_src_text"), KEYWORD, {
    timeout: 5000,
    stableCount: 2,
    retries: 2,
    scrollFind: true,
}), "无法输入搜索词");

var result = assertOk(auto.findWithScroll(textContains(KEYWORD), {
    timeout: 8000,
    maxScrolls: 8,
    stableCount: 2,
    captureOnFailure: true,
}), "未找到搜索结果");

assertOk(auto.stableClick(result.uiObject, {
    timeout: 3000,
    retries: 1,
}), "无法点击搜索结果");
