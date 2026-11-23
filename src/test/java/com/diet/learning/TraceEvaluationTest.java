package com.diet.learning;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
@Transactional
class TraceEvaluationTest {
    private static final String EVALUATION_PATH = "/api/evaluations/traces";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void evaluatesLabeledTracesInTheRequestedSession() throws Exception {
        UUID session = UUID.randomUUID();
        UUID matched = insertSample(session, "ASK", finished("ASK"), 10, 1);
        UUID mismatch = insertSample(session, "RECOMMEND", finished("NO_MATCH"), 20, 2);
        UUID failed = insertSample(session, "RECOMMEND", """
                [{"name":"REQUEST_FAILED","details":{"httpStatus":500}}]
                """, 30, 3);
        UUID missing = insertSample(session, "ASK", """
                [{"name":"REQUEST_RECEIVED","details":{}}]
                """, 40, 4);
        UUID unlabeled = insertSample(session, null, finished("ASK"), 200, 0);
        insertSample(UUID.randomUUID(), "ASK", finished("ASK"), 200, 0);
        String insertFeedback = "INSERT INTO meal_agent_trace_feedback (trace_id, helpful) VALUES (?, ?)";
        jdbc.update(insertFeedback, matched.toString(), true);
        jdbc.update(insertFeedback, mismatch.toString(), false);
        jdbc.update(insertFeedback, unlabeled.toString(), false);

        mvc.perform(get(EVALUATION_PATH).param("sessionId", session.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(4))
                .andExpect(jsonPath("$.scoredCount").value(3))
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.mismatchCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.unscorableCount").value(1))
                .andExpect(jsonPath("$.accuracyPercent").value(33.33))
                .andExpect(jsonPath("$.averageDurationMs").value(25.0))
                .andExpect(jsonPath("$.feedback.helpfulCount").value(1))
                .andExpect(jsonPath("$.feedback.notHelpfulCount").value(1))
                .andExpect(jsonPath("$.feedback.unratedCount").value(2))
                .andExpect(jsonPath("$.feedback.helpfulPercent").value(50.0))
                .andExpect(jsonPath("$.issues.length()").value(3))
                .andExpect(jsonPath("$.issues[0].traceId").value(mismatch.toString()))
                .andExpect(jsonPath("$.issues[0].expectedStatus").value("RECOMMEND"))
                .andExpect(jsonPath("$.issues[0].actualStatus").value("NO_MATCH"))
                .andExpect(jsonPath("$.issues[0].reason").value("STATUS_MISMATCH"))
                .andExpect(jsonPath("$.issues[1].traceId").value(failed.toString()))
                .andExpect(jsonPath("$.issues[1].reason").value("REQUEST_FAILED"))
                .andExpect(jsonPath("$.issues[2].traceId").value(missing.toString()))
                .andExpect(jsonPath("$.issues[2].reason").value("MISSING_RESULT"));

        mvc.perform(get(EVALUATION_PATH).param("sessionId", session.toString()).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(2))
                .andExpect(jsonPath("$.accuracyPercent").value(50.0));
        mvc.perform(get(EVALUATION_PATH).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(1));
    }

    @Test
    void reportsEmptySamplesAndRejectsOutOfRangeLimits() throws Exception {
        mvc.perform(get(EVALUATION_PATH).param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(0))
                .andExpect(jsonPath("$.accuracyPercent").value(nullValue()))
                .andExpect(jsonPath("$.averageDurationMs").value(nullValue()))
                .andExpect(jsonPath("$.feedback.helpfulPercent").value(nullValue()))
                .andExpect(jsonPath("$.issues").isEmpty());
        mvc.perform(get(EVALUATION_PATH).param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(EVALUATION_PATH).param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    private UUID insertSample(UUID session, String expectedStatus, String events, long durationMs, int ageSeconds) {
        UUID traceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO meal_agent_trace (trace_id, session_id, duration_ms, events_json, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, traceId.toString(), session.toString(), durationMs, events,
                Timestamp.from(Instant.now().minusSeconds(ageSeconds)));
        if (expectedStatus != null) {
            jdbc.update("INSERT INTO meal_agent_trace_label (trace_id, expected_status) VALUES (?, ?)",
                    traceId.toString(), expectedStatus);
        }
        return traceId;
    }

    private static String finished(String status) {
        return """
                [{"name":"REQUEST_FINISHED","details":{"resultStatus":"%s"}}]
                """.formatted(status);
    }
}
