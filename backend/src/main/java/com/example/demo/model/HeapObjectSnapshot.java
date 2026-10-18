package com.example.demo.model;

import java.util.List;

/** One identity-deduplicated object, array, or collection node. */
public record HeapObjectSnapshot(String id, String className, String kind,
                                 List<HeapFieldSnapshot> fields, List<MemoryValue> elements,
                                 int length, boolean expanded) {}
