package com.example.demo.service;

import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.FunctionCallSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walks trace steps in order and attaches "function call" rows to the frames that made them.
 *
 * <p>A row appears in the caller when the callee is entered ({@code evaluating}), changes to
 * {@code returned} with the result when the callee exits, and stays until the caller moves to a
 * different source line. Every caller in the stack keeps its own rows, so {@code main} still shows
 * {@code fun_multi(4, 2)} while {@code fun_add} runs two levels deeper. Several calls on one line
 * are listed in the order they run.
 *
 * <p>Frames are tracked by depth from the bottom of the stack ({@code main} is depth 0), which stays
 * stable while deeper frames come and go. If a program exceeds the displayed-frame limit the bottom
 * frames are cut off and rows may be dropped; that only affects very deep recursion.
 */
final class FunctionCallTracker {
    private final List<CallSiteAnalyzer.CallSite> sites;
    private final Map<Integer, Group> groups = new HashMap<>();

    FunctionCallTracker(List<CallSiteAnalyzer.CallSite> sites) {
        this.sites = sites;
    }

    /** Must be called once per raw step, in trace order. */
    TraceStep apply(TraceStep step) {
        List<FrameSnapshot> frames = step.frames() == null ? List.of() : step.frames();
        int size = frames.size();
        if (sites.isEmpty() || size == 0) return step;

        dropFinishedGroups(frames);
        if ("call".equals(step.event()) && size >= 2) onCall(frames);
        if ("return".equals(step.event()) && size >= 2) onReturn(frames, step.returnValue());
        return withCalls(step, frames);
    }

    private void dropFinishedGroups(List<FrameSnapshot> frames) {
        groups.entrySet().removeIf(entry -> {
            int depth = entry.getKey();
            if (depth >= frames.size()) return true;                     // that frame has returned
            FrameSnapshot frame = atDepth(frames, depth);
            Group group = entry.getValue();
            return !group.method.equals(frame.method())                 // a different invocation
                    || frame.line() < group.minLine || frame.line() > group.maxLine;   // caller moved on
        });
    }

    private void onCall(List<FrameSnapshot> frames) {
        int calleeDepth = frames.size() - 1;
        groups.remove(calleeDepth);                                      // a fresh invocation has made no calls yet
        FrameSnapshot callee = frames.get(0);
        FrameSnapshot caller = frames.get(1);
        Group group = groups.get(calleeDepth - 1);

        List<CallSiteAnalyzer.CallSite> candidates = sites.stream()
                .filter(site -> site.callerMethod().equals(caller.method()))
                .filter(site -> sameClass(site.callerClass(), caller.className()))
                .filter(site -> site.calledMethod().equals(callee.method()))
                .filter(site -> site.calledClass() == null || sameClass(site.calledClass(), callee.className()))
                .filter(site -> caller.line() >= site.startLine() && caller.line() <= site.endLine())
                .toList();
        if (candidates.isEmpty()) return;

        List<VariableSnapshot> parameters = parameters(callee);
        Optional<CallSiteAnalyzer.CallSite> unused = best(candidates, group, parameters.size(), true);
        CallSiteAnalyzer.CallSite site;
        if (unused.isPresent()) {
            site = unused.get();
        } else {
            // Every call on this line already ran once: the line is executing again (e.g. a one-line loop).
            groups.remove(calleeDepth - 1);
            group = null;
            site = best(candidates, null, parameters.size(), false).orElseThrow();
        }
        if (group == null) {
            group = new Group(caller.method());
            groups.put(calleeDepth - 1, group);
        }
        group.add(new Row(site, invocation(site, parameters), callee.method()));
    }

    private void onReturn(List<FrameSnapshot> frames, String returnValue) {
        FrameSnapshot callee = frames.get(0);
        Group group = groups.get(frames.size() - 2);
        if (group == null) return;
        for (int index = group.rows.size() - 1; index >= 0; index--) {
            Row row = group.rows.get(index);
            if (row.returned || !row.calleeMethod.equals(callee.method())) continue;
            row.returned = true;
            row.returnValue = CallSiteAnalyzer.CONSTRUCTOR.equals(callee.method())
                    ? createdObject(callee)
                    : (returnValue == null && !"void".equals(callee.returnType()) ? "null" : shortValue(returnValue));
            return;
        }
    }

    private TraceStep withCalls(TraceStep step, List<FrameSnapshot> frames) {
        if (groups.isEmpty()) return step;
        List<FrameSnapshot> updated = new ArrayList<>(frames.size());
        for (int index = 0; index < frames.size(); index++) {
            FrameSnapshot frame = frames.get(index);
            Group group = groups.get(frames.size() - 1 - index);
            if (group == null || group.rows.isEmpty()) {
                updated.add(frame);
            } else {
                updated.add(new FrameSnapshot(frame.method(), frame.className(), frame.returnType(),
                        frame.line(), frame.variables(), group.snapshot()));
            }
        }
        return new TraceStep(step.step(), step.line(), step.event(), step.stdout(), List.copyOf(updated),
                step.explanation(), step.returnValue(), step.heap(), step.lineType(),
                step.variableChanges(), step.heapChanges());
    }

    /** Prefers sites not yet used on this line, then a matching argument count, then evaluation order. */
    private static Optional<CallSiteAnalyzer.CallSite> best(List<CallSiteAnalyzer.CallSite> candidates, Group group,
                                                           int parameterCount, boolean unusedOnly) {
        return candidates.stream()
                .filter(site -> !unusedOnly || group == null || !group.usedOrders.contains(site.order()))
                .min(Comparator.comparing((CallSiteAnalyzer.CallSite site) -> site.argumentCount() != parameterCount)
                        .thenComparingInt(CallSiteAnalyzer.CallSite::order));
    }

    private static FrameSnapshot atDepth(List<FrameSnapshot> frames, int depth) {
        return frames.get(frames.size() - 1 - depth);
    }

    private static List<VariableSnapshot> parameters(FrameSnapshot frame) {
        return frame.variables() == null ? List.of()
                : frame.variables().stream().filter(variable -> "parameter".equals(variable.kind())).toList();
    }

    /** {@code fun_add(0, 4)}; falls back to the source text when values cannot be lined up (varargs). */
    private static String invocation(CallSiteAnalyzer.CallSite site, List<VariableSnapshot> parameters) {
        if (parameters.size() != site.argumentCount()) return site.sourceExpression();
        List<String> values = parameters.stream().map(FunctionCallTracker::argumentValue).toList();
        return site.displayName() + "(" + String.join(", ", values) + ")";
    }

    private static String argumentValue(VariableSnapshot variable) {
        String value = variable.value() == null ? "null" : variable.value();
        if ("null".equals(value) || variable.referenceId() != null && !"java.lang.String".equals(variable.type())) {
            return shortValue(value);
        }
        if ("java.lang.String".equals(variable.type())) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\r", "\\r").replace("\n", "\\n") + "\"";
        }
        if ("char".equals(variable.type())) return "'" + value + "'";
        return value;
    }

    private static String createdObject(FrameSnapshot constructor) {
        if (constructor.variables() == null) return null;
        return constructor.variables().stream().filter(variable -> "this".equals(variable.kind()))
                .map(variable -> shortValue(variable.value())).findFirst().orElse(null);
    }

    /** {@code Main$Node@42} becomes {@code Node@42}; other values are unchanged. */
    static String shortValue(String value) {
        if (value == null) return null;
        int at = value.lastIndexOf('@');
        if (at <= 0) return value;
        String type = value.substring(0, at);
        type = type.substring(type.lastIndexOf('.') + 1);
        type = type.substring(type.lastIndexOf('$') + 1);
        return type + value.substring(at);
    }

    /** Compares a source simple name with a JVM binary name such as {@code Main$Node}; null means unknown. */
    private static boolean sameClass(String sourceName, String binaryName) {
        if (sourceName == null || binaryName == null) return true;
        String simple = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        simple = simple.substring(simple.lastIndexOf('$') + 1);
        return simple.equals(sourceName) || simple.endsWith(sourceName);   // local classes: Main$1Local
    }

    private static final class Row {
        final CallSiteAnalyzer.CallSite site;
        final String invocation;
        final String calleeMethod;
        boolean returned;
        String returnValue;

        Row(CallSiteAnalyzer.CallSite site, String invocation, String calleeMethod) {
            this.site = site;
            this.invocation = invocation;
            this.calleeMethod = calleeMethod;
        }

        FunctionCallSnapshot snapshot() {
            String target = site.target();
            String prefix = target == null ? "" : "return".equals(target) ? "return " : target + " = ";
            String expression = returned && returnValue != null
                    ? prefix + returnValue + " <= " + invocation
                    : prefix + invocation;
            return new FunctionCallSnapshot(site.startLine(), expression, returned ? "returned" : "evaluating",
                    returned ? returnValue : null, prefix + site.sourceExpression());
        }
    }

    private static final class Group {
        final String method;
        final List<Row> rows = new ArrayList<>();
        final java.util.Set<Integer> usedOrders = new java.util.HashSet<>();
        int minLine = Integer.MAX_VALUE;
        int maxLine = Integer.MIN_VALUE;

        Group(String method) {
            this.method = method;
        }

        void add(Row row) {
            rows.add(row);
            usedOrders.add(row.site.order());
            minLine = Math.min(minLine, row.site.startLine());
            maxLine = Math.max(maxLine, row.site.endLine());
        }

        List<FunctionCallSnapshot> snapshot() {
            return rows.stream().map(Row::snapshot).toList();
        }
    }
}
