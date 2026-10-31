package com.example.demo.service.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Optional beta gate for new accounts ({@code visualizer.auth.allowed-signup-emails}, env
 * {@code ALLOWED_SIGNUP_EMAILS}). Entries are comma-separated exact emails ({@code a@b.com}) or whole
 * domains with a leading {@code @} ({@code @cdac.in}). An empty list allows everyone. Only signup is
 * gated; existing accounts keep logging in.
 */
public final class SignupAllowlist {
    private final List<String> entries;

    public SignupAllowlist(String configured) {
        this.entries = configured == null ? List.of() : Arrays.stream(configured.split(","))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    public boolean isOpen() {
        return entries.isEmpty();
    }

    public boolean allows(String email) {
        if (entries.isEmpty()) return true;
        if (email == null) return false;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.lastIndexOf('@');
        String domain = at < 0 ? "" : normalized.substring(at);
        return entries.stream().anyMatch(entry -> entry.startsWith("@") ? entry.equals(domain) : entry.equals(normalized));
    }

    /** Thrown when signup is attempted with an email outside the allowlist. */
    public static final class NotAllowedException extends RuntimeException {
        public NotAllowedException() {
            super("Sign-up is limited to invited email addresses during the beta.");
        }
    }
}
