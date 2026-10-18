package com.example.demo.model;

/** A static field declared by a user class loaded in the debuggee. */
public record StaticFieldSnapshot(String className, String name, String type, MemoryValue value) {}
