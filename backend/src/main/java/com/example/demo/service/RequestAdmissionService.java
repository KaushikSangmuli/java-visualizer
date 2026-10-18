package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Bounds concurrent debuggee launches and applies a simple per-client fixed-window limit. */
@Service
public class RequestAdmissionService {
    private final Semaphore concurrent;
    private final int requestsPerWindow;
    private final long windowMillis;
    private final int maxClients;
    private final Map<String, Window> windows = new HashMap<>();

    public RequestAdmissionService(
            @Value("${visualizer.max-concurrent:4}") int maxConcurrent,
            @Value("${visualizer.rate-limit-requests:60}") int requestsPerWindow,
            @Value("${visualizer.rate-limit-window-ms:60000}") long windowMillis,
            @Value("${visualizer.rate-limit-max-clients:10000}") int maxClients) {
        this.concurrent = new Semaphore(Math.max(1, maxConcurrent));
        this.requestsPerWindow = Math.max(1, requestsPerWindow);
        this.windowMillis = Math.max(1000, windowMillis);
        this.maxClients = Math.max(1, maxClients);
    }

    public Admission acquire(String clientKey) {
        if (!allowClient(clientKey == null ? "unknown" : clientKey)) {
            return new Admission(false, "Rate limit reached. Please wait before sending another visualization request.");
        }
        if (!concurrent.tryAcquire()) {
            return new Admission(false, "Backend is busy. Try again shortly.");
        }
        return new Admission(true, null);
    }

    public void release() {
        concurrent.release();
    }

    private synchronized boolean allowClient(String key) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowMillis);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= maxClients) return false;
            windows.put(key, new Window(now, 1));
            return true;
        }
        if (now - window.startedAt >= windowMillis) {
            windows.put(key, new Window(now, 1));
            return true;
        }
        if (window.requests >= requestsPerWindow) return false;
        window.requests++;
        return true;
    }

    public record Admission(boolean allowed, String message) {}
    private static final class Window {
        private final long startedAt;
        private int requests;
        private Window(long startedAt, int requests) { this.startedAt = startedAt; this.requests = requests; }
    }
}
