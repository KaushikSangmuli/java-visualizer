import { useEffect, useMemo, useState } from 'react';
import CodeEditor from './components/CodeEditor.jsx';
import { ConsolePanel, FocusedPanel, HeapPanel, LoopStatus, StackPanel } from './components/TracePanels.jsx';
import { examples } from './examples.js';
import { loopAtTraceStep } from './loopAnalysis.js';
import AuthPanel from './components/AuthPanel.jsx';

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const apiUrl = (path) => `${API_BASE}${path}`;

function Icon({ name, size = 18 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true };
  const paths = {
    play: <path d="m8 5 12 7-12 7V5Z" fill="currentColor" stroke="none" />,
    pause: <><path d="M8 5h3v14H8z" fill="currentColor" stroke="none" /><path d="M15 5h3v14h-3z" fill="currentColor" stroke="none" /></>,
    back: <><path d="m15 18-6-6 6-6" /><path d="M9 12h11" /></>,
    next: <><path d="m9 18 6-6-6-6" /><path d="M4 12h11" /></>,
    run: <><path d="M8 5h8l4 4v10H4V5h4Z" /><path d="M8 5v5h8V5M8 19v-6h8v6" /></>,
    code: <><path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14" /></>,
    chevron: <path d="m9 18 6-6-6-6" />,
    check: <path d="m5 12 4 4L19 6" />,
    copy: <><rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3" /></>,
  };
  return <svg {...common}>{paths[name]}</svg>;
}

function StatusPill({ status }) {
  if (!status) return <span className="status-pill idle"><span className="status-dot" />Ready to run</span>;
  const labels = { ok: 'Trace complete', compile_error: 'Compile error', runtime_error: 'Runtime error', blocked_api: 'Blocked API', busy: 'Backend busy', error: 'Request error' };
  return <span className={`status-pill ${status === 'ok' ? 'success' : 'failure'}`}><span className="status-dot" />{labels[status] ?? status}</span>;
}

export default function App() {
  const [code, setCode] = useState(examples[0].code);
  const [selectedExample, setSelectedExample] = useState(0);
  const [response, setResponse] = useState(null);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [health, setHealth] = useState('checking');
  const [authLoading, setAuthLoading] = useState(true);
  const [requestError, setRequestError] = useState('');
  const [selectedObjectId, setSelectedObjectId] = useState(null);
  const [focusedItem, setFocusedItem] = useState(null);
  const [fullAnalysis, setFullAnalysis] = useState(true);
  const [hoveredStepIndex, setHoveredStepIndex] = useState(null);
  const [copied, setCopied] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authUser, setAuthUser] = useState(null);
  const [csrfToken, setCsrfToken] = useState('');

  const trace = response?.trace ?? [];
  const step = trace[currentStepIndex] ?? null;
  const activeLine = step?.line ?? response?.errors?.[0]?.line ?? response?.blockedApis?.[0]?.line ?? null;
  const currentSourceLine = activeLine ? code.split('\n')[activeLine - 1] : '';
  const progress = trace.length > 1 ? (currentStepIndex / (trace.length - 1)) * 100 : 0;
  const sourceLoops = response?.metadata?.loops;
  const activeLoop = useMemo(() => loopAtTraceStep(code, trace, currentStepIndex, sourceLoops), [code, trace, currentStepIndex, sourceLoops]);
  const selectedExampleName = examples[selectedExample]?.name ?? 'Custom Java program';
  const statusCaption = useMemo(() => {
    if (!response) return 'The trace will appear here after you run your code.';
    if (response.truncated) return `Execution stopped at a configured limit (${response.truncatedReason ?? 'limit'}).`;
    return response.message || 'Execution finished. Select a step to inspect its state.';
  }, [response]);

  useEffect(() => {
    fetch(apiUrl('/health'), { credentials: 'include' })
      .then((result) => result.json())
      .then((body) => setHealth(body.status === 'ok' ? 'online' : 'offline'))
      .catch(() => setHealth('offline'));
    fetch(apiUrl('/api/auth/csrf'), { credentials: 'include' })
      .then((result) => result.json()).then((body) => setCsrfToken(body.token ?? '')).catch(() => {});
    fetch(apiUrl('/api/auth/me'), { credentials: 'include' })
      .then((result) => result.ok ? result.json() : null)
      .then((body) => setAuthUser(body))
      .catch(() => setAuthUser(null))
      .finally(() => setAuthLoading(false));
  }, []);

  async function getCsrfToken() {
    if (csrfToken) return csrfToken;
    const tokenResponse = await fetch(apiUrl('/api/auth/csrf'), { credentials: 'include' });
    if (!tokenResponse.ok) throw new Error('Could not initialize secure sign-in.');
    const token = (await tokenResponse.json()).token ?? '';
    setCsrfToken(token);
    return token;
  }

  async function authRequest(path, payload) {
    const token = await getCsrfToken();
    const response = await fetch(apiUrl(path), {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token ?? '' },
      body: JSON.stringify(payload),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message ?? 'The request could not be completed.');
    return body;
  }

  async function logout() {
    await authRequest('/api/auth/logout', {});
    setAuthUser(null);
  }

  useEffect(() => {
    if (!playing || trace.length === 0) return undefined;
    if (currentStepIndex >= trace.length - 1) {
      setPlaying(false);
      return undefined;
    }
    const timer = window.setTimeout(() => setCurrentStepIndex((index) => Math.min(index + 1, trace.length - 1)), 650);
    return () => window.clearTimeout(timer);
  }, [playing, currentStepIndex, trace.length]);

  async function runCode() {
    setBusy(true);
    setPlaying(false);
    setRequestError('');
    setSelectedObjectId(null);
    setFocusedItem(null);
    try {
      const result = await fetch(apiUrl('/api/visualize'), {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': await getCsrfToken() },
        body: JSON.stringify({ code }),
      });
      const body = await result.json();
      if (result.status === 401) {
        setAuthUser(null);
        setResponse(null);
        setRequestError('Your sign-in expired. Please sign in again to run the visualizer.');
        return;
      }
      setResponse(body);
      setCurrentStepIndex(0);
    } catch (error) {
      setRequestError('Could not reach the backend. Start Spring Boot on port 8080, then run the trace again.');
      setHealth('offline');
    } finally {
      setBusy(false);
    }
  }

  function chooseExample(index) {
    setSelectedExample(index);
    setCode(examples[index].code);
    setResponse(null);
    setRequestError('');
    setCurrentStepIndex(0);
    setPlaying(false);
    setFocusedItem(null);
  }

  function goToStep(index) {
    const next = Math.max(0, Math.min(index, trace.length - 1));
    setCurrentStepIndex(next);
    setPlaying(false);
  }

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      setRequestError('Clipboard access is unavailable in this browser.');
    }
  }

  const heap = step?.heap ?? null;
  // Per-step variable/heap deltas; shown under the heap (Full analysis) or under the stack (Normal mode).
  const stateChanges = step && (((step.variableChanges?.length ?? 0) > 0 || (step.heapChanges?.length ?? 0) > 0) && <details className="state-changes">
                      <summary>State changes <span>{(step.variableChanges?.length ?? 0) + (step.heapChanges?.length ?? 0)}</span></summary>
                      {step.variableChanges?.map((change, index) => <div className="state-change-row" key={`var-${index}`}><b>{change.change}</b><code>{change.frame}.{change.name}</code><span>{change.before ?? '—'} → {change.after ?? '—'}</span></div>)}
                      {step.heapChanges?.map((change, index) => <div className="heap-change-group" key={`heap-${index}`}>
                        <div className="state-change-row"><b>{change.change}</b><code>{change.className || change.id}</code><span>{change.kind}</span></div>
                        {change.values?.map((value, valueIndex) => <div className="state-change-detail" key={`${value.name}-${valueIndex}`}><b>{value.change}</b><code>{value.name}</code><span>{value.before ?? '—'} → {value.after ?? '—'}</span></div>)}
                      </div>)}
                    </details>);
  const eventClass = step?.event === 'exception' ? 'event-exception' : step?.event === 'call' ? 'event-call' : step?.event === 'return' ? 'event-return' : 'event-step';

  if (authLoading) return <div className="auth-loading" role="status">Loading your account…</div>;
  if (!authUser) return <AuthPanel standalone onClose={() => {}} onRequest={authRequest} onLogin={setAuthUser} />;

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Java Visualizer home">
          <span className="brand-mark"><Icon name="code" size={21} /></span>
          <span className="brand-wordmark">java<span>viz</span></span>
        </a>
        <div className="topbar-center"><span className="topbar-label">INTERACTIVE EXECUTION TRACE</span><span className="topbar-divider" />Java · JDI</div>
        <div className="topbar-actions">
          <button type="button" className="account-button" onClick={() => setAuthOpen(true)}>{authUser ? authUser.email : 'Sign in'}</button>
          <div className={`backend-indicator ${health}`}><span className="status-dot" />{health === 'online' ? 'Backend connected' : health === 'checking' ? 'Checking backend…' : 'Backend offline'}</div>
        </div>
      </header>

      {authOpen && <AuthPanel user={authUser} onClose={() => setAuthOpen(false)} onRequest={authRequest} onLogin={setAuthUser} onLogout={logout} />}

      <main className="page-content" id="top">
        <section className="intro-row">
          <div>
            <div className="eyebrow"><span className="eyebrow-line" />JAVA CODE MEMORY VISUALIZER</div>
            <h1>See your code <span>think.</span></h1>
            <p className="intro-copy">Run Java one step at a time. Follow the call stack, inspect values, and explore the heap as your program executes.</p>
          </div>
          <div className="step-guide" aria-label="How it works">
            <span className="guide-node active"><i>01</i> Write code</span><span className="guide-connector" />
            <span className="guide-node"><i>02</i> Step through</span><span className="guide-connector" />
            <span className="guide-node"><i>03</i> Explore memory</span>
          </div>
        </section>

        <section className="workspace" aria-label="Java code visualizer">
          <article className="editor-card panel">
            <div className="panel-heading editor-heading">
              <div className="panel-title-group"><span className="panel-icon"><Icon name="code" /></span><div><h2>Source code</h2><p>Your Java program</p></div></div>
              <StatusPill status={response?.status} />
            </div>
            <div className="editor-toolbar">
              <label className="sample-picker"><span>EXAMPLE</span>
                <select value={selectedExample < 0 ? 'custom' : selectedExample} onChange={(event) => event.target.value === 'custom' ? setSelectedExample(-1) : chooseExample(Number(event.target.value))}>
                  {examples.map((example, index) => <option value={index} key={example.className}>{example.name}</option>)}
                  <option value="custom">Custom Java source</option>
                </select>
              </label>
              <button className="icon-button copy-button" type="button" onClick={copyCode} aria-label="Copy source code" title="Copy source code">
                <Icon name={copied ? 'check' : 'copy'} size={16} />{copied ? 'Copied' : 'Copy'}
              </button>
            </div>
          <CodeEditor code={code} onChange={(value) => { setCode(value); setSelectedExample(-1); setResponse(null); setRequestError(''); setCurrentStepIndex(0); setFocusedItem(null); }} activeLine={activeLine} scrollKey={currentStepIndex} readOnly={busy} />
            <div className="editor-footer">
              <span className="file-label"><span className="java-file-icon">J</span>{examples.find((item) => item.code === code)?.className ?? 'Main'}.java</span>
              <span className="editor-hint">Java 17 <i /> {code.length.toLocaleString()} / 20,000 chars</span>
            </div>
            <button className="run-button" type="button" onClick={runCode} disabled={busy || !code.trim()}>
              <span className="run-button-icon"><Icon name="run" size={18} /></span>
              <span>{busy ? 'Running your program…' : 'Run & visualize'}</span>
              <span className="run-shortcut">{busy ? <span className="spinner" /> : <Icon name="next" size={17} />}</span>
            </button>
            <div className="editor-note"><span className="note-lock">◇</span> Runs in a separate debug process · Use trusted code only</div>
          </article>

          <div className="trace-column">
            <div className="analysis-mode-toolbar">
              <span>Full analysis</span>
              <button className={`analysis-toggle ${fullAnalysis ? 'is-on' : ''}`} type="button" role="switch" aria-label="Full analysis" aria-checked={fullAnalysis} onClick={() => setFullAnalysis((enabled) => !enabled)}>
                <span className="analysis-toggle-track"><i /></span><strong>{fullAnalysis ? 'ON' : 'OFF'}</strong>
              </button>
            </div>
            <div className={`inspect-grid ${fullAnalysis ? '' : 'normal-mode-grid'}`}>
              <article className="inspect-card panel">
                <div className="inspect-heading"><div><span className="inspect-kicker">01 / STACK</span><h3>Call stack</h3></div><span className="count-chip">{step?.frames?.length ?? 0} frames</span></div>
                <div className="inspect-scroll stack-scroll"><StackPanel frames={step?.frames ?? []} heap={heap} fullAnalysis={fullAnalysis} onReference={(id) => { setSelectedObjectId(id); document.getElementById(`heap-object-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }} onFocus={setFocusedItem} /></div>
              </article>
              {fullAnalysis && <article className="inspect-card panel heap-panel">
                <div className="inspect-heading"><div><span className="inspect-kicker">02 / MEMORY</span><h3>Heap & statics</h3></div><span className="count-chip">{heap?.objects?.length ?? 0} objects</span></div>
                <div className="inspect-scroll heap-scroll"><HeapPanel heap={heap} selectedId={selectedObjectId} onSelect={setSelectedObjectId} onFocus={setFocusedItem} onReference={(id) => { setSelectedObjectId(id); document.getElementById(`heap-object-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }} /></div>
                {stateChanges && <div className="heap-state-changes">{stateChanges}</div>}
              </article>}
            </div>
            {!fullAnalysis && stateChanges && <div className="panel normal-state-changes">{stateChanges}</div>}

            <article className={`trace-card panel ${step ? 'controls-only' : ''}`}>
              {/* Execution trace header (title, finished status, step counter) hidden for now: the slider shows progress.
              <div className="panel-heading trace-heading">
                <div className="panel-title-group"><span className="panel-icon trace-icon"><Icon name="play" size={16} /></span><div><h2>Execution trace</h2><p>{response ? statusCaption : 'Your program, one moment at a time'}</p></div></div>
                {trace.length > 0 && <span className="step-counter">STEP <b>{currentStepIndex + 1}</b><i>/</i>{trace.length}</span>}
              </div>
              */}

              {!response && !requestError && (
                <div className="trace-placeholder">
                  <div className="placeholder-orbit"><span className="orbit-center">{busy ? <span className="spinner large" /> : <Icon name="play" size={22} />}</span><i /><i /><i /></div>
                  <strong>{busy ? 'Following your program…' : 'Ready when you are'}</strong>
                  <p>{busy ? 'Compiling and tracing in the Java backend.' : `Load “${selectedExampleName}” or write your own code, then run it.`}</p>
                </div>
              )}
              {requestError && <div className="request-error"><strong>Can’t connect to the visualizer</strong><p>{requestError}</p></div>}
              {response && response.status === 'compile_error' && (
                <div className="compile-errors"><div className="error-title"><span>!</span><div><strong>Compilation failed</strong><small>Fix the source and try again.</small></div></div>
                  {(response.errors ?? []).map((error, index) => <div className="compile-error-row" key={`${error.line}-${index}`}><span>Line {error.line}</span><code>{error.message}</code></div>)}
                </div>
              )}
              {response && response.status === 'blocked_api' && (
                <div className="compile-errors"><div className="error-title"><span>!</span><div><strong>Restricted API detected</strong><small>This blocklist is an added guardrail, not a secure sandbox.</small></div></div>
                  {(response.blockedApis ?? []).map((item, index) => <div className="compile-error-row" key={`${item.line}-${index}`}><span>Line {item.line}</span><code>{item.api} · {item.reason}</code></div>)}
                </div>
              )}
              {response && response.status !== 'compile_error' && trace.length === 0 && !requestError && (
                <div className="request-error"><strong>{response.status?.replaceAll('_', ' ') ?? 'No trace returned'}</strong><p>{response.message ?? 'The backend returned no trace steps.'}</p></div>
              )}
              {step && (
                <>
                  {/* Step context hidden for now: event badge, source line, explanation, line type and return value.
                      The editor highlights the line and call boxes show return values.
                  <div className="step-context">
                    <div className="step-context-top"><span className={`event-badge ${eventClass}`}>{step.event}</span><span className="context-line-label">SOURCE LINE</span><span className="context-line-number">{step.line}</span></div>
                    <pre className="active-source-line"><span>{String(step.line).padStart(2, '0')}</span>{currentSourceLine || 'Source line unavailable'}</pre>
                    {step.explanation && <p className="step-explanation">{step.explanation}</p>}
                    {step.lineType && <span className="line-type-chip">{step.lineType.replaceAll('_', ' ')}</span>}
                    {step.returnValue !== null && step.returnValue !== undefined && <div className="return-value"><span>RETURN VALUE</span><code>{step.returnValue}</code></div>}
                  </div>
                  */}
                  <div className="step-controls">
                    <button type="button" className="step-icon-button" onClick={() => goToStep(currentStepIndex - 1)} disabled={currentStepIndex === 0} aria-label="Previous step" title="Previous step"><Icon name="back" /></button>
                    <button type="button" className="step-play-button" onClick={() => {
                      if (currentStepIndex === trace.length - 1) goToStep(0);
                      setPlaying((value) => !value);
                    }} aria-label={playing ? 'Pause trace' : 'Play trace'} title={playing ? 'Pause trace' : 'Play trace'}>
                      <Icon name={playing ? 'pause' : 'play'} size={16} />
                    </button>
                    <button type="button" className="step-icon-button" onClick={() => goToStep(currentStepIndex + 1)} disabled={currentStepIndex >= trace.length - 1} aria-label="Next step" title="Next step"><Icon name="next" /></button>
                    <div className="timeline-control">
                      {hoveredStepIndex !== null && <span className="timeline-hover-tooltip" style={{ '--hover-progress': `${trace.length > 1 ? (hoveredStepIndex / (trace.length - 1)) * 100 : 0}%` }} role="tooltip">Step {hoveredStepIndex + 1}</span>}
                      <input type="range" min="0" max={Math.max(0, trace.length - 1)} value={currentStepIndex} style={{ '--progress': `${progress}%` }} aria-label="Trace step" title={`Step ${currentStepIndex + 1} of ${trace.length}`} onChange={(event) => { const index = Number(event.target.value); goToStep(index); setHoveredStepIndex(index); }} onMouseMove={(event) => { const bounds = event.currentTarget.getBoundingClientRect(); const fraction = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width)); setHoveredStepIndex(Math.round(fraction * Math.max(0, trace.length - 1))); }} onMouseLeave={() => setHoveredStepIndex(null)} onFocus={() => setHoveredStepIndex(currentStepIndex)} onBlur={() => setHoveredStepIndex(null)} />
                      <div className="timeline-labels"><span>STEP {currentStepIndex + 1} / {trace.length}</span><span>{trace.length ? `${trace.length} EVENTS` : 'NO EVENTS'}</span></div>
                    </div>
                  </div>
                </>
              )}
            </article>

            <LoopStatus loop={activeLoop} />

            {fullAnalysis && <FocusedPanel focus={focusedItem} heap={heap} onClear={() => setFocusedItem(null)} onReference={(id) => { setSelectedObjectId(id); document.getElementById(`heap-object-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }} />}


            {response?.truncated && <div className="truncated-banner">Trace paused at a safety limit. The steps captured so far are available above.</div>}
          </div>
        </section>

        <section className="bottom-row">
          <ConsolePanel stdout={step?.stdout ?? ''} exception={response?.exception ?? null} status={response?.status} />
          {response?.metadata && <details className="response-details metadata-details"><summary>Code metadata <Icon name="chevron" size={15} /></summary><pre>{JSON.stringify(response.metadata, null, 2)}</pre></details>}
          {response && <details className="response-details"><summary>Raw response <Icon name="chevron" size={15} /></summary><pre>{JSON.stringify(response, null, 2)}</pre></details>}
        </section>
        <footer className="page-footer"><span>JAVA CODE MEMORY VISUALIZER</span><span>Built for learning · Java source only</span></footer>
      </main>
    </div>
  );
}
