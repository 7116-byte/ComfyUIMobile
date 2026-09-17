// Runs the actual embedded JS against a small live-canvas fixture; no ComfyUI job is submitted.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt'), 'utf8');
const match = source.match(/private fun promptApplyScript\(encodedUpdates: String\) = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, 'actual promptApplyScript must exist');
let callbacks = 0;
const widget = {value: 'A', callback: () => callbacks++};
const node = {id: 1, widgets: [widget]};
const context = {window: {__comfyMobileApp: {graph: {_nodes: [node]}}}, TextDecoder, Uint8Array,
  atob: value => Buffer.from(value, 'base64').toString('binary')};
function apply(value) {
  const encoded = Buffer.from(JSON.stringify([{key: '1/model', widgetIndex: 0, value}])).toString('base64');
  const result = JSON.parse(vm.runInNewContext(match[1].replace('$encodedUpdates', encoded), context));
  assert.equal(result.ok, true);
}
apply('A'); assert.equal(callbacks, 0, 'unchanged live widgets must not trigger callbacks');
apply('B'); assert.equal(widget.value, 'B');
apply('A'); assert.equal(widget.value, 'A', 'returning to the original model must update the canvas');
apply(0.5); apply(1); apply(0.5); assert.equal(widget.value, 0.5);
let toggles = 0;
widget.value = {toggled: true};
widget.toggle = value => { toggles++; widget.value.toggled = value; };
apply(true); assert.equal(toggles, 0);
apply(false); apply(true); assert.equal(toggles, 2);
console.log('PASS: actual bridge JS, model A-B-A, decimal reset, unchanged callback guard, group toggles');
