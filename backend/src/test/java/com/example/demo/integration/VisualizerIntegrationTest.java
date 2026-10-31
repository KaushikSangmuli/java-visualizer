package com.example.demo.integration;

import com.example.demo.model.auth.UserAccount;
import com.example.demo.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses an in-memory H2 database so tests never touch the developer's backend/data file. */
@SpringBootTest(properties = {"visualizer.timeout-ms=3000", "visualizer.rate-limit-requests=100",
        "spring.datasource.url=jdbc:h2:mem:visualizer-test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class VisualizerIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;

    @Test
    void visualizationRequiresSignIn() throws Exception {
        mvc.perform(post("/api/visualize").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"public class Main { public static void main(String[] args) { } }\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsHealthAndReturnsEnrichedTraceForJavaSource() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        String response = mvc.perform(post("/api/visualize").with(signedIn("trace@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"public class Main { public static void main(String[] args) { int n = 2; System.out.println(n); } }"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.metadata.methods[0].name").value("main"))
                .andExpect(jsonPath("$.trace[0].frames[0].method").value("main"))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("trace").get(body.path("trace").size() - 1).path("event").asText()).isEqualTo("end");
    }

    @Test
    void returnsCompilerDiagnosticsAndBlockedApiAsHandledResponses() throws Exception {
        mvc.perform(post("/api/visualize").with(signedIn("errors@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"public class Main { public static void main(String[] args) { int value = ; } }"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("compile_error"));

        mvc.perform(post("/api/visualize").with(signedIn("errors@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"public class Main { public static void main(String[] args) { System.exit(0); } }"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked_api"))
                .andExpect(jsonPath("$.blockedApis[0].api").value("System.exit"));
    }

    @Test
    void everyRunIsRecordedWithItsOutcomeForTheSignedInUserOnly() throws Exception {
        String email = "runs-" + UUID.randomUUID() + "@example.com";
        UserAccount account = new UserAccount(email, "{noop}not-used");
        account.verifyEmail();
        users.save(account);

        mvc.perform(post("/api/visualize").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"public class Main { public static void main(String[] args) { int n = 2; } }\"}"))
                .andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(post("/api/visualize").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"public class Main { public static void main(String[] args) { int value = ; } }\"}"))
                .andExpect(jsonPath("$.status").value("compile_error"));

        mvc.perform(get("/api/runs").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(2))
                .andExpect(jsonPath("$.successfulRuns").value(1))
                .andExpect(jsonPath("$.failedRuns").value(1))
                .andExpect(jsonPath("$.recentRuns[0].status").value("compile_error"))
                .andExpect(jsonPath("$.recentRuns[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$.recentRuns[0].errorDetail").isNotEmpty())
                .andExpect(jsonPath("$.recentRuns[1].outcome").value("SUCCESS"));

        // Another account sees none of these runs.
        String other = "other-" + UUID.randomUUID() + "@example.com";
        users.save(new UserAccount(other, "{noop}not-used"));
        mvc.perform(get("/api/runs").with(user(other)))
                .andExpect(jsonPath("$.totalRuns").value(0));
    }

    /** A signed-in caller with no stored account: visualization works, nothing is recorded. */
    private static RequestPostProcessor signedIn(String email) {
        return user(email);
    }
}
