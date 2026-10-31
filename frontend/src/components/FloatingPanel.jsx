import { useCallback, useEffect, useRef, useState } from 'react';

const MARGIN = 8;

function readSaved(storageKey) {
  try {
    const saved = JSON.parse(window.localStorage.getItem(storageKey) ?? 'null');
    return saved && Number.isFinite(saved.x) && Number.isFinite(saved.y) ? saved : null;
  } catch {
    return null;
  }
}

function save(storageKey, position) {
  try {
    if (position) window.localStorage.setItem(storageKey, JSON.stringify(position));
    else window.localStorage.removeItem(storageKey);
  } catch {
    // Private windows can block storage; the panel still works for this visit.
  }
}

/**
 * A panel that floats above the page and can be dragged anywhere by its grip.
 * It stays where it is dropped (also after reloads, remembered in this browser) and is kept
 * inside the window when the window is resized. Default spot: bottom centre of the window.
 * Keyboard: focus the grip, arrow keys move it (Shift = bigger steps), Home or double-click resets.
 */
export default function FloatingPanel({ storageKey, label, children }) {
  const panelRef = useRef(null);
  const dragRef = useRef(null);
  const [position, setPosition] = useState(() => readSaved(storageKey));

  const clamp = useCallback((point) => {
    const panel = panelRef.current;
    if (!panel || !point) return point;
    const maxX = Math.max(MARGIN, window.innerWidth - panel.offsetWidth - MARGIN);
    const maxY = Math.max(MARGIN, window.innerHeight - panel.offsetHeight - MARGIN);
    return { x: Math.min(Math.max(point.x, MARGIN), maxX), y: Math.min(Math.max(point.y, MARGIN), maxY) };
  }, []);

  const defaultPosition = useCallback(() => {
    const panel = panelRef.current;
    if (!panel) return null;
    return { x: (window.innerWidth - panel.offsetWidth) / 2, y: window.innerHeight - panel.offsetHeight - 18 };
  }, []);

  // Place it once it has a size, and keep it on screen when the window changes size.
  useEffect(() => {
    const place = () => setPosition((current) => clamp(current ?? defaultPosition()));
    place();
    window.addEventListener('resize', place);
    return () => window.removeEventListener('resize', place);
  }, [clamp, defaultPosition]);

  const moveTo = (next, persist) => {
    const clamped = clamp(next);
    setPosition(clamped);
    if (persist) save(storageKey, clamped);
  };

  const reset = () => {
    save(storageKey, null);
    setPosition(clamp(defaultPosition()));
  };

  const onPointerDown = (event) => {
    if (event.button !== 0 || !position) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = { offsetX: event.clientX - position.x, offsetY: event.clientY - position.y };
  };
  const onPointerMove = (event) => {
    if (!dragRef.current) return;
    moveTo({ x: event.clientX - dragRef.current.offsetX, y: event.clientY - dragRef.current.offsetY }, false);
  };
  const onPointerUp = (event) => {
    if (!dragRef.current) return;
    dragRef.current = null;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    setPosition((current) => { save(storageKey, current); return current; });
  };
  const onKeyDown = (event) => {
    if (!position) return;
    const step = event.shiftKey ? 50 : 15;
    const moves = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    if (event.key === 'Home') { event.preventDefault(); reset(); return; }
    if (!moves[event.key]) return;
    event.preventDefault();
    moveTo({ x: position.x + moves[event.key][0], y: position.y + moves[event.key][1] }, true);
  };

  return (
    <div
      ref={panelRef}
      className="floating-panel"
      role="region"
      aria-label={label}
      style={position ? { left: `${position.x}px`, top: `${position.y}px` } : { visibility: 'hidden' }}
    >
      <button
        type="button"
        className="floating-grip"
        aria-label={`Move ${label}: drag, or use arrow keys. Home or double-click resets the position.`}
        title="Drag to move · double-click to reset"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onDoubleClick={reset}
        onKeyDown={onKeyDown}
      >
        <span aria-hidden="true">⋮⋮</span>
      </button>
      <div className="floating-panel-body">{children}</div>
    </div>
  );
}
