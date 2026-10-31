package com.example.demo.service;

import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.HeapObjectSnapshot;
import com.example.demo.model.HeapSnapshot;
import com.example.demo.model.TraceStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies {@link CollectionUsageAnalyzer} to each step: an ArrayDeque or LinkedList that the code
 * uses as a stack is shown as a stack (front = top), one used as a queue as a queue (front first).
 * Other collections keep the view the tracer chose.
 */
final class CollectionViewResolver {
    private static final Set<String> DOUBLE_ENDED = Set.of("java.util.ArrayDeque", "java.util.LinkedList",
            "java.util.concurrent.ConcurrentLinkedDeque", "java.util.concurrent.LinkedBlockingDeque");

    private final Map<String, String> viewsByName;

    CollectionViewResolver(String source) {
        this.viewsByName = CollectionUsageAnalyzer.analyze(source);
    }

    TraceStep apply(TraceStep step) {
        HeapSnapshot heap = step.heap();
        if (viewsByName.isEmpty() || heap == null || heap.objects() == null) return step;

        // Which names point at each object: locals/parameters, object fields and static fields.
        Map<String, List<String>> namesById = new HashMap<>();
        if (step.frames() != null) {
            for (FrameSnapshot frame : step.frames()) {
                if (frame.variables() == null) continue;
                frame.variables().forEach(variable -> addName(namesById, variable.referenceId(), variable.name()));
            }
        }
        heap.objects().forEach(object -> {
            if (object.fields() != null) object.fields().forEach(field -> addName(namesById, field.value() == null ? null : field.value().referenceId(), field.name()));
        });
        if (heap.staticFields() != null) {
            heap.staticFields().forEach(field -> addName(namesById, field.value() == null ? null : field.value().referenceId(), field.name()));
        }

        boolean changed = false;
        List<HeapObjectSnapshot> objects = new ArrayList<>(heap.objects().size());
        for (HeapObjectSnapshot object : heap.objects()) {
            String view = object.collection() == null || !DOUBLE_ENDED.contains(object.className()) ? null
                    : namesById.getOrDefault(object.id(), List.of()).stream().map(viewsByName::get)
                    .filter(candidate -> candidate != null).findFirst().orElse(null);
            if (view == null || view.equals(object.collection().view())) {
                objects.add(object);
                continue;
            }
            changed = true;
            objects.add(new HeapObjectSnapshot(object.id(), object.className(), object.kind(), object.fields(),
                    object.elements(), object.length(), object.expanded(),
                    object.collection().withView(view, object.collection().items())));
        }
        if (!changed) return step;
        HeapSnapshot updated = new HeapSnapshot(List.copyOf(objects), heap.staticFields(), heap.truncated(), heap.truncatedReason());
        return new TraceStep(step.step(), step.line(), step.event(), step.stdout(), step.frames(), step.explanation(),
                step.returnValue(), updated, step.lineType(), step.variableChanges(), step.heapChanges());
    }

    private static void addName(Map<String, List<String>> namesById, String id, String name) {
        if (id != null && name != null) namesById.computeIfAbsent(id, key -> new ArrayList<>()).add(name);
    }
}
