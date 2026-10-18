package com.example.demo.controller;

import com.example.demo.model.VisualizeRequest;
import com.example.demo.model.VisualizeResponse;
import com.example.demo.service.VisualizeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
public class VisualizeController {

    private final VisualizeService service;

    public VisualizeController(VisualizeService service) {
        this.service = service;
    }

    /** POST /api/visualize   body: { "code": "..." }   -> always HTTP 200, check "status". */
    @PostMapping("/api/visualize")
    public VisualizeResponse visualize(@RequestBody(required = false) VisualizeRequest request,
                                       HttpServletRequest servletRequest) {
        return service.visualize(request == null ? null : request.code(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
