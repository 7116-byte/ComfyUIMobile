const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root,'app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt'),'utf8');
const support = fs.readFileSync(path.join(root,'app/src/main/assets/seed_support.js'),'utf8');
const match = source.match(/private fun workflowManifestScript\([\s\S]*?\) = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match);
let callbacks=0;
const seed={name:'seed',type:'number',value:-1,options:{min:-1125899906842624,max:1125899906842624},callback(){callbacks++;}};
const button={type:'button',name:'🎲 Randomize Each Time',callback(){seed.value=-1;}};
const node={id:1,type:'Seed (rgthree)',seedWidget:seed,widgets:[seed,button],outputs:[{type:'INT'}]};
const output={id:2,type:'SaveImage',widgets:[],inputs:[{name:'images',type:'INT',link:10}]};
const graph={_nodes:[node,output],links:new Map([[10,{origin_id:1,origin_slot:0,target_id:2,target_slot:0,type:'INT'}]])};
const workflow={nodes:[{id:1,type:node.type,widgets_values:[-1]},{id:2,type:output.type}],links:[[10,1,0,2,0,'INT']]};
const persisted={path:'workflows/test.json',isLoaded:true,activeState:workflow};
const store={activeWorkflow:persisted,openWorkflows:[persisted],workflows:[persisted],
  openWorkflowsInBackground(){},getWorkflowByPath:()=>persisted,syncWorkflows:async()=>{}};
const app={graph,extensionManager:{workflow:store},loadGraphData:async()=>{}};
function script(value) {
  workflow.nodes[0].widgets_values[0]=value;
  return match[1]
    .replace('$encodedWorkflow',Buffer.from(JSON.stringify(workflow)).toString('base64'))
    .replace('$seedSupportScript',support)
    .replace(/\$\{encodedWorkflowPath\?\.let\s*\{[^\n]*\}[^\n]*\}/,"'workflows/test.json'")
    .replaceAll('$inspectCurrentGraph','false').replaceAll('$nativeWorkflowOpen','false');
}
const context={window:{__comfyMobileApp:app}, TextDecoder,Uint8Array,Map,Set,structuredClone,
  atob:s=>Buffer.from(s,'base64').toString('binary'),setTimeout:fn=>fn()};
(async()=>{
  const initial=JSON.parse(await vm.runInNewContext(script(-1),context));
  assert.equal(initial.ok,true,initial.error);
  assert.equal(callbacks,0,'reading unchanged parameters must not invoke mutation callbacks');
  assert.equal(initial.fields[0].actions[0].token,'Randomize Each Time');
  const changed=JSON.parse(await vm.runInNewContext(script(777),context));
  assert.equal(changed.ok,true,changed.error);
  assert.equal(callbacks,1,'actual working-copy edits still invoke the existing callback');
  assert.equal(changed.fields[0].value,777);
  console.log('PASS: production manifest, native actions, unchanged callback guard, changed-value callback');
})().catch(error=>{console.error(error);process.exitCode=1;});
