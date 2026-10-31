package com.example.demo.model;

import java.util.List;

/** One user method frame: its variables, the calls it is making and the loops it is running. */
public record FrameSnapshot(
        String method,
        String className,
        String returnType,
        int line,
        List<VariableSnapshot> variables,
        List<FunctionCallSnapshot> calls,
        List<LoopSnapshot> loops) {

    /** Retains source compatibility for callers constructing frames without call-site or loop data. */
    public FrameSnapshot(String method, String className, String returnType, int line,
                         List<VariableSnapshot> variables) {
        this(method, className, returnType, line, variables, List.of(), List.of());
    }

    public FrameSnapshot(String method, String className, String returnType, int line,
                         List<VariableSnapshot> variables, List<FunctionCallSnapshot> calls) {
        this(method, className, returnType, line, variables, calls, List.of());
    }
}
