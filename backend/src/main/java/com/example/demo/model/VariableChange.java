package com.example.demo.model;

/** A visible variable value changed between consecutive trace snapshots. */
public record VariableChange(String frame, String name, String change, String before, String after) {}
