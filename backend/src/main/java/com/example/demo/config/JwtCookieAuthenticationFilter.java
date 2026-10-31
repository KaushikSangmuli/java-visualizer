package com.example.demo.config;

import com.example.demo.service.auth.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final UserDetailsService users;
    private final JwtTokenService tokens;

    public JwtCookieAuthenticationFilter(JwtDecoder decoder, UserDetailsService users, JwtTokenService tokens) {
        this.decoder = decoder;
        this.users = users;
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authCookie(request);
            if (token != null) {
                try {
                    Jwt jwt = decoder.decode(token);
                    UserDetails user = users.loadUserByUsername(jwt.getSubject());
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // Sliding session: an active user gets a fresh cookie once half its lifetime has passed,
                    // so only inactivity (not a long editing session) signs them out.
                    tokens.renewIfHalfExpired(response, jwt);
                } catch (JwtException | org.springframework.security.core.userdetails.UsernameNotFoundException ignored) {
                    // Invalid or expired cookies remain unauthenticated and are handled by the normal entry point.
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String authCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (JwtTokenService.COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
