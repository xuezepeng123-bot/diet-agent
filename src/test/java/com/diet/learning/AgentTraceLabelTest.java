package com.diet.learning;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
class AgentTraceLabelTest {
    private static final String LABEL_PATH = "/api/traces/{traceId}/label";

    @Autowired
    private MockMvc mvc;

    @Test
    void savesUpdatesAndReadsLabelWithoutChangingTraceEvents() throws Exception {
        String traceId = createTrace();
        mvc.perform(get(LABEL_PATH, traceId)).andExpect(status().isNotFound());

        String label = """
                {"expectedStatus":"ASK","note":"  缺少口味，应该先追问  "}
                """;
        mvc.perform(put(LABEL_PATH, traceId).contentType(MediaType.APPLICATION_JSON).content(label))
                .andExpect(status().isNoContent());
        var saved = mvc.perform(get(LABEL_PATH, traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.expectedStatus").value("ASK"))
                .andExpect(jsonPath("$.note").value("缺少口味，应该先追问"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse();

        mvc.perform(put(LABEL_PATH, traceId).contentType(MediaType.APPLICATION_JSON).content(label))
                .andExpect(status().isNoContent());
        mvc.perform(get(LABEL_PATH, traceId))
                .andExpect(content().bytes(saved.getContentAsByteArray()));

        mvc.perform(put(LABEL_PATH, traceId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"RECOMMEND"}
                                """))
                .andExpect(status().isNoContent());
        mvc.perform(get(LABEL_PATH, traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedStatus").value("RECOMMEND"))
                .andExpect(jsonPath("$.note").value(nullValue()));
        mvc.perform(get("/api/traces/{traceId}", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[3].details.resultStatus").value("ASK"));
    }

    @Test
    void rejectsUnknownTraceAndInvalidLabel() throws Exception {
        String missing = UUID.randomUUID().toString();
        mvc.perform(put(LABEL_PATH, missing).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"ASK"}
                                """))
                .andExpect(status().isNotFound());

        String traceId = createTrace();
        mvc.perform(put(LABEL_PATH, traceId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put(LABEL_PATH, traceId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"ASK","note":"%s"}
                                """.formatted("字".repeat(501))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(LABEL_PATH, traceId)).andExpect(status().isNotFound());
    }

    private String createTrace() throws Exception {
        return mvc.perform(post("/api/conversations/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我想吃晚餐"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andReturn().getResponse().getHeader("X-Trace-Id");
    }
}
