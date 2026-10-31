package com.example.demo.model;

import java.util.List;

/**
 * One identity-deduplicated object, array, or collection node. For recognised java.util collections
 * {@code collection} holds the DSA view and {@code fields}/{@code elements} stay empty.
 */
public record HeapObjectSnapshot(String id, String className, String kind,
                                 List<HeapFieldSnapshot> fields, List<MemoryValue> elements,
                                 int length, boolean expanded, CollectionSnapshot collection) {

    public HeapObjectSnapshot(String id, String className, String kind, List<HeapFieldSnapshot> fields,
                              List<MemoryValue> elements, int length, boolean expanded) {
        this(id, className, kind, fields, elements, length, expanded, null);
    }
}
