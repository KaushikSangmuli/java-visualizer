package com.example.demo.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailDeliveryService {
    private final RestClient client = RestClient.create();
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public EmailDeliveryService(@Value("${visualizer.email.brevo-api-key:}") String apiKey,
                                @Value("${visualizer.email.sender-email:}") String senderEmail,
                                @Value("${visualizer.email.sender-name:Java Visualizer}") String senderName) {
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void sendCode(String destination, String code, boolean passwordReset) {
        if (apiKey.isBlank() || senderEmail.isBlank()) {
            throw new IllegalStateException("Email delivery is not configured. Set VISUALIZER_BREVO_API_KEY and VISUALIZER_EMAIL_SENDER.");
        }
        String purpose = passwordReset ? "password reset" : "email verification";
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", destination)),
                "subject", "Your Java Visualizer " + purpose + " code",
                "textContent", "Your " + purpose + " code is " + code + ". It expires in 10 minutes. If you did not request this, ignore this email.");
        client.post().uri("https://api.brevo.com/v3/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .header("api-key", apiKey)
                .body(payload)
                .retrieve().toBodilessEntity();
    }
}
