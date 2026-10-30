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

function Value({ value, onReference, className = '' }) {
  if (!value) return <span className="muted">—</span>;
  const shape = valueShape(value);
  const style = value.referenceId ? { '--reference-color': colorForId(value.referenceId) } : undefined;
  const shownValue = shape === 'reference' ? `@${value.referenceId}` : value.referenceId ? displayType(value.value ?? 'null') : value.value ?? 'null';
  const contents = <><span className={`value-shape ${shape}`} aria-hidden="true">{shape === 'reference' ? `#${value.referenceId}` : shownValue}</span>{shape === 'reference' && <span className="reference-caption">{displayType(value.type)}</span>}</>;
  if (value.referenceId) {
    return <button className={`value-reference ${className}`} style={style} title={`${displayType(value.value)} · reference ${value.referenceId}`} onClick={() => onReference(value.referenceId)} type="button">{contents}</button>;
  }
  return <span className={`value-display ${shape} ${className}`} title={`${value.type}: ${shownValue}`}>{contents}</span>;
}

function InlineEntity({ value, heap, onReference, visited = new Set(), depth = 0 }) {
  const entity = value?.referenceId ? heap?.objects?.find((object) => object.id === value.referenceId) : null;
  if (!entity || depth >= 5 || visited.has(entity.id)) {
    return <Value value={{ ...value, type: displayType(value?.type) }} onReference={onReference} />;
  }

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

function VariableBox({ variable, heap, onReference, onFocus }) {
  const shape = valueShape(variable);
  const entity = variable.referenceId ? heap?.objects?.find((object) => object.id === variable.referenceId) : null;
  const style = variable.referenceId ? { '--reference-color': colorForId(variable.referenceId) } : undefined;
  const inlineEntity = entity && ['array', 'object', 'collection'].includes(entity.kind);
  return <div className={`variable-box ${shape} ${inlineEntity ? `inline-${entity.kind}` : ''}`} style={style} role="group" tabIndex={0} aria-label={`${variable.name}, ${displayType(variable.type)}${inlineEntity ? `, ${entity.kind} contents shown inline` : ''}. Press Enter to focus.`} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onFocus(); } }} onDoubleClick={onFocus} title="Double-click or press Enter to focus"><div className="variable-box-heading"><strong>{variable.name}</strong><small>{displayType(variable.type)}</small></div>{inlineEntity ? <InlineEntity value={variable} heap={heap} onReference={onReference} /> : <Value value={{ ...variable, type: displayType(variable.type) }} onReference={onReference} />}</div>;
}

function FunctionCalls({ calls = [] }) {
  if (calls.length === 0) return null;
  return <section className="function-calls" aria-label="Active function calls">
    <h5>Function call</h5>
    {calls.map((call, index) => <div className={`function-call-state ${call.state}`} key={`${call.line}-${index}`}>
      <span className="function-call-status">{call.state === 'returned' ? 'Returned' : 'Waiting for return'}</span>
      <code>{call.expression}</code>
    </div>)}
  </section>;
}

export function StackPanel({ frames = [], heap, onReference, onFocus, fullAnalysis = true }) {
  if (frames.length === 0) return <div className="empty-state">Run a program to inspect its call stack.</div>;
  return (
    <div className="frame-list">
      {frames.map((frame, index) => {
        const parameters = (frame.variables ?? []).filter((variable) => variable.kind === 'parameter' || variable.kind === 'this');
        const locals = (frame.variables ?? []).filter((variable) => variable.kind !== 'parameter' && variable.kind !== 'this');
        const callSite = frames[index + 1]?.line;
        if (!fullAnalysis) {
          const renderNormalVariable = (variable) => {
            return <VariableBox key={`${variable.name}-${variable.kind}`} variable={variable} heap={heap} onReference={onReference} onFocus={() => onFocus({ kind: 'variable', name: variable.name, variable })} />;
          };
          return <article className="frame-card normal-frame" key={`${frame.className}-${frame.method}-${index}`}>
            <div className="normal-frame-heading"><span>Name : {frame.method}</span><span>returns {displayType(frame.returnType)}</span></div>
            <div className="normal-variable-grid">{(frame.variables ?? []).map(renderNormalVariable)}</div>
            <FunctionCalls calls={frame.calls} />
          </article>;
        }
        return <article className={`frame-card ${index === 0 ? 'frame-active' : ''}`} key={`${frame.className}-${frame.method}-${index}`}>
          <div className="frame-heading">
            <span className="frame-rank">{index === 0 ? 'ACTIVE' : `CALLER ${index}`}</span>
            <span className="frame-signature"><strong>{frame.method}</strong><span> returns {displayType(frame.returnType)}</span></span>
          </div>
          <div className="frame-meta"><span>{displayType(frame.className)} · line {frame.line}</span>{callSite != null && <span className="call-site">called from line {callSite}</span>}</div>
          {parameters.length > 0 && <section className="variable-group parameter-group"><h5>Parameters</h5><div className="variable-grid">{parameters.map((variable) => <VariableBox key={`${variable.name}-${variable.kind}`} variable={variable} onReference={onReference} onFocus={() => onFocus({ kind: 'variable', name: variable.name, variable })} />)}</div></section>}
          {locals.length > 0 && <section className="variable-group local-group"><h5>Local variables</h5><div className="variable-grid">{locals.map((variable) => <VariableBox key={`${variable.name}-${variable.kind}`} variable={variable} onReference={onReference} onFocus={() => onFocus({ kind: 'variable', name: variable.name, variable })} />)}</div></section>}
          <FunctionCalls calls={frame.calls} />
          {parameters.length === 0 && locals.length === 0 && <div className="empty-variables">No visible variables at this line.</div>}
        </article>;
      })}
    </div>
  );
}

export function HeapPanel({ heap, onReference, onFocus, selectedId, onSelect }) {
  const objects = heap?.objects ?? [];
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
                open={selectedId === object.id}
                onToggle={(event) => onSelect(event.currentTarget.open ? object.id : null)}
                onDoubleClick={() => onFocus({ kind: 'object', object })}
                title="Double-click to focus this heap object"
              >
                <summary>
                  <span className="object-kind">{object.kind}</span>
                  <span className="object-title">{displayType(object.className)}</span>
                  <span className="object-id">#{object.id}</span>
                </summary>
                <button className="focus-object-button" type="button" onClick={(event) => { event.stopPropagation(); onFocus({ kind: 'object', object }); }}>Focus object</button>
                <div className="object-details">
                  {object.kind === 'array' && <div className="object-meta">Length {object.length}{object.elements?.length < object.length ? ` · showing ${object.elements.length}` : ''}</div>}
                  {object.fields?.map((field, index) => (
                    <div className="object-row" key={`${field.name}-${index}`}>
                      <div><strong>{field.name}</strong><small>{displayType(field.type)}</small></div>
                      <Value value={field.value} onReference={onReference} />
                    </div>
                  ))}
                  {object.elements?.length > 0 && <div className="array-elements">{object.elements.map((value, index) => <div className="array-element" key={`element-${index}`} title={`${displayType(value.type)}: ${value.referenceId ? displayType(value.value) : value.value}`}><span className="array-index">[{index}]</span><span className="array-element-value">{value.referenceId ? <Value value={{ ...value, type: displayType(value.type) }} onReference={onReference} /> : value.value ?? 'null'}</span></div>)}</div>}
                  {object.expanded === false && <div className="object-meta">Expansion stopped by a configured limit.</div>}
                  {object.fields?.length === 0 && object.elements?.length === 0 && <div className="object-meta">No captured fields or elements.</div>}
                </div>
              </details>
            ))}
          </div>
        </section>
      ) : statics.length === 0 ? <div className="empty-state heap-empty">No reachable objects or user static fields at this step.</div> : null}
    </div>
  );
}

export function FocusedPanel({ focus, heap, onReference, onClear }) {
  const referenceId = focus?.kind === 'variable' ? focus.variable.referenceId : focus?.kind === 'static' ? focus.variable?.referenceId : null;
  const object = focus?.kind === 'object' ? focus.object : heap?.objects?.find((item) => item.id === referenceId);
  const focusedValue = focus?.kind === 'variable' || focus?.kind === 'static' ? focus.variable : null;
  return (
    <article className="focused-box panel">
      <div className="focused-heading"><div><span className="inspect-kicker">DETAILS</span><h3>Focused box</h3></div><div className="focused-actions"><span className="count-chip">{focus ? focus.kind : 'empty'}</span>{focus && <button type="button" className="focused-clear" onClick={onClear} aria-label="Remove focused item">Clear</button>}</div></div>
      {!focus ? <p className="focused-empty">Double-click a variable or heap object to inspect it here.</p> : (
        <div className="focused-content" onDoubleClick={onClear} title="Double-click a focused value to remove it">
          {focusedValue && <div className="focused-variable"><div><strong>{focus.name}</strong><small>{displayType(focus.type ?? focusedValue.type)}</small></div><Value value={{ ...focusedValue, type: displayType(focusedValue.type) }} onReference={onReference} /></div>}
          {focus.kind === 'object' && <div className="focused-object-title"><strong>{displayType(object.className)}</strong><span>{object.kind} · #{object.id}</span></div>}
          {object && <div className="focused-object-details">
            {object.kind === 'array' && <div className="object-meta">Length {object.length}</div>}
            {object.fields?.map((field) => <div className="focused-field" key={field.name}><span>{field.name}<small>{displayType(field.type)}</small></span><Value value={{ ...field.value, type: displayType(field.value?.type) }} onReference={onReference} /></div>)}
            {object.elements?.map((value, index) => <div className="focused-field" key={`element-${index}`}><span>[{index}]<small>{displayType(value.type)}</small></span><Value value={{ ...value, type: displayType(value.type) }} onReference={onReference} /></div>)}
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
