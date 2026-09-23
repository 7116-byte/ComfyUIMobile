const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const source = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt'), 'utf8');
const match = source.match(/private val PROMPT_CONVERT_SCRIPT = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, 'production prompt conversion script exists');

async function run(seedMode) {
  const widget = { name: 'seed', value: seedMode, serializeValue: () => seedMode };
  const node = { id: 477, type: 'Seed (rgthree)', widgets: [widget] };
  const app = { graph: { _nodes: [node] }, graphToPrompt: async () => ({
    output: { '477': { class_type: 'Seed (rgthree)', inputs: { seed: seedMode } } },
    workflow: { nodes: [{ id: 477, type: 'Seed (rgthree)', widgets_values: [seedMode] }] },
  }) };
  let events = 0;
  const context = { window: {
    __comfyMobileApp: app,
    rgthree: { dispatchCustomEvent(name, detail) {
      assert.equal(name, 'comfy-api-queue-prompt-before');
      events++;
      if (detail.output['477'].inputs.seed === -1) {
        detail.output['477'].inputs.seed = 123456;
        detail.workflow.nodes[0].widgets_values[0] = 123456;
      }
    } },
  } };
  const result = JSON.parse(await vm.runInNewContext(match[1], context));
  assert.equal(result.ok, true, result.error);
  assert.equal(events, 1);
  assert.equal(result.prompt['477'].inputs.seed, seedMode === -1 ? 123456 : seedMode);
  assert.equal(widget.value, seedMode, 'the editable graph keeps its seed mode');
}

Promise.all([run(-1), run(777)]).then(() => {
  console.log('PASS: rgthree pre-queue event resolves random seed without changing editable widget');
}).catch(error => { console.error(error); process.exitCode = 1; });
