package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionUsageAnalyzerTest {
    @Test
    void pushAndPopMeanStackOfferPollOrQueueTypeMeanQueue() {
        Map<String, String> views = CollectionUsageAnalyzer.analyze("""
                import java.util.*;
                public class Main {
                    private Deque<Integer> history = new ArrayDeque<>();
                    void remember(int v) { this.history.push(v); }
                    static void bfs(Queue<Integer> frontier) { }
                    public static void main(String[] args) {
                        Deque<Integer> stack = new ArrayDeque<>();
                        stack.push(1);
                        Deque<Integer> line = new ArrayDeque<>();
                        line.offer(2);
                        Queue<Integer> q = new LinkedList<>();
                        LinkedList<Integer> plain = new LinkedList<>();
                        plain.add(3);
                    }
                }
                """);
        assertEquals("stack", views.get("stack"));
        assertEquals("stack", views.get("history"), "this.field.push counts for the field");
        assertEquals("queue", views.get("line"));
        assertEquals("queue", views.get("q"), "declared Queue type");
        assertEquals("queue", views.get("frontier"), "Queue parameter");
        assertTrue(!views.containsKey("plain"), "a plain LinkedList keeps the linked-list view");
    }

    @Test
    void unparsableSourceGivesNoViews() {
        assertTrue(CollectionUsageAnalyzer.analyze("class {").isEmpty());
    }
}
