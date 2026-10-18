package com.example.demo.service;

import com.example.demo.model.CodeMetadata;
import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.HeapChange;
import com.example.demo.model.HeapObjectSnapshot;
import com.example.demo.model.HeapSnapshot;
import com.example.demo.model.HeapValueChange;
import com.example.demo.model.MemoryValue;
import com.example.demo.model.StaticFieldSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableChange;
import com.example.demo.model.VariableSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adds source construct labels and event-to-event state deltas without changing JDI capture. */
@Service
public class TraceEnricher {
    public List<TraceStep> enrich(List<TraceStep> raw, CodeMetadata metadata) {
        return enrich(raw, metadata, false);
    }

    public List<TraceStep> enrich(List<TraceStep> raw, CodeMetadata metadata, boolean truncated) {
        List<TraceStep> result = new ArrayList<>(raw.size() + (raw.isEmpty() ? 0 : 1));
        Map<String, VariableSnapshot> previousVariables = Map.of();
        Map<String, HeapObjectSnapshot> previousObjects = Map.of();
        Map<String, String> previousStatics = Map.of();
        int loopPasses = 0;
        int previousLoopLine = -1;

        for (int index = 0; index < raw.size(); index++) {
            TraceStep step = raw.get(index);
            String lineType = lineType(step.line(), step.event(), metadata);
            if (metadata.loops().stream().anyMatch(loop -> loop.startLine() == step.line())) {
                if (previousLoopLine == step.line()) loopPasses++;
                else loopPasses = 1;
                previousLoopLine = step.line();
            }
            Integer nextLine = index + 1 < raw.size() ? raw.get(index + 1).line() : null;
            String explanation = explanation(step, lineType, loopPasses, nextLine);
            Map<String, VariableSnapshot> currentVariables = variables(step.frames());
            List<VariableChange> variableChanges = variableChanges(previousVariables, currentVariables);
            previousVariables = currentVariables;

            Map<String, HeapObjectSnapshot> currentObjects = heapObjects(step.heap());
            Map<String, String> currentStatics = staticValues(step.heap());
            List<HeapChange> heapChanges = heapChanges(previousObjects, currentObjects,
                    previousStatics, currentStatics);
            previousObjects = currentObjects;
            previousStatics = currentStatics;
            result.add(copy(step, lineType, variableChanges, heapChanges, explanation, result.size()));
        }

        if (!result.isEmpty()) {
            TraceStep last = result.get(result.size() - 1);
            result.add(new TraceStep(result.size(), last.line(), "end", last.stdout(), last.frames(),
                    endExplanation(last, truncated), null, last.heap(), "end", List.of(), List.of()));
        }
        return List.copyOf(result);
    }

    private static String endExplanation(TraceStep last, boolean truncated) {
        if (truncated) return "Execution stopped at a configured trace limit.";
        if ("exception".equals(last.event())) return "Execution ended after an uncaught exception.";
        return "Execution finished.";
    }

    private static TraceStep copy(TraceStep step, String lineType, List<VariableChange> variableChanges,
                                  List<HeapChange> heapChanges, String explanation, int newStep) {
        return new TraceStep(newStep, step.line(), step.event(), step.stdout(), step.frames(), explanation,
                step.returnValue(), step.heap(), lineType, variableChanges, heapChanges);
    }

    private static String lineType(int line, String event, CodeMetadata metadata) {
        if ("exception".equals(event)) return "exception";
        if ("end".equals(event)) return "end";
        if (metadata.loops().stream().anyMatch(loop -> loop.startLine() == line)) return "loop_condition";
        if (metadata.branches().stream().anyMatch(branch -> branch.startLine() == line)) return "branch_condition";
        if (metadata.returns().stream().anyMatch(statement -> statement.startLine() == line)) return "return";
        if (metadata.declarations().stream().anyMatch(statement -> statement.startLine() == line)) return "declaration";
        if (metadata.loops().stream().anyMatch(loop -> line > loop.startLine() && line <= loop.endLine())) return "loop_body";
        if (metadata.branches().stream().anyMatch(branch -> line > branch.startLine() && line <= branch.endLine())) return "branch_body";
        return "statement";
    }

    private static String explanation(TraceStep step, String lineType, int loopPasses, Integer nextLine) {
        if ("loop_condition".equals(lineType)) return "Reached a loop header (observed pass " + loopPasses + ").";
        if ("loop_body".equals(lineType)) return "Executed a statement inside a loop.";
        if ("branch_condition".equals(lineType)) return "Reached a conditional branch" +
                (nextLine == null ? "." : "; execution next reached line " + nextLine + ".");
        return step.explanation();
    }

    private static Map<String, VariableSnapshot> variables(List<FrameSnapshot> frames) {
        Map<String, VariableSnapshot> result = new LinkedHashMap<>();
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            FrameSnapshot frame = frames.get(frameIndex);
            String frameKey = frame.className() + "." + frame.method() + "[" + frameIndex + "]";
            for (VariableSnapshot variable : frame.variables()) result.put(frameKey + "." + variable.name(), variable);
        }
        return result;
    }

    private static List<VariableChange> variableChanges(Map<String, VariableSnapshot> before,
                                                        Map<String, VariableSnapshot> after) {
        List<VariableChange> result = new ArrayList<>();
        after.forEach((key, value) -> {
            VariableSnapshot old = before.get(key);
            if (old == null) result.add(new VariableChange(framePart(key), value.name(), "added", null, value.value()));
            else if (!Objects.equals(old.value(), value.value())) {
                result.add(new VariableChange(framePart(key), value.name(), "changed", old.value(), value.value()));
            }
        });
        before.forEach((key, old) -> {
            if (!after.containsKey(key)) result.add(new VariableChange(framePart(key), old.name(), "out_of_scope", old.value(), null));
        });
        return List.copyOf(result);
    }

    private static String framePart(String key) {
        int lastDot = key.lastIndexOf('.');
        return lastDot < 0 ? key : key.substring(0, lastDot);
    }

    private static Map<String, HeapObjectSnapshot> heapObjects(HeapSnapshot heap) {
        if (heap == null || heap.objects() == null) return Map.of();
        Map<String, HeapObjectSnapshot> result = new LinkedHashMap<>();
        for (HeapObjectSnapshot object : heap.objects()) result.put(object.id(), object);
        return result;
    }

    private static Map<String, String> staticValues(HeapSnapshot heap) {
        if (heap == null || heap.staticFields() == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (StaticFieldSnapshot field : heap.staticFields()) {
            result.put(field.className() + "." + field.name(), display(field.value()));
        }
        return result;
    }

    private static List<HeapChange> heapChanges(Map<String, HeapObjectSnapshot> before,
                                                Map<String, HeapObjectSnapshot> after,
                                                Map<String, String> beforeStatics,
                                                Map<String, String> afterStatics) {
        List<HeapChange> result = new ArrayList<>();
        after.forEach((id, object) -> {
            HeapObjectSnapshot old = before.get(id);
            List<HeapValueChange> values = diffValues(old == null ? Map.of() : objectValues(old), objectValues(object));
            String change = old == null ? "allocated" : (values.isEmpty() ? null : "updated");
            if (change != null) result.add(new HeapChange(id, object.className(), object.kind(), change, values));
        });
        before.keySet().stream().filter(id -> !after.containsKey(id)).forEach(id -> {
            HeapObjectSnapshot object = before.get(id);
            result.add(new HeapChange(id, object.className(), object.kind(), "unreachable", List.of()));
        });
        afterStatics.forEach((name, value) -> {
            String old = beforeStatics.get(name);
            if (!Objects.equals(old, value)) {
                int split = name.lastIndexOf('.');
                String className = split < 0 ? "" : name.substring(0, split);
                result.add(new HeapChange("static:" + name, className, "static_field",
                        beforeStatics.containsKey(name) ? "updated" : "initialized",
                        List.of(new HeapValueChange(name.substring(split + 1), beforeStatics.containsKey(name) ? "changed" : "added", old, value))));
            }
        });
        return List.copyOf(result);
    }

    private static Map<String, String> objectValues(HeapObjectSnapshot object) {
        Map<String, String> result = new LinkedHashMap<>();
        if (object.fields() != null) object.fields().forEach(field -> result.put(field.name(), display(field.value())));
        if (object.elements() != null) {
            for (int index = 0; index < object.elements().size(); index++) {
                result.put("[" + index + "]", display(object.elements().get(index)));
            }
        }
        return result;
    }

    private static List<HeapValueChange> diffValues(Map<String, String> before, Map<String, String> after) {
        List<HeapValueChange> result = new ArrayList<>();
        after.forEach((name, value) -> {
            String old = before.get(name);
            if (!Objects.equals(old, value)) result.add(new HeapValueChange(name,
                    old == null ? "added" : "changed", old, value));
        });
        before.forEach((name, value) -> {
            if (!after.containsKey(name)) result.add(new HeapValueChange(name, "removed", value, null));
        });
        return List.copyOf(result);
    }

    private static String display(MemoryValue value) {
        if (value == null) return "null";
        return value.referenceId() == null ? value.value() : value.type() + "@" + value.referenceId();
    }
}
