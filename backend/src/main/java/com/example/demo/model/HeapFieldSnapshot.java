package com.example.demo.model;

/** One instance or static field captured without evaluating user code. */
public record HeapFieldSnapshot(String name, String type, MemoryValue value) {}
