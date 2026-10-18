package com.example.demo.model;

import java.util.List;

/** Stack frames are ordered from the currently executing method to its callers. */
public record FrameSnapshot(
        String method,
        String className,
        String returnType,
        int line,
        List<VariableSnapshot> variables) {}
