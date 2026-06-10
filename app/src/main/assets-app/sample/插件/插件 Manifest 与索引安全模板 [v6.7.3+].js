"auto";

/**
 * 插件 Manifest 与官方索引安全字段模板
 *
 * 这个示例用于查看插件中心支持的 manifest/schema 字段。
 * 真正的 APK 下载、sha256 校验和证书 pinning 由插件中心完成。
 */

const pluginManifest = {
    pluginType: "ocr",
    capabilities: ["ocr", "screen_capture"],
    permissions: ["android.permission.INTERNET"],
    riskLevel: "medium",
    minAutoJsVersion: "6.7.3",
    documentationUrl: "https://example.com/plugins/paddle-ocr",
    examples: ["https://example.com/samples/ocr.js"],
    engines: [
        {
            id: "paddle-ocr-pp-ocrv5",
            engine: "paddle-ocr",
            variant: "v5",
            label: "PP-OCRv5",
        },
    ],
};

const officialIndexEntry = {
    packageName: "org.autojs.plugin.ocr",
    title: "Paddle OCR",
    description: "OCR plugin",
    manifest: pluginManifest,
    releases: [
        {
            versionName: "1.2.0",
            versionCode: 12,
            versionDate: "2026-06-09",
            apkUrl: "https://example.com/plugin.apk",
            apkSha256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            certificateSha256: [
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            ],
            changelogText: "Declare capabilities and update OCR engine.",
        },
    ],
};

console.log("Plugin manifest:");
console.log(JSON.stringify(pluginManifest, null, 2));

console.log("Official index entry:");
console.log(JSON.stringify(officialIndexEntry, null, 2));

toastLog("已输出插件 manifest 与官方索引安全字段模板");
