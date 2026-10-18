package com.example.demo.model;

import java.util.List;

/** Bounded object graph and user-class static fields visible at one trace event. */
public record HeapSnapshot(List<HeapObjectSnapshot> objects,
                           List<StaticFieldSnapshot> staticFields,
                           boolean truncated,
                           String truncatedReason) {}
