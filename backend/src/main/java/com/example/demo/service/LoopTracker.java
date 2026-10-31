package com.example.demo.service;

import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.HeapObjectSnapshot;
import com.example.demo.model.HeapSnapshot;
import com.example.demo.model.LoopSnapshot;
import com.example.demo.model.StaticFieldSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attaches the loops each frame is running to that frame, with the iteration number, the ending
 * condition filled with current values, and nested loops inside their parent.
 *
 * <p>An iteration starts each time the frame's line enters the loop body from outside it (from the
 * header/condition line or from before the loop). A run of a loop ends when the frame's line leaves
 * the loop; reaching it again later (for example an inner loop on the next outer iteration) starts a
 * new run with a new {@code activationStep}. Frames are tracked by depth from {@code main}.
 */
final class LoopTracker {
    private static final int LOOKAHEAD_LIMIT = 2000;

    private final List<LoopSiteAnalyzer.LoopSite> sites;
    private final List<TraceStep> raw;
    private final Map<Integer, FrameState> frames = new HashMap<>();

    LoopTracker(List<LoopSiteAnalyzer.LoopSite> sites, List<TraceStep> raw) {
        this.sites = sites;
        this.raw = raw;
    }

    /** Must be called once per step, in trace order; {@code index} is the step's position in the raw trace. */
    TraceStep apply(TraceStep step, int index) {
        List<FrameSnapshot> stack = step.frames() == null ? List.of() : step.frames();
        if (sites.isEmpty() || stack.isEmpty()) return step;
        int size = stack.size();
        frames.entrySet().removeIf(entry -> entry.getKey() >= size
                || !entry.getValue().method.equals(atDepth(stack, entry.getKey()).method()));

        boolean moved = "step".equals(step.event()) || "call".equals(step.event());
        List<FrameSnapshot> updated = new ArrayList<>(size);
        boolean changed = false;
        for (int position = 0; position < size; position++) {
            FrameSnapshot frame = stack.get(position);
            int depth = size - 1 - position;
            boolean top = position == 0;
            List<LoopSiteAnalyzer.LoopSite> running = sites.stream()
                    .filter(site -> site.callerMethod().equals(frame.method()))
                    .filter(site -> sameClass(site.callerClass(), frame.className()))
                    .filter(site -> site.contains(frame.line()))
                    .sorted(Comparator.comparingInt(LoopSiteAnalyzer.LoopSite::startLine)
                            .thenComparing(Comparator.comparingInt(LoopSiteAnalyzer.LoopSite::endLine).reversed()))
                    .toList();
            FrameState state = frames.get(depth);
            if (running.isEmpty()) {
                if (state != null) state.runs.clear();
                if (state != null && top && moved) state.previousLine = frame.line();
                updated.add(frame);
                continue;
            }
            if (state == null) {
                state = new FrameState(frame.method());
                frames.put(depth, state);
            }
            List<Integer> ids = running.stream().map(LoopSiteAnalyzer.LoopSite::id).toList();
            state.runs.keySet().removeIf(id -> !ids.contains(id));

            List<LoopSnapshot> chain = new ArrayList<>();
            for (LoopSiteAnalyzer.LoopSite site : running) {
                Run run = state.runs.computeIfAbsent(site.id(), id -> new Run(index));
                if (top && moved) advance(site, run, frame.line(), state.previousLine);
                Boolean result = top && moved && site.atCondition(frame.line()) && !site.oneLine()
                        ? conditionResult(site, index, depth) : null;
                chain.add(snapshot(site, run, frame, step.heap(), result));
            }
            if (top && moved) state.previousLine = frame.line();

            // Running loops containing one line always nest; fold the chain inner-to-outer.
            LoopSnapshot nested = null;
            for (int i = chain.size() - 1; i >= 0; i--) {
                LoopSnapshot loop = chain.get(i);
                nested = new LoopSnapshot(loop.kind(), loop.startLine(), loop.endLine(), loop.headerLine(),
                        loop.condition(), loop.conditionValues(), loop.conditionResult(), loop.iteration(),
                        loop.activationStep(), loop.variables(), nested == null ? List.of() : List.of(nested));
            }
            updated.add(new FrameSnapshot(frame.method(), frame.className(), frame.returnType(), frame.line(),
                    frame.variables(), frame.calls(), List.of(nested)));
            changed = true;
        }
        if (!changed) return step;
        return new TraceStep(step.step(), step.line(), step.event(), step.stdout(), List.copyOf(updated),
                step.explanation(), step.returnValue(), step.heap(), step.lineType(),
                step.variableChanges(), step.heapChanges());
    }

    private static void advance(LoopSiteAnalyzer.LoopSite site, Run run, int line, Integer previousLine) {
        if (site.oneLine()) {
            if (site.atCondition(line)) run.iteration++;          // approximate: one visit per iteration
            return;
        }
        boolean enteredBody = site.inBody(line) && (previousLine == null || !site.inBody(previousLine));
        if (enteredBody) run.iteration++;
    }

    /** Looks ahead to where this frame goes next: into the body means the condition held. */
    private Boolean conditionResult(LoopSiteAnalyzer.LoopSite site, int index, int depth) {
        for (int next = index + 1; next < raw.size() && next <= index + LOOKAHEAD_LIMIT; next++) {
            List<FrameSnapshot> stack = raw.get(next).frames();
            if (stack == null || stack.size() < depth + 1) return false;      // frame returned or threw
            if (stack.size() > depth + 1) continue;                          // a method called from the condition
            int line = stack.get(0).line();
            if (site.inBody(line)) return true;
            if (site.atCondition(line)) continue;
            return false;
        }
        return null;
    }

    private static LoopSnapshot snapshot(LoopSiteAnalyzer.LoopSite site, Run run, FrameSnapshot frame,
                                         HeapSnapshot heap, Boolean result) {
        Map<String, VariableSnapshot> visible = new HashMap<>();
        if (frame.variables() != null) frame.variables().forEach(variable -> visible.put(variable.name(), variable));
        Map<String, String> statics = new HashMap<>();
        if (heap != null && heap.staticFields() != null) {
            for (StaticFieldSnapshot field : heap.staticFields()) {
                if (field.value() != null) statics.putIfAbsent(field.name(), field.value().value());
            }
        }
        String values;
        if (site.iterable() != null) {
            Integer total = iterableSize(site.iterable(), visible, heap);
            values = total == null ? "iteration " + run.iteration : Math.min(run.iteration, total) + " of " + total;
        } else if (site.condition() == null) {
            values = null;
        } else {
            values = withValues(site.condition(), visible, statics, heap);
        }
        return new LoopSnapshot(site.kind(), site.startLine(), site.endLine(), site.conditionFirst(),
                site.conditionText(), values, result, run.iteration, run.activationStep, site.variables(), List.of());
    }

    /** Replaces variable names (and {@code array.length}) in the condition with their current values. */
    static String withValues(Expression condition, Map<String, VariableSnapshot> visible,
                             Map<String, String> statics, HeapSnapshot heap) {
        Expression copy = condition.clone();
        if (copy instanceof NameExpr name) {
            String value = valueOf(name.getNameAsString(), visible, statics);
            return value == null ? copy.toString() : value;
        }
        for (FieldAccessExpr access : copy.findAll(FieldAccessExpr.class)) {
            if (!"length".equals(access.getNameAsString()) || !(access.getScope() instanceof NameExpr scope)) continue;
            HeapObjectSnapshot array = heapObject(visible.get(scope.getNameAsString()), heap);
            if (array != null && "array".equals(array.kind())) access.replace(new NameExpr(Integer.toString(array.length())));
        }
        for (NameExpr name : copy.findAll(NameExpr.class)) {
            String value = valueOf(name.getNameAsString(), visible, statics);
            if (value != null) name.replace(new NameExpr(value));
        }
        return copy.toString();
    }

    private static String valueOf(String name, Map<String, VariableSnapshot> visible, Map<String, String> statics) {
        VariableSnapshot variable = visible.get(name);
        String value = variable != null ? variable.value() : statics.get(name);
        if (value == null) return null;
        if (variable != null && "java.lang.String".equals(variable.type()) && !"null".equals(value)) return "\"" + value + "\"";
        if (variable != null && "char".equals(variable.type())) return "'" + value + "'";
        return FunctionCallTracker.shortValue(value);
    }

    private static Integer iterableSize(Expression iterable, Map<String, VariableSnapshot> visible, HeapSnapshot heap) {
        if (!(iterable instanceof NameExpr name)) return null;
        HeapObjectSnapshot object = heapObject(visible.get(name.getNameAsString()), heap);
        if (object == null) return null;
        if ("array".equals(object.kind())) return object.length();
        if (object.fields() == null) return null;
        return object.fields().stream().filter(field -> "size".equals(field.name()) && field.value() != null)
                .map(field -> parseInt(field.value().value())).filter(size -> size != null).findFirst().orElse(null);
    }

    private static HeapObjectSnapshot heapObject(VariableSnapshot variable, HeapSnapshot heap) {
        if (variable == null || variable.referenceId() == null || heap == null || heap.objects() == null) return null;
        return heap.objects().stream().filter(object -> variable.referenceId().equals(object.id())).findFirst().orElse(null);
    }

    private static Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static FrameSnapshot atDepth(List<FrameSnapshot> stack, int depth) {
        return stack.get(stack.size() - 1 - depth);
    }

    private static boolean sameClass(String sourceName, String binaryName) {
        if (sourceName == null || binaryName == null) return true;
        String simple = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        simple = simple.substring(simple.lastIndexOf('$') + 1);
        return simple.equals(sourceName) || simple.endsWith(sourceName);
    }

    private static final class FrameState {
        final String method;
        final Map<Integer, Run> runs = new HashMap<>();
        Integer previousLine;

        FrameState(String method) {
            this.method = method;
        }
    }

    private static final class Run {
        final int activationStep;
        int iteration;

        Run(int activationStep) {
            this.activationStep = activationStep;
        }
    }
}
