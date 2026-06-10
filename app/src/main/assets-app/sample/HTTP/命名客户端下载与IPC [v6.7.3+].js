"auto";

console.show();

log("配置命名 HTTP client");
http.client("demo-api", {
    timeout: 15000,
    maxRetries: 1,
    headers: {
        "X-Agent": "AutoJs6"
    },
    allowedHosts: [
        "github.githubassets.com",
        "example.com"
    ],
    interceptors: [
        { type: "query", name: "from", value: "autojs6" }
    ]
    // 生产环境可为 HTTPS 域名启用证书 pinning:
    // pins: { "api.example.com": ["sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="] }
});

log(JSON.stringify(http.client("demo-api"), null, 2));

let config = storages.namespace("demo-data-network-ipc", "downloads");
config.put("lastClient", "demo-api");
log("命名空间存储 lastClient = " + config.get("lastClient"));

let sub = ipc.subscribe("demo.download", function (msg) {
    log("收到 IPC 消息: " + JSON.stringify(msg.payload));
    if (msg.replyTo) {
        ipc.reply(msg, { accepted: true, at: Date.now() });
    }
});

let request = ipc.request("demo.download", {
    url: "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"
}, {
    replyTo: "demo.download.reply"
});
log("IPC request id = " + request.id);

sleep(200);
log("IPC reply inbox = " + JSON.stringify(ipc.messages("demo.download.reply"), null, 2));
ipc.unsubscribe(sub);

let target = files.path("./github-mark-v673.png");
let task = http.download("https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png", target, {
    id: "demo-github-mark",
    client: "demo-api",
    resume: true
});
log("下载任务: " + JSON.stringify(task, null, 2));

sleep(1000);
let status = http.downloadStatus("demo-github-mark");
log("下载状态: " + JSON.stringify(status, null, 2));

let typed = app.sendTypedBroadcast("demo.download.status", status, {
    action: "org.autojs.autojs6.sample.DOWNLOAD_STATUS",
    requestId: "demo-github-mark"
});
log("typed broadcast = " + JSON.stringify(typed, null, 2));

if (status && status.status === "completed") {
    toast("下载完成: " + target);
} else {
    toast("下载任务已创建, 可用 http.downloadStatus 查询进度");
}
