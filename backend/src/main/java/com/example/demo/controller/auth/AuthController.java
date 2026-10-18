package com.example.demo.controller.auth;

import com.example.demo.service.auth.AuthService;
import com.example.demo.service.auth.AuthRateLimiter;
import com.example.demo.service.auth.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final AuthRateLimiter rateLimiter;
    private final JwtTokenService jwtTokens;
    private final String localOtp;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          AuthRateLimiter rateLimiter, JwtTokenService jwtTokens,
                          @org.springframework.beans.factory.annotation.Value("${visualizer.auth.local-otp:}") String localOtp) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.jwtTokens = jwtTokens;
        this.localOtp = localOtp;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute("_csrf") CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request.email(), request.password());
        return ResponseEntity.accepted().body(Map.of("message", localOtp.isBlank()
                ? "If this address can be registered, a verification code has been sent."
                : "Local development OTP: " + localOtp));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody CodeRequest request) {
        boolean verified = authService.verifyEmail(request.email(), request.code());
        return ResponseEntity.status(verified ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(Map.of("message", verified ? "Email verified. You can now log in." : "The code is invalid or expired."));
    }

    @PostMapping("/verification-code/resend")
    public Map<String, String> resendVerificationCode(@Valid @RequestBody EmailRequest request) {
        authService.resendSignupCode(request.email());
        return Map.of("message", "If the address has a pending registration, a code has been sent.");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest servletRequest,
                                                       HttpServletResponse servletResponse) {
        try {
            String client = servletRequest.getRemoteAddr();
            if (!rateLimiter.isAllowed(client)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Too many sign-in attempts. Please try again later."));
            }
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim().toLowerCase(Locale.ROOT), request.password()));
            jwtTokens.writeAuthCookie(servletResponse, authentication.getName());
            rateLimiter.succeeded(client);
            return ResponseEntity.ok(Map.of("email", authentication.getName()));
        } catch (AuthenticationException exception) {
            rateLimiter.failed(servletRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password, or email is not verified."));
        }
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) { return Map.of("email", authentication.getName()); }

    @PostMapping("/logout")
    public Map<String, String> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        jwtTokens.clearAuthCookie(response);
        return Map.of("message", "Logged out.");
    }

    @PostMapping("/password-reset/request")
    public Map<String, String> requestReset(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return Map.of("message", localOtp.isBlank()
                ? "If a verified account exists, a reset code has been sent."
                : "If a verified account exists, use local development OTP: " + localOtp);
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<Map<String, String>> completeReset(@Valid @RequestBody ResetRequest request) {
        boolean changed = authService.resetPassword(request.email(), request.code(), request.newPassword());
        return ResponseEntity.status(changed ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(Map.of("message", changed ? "Password updated. Please log in again." : "The code is invalid or expired."));
    }

    public record SignupRequest(@NotBlank @Email @Size(max = 320) String email,
                                @NotBlank @Size(min = 12, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank String password) { }
    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record CodeRequest(@NotBlank @Email @Size(max = 320) String email,
                              @NotBlank @Pattern(regexp = "(?:[0-9]{4}|[0-9]{6})") String code) { }
    public record ResetRequest(@NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Pattern(regexp = "(?:[0-9]{4}|[0-9]{6})") String code,
                               @NotBlank @Size(min = 12, max = 72) String newPassword) { }
}
