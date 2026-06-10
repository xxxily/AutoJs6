"auto";

console.show();

log("Run details sample started.");
log("Open the editor menu > Run details after this script finishes.");

var checks = [];
if (typeof capabilities !== "undefined") {
    checks = capabilities.check(["a11y", "screen_capture"]);
    log("Capability check result:");
    log(JSON.stringify(checks, null, 2));
}

for (var i = 1; i <= 3; i += 1) {
    log("Step " + i + " / 3");
    sleep(300);
}

try {
    throw new Error("Sample diagnostic error; caught for run-details demonstration.");
} catch (e) {
    console.warn(e.stack || e);
}

if (typeof capabilities !== "undefined") {
    log("Capability audit entries:");
    log(JSON.stringify(capabilities.audit(), null, 2));
}

log("Run details sample finished.");
