package com.example.demo.service.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The sliding session must renew only valid, half-used tokens and never beyond 12 hours after login. */
class JwtTokenServiceTest {
    private final SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtTokenService tokens = new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(key)), 1800, 12, false, "Lax");
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    @Test
    void loginCookieCarriesTheLoginTime() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        tokens.writeAuthCookie(response, "user@example.com");
        Jwt jwt = decoder.decode(cookieValue(response));
        assertThat(jwt.getSubject()).isEqualTo("user@example.com");
        assertThat(jwt.getClaims().get(JwtTokenService.LOGIN_AT_CLAIM)).isInstanceOf(Number.class);
    }

    @Test
    void freshTokenIsNotRenewed() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        tokens.renewIfHalfExpired(response, token(Instant.now().plusSeconds(25 * 60), Instant.now().minusSeconds(300)));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void halfUsedTokenIsRenewedKeepingTheOriginalLoginTime() {
        long loginAt = Instant.now().minusSeconds(3600).getEpochSecond();
        MockHttpServletResponse response = new MockHttpServletResponse();
        tokens.renewIfHalfExpired(response, token(Instant.now().plusSeconds(10 * 60), Instant.ofEpochSecond(loginAt)));
        Jwt renewed = decoder.decode(cookieValue(response));
        assertThat(((Number) renewed.getClaims().get(JwtTokenService.LOGIN_AT_CLAIM)).longValue()).isEqualTo(loginAt);
        assertThat(renewed.getExpiresAt()).isAfter(Instant.now().plusSeconds(25 * 60));
    }

    @Test
    void sessionOlderThanTheMaximumIsNotRenewed() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        tokens.renewIfHalfExpired(response, token(Instant.now().plusSeconds(10 * 60), Instant.now().minusSeconds(13 * 3600)));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void tokenWithoutLoginTimeIsNotRenewed() {
        Jwt legacy = Jwt.withTokenValue("legacy").header("alg", "HS256").subject("user@example.com")
                .issuedAt(Instant.now().minusSeconds(1500)).expiresAt(Instant.now().plusSeconds(300)).build();
        MockHttpServletResponse response = new MockHttpServletResponse();
        tokens.renewIfHalfExpired(response, legacy);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static Jwt token(Instant expiresAt, Instant loginAt) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("user@example.com")
                .issuedAt(expiresAt.minusSeconds(1800)).expiresAt(expiresAt)
                .claim(JwtTokenService.LOGIN_AT_CLAIM, loginAt.getEpochSecond()).build();
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(JwtTokenService.COOKIE_NAME + "=");
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }
}
