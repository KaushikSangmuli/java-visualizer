package com.example.demo.service.auth;

import com.example.demo.model.auth.EmailChallenge;
import com.example.demo.model.auth.UserAccount;
import com.example.demo.repository.auth.EmailChallengeRepository;
import com.example.demo.repository.auth.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final UserAccountRepository users;
    private final EmailChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService emailDelivery;
    private final String localOtp;

    public AuthService(UserAccountRepository users, EmailChallengeRepository challenges,
                       PasswordEncoder passwordEncoder, EmailDeliveryService emailDelivery,
                       @Value("${visualizer.auth.local-otp:}") String localOtp) {
        this.users = users;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.emailDelivery = emailDelivery;
        this.localOtp = localOtp;
    }

    @Transactional
    public void signup(String rawEmail, String password) {
        validatePasswordEncoding(password);
        String email = normalize(rawEmail);
        if (users.findByEmail(email).isPresent()) return; // Keep the public response identical for existing addresses.
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(password)));
        sendChallenge(email, EmailChallenge.Purpose.SIGNUP, false);
    }

    @Transactional
    public void resendSignupCode(String rawEmail) {
        String email = normalize(rawEmail);
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || user.getEmailVerifiedAt() != null) return;
        sendChallenge(email, EmailChallenge.Purpose.SIGNUP, false);
    }

    @Transactional
    public boolean verifyEmail(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Optional<EmailChallenge> optional = latestUsable(email, EmailChallenge.Purpose.SIGNUP, code);
        if (optional.isEmpty()) return false;
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null) return false;
        optional.get().consume();
        user.verifyEmail();
        return true;
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalize(rawEmail);
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || user.getEmailVerifiedAt() == null) return;
        sendChallenge(email, EmailChallenge.Purpose.PASSWORD_RESET, true);
    }

    @Transactional
    public boolean resetPassword(String rawEmail, String code, String newPassword) {
        validatePasswordEncoding(newPassword);
        String email = normalize(rawEmail);
        Optional<EmailChallenge> optional = latestUsable(email, EmailChallenge.Purpose.PASSWORD_RESET, code);
        if (optional.isEmpty()) return false;
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || user.getEmailVerifiedAt() == null) return false;
        optional.get().consume();
        user.changePassword(passwordEncoder.encode(newPassword));
        return true;
    }

    private Optional<EmailChallenge> latestUsable(String email, EmailChallenge.Purpose purpose, String code) {
        EmailChallenge challenge = challenges.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose).orElse(null);
        if (challenge == null || challenge.isConsumed() || challenge.isExpired() || challenge.getAttempts() >= MAX_ATTEMPTS) return Optional.empty();
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.failedAttempt();
            return Optional.empty();
        }
        return Optional.of(challenge);
    }

    private void sendChallenge(String email, EmailChallenge.Purpose purpose, boolean reset) {
        EmailChallenge previous = challenges.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose).orElse(null);
        if (previous != null && Instant.now().isBefore(previous.createdAtPlus(RESEND_DELAY))) {
            throw new IllegalStateException("Please wait before requesting another email code.");
        }
        String code = localOtp.isBlank() ? "%06d".formatted(RANDOM.nextInt(1_000_000)) : localOtp;
        challenges.deleteByEmailAndPurpose(email, purpose);
        challenges.save(new EmailChallenge(email, purpose, passwordEncoder.encode(code), Instant.now().plus(CODE_LIFETIME)));
        if (localOtp.isBlank()) emailDelivery.sendCode(email, code, reset);
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private void validatePasswordEncoding(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password is too long when encoded as UTF-8. Use at most 72 bytes.");
        }
    }
}
