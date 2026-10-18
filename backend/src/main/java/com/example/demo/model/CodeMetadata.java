package com.example.demo.model;

import java.util.List;

/** Parser-derived source summary used by clients to explain trace locations. */
public record CodeMetadata(List<MethodMetadata> methods, List<SourceRange> loops,
                           List<SourceRange> branches, List<SourceRange> declarations,
                           List<SourceRange> returns) {}
