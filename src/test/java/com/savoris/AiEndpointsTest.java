package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.StaffEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"savoris.ai.api-key=", "savoris.ai.model=", "savoris.ai.max-requests-per-minute=1"})
class AiEndpointsTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetAiRateLimit() {
        jdbc.update("DELETE FROM ai_request_rate_window");
    }

    @Test
    void publicChatReturnsClearUnavailableErrorWhenProviderIsNotConfigured() throws Exception {
        mockMvc.perform(post("/public/ai/chat").contentType(APPLICATION_JSON)
                .content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    void publicChatAppliesSharedRateLimit() throws Exception {
        mockMvc.perform(post("/public/ai/chat").contentType(APPLICATION_JSON)
                .content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/public/ai/chat").contentType(APPLICATION_JSON)
                .content("{\"message\":\"Xin chào lần nữa\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));
    }

    @Test
    void staleMinuteBucketCannotReplaceANewerWindow() throws Exception {
        jdbc.update("""
            INSERT INTO ai_request_rate_window (id, window_started_at, request_count)
            VALUES (1, date_trunc('minute', statement_timestamp()) + interval '1 minute', 1)
            """);

        mockMvc.perform(post("/public/ai/chat").contentType(APPLICATION_JSON)
                .content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));

        assertThat(jdbc.queryForObject("""
            SELECT window_started_at > date_trunc('minute', statement_timestamp())
            FROM ai_request_rate_window WHERE id = 1
            """, Boolean.class)).isTrue();
    }

    @Test
    void kitchenForecastRequiresManagementRole() throws Exception {
        StaffEntity staff = createStaff(StaffRole.STAFF, createBranch(null));
        mockMvc.perform(get("/dashboard/branch/1/kitchen-forecast"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/dashboard/branch/1/kitchen-forecast").with(as(staff)))
            .andExpect(status().isForbidden());
    }
}
