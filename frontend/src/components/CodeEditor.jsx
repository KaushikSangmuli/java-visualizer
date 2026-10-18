import { useEffect, useMemo, useRef } from 'react';

export default function CodeEditor({ code, onChange, activeLine, scrollKey, readOnly = false }) {
  const gutterRef = useRef(null);
  const backdropRef = useRef(null);
  const codeAreaRef = useRef(null);
  const lines = useMemo(() => code.split('\n'), [code]);

  useEffect(() => {
    if (!activeLine || !codeAreaRef.current) return;
    const editor = codeAreaRef.current;
    const lineHeight = Number.parseFloat(getComputedStyle(editor).lineHeight) || 26;
    const lineTop = (activeLine - 1) * lineHeight + 14;
    const lineBottom = lineTop + lineHeight;
    if (lineTop < editor.scrollTop || lineBottom > editor.scrollTop + editor.clientHeight) {
      editor.scrollTop = Math.max(0, lineTop - (editor.clientHeight - lineHeight) / 2);
      if (gutterRef.current) gutterRef.current.scrollTop = editor.scrollTop;
      if (backdropRef.current) backdropRef.current.style.transform = `translate(${-editor.scrollLeft}px, ${-editor.scrollTop}px)`;
    }
  }, [activeLine, scrollKey]);

  function syncScroll(event) {
    const { scrollTop, scrollLeft } = event.currentTarget;
    if (gutterRef.current) gutterRef.current.scrollTop = scrollTop;
    if (backdropRef.current) backdropRef.current.style.transform = `translate(${-scrollLeft}px, ${-scrollTop}px)`;
  }

  return (
    <div className="editor-shell">
      <div className="editor-gutter" ref={gutterRef} aria-hidden="true">
        <div className="editor-gutter-content">
          {lines.map((_, index) => (
            <div className={activeLine === index + 1 ? 'gutter-line active' : 'gutter-line'} key={index}>{index + 1}</div>
          ))}
        </div>
      </div>
      <div className="editor-code-area">
        <pre className="editor-backdrop" ref={backdropRef} aria-hidden="true">
          {lines.map((line, index) => (
            <span className={activeLine === index + 1 ? 'backdrop-line active' : 'backdrop-line'} key={index}>
              {line || ' '}{index < lines.length - 1 ? '\n' : ''}
            </span>
          ))}
        </pre>
        <textarea
          className="code-input"
          ref={codeAreaRef}
          aria-label="Java source code"
          autoCapitalize="off"
          autoComplete="off"
          autoCorrect="off"
          spellCheck="false"
          readOnly={readOnly}
          value={code}
          onChange={(event) => onChange(event.target.value)}
          onScroll={syncScroll}
          onKeyDown={(event) => {
            if (event.key === 'Tab') {
              event.preventDefault();
              const field = event.currentTarget;
              const start = field.selectionStart;
              const end = field.selectionEnd;
              onChange(`${code.slice(0, start)}    ${code.slice(end)}`);
              requestAnimationFrame(() => { field.selectionStart = field.selectionEnd = start + 4; });
            }
          }}
        />
      </div>
    </div>
  );
}
