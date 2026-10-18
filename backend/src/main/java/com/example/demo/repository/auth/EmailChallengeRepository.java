package com.example.demo.repository.auth;

import com.example.demo.model.auth.EmailChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailChallengeRepository extends JpaRepository<EmailChallenge, UUID> {
    Optional<EmailChallenge> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailChallenge.Purpose purpose);
    void deleteByEmailAndPurpose(String email, EmailChallenge.Purpose purpose);
}
