package com.example.demo.service.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {
    public static final String COOKIE_NAME = "VISUALIZER_AUTH";
    private final JwtEncoder encoder;
    private final long ttlSeconds;
    private final boolean secureCookie;
    private final String sameSite;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${visualizer.auth.jwt-ttl-seconds:1800}") long ttlSeconds,
                           @Value("${visualizer.auth.cookie-secure:false}") boolean secureCookie,
                           @Value("${visualizer.auth.cookie-same-site:Lax}") String sameSite) {
        this.encoder = encoder;
        this.ttlSeconds = ttlSeconds;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public void writeAuthCookie(HttpServletResponse response, String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("java-visualizer")
                .issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds)).subject(subject).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, ttlSeconds).toString());
    }

    /** Re-issues the cookie when less than half of its lifetime is left. */
    public void renewIfHalfExpired(HttpServletResponse response, Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || jwt.getSubject() == null) return;
        if (expiresAt.minusSeconds(ttlSeconds / 2).isBefore(Instant.now())) {
            writeAuthCookie(response, jwt.getSubject());
        }
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secureCookie)
                .path("/").sameSite(sameSite).maxAge(maxAge).build();
    }
}
