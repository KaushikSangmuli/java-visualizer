package com.example.demo.model;

import java.util.List;

/** An object identity appeared, changed, or disappeared in the bounded heap snapshot. */
public record HeapChange(String id, String className, String kind, String change,
                         List<HeapValueChange> values) {}
