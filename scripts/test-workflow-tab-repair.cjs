// Regression for ComfyUI frontend 1.52.x leaving deleted workflow paths in
// persisted WebView tab order. The frontend maps those paths to undefined and
// crashes in beforeLoadNewGraph while reading item.path.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const source = fs.readFileSync(
  path.join(__dirname, '../app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt'),
  'utf8',
);

const helperMatch = source.match(
  /const repairOpenWorkflowTabs = \(preferredPath = ''\) => \{([\s\S]*?)\n            \};/,
);
assert.ok(helperMatch, 'workflow tab repair helper must exist in the actual bridge source');
assert.match(helperMatch[1], /filter\(item => item && typeof item\.path === 'string' && item\.path\)/);
assert.match(helperMatch[1], /openWorkflowsInBackground\(\{left: paths, right: \[\]\}\)/);

function repairFixture({preferredPath = '', openWorkflows, activePath = '', workflowPaths = []}) {
  let request;
  const workflowStore = {
    openWorkflows,
    activeWorkflow: activePath ? {path: activePath} : undefined,
    workflows: workflowPaths.map(itemPath => ({path: itemPath})),
    openWorkflowsInBackground(value) { request = value; },
  };
  const repairOpenWorkflowTabs = Function(
    'workflowStore',
    `return (preferredPath = '') => {${helperMatch[1]}\n};`,
  )(workflowStore);
  repairOpenWorkflowTabs(preferredPath);
  return request;
}

assert.deepEqual(
  repairFixture({
    preferredPath: 'workflows/KREA2/current.json',
    openWorkflows: [undefined, {path: 'workflows/KREA2/current.json'}],
  }),
  {left: ['workflows/KREA2/current.json'], right: []},
  'stale undefined tabs must be removed without reading their path',
);

assert.deepEqual(
  repairFixture({openWorkflows: [undefined]}),
  {left: [], right: []},
  'the official cleanup action must still run when every persisted tab is stale',
);

console.log('PASS: stale ComfyUI workflow tabs are filtered before loadGraphData');
