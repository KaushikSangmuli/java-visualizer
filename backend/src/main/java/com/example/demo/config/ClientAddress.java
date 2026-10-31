package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The caller's IP address for rate limiting and login throttling.
 *
 * <p>Behind Azure Container Apps every request arrives from the ingress proxy, so
 * {@code getRemoteAddr()} would be the same for all users. When {@code visualizer.trust-forwarded-for}
 * is true (the {@code azure} profile), the last {@code X-Forwarded-For} entry is used: it is the one
 * the platform's ingress appended, so a client cannot spoof it by sending its own header.
 * Locally the header is ignored.
 */
@Component
public class ClientAddress {
    private final boolean trustForwardedFor;

    public ClientAddress(@Value("${visualizer.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String of(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String last = hops[hops.length - 1].trim();
                if (!last.isEmpty()) return last;
            }
        }
        return request.getRemoteAddr();
    }
}
