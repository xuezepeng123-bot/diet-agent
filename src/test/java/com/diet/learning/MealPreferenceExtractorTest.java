package com.diet.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MealPreferenceExtractorTest {
    @Test
    void attributesValidPreferencesToModel() {
        var extractor = withModel(modelReply("""
                {"mealTime":"晚餐","taste":"清淡"}
                """));

        var extraction = extractor.extractWithDetails("晚上想吃点不辣的");

        assertThat(extraction.preferences()).isEqualTo(new MealPreferenceExtractor.Preferences("晚餐", "清淡"));
        assertThat(extraction.source()).isEqualTo(MealPreferenceExtractor.Source.MODEL);
        assertThat(extraction.fallbackReason()).isNull();
    }

    @Test
    void fallsBackWhenModelReturnsUnsupportedPreference() {
        var extractor = withModel(modelReply("""
                {"mealTime":"晚餐","taste":"特辣"}
                """));

        var extraction = extractor.extractWithDetails("我想吃清淡的晚餐");

        assertThat(extraction.preferences()).isEqualTo(new MealPreferenceExtractor.Preferences("晚餐", "清淡"));
        assertThat(extraction.source()).isEqualTo(MealPreferenceExtractor.Source.KEYWORDS);
        assertThat(extraction.fallbackReason()).isEqualTo(MealPreferenceExtractor.FallbackReason.MODEL_FAILURE);
    }

    @Test
    void fallsBackWhenModelCallFails() {
        var extractor = withModel(Flux.error(new IllegalStateException("test model failure")));

        var extraction = extractor.extractWithDetails("我想吃晚餐");

        assertThat(extraction.preferences()).isEqualTo(new MealPreferenceExtractor.Preferences("晚餐", null));
        assertThat(extraction.source()).isEqualTo(MealPreferenceExtractor.Source.KEYWORDS);
        assertThat(extraction.fallbackReason()).isEqualTo(MealPreferenceExtractor.FallbackReason.MODEL_FAILURE);
    }

    private static Flux<ChatResponse> modelReply(String text) {
        return Flux.just(ChatResponse.builder().id("test-response").finishReason("stop")
                .content(List.of(TextBlock.builder().text(text).build())).build());
    }

    private static MealPreferenceExtractor withModel(Flux<ChatResponse> response) {
        var model = mock(DashScopeChatModel.class);
        when(model.getModelName()).thenReturn("test-model");
        when(model.stream(anyList(), anyList(), any())).thenReturn(response);
        var chatModel = mock(DietChatModel.class);
        when(chatModel.getModel()).thenReturn(model);
        return new MealPreferenceExtractor(new ObjectMapper(), chatModel);
    }
}
