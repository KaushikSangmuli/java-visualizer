package com.example.demo.model;

/** A primitive/string value or a reference to an entry in the event's heap snapshot. */
public record MemoryValue(String type, String value, String referenceId) {}
