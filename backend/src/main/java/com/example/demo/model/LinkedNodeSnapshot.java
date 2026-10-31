package com.example.demo.model;

/**
 * One node of a linked list: its own address, the value it holds and the addresses of the previous
 * and next nodes (null at the ends).
 */
public record LinkedNodeSnapshot(String id, String prevId, MemoryValue value, String nextId) {}
