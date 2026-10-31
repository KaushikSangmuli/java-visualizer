package com.example.demo.service.run;

import com.example.demo.model.BlockedApi;
import com.example.demo.model.CompileError;
import com.example.demo.model.ExceptionInfo;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VisualizeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunOutcomeTest {
    private static final TraceStep STEP = new TraceStep(0, 1, "step", "", List.of(), "", null, null, null, List.of(), List.of());

    @Test
    void completedRunIsSuccessWithoutErrorDetail() {
        var response = new VisualizeResponse("ok", null, null, List.of(STEP, STEP), false, null, null, "hi", null, null);
        RunOutcome outcome = RunOutcome.from(response);
        assertEquals(RunOutcome.SUCCESS, outcome.outcome());
        assertEquals("ok", outcome.status());
        assertEquals(null, outcome.errorDetail());
        assertEquals(2, outcome.stepCount());
    }

    @Test
    void compileErrorsAreStoredLineByLine() {
        RunOutcome outcome = RunOutcome.from(VisualizeResponse.compileError(List.of(
                new CompileError(3, "';' expected"), new CompileError(5, "cannot find symbol"))));
        assertEquals(RunOutcome.FAILED, outcome.outcome());
        assertEquals("compile_error", outcome.status());
        assertEquals("Compilation failed.", outcome.message());
        assertEquals("Line 3: ';' expected\nLine 5: cannot find symbol", outcome.errorDetail());
    }

    @Test
    void runtimeExceptionStoresTypeLineAndMessage() {
        var exception = new ExceptionInfo("java.lang.ArithmeticException", 7, "/ by zero");
        var response = new VisualizeResponse("runtime_error", "java.lang.ArithmeticException: / by zero", null,
                List.of(STEP), false, null, exception, "", null, null);
        RunOutcome outcome = RunOutcome.from(response);
        assertEquals(RunOutcome.FAILED, outcome.outcome());
        assertEquals("java.lang.ArithmeticException at line 7: / by zero", outcome.errorDetail());
    }

    @Test
    void blockedApiTruncatedBusyAndEmptyRunsAreFailures() {
        RunOutcome blocked = RunOutcome.from(VisualizeResponse.blocked(List.of(new BlockedApi(4, "System.exit", "terminates the JVM")), null));
        assertEquals("Line 4: System.exit - terminates the JVM", blocked.errorDetail());

        var truncated = new VisualizeResponse("ok", "Trace truncated. Possible infinite loop or too many steps.", null,
                List.of(STEP), true, "max_steps", null, "", null, null);
        RunOutcome limit = RunOutcome.from(truncated);
        assertEquals(RunOutcome.FAILED, limit.outcome());
        assertTrue(limit.truncated());
        assertEquals("Stopped at a trace limit: max_steps", limit.errorDetail());

        RunOutcome busy = RunOutcome.from(VisualizeResponse.error("busy", "Too many requests."));
        assertEquals("busy", busy.status());
        assertEquals("Too many requests.", busy.errorDetail());

        assertEquals(RunOutcome.FAILED, RunOutcome.from(null).outcome());
    }

    @Test
    void longMessagesAreCutToTheColumnSize() {
        String huge = "x".repeat(RunOutcome.MAX_DETAIL + 50);
        RunOutcome outcome = RunOutcome.from(VisualizeResponse.error("error", huge));
        assertEquals(RunOutcome.MAX_MESSAGE, outcome.message().length());
        assertEquals(RunOutcome.MAX_DETAIL, outcome.errorDetail().length());
    }
}
