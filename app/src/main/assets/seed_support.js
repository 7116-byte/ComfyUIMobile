// Adapter for real ComfyUI widgets. Never infer random capability from a field name.
(() => {
  if (window.__comfyMobileSeeds) return;
  const typeOf = node => String(node?.comfyClass || node?.type || '');
  const isRgthree = node => typeOf(node) === 'Seed (rgthree)';
  const buttons = [
    ['Randomize Each Time', '每次生成随机'],
    ['New Fixed Random', '生成新的固定种子'],
    ['Use Last Queued Seed', '使用上次排队种子'],
  ];
  const getNode = (graph, path) => {
    let node;
    for (const id of String(path).split(':')) {
      node = (graph?._nodes || []).find(item => String(item.id) === id);
      if (!node) return null;
      graph = node.subgraph;
    }
    return node;
  };
  const sourceFor = (node, widget, path, seen = new Set()) => {
    if (!node?.subgraph || seen.has(node)) return {node, widget, path};
    const nextSeen = new Set(seen).add(node);
    const input = node.getSlotFromWidget?.(widget) ||
      (node.inputs || []).find(item => item.widget?.name === widget.name || item.name === widget.name);
    const slot = input?._subgraphSlot || node.subgraph.inputNode?.slots?.find(item => item.name === input?.name);
    const targets = [];
    for (const id of slot?.linkIds || []) {
      const link = node.subgraph.getLink?.(id) || node.subgraph.links?.get?.(id) || node.subgraph.links?.[id];
      const resolved = link?.resolve?.(node.subgraph);
      const child = resolved?.inputNode || node.subgraph.getNodeById?.(link?.target_id);
      const childInput = resolved?.input || child?.inputs?.[link?.target_slot];
      const childWidget = child?.getWidgetFromSlot?.(childInput) ||
        child?.widgets?.find(item => item.name === (childInput?.widget?.name || childInput?.name));
      if (child && childWidget) targets.push(sourceFor(child, childWidget, path + ':' + child.id, nextSeen));
    }
    // A fan-out promotion has no single authoritative button/control. Do not guess.
    return targets.length === 1 ? targets[0] : {node, widget, path};
  };
  const controlFor = widget => (widget?.linkedWidgets || []).find(item =>
    item.name === 'control_after_generate' &&
    Array.isArray(item.options?.values) && item.options.values.includes('randomize') &&
    item.options.values.includes('fixed'));
  const actionsFor = (node, widget, path, hostWidgetIndex = -1) => {
    if (typeof widget?.value !== 'number') return [];
    const source = sourceFor(node, widget, path);
    if (isRgthree(source.node) && (source.node.seedWidget === source.widget || source.widget.name === 'seed')) {
      return buttons.flatMap(([token, label]) => {
        const button = token === 'Use Last Queued Seed' && source.node.lastSeedButton ||
          (source.node.widgets || []).find(item => item.type === 'button' && String(item.name).includes(token));
        if (!button || typeof (button.callback || button.onClick) !== 'function') return [];
        return [{nodePath:source.path, widgetIndex:source.node.widgets.indexOf(button),
          hostNodePath:path, hostWidgetIndex:hostWidgetIndex >= 0 ? hostWidgetIndex :
            node.widgets.findIndex(item => item.widgetId ? item.widgetId === widget.widgetId : item.name === widget.name),
          hostWidgetName:widget.name,
          widgetName:button.name, kind:'button', token, label, enabled:!button.disabled}];
      });
    }
    const control = controlFor(source.widget);
    if (!control || source.node.widgets.indexOf(control) < 0) return [];
    return [['randomize', '每次生成随机'], ['fixed', '固定当前种子']].map(([value, label]) => ({
      nodePath:source.path, widgetIndex:source.node.widgets.indexOf(control), widgetName:control.name,
      kind:'control', value, label, enabled:control.value !== value,
    }));
  };
  const walk = (graph, prefix = '', ancestors = new Set()) => {
    if (!graph || ancestors.has(graph)) return [];
    const next = new Set(ancestors).add(graph);
    return (graph._nodes || []).filter(node => ![2, 4].includes(Number(node.mode || 0))).flatMap(node => {
      const path = prefix + node.id;
      return [{node, path}, ...walk(node.subgraph, path + ':', next)];
    });
  };
  const executed = new WeakMap();
  const promotedControl = (node, phase, runBefore) => {
    if (!node.subgraph || (phase === 'beforeQueued') !== runBefore) return;
    for (const widget of node.widgets || []) {
      const input = node.getSlotFromWidget?.(widget) ||
        (node.inputs || []).find(item => item.widget?.name === widget.name || item.name === widget.name);
      if (input?.link != null) continue;
      const source = sourceFor(node, widget, String(node.id));
      const control = source.node !== node ? controlFor(source.widget) : null;
      if (!control || typeof widget.value !== 'number') continue;
      const key = widget.widgetId || widget.name;
      const previous = executed.get(node) || new Set();
      if (runBefore && !previous.has(key)) {
        previous.add(key);
        executed.set(node, previous);
        continue;
      }
      const min = Math.max(-1125899906842624, Number(widget.options?.min ?? 0));
      const max = Math.min(1125899906842624, Number(widget.options?.max ?? 1125899906842624));
      const step = Number(widget.options?.step2 || 1);
      let value = widget.value;
      if (control.value === 'randomize') value = Math.floor(Math.random() * ((max - min) / step)) * step + min;
      else if (control.value === 'increment' || control.value === 'increment-wrap') value += step;
      else if (control.value === 'decrement') value -= step;
      else continue;
      value = Math.max(min, Math.min(max, value));
      widget.value = value;
      widget.callback?.(value);
    }
  };
  const runHooks = (app, phase) => {
    const runBefore = app.ui?.settings?.getSettingValue?.('Comfy.WidgetControlMode') === 'before';
    const seenWidgets = new Set();
    for (const {node} of walk(app.rootGraph || app.graph)) {
      for (const widget of node.widgets || []) {
        if (seenWidgets.has(widget)) continue;
        seenWidgets.add(widget);
        if (typeof widget[phase] === 'function') widget[phase]({isPartialExecution:false});
      }
      promotedControl(node, phase, runBefore);
    }
  };
  const serializedNode = (workflow, path) => {
    let graph = workflow;
    let node;
    for (const id of String(path).split(':')) {
      node = graph?.nodes?.find(item => String(item.id) === id);
      if (!node) return null;
      graph = (graph.definitions?.subgraphs || workflow.definitions?.subgraphs || []).find(item => item.id === node.type);
    }
    return node;
  };
  const resolveNestedSeeds = (app, result) => {
    const graph = app.rootGraph || app.graph;
    for (const {node, path} of walk(graph)) {
      if (!isRgthree(node)) continue;
      const output = result.output?.[path];
      const widget = node.seedWidget;
      if (!widget || !output?.inputs || typeof output.inputs[widget.name] !== 'number') continue;
      // The native rgthree event has already handled ordinary root nodes.
      if (!path.includes(':') && output.inputs[widget.name] >= 0) continue;
      if (typeof node.getSeedToUse !== 'function') throw new Error('子图随机种子节点未提供原生种子处理方法');
      const previous = widget.value;
      let value;
      try {
        widget.value = output.inputs[widget.name];
        value = node.getSeedToUse();
      } finally { widget.value = previous; }
      if (!Number.isSafeInteger(value) || value < 0) throw new Error('子图随机种子未能转换成有效种子');
      output.inputs[widget.name] = value;
      node.lastSeed = value;
      if (node.lastSeedButton) {
        node.lastSeedButton.disabled = value === previous;
        node.lastSeedButton.name = value === previous ? '♻️ (Use Last Queued Seed)' : '♻️ ' + value;
      }
      const savedNode = serializedNode(result.workflow, path);
      if (savedNode?.widgets_values) savedNode.widgets_values[node.widgets.indexOf(widget)] = value;
      // Store the concrete run seed on each exposed host, without changing its editable mode.
      for (const entry of walk(graph)) {
        if (!entry.node.subgraph) continue;
        (entry.node.widgets || []).forEach((hostWidget, index) => {
          const source = sourceFor(entry.node, hostWidget, entry.path);
          if (source.path !== path || source.widget !== widget) return;
          const host = serializedNode(result.workflow, entry.path);
          if (host?.widgets_values) host.widgets_values[index] = value;
        });
      }
    }
  };
  const invoke = async (app, action) => {
    const node = getNode(app.rootGraph || app.graph, action.nodePath);
    const widget = node?.widgets?.[action.widgetIndex];
    if (!widget || widget.name !== action.widgetName) throw new Error('种子控件已变化，请重新读取参数');
    if (action.kind === 'button') {
      if (!isRgthree(node) || widget.type !== 'button' || widget.disabled ||
          !buttons.some(([token]) => token === action.token)) throw new Error('当前种子操作不可用');
      const expected = action.token === 'Use Last Queued Seed' ? node.lastSeedButton === widget ||
        String(widget.name).includes(action.token) : String(widget.name).includes(action.token);
      if (!expected) throw new Error('种子按钮不匹配');
      await (widget.callback || widget.onClick).call(widget);
      if (action.hostNodePath !== action.nodePath) {
        const host = getNode(app.rootGraph || app.graph, action.hostNodePath);
        const target = host?.widgets?.[action.hostWidgetIndex];
        if (!target || target.name !== action.hostWidgetName) throw new Error('子图种子控件已变化');
        target.value = node.seedWidget.value;
        target.callback?.(target.value);
      }
    } else if (action.kind === 'control' && widget.name === 'control_after_generate' &&
        ['randomize', 'fixed'].includes(action.value) && widget.options?.values?.includes(action.value)) {
      widget.value = action.value;
      widget.callback?.(action.value);
    } else throw new Error('此控件不支持该种子操作');
    (app.rootGraph || app.graph).setDirtyCanvas?.(true, true);
  };
  window.__comfyMobileSeeds = {actionsFor, runHooks, invoke, sourceFor, walk, resolveNestedSeeds};
})();
