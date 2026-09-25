const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const source = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/local/comfyuimobile/MainViewModel.kt'), 'utf8');
const start = source.indexOf('private suspend fun installImportedWorkflow(');
const end = source.indexOf('\n    fun exportPreview(', start);
assert.ok(start >= 0 && end > start, 'import function can be inspected');
const importFlow = source.slice(start, end);

assert.doesNotMatch(importFlow, /client\.writeWorkflow\(/,
  'opening an image or local workflow file must not write a server workflow');
assert.match(importFlow, /workflowPath = null/,
  'imports must open a temporary ComfyUI frontend tab');
assert.match(importFlow, /isTemporary = true/,
  'imports must remain marked temporary until explicit Save or Save As');

console.log('PASS: imported image/file workflows stay temporary until explicit save');
