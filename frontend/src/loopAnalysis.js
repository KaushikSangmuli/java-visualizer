function maskCommentsAndLiterals(source) {
  const chars = [...source];
  let state = 'code';
  let escaped = false;
  for (let i = 0; i < chars.length; i += 1) {
    const current = chars[i];
    const next = chars[i + 1];
    const third = chars[i + 2];
    if (state === 'line-comment') {
      if (current === '\n') state = 'code';
      else chars[i] = ' ';
    } else if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        chars[i] = ' ';
        chars[i + 1] = ' ';
        i += 1;
        state = 'code';
      } else if (current !== '\n') chars[i] = ' ';
    } else if (state === 'string' || state === 'character') {
      if (current !== '\n') chars[i] = ' ';
      if (escaped) escaped = false;
      else if (current === '\\') escaped = true;
      else if ((state === 'string' && current === '"') || (state === 'character' && current === "'")) state = 'code';
    } else if (state === 'text-block') {
      if (current === '"' && next === '"' && third === '"') {
        chars[i] = chars[i + 1] = chars[i + 2] = ' ';
        i += 2;
        state = 'code';
      } else if (current !== '\n') chars[i] = ' ';
    } else if (current === '/' && next === '/') {
      chars[i] = chars[i + 1] = ' ';
      i += 1;
      state = 'line-comment';
    } else if (current === '/' && next === '*') {
      chars[i] = chars[i + 1] = ' ';
      i += 1;
      state = 'block-comment';
    } else if (current === '"' && next === '"' && third === '"') {
      chars[i] = chars[i + 1] = chars[i + 2] = ' ';
      i += 2;
      state = 'text-block';
    } else if (current === '"') {
      chars[i] = ' ';
      state = 'string';
    } else if (current === "'") {
      chars[i] = ' ';
      state = 'character';
    }
  }
  return chars.join('');
}

function lineNumber(source, offset) {
  let line = 1;
  for (let i = 0; i < offset; i += 1) if (source[i] === '\n') line += 1;
  return line;
}

function matchingDelimiter(source, start, open, close) {
  let depth = 0;
  for (let i = start; i < source.length; i += 1) {
    if (source[i] === open) depth += 1;
    else if (source[i] === close && --depth === 0) return i;
  }
  return -1;
}

function bodyRange(masked, source, from) {
  let cursor = from;
  while (/\s/.test(masked[cursor] ?? '') && cursor < masked.length) cursor += 1;
  if (masked[cursor] === '{') {
    const closing = matchingDelimiter(masked, cursor, '{', '}');
    const bodyStart = cursor + 1;
    const firstExecutable = bodyStart + (masked.slice(bodyStart).match(/\S/)?.index ?? 0);
    return { start: lineNumber(source, firstExecutable), end: lineNumber(source, Math.max(bodyStart, closing)) };
  }
  const semicolon = masked.indexOf(';', cursor);
  const endOffset = semicolon < 0 ? cursor : semicolon;
  return { start: lineNumber(source, cursor), end: lineNumber(source, endOffset) };
}

export function detectLoops(source) {
  const masked = maskCommentsAndLiterals(source);
  const loops = [];
  const loopPattern = /\b(for|while)\s*\(/g;
  let match;
  while ((match = loopPattern.exec(masked)) !== null) {
    const open = masked.indexOf('(', match.index);
    const close = matchingDelimiter(masked, open, '(', ')');
    if (close < 0) continue;
    const body = bodyRange(masked, source, close + 1);
    loops.push({ headerLine: lineNumber(source, match.index), startLine: Math.min(lineNumber(source, match.index), body.start), endLine: Math.max(lineNumber(source, match.index), body.end) });
  }
  const doPattern = /\bdo\s*(?=\{)/g;
  while ((match = doPattern.exec(masked)) !== null) {
    const open = masked.indexOf('{', match.index);
    const close = matchingDelimiter(masked, open, '{', '}');
    if (close < 0) continue;
    const body = bodyRange(masked, source, open + 1);
    loops.push({ headerLine: lineNumber(source, match.index), startLine: lineNumber(source, match.index), endLine: Math.max(lineNumber(source, close), body.end) });
  }
  return loops.sort((left, right) => (left.endLine - left.startLine) - (right.endLine - right.startLine));
}

export function loopAtTraceStep(source, trace, stepIndex, sourceMetadataLoops) {
  if (!Array.isArray(trace) || trace.length === 0 || trace[stepIndex]?.event === 'end') return null;
  const line = trace[stepIndex]?.line;
  if (!line) return null;
  const loops = Array.isArray(sourceMetadataLoops) && sourceMetadataLoops.length > 0
    ? sourceMetadataLoops.map((loop) => ({ ...loop, headerLine: loop.startLine }))
    : detectLoops(source);
  const activeLoop = loops.find((loop) => line >= loop.startLine && line <= loop.endLine);
  if (!activeLoop) return null;
  const visits = trace.slice(0, stepIndex + 1).filter((step) => step.line === activeLoop.headerLine && step.event !== 'return' && step.event !== 'exception').length;
  return { ...activeLoop, iteration: Math.max(1, visits) };
}
