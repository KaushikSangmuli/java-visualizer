package com.example.demo.service.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupAllowlistTest {
    @Test
    void emptyListAllowsEveryone() {
        assertTrue(new SignupAllowlist("").allows("anyone@example.com"));
        assertTrue(new SignupAllowlist(null).isOpen());
        assertTrue(new SignupAllowlist(" , ").isOpen());
    }

    @Test
    void exactEmailsAndDomainsMatchIgnoringCaseAndSpaces() {
        SignupAllowlist list = new SignupAllowlist(" @CDAC.in , Kaushik@Gmail.com ");
        assertTrue(list.allows("student@cdac.in"));
        assertTrue(list.allows("  KAUSHIK@gmail.com "));
        assertFalse(list.allows("other@gmail.com"));
        assertFalse(list.allows("student@evil-cdac.in"), "domain must match exactly, not as a suffix");
        assertFalse(list.allows("cdac.in@example.com"));
        assertFalse(list.allows(null));
    }
}
