package com.example.demo.model;

/** One changed field, array element, or static value within a heap entity. */
public record HeapValueChange(String name, String change, String before, String after) {}
