package com.example.demo.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_challenge")
public class EmailChallenge {
    public enum Purpose { SIGNUP, PASSWORD_RESET }
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "email", nullable = false, length = 320) private String email;
    @Enumerated(EnumType.STRING) @Column(name = "purpose", nullable = false, length = 32) private Purpose purpose;
    @Column(name = "code_hash", nullable = false, length = 100) private String codeHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "consumed_at") private Instant consumedAt;

    protected EmailChallenge() { }
    public EmailChallenge(String email, Purpose purpose, String codeHash, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.email = email; this.purpose = purpose;
        this.codeHash = codeHash; this.createdAt = Instant.now(); this.expiresAt = expiresAt;
    }
    public String getEmail() { return email; }
    public Purpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public Instant createdAtPlus(java.time.Duration duration) { return createdAt.plus(duration); }
    public void failedAttempt() { attempts++; }
    public void consume() { consumedAt = Instant.now(); }
}
