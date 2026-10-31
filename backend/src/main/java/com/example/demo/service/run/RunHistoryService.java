package com.example.demo.service.run;

import com.example.demo.model.VisualizeResponse;
import com.example.demo.model.run.VisualizationRun;
import com.example.demo.repository.auth.UserAccountRepository;
import com.example.demo.repository.run.VisualizationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Records each visualization run for the signed-in user and reports their run history. */
@Service
public class RunHistoryService {
    private static final Logger log = LoggerFactory.getLogger(RunHistoryService.class);

    private final UserAccountRepository users;
    private final VisualizationRunRepository runs;

    public RunHistoryService(UserAccountRepository users, VisualizationRunRepository runs) {
        this.users = users;
        this.runs = runs;
    }

    /**
     * Saves one run. Recording must never break visualization, so database problems are logged
     * (without code or credentials) and the user still gets their trace.
     */
    public void record(String email, String code, VisualizeResponse response, long durationMs) {
        if (email == null) return;
        try {
            users.findByEmail(normalize(email)).ifPresent(user -> runs.save(new VisualizationRun(
                    user.getId(), RunOutcome.from(response), code == null ? 0 : code.length(), durationMs)));
        } catch (RuntimeException exception) {
            log.warn("Could not record visualization run: {}", exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<RunSummary> summary(String email) {
        return users.findByEmail(normalize(email)).map(user -> new RunSummary(
                runs.countByUserId(user.getId()),
                runs.countByUserIdAndOutcome(user.getId(), RunOutcome.SUCCESS),
                runs.countByUserIdAndOutcome(user.getId(), RunOutcome.FAILED),
                runs.findTop20ByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(RunView::of).toList()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Totals plus the 20 most recent runs, newest first. */
    public record RunSummary(long totalRuns, long successfulRuns, long failedRuns, List<RunView> recentRuns) {}

    public record RunView(Instant createdAt, String outcome, String status, String message, String errorDetail,
                          int stepCount, boolean truncated, int codeLength, long durationMs) {
        static RunView of(VisualizationRun run) {
            return new RunView(run.getCreatedAt(), run.getOutcome(), run.getStatus(), run.getMessage(),
                    run.getErrorDetail(), run.getStepCount(), run.isTruncated(), run.getCodeLength(), run.getDurationMs());
        }
    }
}
