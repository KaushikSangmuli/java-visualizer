// Helpers for the loop boxes the backend attaches to each stack frame (`frame.loops`).

/** Running loops of a frame, outermost first (each loop holds at most one running nested loop). */
export function loopChain(loops = []) {
  const chain = [];
  let current = loops[0];
  while (current) {
    chain.push(current);
    current = current.loops?.[0];
  }
  return chain;
}

/** Innermost running loop whose lines contain `line`, or null. */
export function innermostLoopAt(loops, line) {
  const chain = loopChain(loops);
  for (let index = chain.length - 1; index >= 0; index -= 1) {
    if (line >= chain[index].startLine && line <= chain[index].endLine) return chain[index];
  }
  return null;
}

function findRun(loops, startLine, activationStep) {
  return loopChain(loops).find((loop) => loop.startLine === startLine && loop.activationStep === activationStep) ?? null;
}

/**
 * Value of a loop variable at the end of each iteration of one run of the loop, up to `stepIndex`.
 * `depth` counts frames from the bottom of the stack (main = 0), which stays stable while calls come and go.
 * The current iteration shows its latest value.
 */
export function loopVariableHistory(trace, stepIndex, { depth, startLine, activationStep, name }) {
  const byIteration = new Map();
  const last = Math.min(stepIndex, (trace?.length ?? 0) - 1);
  for (let index = activationStep; index <= last; index += 1) {
    const frames = trace[index]?.frames ?? [];
    const frame = frames[frames.length - 1 - depth];
    if (!frame) continue;
    const run = findRun(frame.loops, startLine, activationStep);
    if (!run || run.iteration < 1) continue;
    const variable = frame.variables?.find((item) => item.name === name);
    if (variable) byIteration.set(run.iteration, variable);
  }
  return [...byIteration.entries()].map(([iteration, variable]) => ({ iteration, variable }));
}

export function loopKindLabel(kind) {
  return { for: 'for loop', 'for-each': 'for-each loop', while: 'while loop', 'do-while': 'do-while loop' }[kind] ?? 'loop';
}
