package com.example.demo.model.run;

import com.example.demo.service.run.RunOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One "Run & visualize" click by a signed-in user. The submitted source code is deliberately not stored. */
@Entity
@Table(name = "visualization_run")
public class VisualizationRun {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "outcome", nullable = false, length = 16) private String outcome;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "message", length = 1000) private String message;
    @Column(name = "error_detail", length = 4000) private String errorDetail;
    @Column(name = "step_count", nullable = false) private int stepCount;
    @Column(name = "truncated", nullable = false) private boolean truncated;
    @Column(name = "code_length", nullable = false) private int codeLength;
    @Column(name = "duration_ms", nullable = false) private long durationMs;

    protected VisualizationRun() { }

    public VisualizationRun(UUID userId, RunOutcome outcome, int codeLength, long durationMs) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.outcome = outcome.outcome();
        this.status = outcome.status();
        this.message = outcome.message();
        this.errorDetail = outcome.errorDetail();
        this.stepCount = outcome.stepCount();
        this.truncated = outcome.truncated();
        this.codeLength = codeLength;
        this.durationMs = durationMs;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getOutcome() { return outcome; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getErrorDetail() { return errorDetail; }
    public int getStepCount() { return stepCount; }
    public boolean isTruncated() { return truncated; }
    public int getCodeLength() { return codeLength; }
    public long getDurationMs() { return durationMs; }
}
