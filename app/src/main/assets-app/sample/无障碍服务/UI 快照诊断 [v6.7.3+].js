/**
 * UI snapshot diagnostics.
 *
 * This sample exports before/after UI tree snapshots and a diff report when an
 * automation step fails. Enable accessibility service before running it.
 */

auto.waitFor();

const OUTPUT_DIR = files.join(files.getSdcardPath(), 'AutoJs6', 'ui-snapshot-diagnostics');
files.ensureDir(OUTPUT_DIR);

function snapshot() {
    return auto.snapshot({
        redact: true,
        includeWindows: true,
        maxDepth: 50,
        maxNodes: 2000,
    });
}

function writeJson(name, data) {
    const path = files.join(OUTPUT_DIR, name);
    files.write(path, JSON.stringify(data, null, 2));
    return path;
}

const before = snapshot();

try {
    const ok = click('确定');
    if (!ok) {
        throw new Error('Cannot click target text: 确定');
    }
    sleep(500);
    const after = snapshot();
    const diff = auto.diffSnapshot(before, after);
    toastLog('UI snapshot diff changed nodes: ' + diff.changedCount);
} catch (error) {
    const after = snapshot();
    const diff = auto.diffSnapshot(before, after);
    const filesWritten = [
        writeJson('before.json', before),
        writeJson('after.json', after),
        writeJson('diff.json', diff),
        writeJson('error.json', {
            message: String(error && error.message || error),
            stack: String(error && error.stack || ''),
            currentPackage: currentPackage(),
            currentActivity: currentActivity(),
        }),
    ];
    console.error(error);
    toastLog('UI snapshot diagnostics exported: ' + filesWritten.join(', '));
}
