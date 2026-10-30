package com.example.demo.model;

/** A user-method invocation displayed in its caller frame for one trace event. */
public record FunctionCallSnapshot(int line, String expression, String state, String returnValue) {}
