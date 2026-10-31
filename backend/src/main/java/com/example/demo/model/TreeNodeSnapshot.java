package com.example.demo.model;

/**
 * One node of a TreeMap/TreeSet red-black tree: its address, key (and value for maps), child addresses
 * (null when absent) and colour.
 */
public record TreeNodeSnapshot(String id, MemoryValue key, MemoryValue value, String leftId, String rightId, boolean red) {}
