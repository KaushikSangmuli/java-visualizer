package com.example.demo.model;

/** A safe textual snapshot of one visible value in a suspended user frame. */
public record VariableSnapshot(String name, String type, String value, String kind, String referenceId) {}
