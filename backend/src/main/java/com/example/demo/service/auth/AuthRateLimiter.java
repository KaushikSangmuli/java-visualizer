package com.example.demo.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Small per-instance login throttle; a shared distributed limiter is needed before horizontal scale-out. */
@Component
public class AuthRateLimiter {
    private record Window(int failures, Instant expiresAt) { }
    private final ConcurrentMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long windowMillis;
    private final int maxClients;

    public AuthRateLimiter(@Value("${visualizer.auth.login-max-failures:10}") int maxFailures,
                           @Value("${visualizer.auth.login-window-ms:900000}") long windowMillis,
                           @Value("${visualizer.rate-limit-max-clients:10000}") int maxClients) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowMillis;
        this.maxClients = maxClients;
    }

    public boolean isAllowed(String client) {
        Window window = attempts.get(client);
        if (window == null) return true;
        if (Instant.now().isAfter(window.expiresAt())) { attempts.remove(client, window); return true; }
        return window.failures() < maxFailures;
    }

    public void failed(String client) {
        Instant now = Instant.now();
        attempts.compute(client, (key, old) -> old == null || now.isAfter(old.expiresAt())
                ? new Window(1, now.plusMillis(windowMillis))
                : new Window(old.failures() + 1, old.expiresAt()));
        if (attempts.size() > maxClients) attempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    public void succeeded(String client) { attempts.remove(client); }
}
