package com.example.demo.model;

import java.util.List;

public record VisualizeResponse(
        String status,
        String message,
        List<CompileError> errors,
        List<TraceStep> trace,
        boolean truncated,
        String truncatedReason,
        ExceptionInfo exception,
        String stdout,
        CodeMetadata metadata,
        List<BlockedApi> blockedApis) {

    public static VisualizeResponse error(String status, String message) {
        return new VisualizeResponse(status, message, null, List.of(), false, null, null, "", null, null);
    }

    public static VisualizeResponse compileError(List<CompileError> errors) {
        return new VisualizeResponse("compile_error", "Compilation failed.", errors, List.of(), false, null, null, "", null, null);
    }

    public static VisualizeResponse blocked(List<BlockedApi> apis, CodeMetadata metadata) {
        return new VisualizeResponse("blocked_api", "Source uses APIs restricted by the visualizer policy.",
                null, List.of(), false, null, null, "", metadata, apis);
    }
}
