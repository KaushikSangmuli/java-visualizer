package com.example.demo.controller.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> state(IllegalStateException exception) {
        boolean throttled = exception.getMessage() != null && exception.getMessage().startsWith("Please wait");
        return ResponseEntity.status(throttled ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", throttled ? exception.getMessage() : "Account email service is not available. Please try again later."));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> emailProvider(RestClientException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Account email service is not available. Please try again later."));
    }

    @ExceptionHandler(com.example.demo.service.auth.SignupAllowlist.NotAllowedException.class)
    public ResponseEntity<Map<String, String>> signupNotAllowed(com.example.demo.service.auth.SignupAllowlist.NotAllowedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
