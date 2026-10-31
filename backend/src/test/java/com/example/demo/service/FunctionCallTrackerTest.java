package com.example.demo.service;

import com.example.demo.model.CodeMetadata;
import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.FunctionCallSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The raw steps below mirror what the JDI tracer records (verified against real traces):
 * a "call" event with the callee on top, callee line steps, a "return" event while the callee is
 * still on top, then a step back in the caller on the call line.
 */
class FunctionCallTrackerTest {
    private static final CodeMetadata NO_METADATA = new CodeMetadata(List.of(), List.of(), List.of(), List.of(), List.of());

    private static final String FUN_MULTI = """
            public class Main {
                static int fun_add(int a, int b) {
                    return a + b;
                }

                static int fun_multi(int x, int y) {
                    int times = Math.min(x, y);
                    int number = Math.max(x, y);
                    int result = 0;
                    for (int i = 0; i < times; i++) {
                        result = fun_add(result, number);
                    }
                    return result;
                }

                public static void main(String[] args) {
                    int a = 4, b = 2;
                    System.out.println(a + " x " + b + " = " + fun_multi(a, b));
                }
            }
            """;

    @Test
    void callSitesAreListedInEvaluationOrderWithTargets() {
        String source = """
                public class Main {
                    static class Node { Node(int v) {} }
                    static int add(int a, int b) { return a + b; }
                    static int fact(int n) { return n <= 1 ? 1 : n * fact(n - 1); }
                    static int twice(int n) { return add(n, n); }
                    public static void main(String[] args) {
                        int x = add(add(1, 2), 3);
                        Node node = new Node(5);
                        Runnable r = () -> add(1, 1);
                    }
                }
                """;
        List<CallSiteAnalyzer.CallSite> sites = CallSiteAnalyzer.analyze(source);

        CallSiteAnalyzer.CallSite recursive = find(sites, "fact", "fact");
        assertEquals("fact(n - 1)", recursive.sourceExpression());
        assertEquals(null, recursive.target());                       // nested in n * ..., no target

        assertEquals("return", find(sites, "twice", "add").target());

        List<CallSiteAnalyzer.CallSite> mainAdds = sites.stream()
                .filter(site -> site.callerMethod().equals("main") && site.calledMethod().equals("add")).toList();
        assertEquals(2, mainAdds.size(), "the lambda call is not attributed to main");
        assertEquals("add(1, 2)", mainAdds.get(0).sourceExpression(), "inner call runs first");
        assertEquals(null, mainAdds.get(0).target());
        assertEquals("x", mainAdds.get(1).target());

        CallSiteAnalyzer.CallSite constructor = find(sites, "main", CallSiteAnalyzer.CONSTRUCTOR);
        assertEquals("Node", constructor.calledClass());
        assertEquals("new Node", constructor.displayName());
        assertEquals("node", constructor.target());
    }

    @Test
    void unparsableSourceProducesNoCallSites() {
        assertTrue(CallSiteAnalyzer.analyze("public class {").isEmpty());
        assertTrue(CallSiteAnalyzer.analyze(null).isEmpty());
    }

    @Test
    void everyWaitingCallerKeepsItsCallAndReturnedStateLastsUntilTheLineChanges() {
        FrameSnapshot main = frame("main", 18);
        FrameSnapshot multi = frame("fun_multi", 11, local("result", "0"));
        FrameSnapshot add = frame("fun_add", 3, param("a", "0"), param("b", "4"));
        List<TraceStep> raw = List.of(
                step("call", 7, null, frame("fun_multi", 7, param("x", "4"), param("y", "2")), main),
                step("step", 11, null, multi, main),
                step("call", 3, null, add, multi, main),
                step("return", 3, "4", add, multi, main),
                step("step", 11, null, frame("fun_multi", 11, local("result", "4")), main),
                step("step", 10, null, frame("fun_multi", 10, local("result", "4")), main),
                step("step", 13, null, frame("fun_multi", 13, local("result", "8")), main),
                step("return", 13, "8", frame("fun_multi", 13, local("result", "8")), main),
                step("step", 18, null, main),
                step("step", 19, null, frame("main", 19)));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, FUN_MULTI);

        // While fun_add runs, both fun_multi and main show their pending call.
        assertEquals(List.of("result = fun_add(0, 4)"), expressions(result.get(2), 1));
        assertEquals(List.of("fun_multi(4, 2)"), expressions(result.get(2), 2));
        assertEquals("evaluating", calls(result.get(2), 1).get(0).state());
        assertEquals("result = fun_add(result, number)", calls(result.get(2), 1).get(0).sourceExpression());

        // Return event and the following step on the same line show the result.
        assertEquals(List.of("result = 4 <= fun_add(0, 4)"), expressions(result.get(3), 1));
        FunctionCallSnapshot returned = calls(result.get(4), 0).get(0);
        assertEquals("returned", returned.state());
        assertEquals("4", returned.returnValue());

        // fun_multi moved to line 10: its box is gone; main is still waiting.
        assertTrue(calls(result.get(5), 0).isEmpty());
        assertEquals(List.of("fun_multi(4, 2)"), expressions(result.get(5), 1));

        assertEquals(List.of("8 <= fun_multi(4, 2)"), expressions(result.get(8), 0));
        assertTrue(calls(result.get(9), 0).isEmpty());
    }

    @Test
    void severalCallsOnOneLineAreShownInExecutionOrder() {
        String source = """
                public class Main {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int x = add(add(1, 2), 3);
                    }
                }
                """;
        FrameSnapshot main = frame("main", 4);
        FrameSnapshot inner = frame("add", 2, param("a", "1"), param("b", "2"));
        FrameSnapshot outer = frame("add", 2, param("a", "3"), param("b", "3"));
        List<TraceStep> raw = List.of(
                step("call", 2, null, inner, main),
                step("return", 2, "3", inner, main),
                step("step", 4, null, main),
                step("call", 2, null, outer, main),
                step("return", 2, "6", outer, main),
                step("step", 4, null, main),
                step("step", 5, null, frame("main", 5)));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, source);

        assertEquals(List.of("3 <= add(1, 2)", "x = add(3, 3)"), expressions(result.get(3), 1));
        assertEquals(List.of("3 <= add(1, 2)", "x = 6 <= add(3, 3)"), expressions(result.get(5), 0));
        assertTrue(calls(result.get(6), 0).isEmpty());
    }

    @Test
    void repeatedCallOnTheSameLineReplacesThePreviousRow() {
        String source = """
                public class Main {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int s = 0;
                        for (int i = 0; i < 2; i++) s = add(s, i);
                    }
                }
                """;
        FrameSnapshot main = frame("main", 5);
        List<TraceStep> raw = List.of(
                step("call", 2, null, frame("add", 2, param("a", "0"), param("b", "0")), main),
                step("return", 2, "0", frame("add", 2, param("a", "0"), param("b", "0")), main),
                step("step", 5, null, main),
                step("call", 2, null, frame("add", 2, param("a", "0"), param("b", "1")), main));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, source);

        assertEquals(List.of("s = add(0, 1)"), expressions(result.get(3), 1));
    }

    @Test
    void constructorCallShowsTheCreatedObject() {
        String source = """
                public class Main {
                    static class Node {
                        int value;
                        Node(int value) { this.value = value; }
                    }
                    public static void main(String[] args) {
                        Node n = new Node(5);
                    }
                }
                """;
        FrameSnapshot main = frame("main", 7);
        FrameSnapshot constructor = new FrameSnapshot("<init>", "Main$Node", "void", 4, List.of(
                new VariableSnapshot("this", "Main$Node", "Main$Node@42", "this", "42"), param("value", "5")));
        List<TraceStep> raw = List.of(
                step("call", 4, null, constructor, main),
                step("return", 4, null, constructor, main));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, source);

        assertEquals(List.of("n = new Node(5)"), expressions(result.get(0), 1));
        assertEquals(List.of("n = Node@42 <= new Node(5)"), expressions(result.get(1), 1));
    }

    @Test
    void stringAndCharArgumentsAreQuotedAndLibraryCallsAreIgnored() {
        String source = """
                public class Main {
                    static void greet(String name, char mark) { System.out.println(name + mark); }
                    public static void main(String[] args) {
                        greet("bob", '!');
                    }
                }
                """;
        FrameSnapshot greet = new FrameSnapshot("greet", "Main", "void", 2, List.of(
                new VariableSnapshot("name", "java.lang.String", "bob", "parameter", "9"),
                new VariableSnapshot("mark", "char", "!", "parameter", null)));
        List<TraceStep> raw = List.of(
                step("call", 2, null, greet, frame("main", 4)),
                step("return", 2, null, greet, frame("main", 4)));

        List<TraceStep> result = new TraceEnricher().enrich(raw, NO_METADATA, false, source);

        assertEquals(List.of("greet(\"bob\", '!')"), expressions(result.get(0), 1));
        FunctionCallSnapshot voidCall = calls(result.get(1), 1).get(0);
        assertEquals("returned", voidCall.state());
        assertEquals(null, voidCall.returnValue());
        assertEquals("greet(\"bob\", '!')", voidCall.expression());
    }

    private static CallSiteAnalyzer.CallSite find(List<CallSiteAnalyzer.CallSite> sites, String caller, String called) {
        return sites.stream().filter(site -> site.callerMethod().equals(caller) && site.calledMethod().equals(called))
                .findFirst().orElseThrow();
    }

    private static List<FunctionCallSnapshot> calls(TraceStep step, int frameIndex) {
        return step.frames().get(frameIndex).calls();
    }

    private static List<String> expressions(TraceStep step, int frameIndex) {
        return calls(step, frameIndex).stream().map(FunctionCallSnapshot::expression).toList();
    }

    private static TraceStep step(String event, int line, String returnValue, FrameSnapshot... frames) {
        return new TraceStep(0, line, event, "", List.of(frames), event, returnValue, null, null, List.of(), List.of());
    }

    private static FrameSnapshot frame(String method, int line, VariableSnapshot... variables) {
        return new FrameSnapshot(method, "Main", "main".equals(method) ? "void" : "int", line,
                new ArrayList<>(List.of(variables)));
    }

    private static VariableSnapshot param(String name, String value) {
        return new VariableSnapshot(name, "int", value, "parameter", null);
    }

    private static VariableSnapshot local(String name, String value) {
        return new VariableSnapshot(name, "int", value, "local", null);
    }
}
