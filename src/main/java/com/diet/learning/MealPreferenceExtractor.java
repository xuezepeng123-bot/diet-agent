package com.diet.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MealPreferenceExtractor {
    private static final Logger log = LoggerFactory.getLogger(MealPreferenceExtractor.class);
    static final List<String> MEAL_TIMES = List.of("早餐", "午餐", "晚餐");
    private static final List<String> TASTES = List.of("清淡", "微辣");
    private static final String PROMPT = """
            从用户的一句话识别意图和餐食偏好。只输出一个 JSON 对象，包含 mealTime、taste、tags、intent 和 confidence。
            mealTime 只能是早餐、午餐、晚餐。taste 只能是清淡、微辣。
            tags 是可选偏好对象，按用户明确表达的内容填入，每个字段最多一个值。字段和允许值为 %s。
            未确定的 mealTime 和 taste 填 null，未确定的标签省略。不要把被用户排除的选项当成偏好。
            intent 取 MEAL_RECOMMENDATION（推荐）、CLARIFY_NEEDED（信息不足）、MEAL_ADJUST（调整上次推荐）、
            MEAL_PLAN（多餐规划）、HEALTH_RISK（诊疗或极端节食请求）或 OTHER（非餐食需求）。confidence 是 0 到 1 的数字。
            “我想吃晚餐”应输出 {"mealTime":"晚餐","taste":null,"tags":{},"intent":"CLARIFY_NEEDED","confidence":0.95}
            “晚上想吃点不辣的”中的 mealTime 为晚餐，taste 为清淡。
            """.formatted(MealSlotDictionary.OPTIONS);

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
            Extraction extraction = extractWithModel(text);
            log.info("Meal preferences extracted by DashScope");
            return extraction;
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("DashScope extraction failed, using keywords ({})", ex.getClass().getSimpleName());
            return extractByKeywords(text, FallbackReason.MODEL_FAILURE);
        }
    }

    private Extraction extractByKeywords(String text, FallbackReason reason) {
        var preferences = new Preferences(firstMatch(text, MEAL_TIMES), firstMatch(text, TASTES),
                MealSlotDictionary.fromText(text));
        return new Extraction(preferences, Source.KEYWORDS, reason, null);
    }

    private Extraction extractWithModel(String text) throws JsonProcessingException {
        ReActAgent agent = ReActAgent.builder()
                .name("meal_preference_extractor")
                .model(model)
                .sysPrompt(PROMPT)
                .maxIters(1)
                .generateOptions(GenerateOptions.builder().maxTokens(256).build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(10)).maxAttempts(1).build())
                .build();

        Msg reply = agent.call(Msg.builder().role(MsgRole.USER).textContent(text).build()).block();
        JsonNode root = json.readTree(reply.getTextContent());
        var preferences = new Preferences(readChoice(root.get("mealTime"), MEAL_TIMES),
                readChoice(root.get("taste"), TASTES), MealSlotDictionary.fromJson(root.get("tags")));
        return new Extraction(preferences, Source.MODEL, null, readPrediction(root));
    }

    private IntentPrediction readPrediction(JsonNode root) {
        String intent = root.path("intent").textValue();
        JsonNode score = root.path("confidence");
        double confidence = score.asDouble(-1);
        if (intent == null || !score.isNumber() || !(confidence >= 0 && confidence <= 1)) {
            return null;
        }
        try {
            return new IntentPrediction(MealIntentRouter.Intent.valueOf(intent), confidence);
        } catch (IllegalArgumentException error) {
            return null;
        }
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

    public record Preferences(String mealTime, String taste, Map<String, String> tags) {
        public Preferences {
            tags = Map.copyOf(tags);
        }

        public Preferences(String mealTime, String taste) {
            this(mealTime, taste, Map.of());
        }

        Preferences merge(Preferences latest) {
            var updatedTags = new HashMap<>(tags);
            updatedTags.putAll(latest.tags());
            return new Preferences(latest.mealTime() == null ? mealTime : latest.mealTime(),
                    latest.taste() == null ? taste : latest.taste(), updatedTags);
        }
    }

    record Extraction(Preferences preferences, Source source, FallbackReason fallbackReason, IntentPrediction prediction) {}

    record IntentPrediction(MealIntentRouter.Intent intent, double confidence) {}

    enum Source { MODEL, KEYWORDS }

    enum FallbackReason { EMPTY_INPUT, MODEL_NOT_CONFIGURED, MODEL_FAILURE }
}
