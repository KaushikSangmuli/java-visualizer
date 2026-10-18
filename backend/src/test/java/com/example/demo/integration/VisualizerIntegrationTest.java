package com.example.demo.integration;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"visualizer.timeout-ms=3000", "visualizer.rate-limit-requests=100"})
@AutoConfigureMockMvc
class VisualizerIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void reportsHealthAndReturnsEnrichedTraceForJavaSource() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        String response = mvc.perform(post("/api/visualize").contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(post("/api/visualize").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"public class Main { public static void main(String[] args) { int value = ; } }"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("compile_error"));

        mvc.perform(post("/api/visualize").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"public class Main { public static void main(String[] args) { System.exit(0); } }"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked_api"))
                .andExpect(jsonPath("$.blockedApis[0].api").value("System.exit"));
    }
}
