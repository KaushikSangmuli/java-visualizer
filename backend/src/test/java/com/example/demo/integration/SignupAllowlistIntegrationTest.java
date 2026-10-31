package com.example.demo.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With an allowlist configured, only listed emails/domains can create accounts. */
@SpringBootTest(properties = {"visualizer.auth.allowed-signup-emails=@cdac.in, owner@example.com",
        "spring.datasource.url=jdbc:h2:mem:visualizer-allowlist-test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SignupAllowlistIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void outsiderIsRejectedAndListedAddressesCanSignUp() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stranger@example.org\",\"password\":\"a-long-password-123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sign-up is limited to invited email addresses during the beta."));

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@cdac.in\",\"password\":\"a-long-password-123\"}"))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Owner@Example.com\",\"password\":\"a-long-password-123\"}"))
                .andExpect(status().isAccepted());
    }
}
