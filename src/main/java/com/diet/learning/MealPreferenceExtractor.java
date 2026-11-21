package com.diet.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MealPreferenceExtractor {
    private static final Logger log = LoggerFactory.getLogger(MealPreferenceExtractor.class);
    private static final List<String> MEAL_TIMES = List.of("早餐", "午餐", "晚餐");
    private static final List<String> TASTES = List.of("清淡", "微辣");
    private static final String PROMPT = """
            从用户的一句话提取餐次和口味。只输出一个 JSON 对象，包含 mealTime 和 taste。
            mealTime 只能是早餐、午餐、晚餐。taste 只能是清淡、微辣。
            用户没说或无法确定的字段填 null。不要把被用户排除的选项当成偏好。
            “晚上想吃点不辣的”应输出 {"mealTime":"晚餐","taste":"清淡"}
            “我想吃晚餐”应输出 {"mealTime":"晚餐","taste":null}
            """;

    private final ObjectMapper json;
    private final DashScopeChatModel model;

    public MealPreferenceExtractor(ObjectMapper json, DietChatModel chatModel) {
        this.json = json;
        this.model = chatModel.getModel();
    }

    public Preferences extract(String message) {
        return extractWithDetails(message).preferences();
    }

    Extraction extractWithDetails(String message) {
        String text = message == null ? "" : message;
        if (text.isBlank()) {
            return extractByKeywords(text, FallbackReason.EMPTY_INPUT);
        }
        if (model == null) {
            return extractByKeywords(text, FallbackReason.MODEL_NOT_CONFIGURED);
        }

        try {
            Preferences preferences = extractWithModel(text);
            log.info("Meal preferences extracted by DashScope");
            return new Extraction(preferences, Source.MODEL, null);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("DashScope extraction failed, using keywords ({})", ex.getClass().getSimpleName());
            return extractByKeywords(text, FallbackReason.MODEL_FAILURE);
        }
    }

    private Extraction extractByKeywords(String text, FallbackReason reason) {
        var preferences = new Preferences(firstMatch(text, MEAL_TIMES), firstMatch(text, TASTES));
        return new Extraction(preferences, Source.KEYWORDS, reason);
    }

    private Preferences extractWithModel(String text) throws JsonProcessingException {
        ReActAgent agent = ReActAgent.builder()
                .name("meal_preference_extractor")
                .model(model)
                .sysPrompt(PROMPT)
                .maxIters(1)
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(10)).maxAttempts(1).build())
                .build();

        Msg reply = agent.call(Msg.builder().role(MsgRole.USER).textContent(text).build()).block();
        JsonNode root = json.readTree(reply.getTextContent());
        return new Preferences(readChoice(root.get("mealTime"), MEAL_TIMES),
                readChoice(root.get("taste"), TASTES));
    }

    private String readChoice(JsonNode value, List<String> allowed) {
        if (value != null && value.isNull()) {
            return null;
        }
        if (value != null && value.isTextual() && allowed.contains(value.asText())) {
            return value.asText();
        }
        throw new IllegalArgumentException("Invalid meal preference");
    }

    private String firstMatch(String text, List<String> options) {
        return options.stream().filter(text::contains).findFirst().orElse(null);
    }

    public record Preferences(String mealTime, String taste) {}

    record Extraction(Preferences preferences, Source source, FallbackReason fallbackReason) {}

    enum Source { MODEL, KEYWORDS }

    enum FallbackReason { EMPTY_INPUT, MODEL_NOT_CONFIGURED, MODEL_FAILURE }
}
