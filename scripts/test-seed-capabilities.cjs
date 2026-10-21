const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const script = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/seed_support.js'), 'utf8');
const window = {};
vm.runInNewContext(script, {window});
const adapter = window.__comfyMobileSeeds;

function rgthree(id) {
  const seed = {name:'seed', type:'number', value:123, options:{min:-1125899906842624,max:1125899906842624}};
  const node = {id, type:'Seed (rgthree)', seedWidget:seed, widgets:[seed], lastSeed:456};
  node.widgets.push({type:'button', name:'🎲 Randomize Each Time', callback() { seed.value = -1; }});
  node.widgets.push({type:'button', name:'🎲 New Fixed Random', callback() { seed.value = 876; }});
  node.lastSeedButton = {type:'button', name:'♻️ 456', callback() { seed.value = node.lastSeed; }};
  node.widgets.push(node.lastSeedButton);
  node.getSeedToUse = () => seed.value === -1 ? ++node.lastSeed : seed.value;
  return node;
}
function promoted(id, child, name='seed') {
  const host = {name, type:'number', value:123, options:{min:0,max:10000}};
  const input = {name, widget:{name}, link:null};
  const childInput = {name:'seed', widget:{name:'seed'}};
  const graph = {_nodes:[child], inputNode:{slots:[{name,linkIds:[10]}]},
    getLink() { return {resolve() { return {inputNode:child,input:childInput}; }}; }};
  child.getWidgetFromSlot = () => child.widgets[0];
  const node = {id,type:'subgraph-uuid',subgraph:graph,inputs:[input],widgets:[host],getSlotFromWidget:()=>input};
  return {node, host};
}
(async () => {
  assert.equal(adapter.actionsFor({type:'Integer',widgets:[]}, {name:'seed_count',value:5}, '1').length, 0);
  assert.equal(adapter.actionsFor({type:'Integer',widgets:[]}, {name:'seed',value:5}, '1').length, 0,
    'a seed-like name alone must never create random capability');
  const child = rgthree(7);
  const root = {_nodes:[child]};
  const actions = adapter.actionsFor(child, child.seedWidget, '7');
  assert.equal(actions.length, 3);
  await adapter.invoke({graph:root}, actions[0]);
  assert.equal(child.seedWidget.value, -1, 'each-time mode uses native -1, not a fixed random number');
  await adapter.invoke({graph:root}, actions[1]);
  assert.equal(child.seedWidget.value, 876);
  await adapter.invoke({graph:root}, actions[2]);
  assert.equal(child.seedWidget.value, 456, 'last-seed button is found even after its label becomes a number');

  const {node,host} = promoted(18,child,'自定义种子别名');
  const app = {graph:{_nodes:[node]}};
  const nestedActions = adapter.actionsFor(node,host,'18');
  assert.equal(nestedActions.length,3);
  await adapter.invoke(app,nestedActions[0]);
  assert.equal(host.value,-1, 'promoted seed writes the authoritative host value');
  assert.equal(child.seedWidget.value,-1);
  const fallback = {workflow:{nodes:[{id:7,type:'Seed (rgthree)',widgets_values:[-1]}]},
    output:{'7':{inputs:{seed:-1}}}};
  adapter.resolveNestedSeeds({graph:root},fallback);
  assert.equal(fallback.output['7'].inputs.seed,457,'missing global rgthree event still uses the native node method');
  child.lastSeed = 456;
  const metadata = {nodes:[{id:18,type:'subgraph-uuid',widgets_values:[-1]}],definitions:{subgraphs:[{
    id:'subgraph-uuid',nodes:[{id:7,type:'Seed (rgthree)',widgets_values:[-1]}],
  }]}};
  const run = {workflow:metadata,output:{'18:7':{inputs:{seed:-1}}}};
  adapter.resolveNestedSeeds(app,run);
  assert.equal(run.output['18:7'].inputs.seed,457);
  assert.equal(metadata.nodes[0].widgets_values[0],457);
  assert.equal(metadata.definitions.subgraphs[0].nodes[0].widgets_values[0],457);
  assert.equal(host.value,-1, 'metadata gets a concrete run seed, editable host stays random');
  assert.equal(child.seedWidget.value,-1);
  adapter.resolveNestedSeeds(app,{workflow:metadata,output:{'18:7':{inputs:{seed:-1}}}});
  assert.equal(child.lastSeed,458, 'consecutive runs resolve different seeds');
  // Frontend 1.55 projects new store-backed widget descriptor objects on every read.
  Object.defineProperty(node,'widgets',{get:()=>[{
    name:host.name, type:'number', options:host.options,
    get value(){return host.value;}, set value(value){host.value=value;},
  }]});
  const transient = adapter.actionsFor(node,node.widgets[0],'18',0);
  await adapter.invoke(app,transient[1]);
  assert.equal(host.value,876,'promoted button targets are index-based, not object-identity-based');
  await adapter.invoke(app,transient[0]);
  assert.equal(host.value,-1);
  const stale = {...nestedActions[0],widgetName:'wrong'};
  await assert.rejects(adapter.invoke(app,stale),/种子控件已变化/);

  let before = 0, after = 0;
  const control = {name:'control_after_generate',type:'combo',value:'randomize',options:{values:['fixed','randomize']},
    beforeQueued(){before++;},afterQueued(){after++;}};
  const seed = {name:'noise_seed',type:'number',value:4,options:{min:0,max:10000},linkedWidgets:[control]};
  const sampler = {id:9,type:'KSampler',widgets:[seed,control]};
  const standard = adapter.actionsFor(sampler,seed,'9');
  assert.equal(standard.length,2);
  assert.equal(standard[0].enabled,false, 'current mode is visibly selected');
  await adapter.invoke({graph:{_nodes:[sampler]}},standard[1]);
  assert.equal(control.value,'fixed');
  const promotedSampler = promoted(28,sampler);
  const nestedApp = {graph:{_nodes:[promotedSampler.node]}};
  control.value='randomize';
  adapter.runHooks(nestedApp,'beforeQueued');
  assert.equal(before,1,'subgraph native before-queue hook runs');
  assert.equal(promotedSampler.host.value,123,'after mode does not advance before queue');
  adapter.runHooks(nestedApp,'afterQueued');
  assert.equal(after,1,'subgraph native after-queue hook runs');
  assert.ok(promotedSampler.host.value>=0 && promotedSampler.host.value<=10000);
  const first=promotedSampler.host.value;
  control.value='increment';
  adapter.runHooks(nestedApp,'afterQueued');
  assert.equal(promotedSampler.host.value,Math.min(10000,first+1));
  nestedApp.ui={settings:{getSettingValue:()=> 'before'}};
  const start=promotedSampler.host.value;
  adapter.runHooks(nestedApp,'beforeQueued');
  assert.equal(promotedSampler.host.value,start,'before mode skips its first execution like ComfyUI');
  adapter.runHooks(nestedApp,'beforeQueued');
  assert.equal(promotedSampler.host.value,Math.min(10000,start+1));
  console.log('PASS: native seed capability, -1, fixed/last seed, promoted aliases, concrete metadata, repeated queue hooks, stale action guard');
})().catch(error => {console.error(error);process.exitCode=1;});
