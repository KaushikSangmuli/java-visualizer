package com.example.demo.service.run;

import com.example.demo.model.BlockedApi;
import com.example.demo.model.CompileError;
import com.example.demo.model.ExceptionInfo;
import com.example.demo.model.VisualizeResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What is stored about one run: SUCCESS only when the program ran to completion without an
 * exception or trace limit; everything else is FAILED with a readable reason.
 *
 * @param outcome     {@code SUCCESS} or {@code FAILED}
 * @param status      API status: ok, compile_error, runtime_error, blocked_api, busy or error
 * @param message     short summary (the response message, or a generated one)
 * @param errorDetail why it failed: compiler errors, exception, blocked APIs or limit; null on success
 */
public record RunOutcome(String outcome, String status, String message, String errorDetail,
                         int stepCount, boolean truncated) {
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    static final int MAX_MESSAGE = 1000;
    static final int MAX_DETAIL = 4000;

    public static RunOutcome from(VisualizeResponse response) {
        if (response == null) return new RunOutcome(FAILED, "error", "No response was produced.", null, 0, false);
        String status = response.status() == null ? "error" : response.status();
        int steps = response.trace() == null ? 0 : response.trace().size();
        boolean truncated = response.truncated();
        boolean success = "ok".equals(status) && !truncated;
        String detail = success ? null : detail(status, response);
        String message = response.message();
        if (message == null && !success) message = truncated ? "Trace stopped at a safety limit." : "Run failed.";
        return new RunOutcome(success ? SUCCESS : FAILED, status, limit(message, MAX_MESSAGE),
                limit(detail, MAX_DETAIL), steps, truncated);
    }

    private static String detail(String status, VisualizeResponse response) {
        return switch (status) {
            case "compile_error" -> join(response.errors(), (CompileError error) -> "Line " + error.line() + ": " + error.message());
            case "blocked_api" -> join(response.blockedApis(), (BlockedApi api) -> "Line " + api.line() + ": " + api.api() + " - " + api.reason());
            case "runtime_error" -> exception(response.exception());
            case "ok" -> "Stopped at a trace limit: " + (response.truncatedReason() == null ? "limit" : response.truncatedReason());
            default -> response.message();
        };
    }

    private static String exception(ExceptionInfo exception) {
        if (exception == null) return null;
        return exception.type() + " at line " + exception.line()
                + (exception.message() == null ? "" : ": " + exception.message());
    }

    private static <T> String join(List<T> items, java.util.function.Function<T, String> format) {
        if (items == null || items.isEmpty()) return null;
        return items.stream().map(format).collect(Collectors.joining("\n"));
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max - 3) + "...";
    }
}
