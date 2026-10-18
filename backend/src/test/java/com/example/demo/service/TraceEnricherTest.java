package com.example.demo.service;

import com.example.demo.model.CodeMetadata;
import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.HeapObjectSnapshot;
import com.example.demo.model.HeapFieldSnapshot;
import com.example.demo.model.HeapSnapshot;
import com.example.demo.model.MemoryValue;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEnricherTest {
    @Test
    void emitsLineClassificationStateDeltasAndFinalEndEvent() {
        var metadata = new CodeMetadata(List.of(), List.of(), List.of(), List.of(), List.of());
        var first = step(1, List.of(new VariableSnapshot("n", "int", "1", "local", null)));
        var second = step(2, List.of(new VariableSnapshot("n", "int", "2", "local", null)));

        List<TraceStep> result = new TraceEnricher().enrich(List.of(first, second), metadata);

        assertThat(result).hasSize(3);
        assertThat(result.get(1).variableChanges()).singleElement().satisfies(change -> {
            assertThat(change.name()).isEqualTo("n");
            assertThat(change.before()).isEqualTo("1");
            assertThat(change.after()).isEqualTo("2");
        });
        assertThat(result.get(0).heapChanges()).singleElement().satisfies(change -> {
            assertThat(change.id()).isEqualTo("17");
            assertThat(change.change()).isEqualTo("allocated");
            assertThat(change.values()).singleElement().satisfies(value -> {
                assertThat(value.name()).isEqualTo("n");
                assertThat(value.after()).isEqualTo("1");
            });
        });
        assertThat(result.get(1).heapChanges()).singleElement().satisfies(change -> {
            assertThat(change.change()).isEqualTo("updated");
            assertThat(change.values()).singleElement().satisfies(value -> {
                assertThat(value.before()).isEqualTo("1");
                assertThat(value.after()).isEqualTo("2");
            });
        });
        assertThat(result.get(2).event()).isEqualTo("end");
        assertThat(result.get(2).lineType()).isEqualTo("end");

        var limited = new TraceEnricher().enrich(List.of(first), metadata, true);
        assertThat(limited.get(limited.size() - 1).explanation()).contains("trace limit");
    }

    private static TraceStep step(int line, List<VariableSnapshot> variables) {
        var frame = new FrameSnapshot("main", "Demo", "void", line, variables);
        var memoryValue = new MemoryValue("int", variables.get(0).value(), null);
        var object = new HeapObjectSnapshot("17", "Demo$Node", "object",
                List.of(new HeapFieldSnapshot("n", "int", memoryValue)), List.of(), -1, true);
        var heap = new HeapSnapshot(List.of(object), List.of(), false, null);
        return new TraceStep(line - 1, line, "step", "", List.of(frame), "line", null, heap,
                null, List.of(), List.of());
    }
}
