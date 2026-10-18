package com.example.demo.model;

import java.util.List;

public record MethodMetadata(String name, String className, String returnType,
                             int startLine, int endLine, List<String> parameters) {}
