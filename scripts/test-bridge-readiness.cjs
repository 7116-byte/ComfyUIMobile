// Exercise the embedded frontend readiness probes without loading/mutating a graph.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt'), 'utf8');
function script(name) {
  const match = source.match(new RegExp(`private val ${name} = """([\\s\\S]*?)"""\\.trimIndent\\(\\)`));
  assert.ok(match, `${name} exists in the production bridge`);
  return match[1];
}
function fixture() {
  let clock = 0;
  let graphReady = true;
  let prematureGraphReads = 0;
  const graph = {nodes: [{id: 7, value: 0.54}]};
  const app = {
    get isGraphReady() { return graphReady; },
    get rootGraph() {
      if (!graphReady) prematureGraphReads++;
      return graphReady ? graph : undefined;
    },
    get graph() { return graphReady ? graph : undefined; },
    vueAppReady: true,
    ui: {settings: {getSettingValue: name => name === 'Comfy.Locale' ? 'zh' : false}},
    extensionManager: {spinner: false, workflow: {
      getWorkflowByPath() {}, syncWorkflows() {},
      activeWorkflow: {path: 'workflows/example.json'}, workflows: [],
    }},
  };
  return {app, graph, tick: () => { clock += 800; },
    setGraphReady: value => { graphReady = value; },
    prematureGraphReads: () => prematureGraphReads, context: {
    window: {comfyAPI: {app: {app}}, LiteGraph: {vueNodesMode: false}},
    document: {querySelectorAll: () => []}, Date: {now: () => clock},
  }};
}
async function main() {
  const f = fixture();
  const reuse = () => vm.runInNewContext(script('REUSE_SCRIPT'), f.context) === 'ready';
  assert.equal(reuse(), false, 'an uninitialized context cannot skip readiness');
  const ready = () => vm.runInNewContext(script('READY_SCRIPT'), f.context).then(JSON.parse);
  f.setGraphReady(false);
  const waitingForGraph = await ready();
  assert.equal(waitingForGraph.details.graphReady, false);
  assert.equal(f.prematureGraphReads(), 0, 'readiness probes must not access rootGraph before initialization');
  f.setGraphReady(true);
  const cold = await ready();
  assert.equal(cold.ok, false, 'cold page waits for its first stable state');
  assert.equal(cold.details.graphReady, true, 'wait logs distinguish graph from workflow readiness');
  assert.equal(cold.details.spinner, false);
  f.tick();
  assert.equal((await ready()).ok, true);
  assert.equal(reuse(), true, 'a healthy page can reconnect without reloading');
  assert.equal(f.app.rootGraph, f.graph);
  assert.equal(f.graph.nodes[0].value, 0.54, 'probe must preserve unsaved parameters');
  f.app.extensionManager.spinner = true;
  assert.equal(reuse(), false, 'an in-progress graph restore must not be treated as ready');
  const busy = await ready();
  assert.equal(busy.details.spinner, true);
  assert.match(busy.error, /spinner=true/, 'busy state is reported without guessing tab restoration');
  f.app.extensionManager.spinner = false;
  f.context.window.__comfyMobileApp = {};
  assert.equal(reuse(), false, 'old app object after navigation cannot be reused');
  f.context.window.__comfyMobileApp = f.app;
  f.context.window.LiteGraph.vueNodesMode = true;
  assert.equal(reuse(), false, 'classic-canvas compatibility checks remain enforced');
  f.context.window.LiteGraph.vueNodesMode = false;
  f.app.vueAppReady = false;
  assert.equal(reuse(), false);
  console.log('PASS: cold initialization, warm reconnect, graph preservation, invalid context and renderer guards');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
