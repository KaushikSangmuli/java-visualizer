package com.example.demo.model;

import java.util.List;

public record TraceStep(
        int step,
        int line,
        String event,
        String stdout,
        List<FrameSnapshot> frames,
        String explanation,
        String returnValue,
        HeapSnapshot heap,
        String lineType,
        List<VariableChange> variableChanges,
        List<HeapChange> heapChanges) {}
