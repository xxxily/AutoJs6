/*
 * AI Copilot 二期回归样例
 *
 * 这个脚本本身是安全可运行的说明样例, 用来记录 AI 结果应用前的验收规则:
 *
 * 1. 不存在 API 拦截:
 *    - 如果 AI 结果中出现 missingApi(), 且本地能力索引没有该 API, 预览应阻断应用。
 *
 * 2. 高风险必须声明:
 *    - 如果 AI 结果包含 shell('pm uninstall ...'), risks/requirements 必须声明 Shell 和安装/卸载风险。
 *    - 未声明时只能关闭预览或重新生成, 不能继续应用。
 *
 * 3. 最小补丁与上下文漂移:
 *    - 修改选区应使用 operation=replace_selection, 只替换选中内容。
 *    - 修改当前文件应优先使用 operation=patch 的 unified diff。
 *    - 发送请求后用户又编辑了文件, 或 diff hunk 上下文不匹配时, 补丁必须拒绝应用。
 *
 * 4. 隐私上下文:
 *    - 文件、日志、剪贴板、UI 快照、截图、OCR 摘要均受设置控制。
 *    - 剪贴板、UI 快照、截图、OCR 默认不发送, 开启后发送前仍要确认摘要。
 */

'use strict';

console.show();
console.log('AI Copilot v2 regression sample');
console.log('Expected: unknown APIs are blocked before apply.');
console.log('Expected: risky capabilities must be declared.');
console.log('Expected: patches are rejected when editor context drifts.');
toast('AI Copilot 二期回归样例已运行');
