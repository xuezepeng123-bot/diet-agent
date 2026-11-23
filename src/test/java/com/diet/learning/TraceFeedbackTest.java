package com.diet.learning;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
@Transactional
class TraceFeedbackTest {
    private static final String PATH = "/api/traces/{traceId}/feedback";

    @Autowired
    private MockMvc mvc;

    @Test
    void setsRetriesAndUpdatesFeedbackForAnUnlabeledTrace() throws Exception {
        String traceId = mvc.perform(post("/api/conversations/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"message":"我想吃晚餐"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse().getHeader("X-Trace-Id");

        mvc.perform(get(PATH, traceId)).andExpect(status().isNotFound());
        String helpful = "{\"helpful\":true}";
        mvc.perform(put(PATH, traceId).contentType(MediaType.APPLICATION_JSON).content(helpful))
                .andExpect(status().isNoContent());
        var saved = mvc.perform(get(PATH, traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.helpful").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse();

        mvc.perform(put(PATH, traceId).contentType(MediaType.APPLICATION_JSON).content(helpful))
                .andExpect(status().isNoContent());
        mvc.perform(get(PATH, traceId)).andExpect(content().bytes(saved.getContentAsByteArray()));

        mvc.perform(put(PATH, traceId).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(PATH, traceId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(false));
    }

    @Test
    void rejectsUnknownTraceAndNonBooleanFeedback() throws Exception {
        UUID missingTrace = UUID.randomUUID();
        mvc.perform(put(PATH, missingTrace).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(PATH, missingTrace).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PATH, missingTrace).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":\"true\"}"))
                .andExpect(status().isBadRequest());
    }
}
