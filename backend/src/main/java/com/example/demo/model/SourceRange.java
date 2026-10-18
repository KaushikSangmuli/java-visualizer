package com.example.demo.model;

/** One source construct's inclusive line range, emitted from parser metadata. */
public record SourceRange(int startLine, int endLine, String kind, String label) {}
