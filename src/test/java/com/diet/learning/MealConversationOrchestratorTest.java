package com.diet.learning;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "DIET_AGENT_REPLY_ENABLED=true")
@AutoConfigureMockMvc
class MealConversationOrchestratorTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private DietChatModel chatModel;

    @Test
    void rendersReplyAfterTheTransactionAndKeepsBusinessResult() throws Exception {
        var model = fakeModel();
        when(model.stream(anyList(), anyList(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return modelResponse("""
                    {"reply":"清蒸鲈鱼口味清淡，适合晚餐。","status":"NO_MATCH","meals":[]}
                    """);
        });

        var response = mvc.perform(post("/api/conversations/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我想吃清淡的晚餐 test-input-only"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("RECOMMEND"))
                .andExpect(jsonPath("$.result.meals[0].name").value("清蒸鲈鱼"))
                .andExpect(jsonPath("$.result.reply").value("清蒸鲈鱼口味清淡，适合晚餐。"))
                .andReturn().getResponse();

        verify(model).stream(argThat(messages -> {
            String payload = messages.getLast().getTextContent();
            return payload.contains("清蒸鲈鱼") && !payload.contains("test-input-only");
        }), anyList(), any());
        mvc.perform(get("/api/traces/{traceId}", response.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[3].details.replySource").value("MODEL"))
                .andExpect(jsonPath("$.events[3].details.replyDurationMs").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void preservesClarificationWhenModelOutputIsInvalid() throws Exception {
        var model = fakeModel();
        when(model.stream(anyList(), anyList(), any())).thenReturn(modelResponse("{\"reply\":12}"));

        var response = mvc.perform(post("/api/conversations/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我想吃晚餐"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andExpect(jsonPath("$.result.reply").value("这顿想按口味来，还是更看重均衡、控油或高蛋白？"))
                .andExpect(jsonPath("$.result.missingSlots[0]").value("healthGoal"))
                .andReturn().getResponse();

        mvc.perform(get("/api/traces/{traceId}", response.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[3].details.replySource").value("TEMPLATE_FALLBACK"));
    }

    private DashScopeChatModel fakeModel() {
        var model = mock(DashScopeChatModel.class);
        when(chatModel.getModel()).thenReturn(model);
        when(model.getModelName()).thenReturn("reply-test-model");
        return model;
    }

    private static Flux<ChatResponse> modelResponse(String text) {
        return Flux.just(ChatResponse.builder().id("reply-test").finishReason("stop")
                .content(List.of(TextBlock.builder().text(text).build())).build());
    }
}
