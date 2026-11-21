package com.diet.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
class MealConversationTraceTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void persistsExtractionAndRecommendationDetailsForEachTurn() throws Exception {
        var first = mvc.perform(post("/api/conversations/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我想吃晚餐"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andReturn().getResponse();
        String sessionId = json.readTree(first.getContentAsByteArray()).get("sessionId").asText();

        mvc.perform(get("/api/traces/{traceId}", first.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.events[*].name", contains("REQUEST_RECEIVED", "ROUTE_SELECTED",
                        "PREFERENCES_EXTRACTED", "REQUEST_FINISHED")))
                .andExpect(jsonPath("$.events[1].details.route").value("RECOMMENDATION"))
                .andExpect(jsonPath("$.events[2].details.durationMs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.events[2].details.extraction.source").value("KEYWORDS"))
                .andExpect(jsonPath("$.events[2].details.extraction.fallbackReason").value("MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.events[2].details.extraction.preferences.mealTime").value("晚餐"))
                .andExpect(jsonPath("$.events[2].details.extraction.preferences.taste").value(nullValue()))
                .andExpect(jsonPath("$.events[3].details.resultStatus").value("ASK"))
                .andExpect(jsonPath("$.events[3].details.mealCount").value(0));

        var second = mvc.perform(post("/api/conversations/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","message":"清淡"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("RECOMMEND"))
                .andExpect(jsonPath("$.result.meals[0].name").value("清蒸鲈鱼"))
                .andReturn().getResponse();

        mvc.perform(get("/api/traces/{traceId}", second.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.events[2].details.extraction.preferences.mealTime").value(nullValue()))
                .andExpect(jsonPath("$.events[2].details.extraction.preferences.taste").value("清淡"))
                .andExpect(jsonPath("$.events[3].details.mealCount").value(1));
    }

    @Test
    void recordsChangeBatchRouteWithoutExtractingPreferences() throws Exception {
        var response = mvc.perform(post("/api/conversations/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"换一批"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse();

        mvc.perform(get("/api/traces/{traceId}", response.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].name", contains("REQUEST_RECEIVED", "ROUTE_SELECTED", "REQUEST_FAILED")))
                .andExpect(jsonPath("$.events[1].details.route").value("CHANGE_BATCH"))
                .andExpect(jsonPath("$.events[2].details.httpStatus").value(400));
    }
}
