package com.example.demo.controller;

import com.example.demo.service.run.RunHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/runs: the signed-in user's run totals and recent runs (requires authentication). */
@RestController
public class RunHistoryController {
    private final RunHistoryService runHistory;

    public RunHistoryController(RunHistoryService runHistory) {
        this.runHistory = runHistory;
    }

    @GetMapping("/api/runs")
    public ResponseEntity<RunHistoryService.RunSummary> runs(Authentication authentication) {
        return runHistory.summary(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
