package com.example.demo.model;

import java.util.List;

/** Stack frames are ordered from the currently executing method to its callers. */
public record FrameSnapshot(
        String method,
        String className,
        String returnType,
        int line,
        List<VariableSnapshot> variables,
        List<FunctionCallSnapshot> calls) {

    /** Retains source compatibility for callers constructing frames without call-site data. */
    public FrameSnapshot(String method, String className, String returnType, int line,
                         List<VariableSnapshot> variables) {
        this(method, className, returnType, line, variables, List.of());
    }
}
