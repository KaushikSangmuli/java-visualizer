import { useEffect, useMemo, useState } from 'react';
import TreeDiagram from './TreeDiagram.jsx';
import { collectionTreeNodes, isTreeNodeObject, namesByReference, treeRoots, userTreeNodes } from '../treeModel.js';
import { innermostLoopAt, loopChain, loopKindLabel, loopVariableHistory } from '../loopHistory.js';

const referenceColors = ['#4d88e8', '#16a085', '#d0783e', '#9866cf', '#d15478', '#1688a8', '#748d25', '#c15d45'];

function colorForId(id) {
  const hash = String(id).split('').reduce((total, char) => total * 31 + char.charCodeAt(0), 7);
  return referenceColors[Math.abs(hash) % referenceColors.length];
}

function displayType(type = '') {
  return type.replaceAll('$', '.').replace(/^java\.lang\./, '');
}

function valueShape(value) {
  const type = value?.type ?? '';
  if (type.endsWith('[]')) return 'array';
  if (type === 'java.lang.String' || type === 'String') return 'string';
  if (value?.referenceId) return 'reference';
  return 'primitive';
}

// `compact` is used for array elements: a reference shows only its colour-coded id (#463),
// because the element type is already shown once in the array's header.
function Value({ value, onReference, className = '', compact = false }) {
  if (!value) return <span className="muted">—</span>;
  if (compact && value.referenceId && value.type !== 'java.lang.String' && value.type !== 'String') {
    return <button className={`value-reference compact-reference ${className}`} style={{ '--reference-color': colorForId(value.referenceId) }} title={`${displayType(value.value)} · reference ${value.referenceId}`} onClick={() => onReference(value.referenceId)} type="button"><span className="value-shape compact">#{value.referenceId}</span></button>;
  }
  const shape = valueShape(value);
  const style = value.referenceId ? { '--reference-color': colorForId(value.referenceId) } : undefined;
  const shownValue = shape === 'reference' ? `@${value.referenceId}` : value.referenceId ? displayType(value.value ?? 'null') : value.value ?? 'null';
  const contents = <><span className={`value-shape ${shape}`} aria-hidden="true">{shape === 'reference' ? `#${value.referenceId}` : shownValue}</span>{shape === 'reference' && <span className="reference-caption">{displayType(value.type)}</span>}</>;
  if (value.referenceId) {
    return <button className={`value-reference ${className}`} style={style} title={`${displayType(value.value)} · reference ${value.referenceId}`} onClick={() => onReference(value.referenceId)} type="button">{contents}</button>;
  }
  return <span className={`value-display ${shape} ${className}`} title={`${value.type}: ${shownValue}`}>{contents}</span>;
}

// ---------------------------------------------------------------- collections
// java.util collections arrive with a DSA view from the backend (`object.collection`): only the
// elements, the size and the shape that matters while dry-running code.

const shortId = (id) => (id ? `#${id}` : '∅');

function CollectionItem({ value, onReference }) {
  return <Value compact value={{ ...value, type: displayType(value?.type) }} onReference={onReference} />;
}

function LinkedNodes({ collection, onReference }) {
  const nodes = collection.nodes ?? [];
  if (nodes.length === 0) return <span className="collection-empty">empty</span>;
  return <div className="linked-nodes" role="list">
    {nodes.map((node, index) => <div className="linked-node-wrap" role="listitem" key={node.id}>
      {index > 0 && <span className="linked-arrow" aria-hidden="true">⇄</span>}
      <div className="linked-node">
        <span className="linked-node-labels">{index === 0 && <b>first</b>}<small>{shortId(node.id)}</small>{index === nodes.length - 1 && <b>last</b>}</span>
        <div className="linked-node-cells">
          <span className="linked-cell pointer" title="prev node address">{shortId(node.prevId)}</span>
          <span className="linked-cell value" title="value"><CollectionItem value={node.value} onReference={onReference} /></span>
          <span className="linked-cell pointer" title="next node address">{shortId(node.nextId)}</span>
        </div>
      </div>
    </div>)}
  </div>;
}

export function CollectionView({ collection, onReference }) {
  const items = collection.items ?? [];
  const entries = collection.entries ?? [];
  const view = collection.view;
  const shown = view === 'map' ? entries.length : view === 'linked-list' ? (collection.nodes ?? []).length : items.length;
  const empty = collection.size === 0;
  let body;
  if (empty) {
    body = <span className="collection-empty">empty</span>;
  } else if (view === 'linked-list') {
    body = <LinkedNodes collection={collection} onReference={onReference} />;
  } else if (view === 'map') {
    body = <div className="collection-map" role="table">
      {entries.map((entry, index) => <div className="map-entry" role="row" key={index}>
        <span className="map-key" role="cell"><CollectionItem value={entry.key} onReference={onReference} /></span>
        <span className="map-value" role="cell"><CollectionItem value={entry.value} onReference={onReference} /></span>
      </div>)}
    </div>;
  } else if (view === 'set') {
    body = <div className="collection-set">{'{'}{items.map((item, index) => <span className="set-item" key={index}><CollectionItem value={item} onReference={onReference} /></span>)}{'}'}</div>;
  } else if (view === 'stack') {
    body = <div className="collection-stack" role="list">
      {items.map((item, index) => <div className="stack-cell" role="listitem" key={index}>
        <CollectionItem value={item} onReference={onReference} />{index === 0 && <small className="cell-tag">top</small>}
      </div>)}
    </div>;
  } else if (view === 'queue' || view === 'priority-queue') {
    const firstLabel = view === 'queue' ? 'front' : 'next out';
    body = <div className="collection-queue-wrap">
      <div className="collection-queue" role="list">
        {items.map((item, index) => <div className="queue-cell" role="listitem" key={index}>
          <CollectionItem value={item} onReference={onReference} />
          {index === 0 && <small className="cell-tag">{firstLabel}</small>}
          {view === 'queue' && index === items.length - 1 && index > 0 && <small className="cell-tag">back</small>}
        </div>)}
      </div>
      {view === 'priority-queue' && collection.order === 'heap' && <small className="collection-note">heap order (custom comparator) · first is next out</small>}
    </div>;
  } else {
    body = <div className="collection-list" role="list">
      {items.map((item, index) => <div className="list-cell" role="listitem" key={index}><small>[{index}]</small><CollectionItem value={item} onReference={onReference} /></div>)}
    </div>;
  }
  const tree = (collection.tree ?? []).length > 0 ? collectionTreeNodes(collection) : null;
  return <div className={`collection-view view-${view}`}>
    <div className="collection-head"><b>{collection.typeName}</b><span>size {collection.size}</span>{!collection.complete && <span className="collection-note">showing first {shown}</span>}</div>
    {tree && <TreeDiagram nodes={tree} rootId={collection.tree[0].id} />}
    {tree && !empty && <small className="tree-inorder-label">in order</small>}
    {body}
  </div>;
}

function InlineEntity({ value, heap, onReference, visited = new Set(), depth = 0, trees = null }) {
  const entity = value?.referenceId ? heap?.objects?.find((object) => object.id === value.referenceId) : null;
  // A variable pointing at a tree root shows the whole tree; one pointing inside a tree shows that node.
  if (entity && trees && isTreeNodeObject(entity)) {
    if (trees.roots.has(entity.id)) return <TreeDiagram nodes={trees.nodes} rootId={entity.id} highlights={trees.highlights} />;
    const node = trees.nodes.get(entity.id);
    return <span className="tree-node-chip" title={`Tree node #${entity.id}`}><span className="tree-node tree-node-inline"><span className="tree-node-value">{node?.label}</span></span><small>#{entity.id}</small></span>;
  }
  if (!entity || depth >= 5 || visited.has(entity.id)) {
    return <Value compact={depth > 0} value={{ ...value, type: displayType(value?.type) }} onReference={onReference} />;
  }

  if (entity.collection) return <CollectionView collection={entity.collection} onReference={onReference} />;
  const nextVisited = new Set(visited).add(entity.id);
  const children = entity.kind === 'array'
    ? (entity.elements ?? []).map((element, index) => ({ name: `[${index}]`, value: element }))
    : (entity.fields ?? []).map((field) => ({ name: field.name, type: field.type, value: field.value }));

  return <div className={`inline-entity ${entity.kind === 'array' ? 'inline-entity-array' : 'inline-entity-object'}`}>
    {children.length ? children.map((child) => <div className="inline-entity-child" key={child.name}>
      <span className="inline-entity-label">{child.name}{child.type && <small>{displayType(child.type)}</small>}</span>
      <InlineEntity value={child.value} heap={heap} onReference={onReference} visited={nextVisited} depth={depth + 1} />
    </div>) : <span className="inline-entity-empty">{entity.kind === 'array' ? '[]' : '{}'} empty</span>}
    {entity.expanded === false && <span className="inline-entity-empty">Further expansion limited</span>}
  </div>;
}

function VariableBox({ variable, heap, onReference, onFocus, trees = null }) {
  const shape = valueShape(variable);
  const entity = variable.referenceId ? heap?.objects?.find((object) => object.id === variable.referenceId) : null;
  const style = variable.referenceId ? { '--reference-color': colorForId(variable.referenceId) } : undefined;
  const inlineEntity = entity && ['array', 'object', 'collection'].includes(entity.kind);
  const treeEntity = inlineEntity && trees && isTreeNodeObject(entity);
  return <div className={`variable-box ${shape} ${inlineEntity ? `inline-${entity.kind}` : ''}`} style={style} role="group" tabIndex={0} aria-label={`${variable.name}, ${displayType(variable.type)}${inlineEntity ? `, ${entity.kind} contents shown inline` : ''}. Press Enter to focus.`} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onFocus(); } }} onDoubleClick={onFocus} title="Double-click or press Enter to focus"><div className="variable-box-heading"><strong>{variable.name}</strong><small>{displayType(variable.type)}</small></div>{inlineEntity ? <InlineEntity value={variable} heap={heap} onReference={onReference} trees={trees} /> : <Value value={{ ...variable, type: displayType(variable.type) }} onReference={onReference} />}</div>;
}

// Calls this frame made on its current line, in execution order. Values are shown; hovering
// (or keyboard focus) reveals the call as written in the source, e.g. fun_add(result, number).
function FunctionCalls({ calls = [] }) {
  if (calls.length === 0) return null;
  return <section className="function-calls" aria-label="Function calls made on this line">
    <h5>{calls.length === 1 ? 'Function call' : 'Function calls'}</h5>
    {calls.map((call, index) => {
      const returned = call.state === 'returned';
      const source = call.sourceExpression ?? call.expression;
      return <div
        className={`function-call-state ${call.state}`}
        key={`${call.line}-${index}`}
        tabIndex={0}
        aria-label={`${returned ? 'Returned' : 'Waiting for return'}: ${call.expression}. Written as ${source}`}
      >
        <span className="function-call-status">{returned ? 'Returned' : 'Waiting for return'}</span>
        <code>{call.expression}</code>
        <span className="function-call-source" aria-hidden="true">{source}</span>
      </div>;
    })}
  </section>;
}

// One running loop inside a frame: its own variables, the calls made inside it, and any nested loop.
// The ending condition sits in the top-right corner: source text small, current values larger.
function LoopBox({ loop, variables, callsByLoop, renderLoopVariable }) {
  const own = (loop.variables ?? []).map((name) => variables.find((variable) => variable.name === name)).filter(Boolean);
  const calls = callsByLoop.get(loop) ?? [];
  const result = loop.conditionResult;
  return <section className="loop-box" aria-label={`${loopKindLabel(loop.kind)} at line ${loop.startLine}`}>
    <header className="loop-box-heading">
      <span className="loop-box-title"><b>{loopKindLabel(loop.kind)}</b> · line {loop.startLine} · {loop.iteration > 0 ? `iteration ${loop.iteration}` : 'starting'}</span>
      <span className="loop-condition" title="Loop ending condition">
        <small>{loop.condition}</small>
        {loop.conditionValues && <strong>{loop.conditionValues}</strong>}
        {result !== null && result !== undefined && <i className={result ? 'condition-true' : 'condition-false'} aria-label={result ? 'condition true, loop continues' : 'condition false, loop ends'}>{result ? '✓' : '✗'}</i>}
      </span>
    </header>
    {own.length > 0 && <div className="loop-variable-grid">{own.map((variable) => renderLoopVariable(variable, loop))}</div>}
    <FunctionCalls calls={calls} />
    {(loop.loops ?? []).map((nested) => <LoopBox key={`${nested.startLine}-${nested.activationStep}`} loop={nested} variables={variables} callsByLoop={callsByLoop} renderLoopVariable={renderLoopVariable} />)}
  </section>;
}

export function StackPanel({ frames = [], heap, onReference, onFocus, fullAnalysis = true }) {
  // Collapsed frames are remembered by stack depth + method, so a frame stays closed while the user steps.
  const [collapsed, setCollapsed] = useState(() => new Set());
  const trees = useMemo(() => {
    const nodes = userTreeNodes(heap);
    return nodes.size === 0 ? null : { nodes, roots: new Set(treeRoots(nodes)), highlights: namesByReference(frames) };
  }, [heap, frames]);
  if (frames.length === 0) return <div className="empty-state">Run a program to inspect its call stack.</div>;
  const toggleFrame = (key) => setCollapsed((current) => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });
  return (
    <div className="frame-list">
      {frames.map((frame, index) => {
        // Order inside a frame: parameters, calls made outside loops, local variables, running loops, then anything else (e.g. `this`).
        // Variables declared by a running loop and calls made inside it are shown in that loop's box instead.
        const variables = frame.variables ?? [];
        const loops = frame.loops ?? [];
        const loopVariableNames = new Set(loopChain(loops).flatMap((loop) => loop.variables ?? []));
        const parameters = variables.filter((variable) => variable.kind === 'parameter');
        const locals = variables.filter((variable) => variable.kind === 'local' && !loopVariableNames.has(variable.name));
        const others = variables.filter((variable) => variable.kind !== 'parameter' && variable.kind !== 'local');
        const callsByLoop = new Map();
        const frameCalls = [];
        for (const call of frame.calls ?? []) {
          const owner = innermostLoopAt(loops, call.line);
          if (owner) callsByLoop.set(owner, [...(callsByLoop.get(owner) ?? []), call]);
          else frameCalls.push(call);
        }
        const depth = frames.length - 1 - index;
        const frameKey = `${depth}-${frame.className}-${frame.method}`;
        const isCollapsed = collapsed.has(frameKey);
        const collapseButton = <button type="button" className="frame-collapse" aria-expanded={!isCollapsed} aria-label={`${isCollapsed ? 'Expand' : 'Collapse'} ${frame.method}`} title={isCollapsed ? 'Expand' : 'Collapse'} onClick={() => toggleFrame(frameKey)}>{isCollapsed ? '▸' : '▾'}</button>;
        const callSite = frames[index + 1]?.line;
        const renderVariable = (variable) => <VariableBox key={`${variable.name}-${variable.kind}`} variable={variable} heap={fullAnalysis ? undefined : heap} trees={fullAnalysis ? null : trees} onReference={onReference} onFocus={() => onFocus({ kind: 'variable', name: variable.name, variable })} />;
        const renderLoopVariable = (variable, loop) => <VariableBox key={`${variable.name}-loop`} variable={variable} heap={fullAnalysis ? undefined : heap} trees={fullAnalysis ? null : trees} onReference={onReference} onFocus={() => onFocus({ kind: 'loop-variable', name: variable.name, variable, depth, startLine: loop.startLine, activationStep: loop.activationStep, loopKind: loop.kind })} />;
        const loopBoxes = loops.map((loop) => <LoopBox key={`${loop.startLine}-${loop.activationStep}`} loop={loop} variables={variables} callsByLoop={callsByLoop} renderLoopVariable={renderLoopVariable} />);
        if (!fullAnalysis) {
          const normalGroup = (label, items) => items.length > 0 && <div className="normal-variable-group">
            <span className="normal-group-label">{label}</span>
            <div className="normal-variable-grid">{items.map(renderVariable)}</div>
          </div>;
          return <article className={`frame-card normal-frame ${isCollapsed ? 'frame-collapsed' : ''}`} key={`${frame.className}-${frame.method}-${index}`}>
            <div className="normal-frame-heading">{collapseButton}<span className="normal-frame-name">{frame.method}</span><span className="normal-frame-return">returns {displayType(frame.returnType)}</span></div>
            {!isCollapsed && <>
            {normalGroup('Parameters', parameters)}
            <FunctionCalls calls={frameCalls} />
            {normalGroup('Local variables', locals)}
            {loopBoxes}
            {normalGroup('Other', others)}
            {variables.length === 0 && <div className="empty-variables">No visible variables at this line.</div>}
            </>}
          </article>;
        }
        return <article className={`frame-card ${index === 0 ? 'frame-active' : ''} ${isCollapsed ? 'frame-collapsed' : ''}`} key={`${frame.className}-${frame.method}-${index}`}>
          <div className="frame-heading">
            <span className="frame-rank">{collapseButton}{index === 0 ? 'ACTIVE' : `CALLER ${index}`}</span>
            <span className="frame-signature"><strong>{frame.method}</strong><span> returns {displayType(frame.returnType)}</span></span>
          </div>
          {!isCollapsed && <>
          <div className="frame-meta"><span>{displayType(frame.className)} · line {frame.line}</span>{callSite != null && <span className="call-site">called from line {callSite}</span>}</div>
          {parameters.length > 0 && <section className="variable-group parameter-group"><h5>Parameters</h5><div className="variable-grid">{parameters.map(renderVariable)}</div></section>}
          <FunctionCalls calls={frameCalls} />
          {locals.length > 0 && <section className="variable-group local-group"><h5>Local variables</h5><div className="variable-grid">{locals.map(renderVariable)}</div></section>}
          {loopBoxes}
          {others.length > 0 && <section className="variable-group other-group"><h5>Other</h5><div className="variable-grid">{others.map(renderVariable)}</div></section>}
          {variables.length === 0 && <div className="empty-variables">No visible variables at this line.</div>}
          </>}
        </article>;
      })}
    </div>
  );
}

export function HeapPanel({ heap, onReference, onFocus, selectedId, onSelect, footer = null, frames = [] }) {
  // Each object opens and closes on its own; following a reference also opens its target.
  const [openIds, setOpenIds] = useState(() => new Set());
  useEffect(() => {
    if (selectedId) setOpenIds((current) => current.has(selectedId) ? current : new Set(current).add(selectedId));
  }, [selectedId]);
  const setOpen = (id, open) => setOpenIds((current) => {
    if (current.has(id) === open) return current;
    const next = new Set(current);
    if (open) next.add(id); else next.delete(id);
    return next;
  });
  const treeNodes = useMemo(() => userTreeNodes(heap), [heap]);
  const highlights = useMemo(() => namesByReference(frames), [frames]);
  // Tree nodes are drawn once as a tree (below) instead of one card per node.
  const objects = (heap?.objects ?? []).filter((object) => !treeNodes.has(object.id));
  const statics = heap?.staticFields ?? [];
  if (!heap) return <div className="empty-state">Heap snapshots appear after a trace is available.</div>;
  return (
    <div className="heap-content">
      <div className="heap-summary">
        <span><strong>{objects.length}</strong> object{objects.length === 1 ? '' : 's'}</span>
        <span><strong>{statics.length}</strong> static field{statics.length === 1 ? '' : 's'}</span>
      </div>
      {heap.truncated && <div className="heap-warning">Partial snapshot · {heap.truncatedReason ?? 'limit reached'}</div>}
      {statics.length > 0 && (
        <section className="heap-group">
          <h4>Static fields</h4>
          {statics.map((field, index) => (
            <div className="static-row" key={`${field.className}-${field.name}-${index}`} onDoubleClick={() => onFocus({ kind: 'static', name: field.name, variable: field.value, type: field.type, className: field.className })} title="Double-click to focus">
              <div><strong>{field.name}</strong><small>{displayType(field.className)}</small></div>
              <Value value={field.value} onReference={onReference} />
            </div>
          ))}
        </section>
      )}
      {treeNodes.size > 0 && <section className="heap-group">
        <h4>Trees</h4>
        {treeRoots(treeNodes).map((rootId) => {
          const root = heap.objects.find((object) => object.id === rootId);
          return <div className="tree-card" key={rootId}>
            <TreeDiagram nodes={treeNodes} rootId={rootId} highlights={highlights}
              caption={<><b>{displayType(root?.className)}</b><span>root #{rootId}</span></>}
              onNodeFocus={(id) => { const node = heap.objects.find((object) => object.id === id); if (node) onFocus({ kind: 'object', object: node }); }} />
          </div>;
        })}
      </section>}
      {objects.length > 0 ? (
        <section className="heap-group">
          <h4>Objects</h4>
          <div className="object-list">
            {objects.map((object) => (
              <details
                className={`object-card ${object.kind === 'array' ? 'array-card' : 'reference-card'} ${selectedId === object.id ? 'object-selected' : ''}`}
                style={{ '--reference-color': colorForId(object.id) }}
                id={`heap-object-${object.id}`}
                key={object.id}
                open={openIds.has(object.id)}
                onToggle={(event) => { const open = event.currentTarget.open; setOpen(object.id, open); if (open) onSelect(object.id); else if (selectedId === object.id) onSelect(null); }}
                onDoubleClick={() => onFocus({ kind: 'object', object })}
                title="Double-click to focus this heap object"
              >
                <summary>
                  <span className="object-kind">{object.collection ? object.collection.view.replace('-', ' ') : object.kind}</span>
                  <span className="object-title">{object.collection ? `${object.collection.typeName} · size ${object.collection.size}` : displayType(object.className)}</span>
                  <span className="object-id">#{object.id}</span>
                </summary>
                <button className="focus-object-button" type="button" onClick={(event) => { event.stopPropagation(); onFocus({ kind: 'object', object }); }}>Focus object</button>
                <div className="object-details">
                  {object.collection && <CollectionView collection={object.collection} onReference={onReference} />}
                  {object.kind === 'array' && <div className="object-meta">Length {object.length}{object.elements?.length < object.length ? ` · showing ${object.elements.length}` : ''}</div>}
                  {object.fields?.map((field, index) => (
                    <div className="object-row" key={`${field.name}-${index}`}>
                      <div><strong>{field.name}</strong><small>{displayType(field.type)}</small></div>
                      <Value value={field.value} onReference={onReference} />
                    </div>
                  ))}
                  {object.elements?.length > 0 && <div className="array-elements">{object.elements.map((value, index) => <div className="array-element" key={`element-${index}`} title={`${displayType(value.type)}: ${value.referenceId ? displayType(value.value) : value.value}`}><span className="array-index">[{index}]</span><span className="array-element-value">{value.referenceId ? <Value compact value={value} onReference={onReference} /> : value.value ?? 'null'}</span></div>)}</div>}
                  {object.expanded === false && <div className="object-meta">Expansion stopped by a configured limit.</div>}
                  {!object.collection && object.fields?.length === 0 && object.elements?.length === 0 && <div className="object-meta">No captured fields or elements.</div>}
                </div>
              </details>
            ))}
          </div>
        </section>
      ) : statics.length === 0 && treeNodes.size === 0 ? <div className="empty-state heap-empty">No reachable objects or user static fields at this step.</div> : null}
      {footer}
    </div>
  );
}

export function FocusedPanel({ focus, heap, onReference, onClear, trace = [], stepIndex = 0 }) {
  const loopVariable = focus?.kind === 'loop-variable';
  // Loop variables show one value per iteration of the loop run, recomputed as the user steps.
  const history = loopVariable ? loopVariableHistory(trace, stepIndex, focus) : [];
  const referenceId = focus?.kind === 'variable' ? focus.variable.referenceId : focus?.kind === 'static' ? focus.variable?.referenceId : null;
  const object = focus?.kind === 'object' ? focus.object : heap?.objects?.find((item) => item.id === referenceId);
  const focusedValue = focus?.kind === 'variable' || focus?.kind === 'static' ? focus.variable : null;
  return (
    <article className="focused-box panel">
      <div className="focused-heading"><div><span className="inspect-kicker">DETAILS</span><h3>Focused box</h3></div><div className="focused-actions"><span className="count-chip">{focus ? focus.kind.replace('-', ' ') : 'empty'}</span>{focus && <button type="button" className="focused-clear" onClick={onClear} aria-label="Remove focused item">Clear</button>}</div></div>
      {!focus ? <p className="focused-empty">Double-click a variable or heap object to inspect it here.</p> : (
        <div className="focused-content" onDoubleClick={onClear} title="Double-click a focused value to remove it">
          {loopVariable && <div className="loop-history">
            <div className="loop-history-title"><strong>{focus.name}</strong><small>{displayType(focus.variable?.type)} · {loopKindLabel(focus.loopKind)} at line {focus.startLine}</small></div>
            {history.length === 0 ? <span className="object-meta">No completed iteration yet.</span> : <ol className="loop-history-values" aria-label={`Values of ${focus.name} per iteration`}>
              {history.map(({ iteration, variable }, position) => <li key={iteration} className={position === history.length - 1 ? 'current' : ''}>
                <small>iter {iteration}</small>
                <Value value={{ ...variable, type: displayType(variable.type) }} onReference={onReference} />
              </li>)}
            </ol>}
          </div>}
          {focusedValue && <div className="focused-variable"><div><strong>{focus.name}</strong><small>{displayType(focus.type ?? focusedValue.type)}</small></div><Value value={{ ...focusedValue, type: displayType(focusedValue.type) }} onReference={onReference} /></div>}
          {focus.kind === 'object' && <div className="focused-object-title"><strong>{object.collection ? object.collection.typeName : displayType(object.className)}</strong><span>{object.collection ? object.collection.view.replace('-', ' ') : object.kind} · #{object.id}</span></div>}
          {object?.collection && <div className="focused-object-details"><CollectionView collection={object.collection} onReference={onReference} /></div>}
          {object && isTreeNodeObject(object) && <div className="focused-object-details"><TreeDiagram nodes={userTreeNodes(heap)} rootId={object.id} caption={<span>subtree from #{object.id}</span>} /></div>}
          {object && !object.collection && <div className="focused-object-details">
            {object.kind === 'array' && <div className="object-meta">Length {object.length}</div>}
            {object.fields?.map((field) => <div className="focused-field" key={field.name}><span>{field.name}<small>{displayType(field.type)}</small></span><Value value={{ ...field.value, type: displayType(field.value?.type) }} onReference={onReference} /></div>)}
            {object.elements?.map((value, index) => <div className="focused-field" key={`element-${index}`}><span>[{index}]</span><Value compact value={value} onReference={onReference} /></div>)}
            {object.fields?.length === 0 && object.elements?.length === 0 && <div className="object-meta">No captured fields or elements.</div>}
          </div>}
        </div>
      )}
    </article>
  );
}

export function LoopStatus({ loop }) {
  return <article className={`loop-status panel ${loop ? 'loop-active' : ''}`} aria-live="polite">
    <span className="loop-icon">↻</span>
    {loop ? <><strong>Loop at line {loop.headerLine}</strong><span>Iteration {loop.iteration}</span><small>Lines {loop.startLine}–{loop.endLine}</small></> : <><strong>Loop status</strong><span>No loop active at this step</span></>}
  </article>;
}

export function ConsolePanel({ stdout, exception, status }) {
  return (
    <div className="console-panel">
      <div className="console-heading"><span className="console-dot" /><span>Program output</span><span className="console-step">{status ?? 'waiting'}</span></div>
      <pre className="console-output">{stdout || <span className="console-placeholder">Output from System.out.println appears here.</span>}</pre>
      {exception && (
        <div className="exception-box">
          <strong>{displayType(exception.type)}</strong><span> at line {exception.line}</span>
          {exception.message && <p>{exception.message}</p>}
        </div>
      )}
    </div>
  );
}
