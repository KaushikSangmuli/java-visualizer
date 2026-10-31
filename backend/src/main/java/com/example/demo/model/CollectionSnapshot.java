package com.example.demo.model;

import java.util.List;

/**
 * DSA view of a java.util collection: only its elements, size and (for maps) key/value pairs,
 * or (for linked lists) its nodes with their addresses. Internal fields such as capacity,
 * {@code modCount} or hash buckets are left out.
 *
 * @param view     list, linked-list, stack, queue, priority-queue, set or map
 * @param typeName simple class name shown to the learner, e.g. {@code ArrayList}
 * @param size     number of elements (or entries)
 * @param items    elements in display order: index order for lists, top first for stacks,
 *                 front first for queues, next-out first for priority queues, iteration order for sets
 * @param entries  key/value pairs for maps, in iteration order
 * @param nodes    linked-list nodes from first to last, each with its own address and neighbours' addresses
 * @param order    extra ordering note: {@code priority} (sorted, next out first) or {@code heap} (heap array
 *                 order because a custom comparator cannot be applied without running user code); otherwise null
 * @param complete false when only the first elements were captured (configured element limit)
 */
public record CollectionSnapshot(String view, String typeName, int size, List<MemoryValue> items,
                                 List<MapEntrySnapshot> entries, List<LinkedNodeSnapshot> nodes,
                                 String order, boolean complete) {

    public CollectionSnapshot withView(String newView, List<MemoryValue> newItems) {
        return new CollectionSnapshot(newView, typeName, size, newItems, entries, nodes, order, complete);
    }
}
