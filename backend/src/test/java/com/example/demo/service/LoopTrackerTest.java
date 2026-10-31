package com.example.demo.service;

import com.example.demo.model.CodeMetadata;
import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.LoopSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Raw steps mirror real JDI line events for the source below (verified against actual traces). */
class LoopTrackerTest {
    private static final CodeMetadata NO_METADATA = new CodeMetadata(List.of(), List.of(), List.of(), List.of(), List.of());

    private static final String NESTED = """
            public class Main {
                public static void main(String[] args) {
                    int total = 0;
                    for (int i = 0; i < 2; i++) {
                        int row = i * 10;
                        for (int j = 0; j < 1; j++) {
                            total = total + j;
                        }
                    }
                }
            }
            """;

    @Test
    void analyzerFindsLoopKindsConditionsAndOwnVariables() {
        String source = """
                public class Main {
                    public static void main(String[] args) {
                        int n = 3;
                        while (n > 1) {
                            int half = n / 2;
                            n--;
                        }
                        int[] arr = {5, 6};
                        for (int x : arr) {
                            for (int y = 0; y < x; y++) {
                                int inner = y;
                            }
                        }
                        do {
                            n++;
                        } while (n < 3);
                    }
                }
                """;
        List<LoopSiteAnalyzer.LoopSite> sites = LoopSiteAnalyzer.analyze(source);
        assertEquals(List.of("while", "for-each", "for", "do-while"), sites.stream().map(LoopSiteAnalyzer.LoopSite::kind).toList());

        LoopSiteAnalyzer.LoopSite whileLoop = sites.get(0);
        assertEquals("n > 1", whileLoop.conditionText());
        assertEquals(List.of("half"), whileLoop.variables());
        assertTrue(whileLoop.atCondition(4));
        assertTrue(whileLoop.inBody(5));

        assertEquals("x : arr", sites.get(1).conditionText());
        assertEquals(List.of("x"), sites.get(1).variables(), "inner's variable belongs to the nested loop");
        assertEquals(List.of("y", "inner"), sites.get(2).variables());

        LoopSiteAnalyzer.LoopSite doWhile = sites.get(3);
        assertEquals(16, doWhile.conditionFirst(), "do-while checks its condition on the while line");
        assertTrue(doWhile.inBody(15));
    }

    @Test
    void nestedLoopsTrackIterationsConditionValuesAndNewInnerRuns() {
        List<TraceStep> raw = List.of(
                step(3),
                step(4, local("total", "0")),                                        // outer check, i not declared yet
                step(5, local("total", "0"), local("i", "0")),                       // outer iteration 1
                step(6, local("total", "0"), local("i", "0"), local("row", "0")),   // inner check
                step(7, local("total", "0"), local("i", "0"), local("row", "0"), local("j", "0")),
                step(6, local("total", "0"), local("i", "0"), local("row", "0"), local("j", "0")),   // inner exits
                step(4, local("total", "0"), local("i", "0")),                       // outer check again
                step(5, local("total", "0"), local("i", "1")),                       // outer iteration 2
                step(6, local("total", "0"), local("i", "1"), local("row", "10")),  // new inner run
                step(7, local("total", "0"), local("i", "1"), local("row", "10"), local("j", "0")),
                step(6, local("total", "0"), local("i", "1"), local("row", "10"), local("j", "0")),
                step(4, local("total", "0"), local("i", "1")),
                step(10, local("total", "0")));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, NESTED);

        LoopSnapshot firstCheck = outer(result.get(1));
        assertEquals(0, firstCheck.iteration());
        assertEquals(Boolean.TRUE, firstCheck.conditionResult());
        assertEquals("i < 2", firstCheck.condition());
        assertEquals(List.of("i", "row"), firstCheck.variables());

        LoopSnapshot inBody = outer(result.get(4));
        assertEquals(1, inBody.iteration());
        assertEquals("0 < 2", inBody.conditionValues());
        assertEquals(null, inBody.conditionResult(), "no verdict outside the condition line");
        LoopSnapshot inner = inBody.loops().get(0);
        assertEquals(1, inner.iteration());
        assertEquals("0 < 1", inner.conditionValues());
        assertEquals(List.of("j"), inner.variables());

        assertEquals(Boolean.FALSE, outer(result.get(5)).loops().get(0).conditionResult());

        LoopSnapshot secondOuter = outer(result.get(9));
        assertEquals(2, secondOuter.iteration());
        assertEquals("1 < 2", secondOuter.conditionValues());
        int firstInnerRun = inBody.loops().get(0).activationStep();
        assertTrue(secondOuter.loops().get(0).activationStep() > firstInnerRun, "inner loop starts a new run");

        assertEquals(Boolean.FALSE, outer(result.get(11)).conditionResult());
        assertTrue(result.get(12).frames().get(0).loops().isEmpty(), "loop box disappears after the loop");
    }

    @Test
    void callerFramesKeepTheirLoopWhileACalleeRuns() {
        String source = """
                public class Main {
                    static int sq(int v) { return v * v; }
                    public static void main(String[] args) {
                        for (int i = 0; i < 3; i++) {
                            int s = sq(i);
                        }
                    }
                }
                """;
        FrameSnapshot callee = new FrameSnapshot("sq", "Main", "int", 2, List.of(param("v", "0")));
        List<TraceStep> raw = List.of(
                step(4),
                step(5, local("i", "0")),
                new TraceStep(0, 2, "call", "", List.of(callee, frame(5, local("i", "0"))), "", null, null, null, List.of(), List.of()));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, source);

        FrameSnapshot caller = result.get(2).frames().get(1);
        assertEquals(1, caller.loops().get(0).iteration());
        assertEquals("s = sq(0)", caller.calls().get(0).expression());
        assertEquals(5, caller.calls().get(0).line(), "the call's line places it inside the loop box");
    }

    private static LoopSnapshot outer(TraceStep step) {
        return step.frames().get(0).loops().get(0);
    }

    private static TraceStep step(int line, VariableSnapshot... variables) {
        return new TraceStep(0, line, "step", "", List.of(frame(line, variables)), "", null, null, null, List.of(), List.of());
    }

    private static FrameSnapshot frame(int line, VariableSnapshot... variables) {
        return new FrameSnapshot("main", "Main", "void", line, new ArrayList<>(List.of(variables)));
    }

    private static VariableSnapshot local(String name, String value) {
        return new VariableSnapshot(name, "int", value, "local", null);
    }

    private static VariableSnapshot param(String name, String value) {
        return new VariableSnapshot(name, "int", value, "parameter", null);
    }
}
