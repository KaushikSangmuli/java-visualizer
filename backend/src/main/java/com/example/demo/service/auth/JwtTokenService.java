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
    /** Epoch seconds of the original password login; copied unchanged into renewed tokens. */
    static final String LOGIN_AT_CLAIM = "login_at";
    private final JwtEncoder encoder;
    private final long ttlSeconds;
    private final long maxSessionSeconds;
    private final boolean secureCookie;
    private final String sameSite;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${visualizer.auth.jwt-ttl-seconds:1800}") long ttlSeconds,
                           @Value("${visualizer.auth.max-session-hours:12}") long maxSessionHours,
                           @Value("${visualizer.auth.cookie-secure:false}") boolean secureCookie,
                           @Value("${visualizer.auth.cookie-same-site:Lax}") String sameSite) {
        this.encoder = encoder;
        this.ttlSeconds = ttlSeconds;
        this.maxSessionSeconds = Math.max(ttlSeconds, maxSessionHours * 3600);
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    /** Called after a successful password login: starts a new session. */
    public void writeAuthCookie(HttpServletResponse response, String subject) {
        writeAuthCookie(response, subject, Instant.now().getEpochSecond());
    }

    private void writeAuthCookie(HttpServletResponse response, String subject, long loginAtEpochSecond) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("java-visualizer")
                .issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds)).subject(subject)
                .claim(LOGIN_AT_CLAIM, loginAtEpochSecond).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, ttlSeconds).toString());
    }

    /**
     * Sliding session: re-issues the cookie when less than half of its lifetime is left, so an active
     * user is not signed out mid-session. Only already-verified tokens reach this method (signature and
     * expiry are checked by the decoder first). Renewal never goes beyond {@code max-session-hours}
     * after the original password login, so a stolen cookie cannot be kept alive indefinitely; tokens
     * without the login time (issued before this change) are not renewed.
     */
    public void renewIfHalfExpired(HttpServletResponse response, Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        Object loginAt = jwt.getClaims().get(LOGIN_AT_CLAIM);
        if (expiresAt == null || jwt.getSubject() == null || !(loginAt instanceof Number number)) return;
        Instant now = Instant.now();
        boolean halfUsed = expiresAt.minusSeconds(ttlSeconds / 2).isBefore(now);
        boolean withinMaxSession = Instant.ofEpochSecond(number.longValue()).plusSeconds(maxSessionSeconds).isAfter(now);
        if (halfUsed && withinMaxSession) writeAuthCookie(response, jwt.getSubject(), number.longValue());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secureCookie)
                .path("/").sameSite(sameSite).maxAge(maxAge).build();
    }
}
