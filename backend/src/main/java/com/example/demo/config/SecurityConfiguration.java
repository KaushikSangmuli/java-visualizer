package com.example.demo.config;

import com.example.demo.repository.auth.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
public class SecurityConfiguration {
    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return email -> users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, UserDetailsService users,
                                           com.example.demo.service.auth.JwtTokenService jwtTokens,
                                           @org.springframework.beans.factory.annotation.Value("${visualizer.auth.cookie-secure:false}") boolean secureCookie,
                                           @org.springframework.beans.factory.annotation.Value("${visualizer.auth.cookie-same-site:Lax}") String sameSite) throws Exception {
        // The CSRF cookie must travel like the auth cookie: with frontend and backend on different
        // sites (Static Web Apps + Container Apps) it needs SameSite=None; Secure, or every POST fails.
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite(sameSite).secure(secureCookie));
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        // The React client reads the CSRF token from /api/auth/csrf and echoes it in a header.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // "/error" must be open: Spring Boot forwards every 403/500 there, and if it required
                        // a login those errors would all turn into a misleading 401 that logs the user out.
                        .requestMatchers("/error", "/health", "/api/auth/csrf", "/api/auth/signup",
                                "/api/auth/verify-email", "/api/auth/verification-code/resend", "/api/auth/login",
                                "/api/auth/password-reset/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(new JwtCookieAuthenticationFilter(decoder, users, jwtTokens),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Authentication required.\"}");
                        })
                        // Also used by the CSRF filter: a missing or stale security token is a 403, not a logout.
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Security token expired. Please try again.\",\"code\":\"csrf\"}");
                        }));
        return http.build();
    }
}
