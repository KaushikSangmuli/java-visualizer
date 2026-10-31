package com.example.demo.controller;

import com.example.demo.config.ClientAddress;
import com.example.demo.model.VisualizeRequest;
import com.example.demo.model.VisualizeResponse;
import com.example.demo.service.VisualizeService;
import com.example.demo.service.run.RunHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VisualizeController {

    private final VisualizeService service;
    private final RunHistoryService runHistory;
    private final ClientAddress clientAddress;

    public VisualizeController(VisualizeService service, RunHistoryService runHistory, ClientAddress clientAddress) {
        this.service = service;
        this.runHistory = runHistory;
        this.clientAddress = clientAddress;
    }

    /**
     * POST /api/visualize   body: { "code": "..." }   -> always HTTP 200, check "status".
     * Every call (success or failure) is recorded in the signed-in user's run history.
     */
    @PostMapping("/api/visualize")
    public VisualizeResponse visualize(@RequestBody(required = false) VisualizeRequest request,
                                       HttpServletRequest servletRequest, Authentication authentication) {
        long started = System.nanoTime();
        String code = request == null ? null : request.code();
        // Rate-limit per signed-in account (cannot be spoofed); fall back to the client address.
        String clientKey = authentication != null ? "user:" + authentication.getName() : clientAddress.of(servletRequest);
        VisualizeResponse response = service.visualize(code, clientKey);
        if (authentication != null) {
            runHistory.record(authentication.getName(), code, response, (System.nanoTime() - started) / 1_000_000);
        }
        return response;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
