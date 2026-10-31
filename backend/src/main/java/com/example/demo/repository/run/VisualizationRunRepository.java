package com.example.demo.repository.run;

import com.example.demo.model.run.VisualizationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Every query is scoped to one user id, so a user can only ever read their own runs. */
public interface VisualizationRunRepository extends JpaRepository<VisualizationRun, UUID> {
    long countByUserId(UUID userId);

    long countByUserIdAndOutcome(UUID userId, String outcome);

    List<VisualizationRun> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
