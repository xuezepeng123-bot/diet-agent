package com.diet.learning;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TraceJudgeTest {
    private static final String PATH = "/api/evaluations/traces/{traceId}/judge";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AgentTraceService traceService;

    @MockBean
    private DietChatModel chatModel;

    @Test
    void storesJudgmentAndPreservesItWhenReevaluationFails() throws Exception {
        String traceId = createTrace();
        var model = mock(DashScopeChatModel.class);
        when(chatModel.getModel()).thenReturn(model);
        when(model.getModelName()).thenReturn("judge-test-model");
        when(model.stream(anyList(), anyList(), any()))
                .thenReturn(reply("{\"score\":86,\"reason\":\"追问具体，表达清楚\"}"))
                .thenReturn(reply("{\"score\":101,\"reason\":\"超出范围\"}"))
                .thenReturn(Flux.error(new IllegalStateException("test model failure")));

        var saved = mvc.perform(post(PATH, traceId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.score").value(86))
                .andExpect(jsonPath("$.reason").value("追问具体，表达清楚"))
                .andExpect(jsonPath("$.modelName").value("judge-test-model"))
                .andReturn().getResponse();

        verify(model).stream(argThat(messages -> {
            String payload = messages.getLast().getTextContent();
            return payload.contains("你喜欢清淡还是微辣？") && !payload.contains("test-input-only");
        }), anyList(), any());

        mvc.perform(post(PATH, traceId)).andExpect(status().isBadGateway());
        mvc.perform(post(PATH, traceId)).andExpect(status().isBadGateway());
        mvc.perform(get(PATH, traceId)).andExpect(status().isOk())
                .andExpect(content().bytes(saved.getContentAsByteArray()));
    }

    @Test
    void reportsMissingTraceHistoricalTraceAndUnavailableModel() throws Exception {
        mvc.perform(post(PATH, UUID.randomUUID())).andExpect(status().isNotFound());

        var historicalTrace = new AgentTrace();
        historicalTrace.record("REQUEST_FINISHED", Map.of("resultStatus", "ASK", "mealCount", 0));
        traceService.save(historicalTrace, null);
        mvc.perform(post(PATH, historicalTrace.id())).andExpect(status().isUnprocessableEntity());

        String traceId = createTrace();
        mvc.perform(post(PATH, traceId)).andExpect(status().isServiceUnavailable());
        mvc.perform(get(PATH, traceId)).andExpect(status().isNotFound());
    }

    private String createTrace() throws Exception {
        return mvc.perform(post("/api/conversations/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我想吃晚餐 test-input-only"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andReturn().getResponse().getHeader("X-Trace-Id");
    }

    private static Flux<ChatResponse> reply(String text) {
        return Flux.just(ChatResponse.builder().id("judge-reply").finishReason("stop")
                .content(List.of(TextBlock.builder().text(text).build())).build());
    }
}
