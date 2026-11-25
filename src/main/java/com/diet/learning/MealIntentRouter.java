package com.diet.learning;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class MealIntentRouter {
    private static final double CONFIDENCE_THRESHOLD = 0.55;
    private static final List<String> HEALTH_TERMS = List.of("糖尿病", "高血压", "诊断", "治疗", "治好", "处方", "绝食", "极端节食");
    private static final List<String> PLAN_TERMS = List.of("三餐", "早中晚", "早午晚", "一天的饮食", "规划一天");
    private static final Set<String> CHANGE_BATCH = Set.of("换一批", "再来一批", "换一组", "还有别的吗");
    private static final Set<String> OTHER = Set.of("你好", "你是谁", "讲个笑话", "讲个故事");

    Decision direct(String message) {
        String phrase = message.strip().replaceFirst("[。！？.!?]+$", "").strip();
        if (HEALTH_TERMS.stream().anyMatch(phrase::contains)) {
            return new Decision(Intent.HEALTH_RISK, Route.HEALTH_RISK, Reason.RULE);
        }
        if (CHANGE_BATCH.contains(phrase)) {
            return new Decision(Intent.MEAL_ADJUST, Route.CHANGE_BATCH, Reason.RULE);
        }
        if (PLAN_TERMS.stream().anyMatch(phrase::contains)) {
            return new Decision(Intent.MEAL_PLAN, Route.MEAL_PLAN, Reason.RULE);
        }
        if (OTHER.contains(phrase)) {
            return new Decision(Intent.OTHER, Route.OTHER, Reason.RULE);
        }
        return null;
    }

    Decision resolve(Decision direct, MealPreferenceExtractor.Extraction extraction,
            MealConversationService.SessionContext context) {
        var latest = extraction.preferences();
        var prediction = extraction.prediction();
        boolean suppliedPreferences = latest.mealTime() != null || latest.taste() != null;
        boolean complete = (latest.mealTime() != null || context.mealTime() != null)
                && (latest.taste() != null || context.taste() != null);

        if (prediction != null && prediction.intent() == Intent.HEALTH_RISK) {
            return new Decision(Intent.HEALTH_RISK, Route.HEALTH_RISK, Reason.MODEL);
        }
        if (direct != null) {
            return direct;
        }
        if (prediction != null && prediction.confidence() >= CONFIDENCE_THRESHOLD) {
            switch (prediction.intent()) {
                case MEAL_PLAN -> {
                    return new Decision(Intent.MEAL_PLAN, Route.MEAL_PLAN, Reason.MODEL);
                }
                case OTHER -> {
                    if (!suppliedPreferences) {
                        return new Decision(Intent.OTHER, Route.OTHER, Reason.MODEL);
                    }
                }
                case MEAL_ADJUST -> {
                    if (context.hasRecommendations() && !suppliedPreferences) {
                        return new Decision(Intent.MEAL_ADJUST, Route.CHANGE_BATCH, Reason.MODEL);
                    }
                    if (context.hasRecommendations() && complete) {
                        return new Decision(Intent.MEAL_ADJUST, Route.RECOMMENDATION, Reason.MODEL);
                    }
                }
                default -> { }
            }
        }
        if (prediction != null && prediction.confidence() < CONFIDENCE_THRESHOLD && complete) {
            return new Decision(Intent.CLARIFY_NEEDED, Route.CONFIRM_PREFERENCES, Reason.LOW_CONFIDENCE);
        }
        return new Decision(complete ? Intent.MEAL_RECOMMENDATION : Intent.CLARIFY_NEEDED,
                Route.RECOMMENDATION, Reason.SESSION_PREFERENCES);
    }

    enum Intent { MEAL_RECOMMENDATION, CLARIFY_NEEDED, MEAL_ADJUST, MEAL_PLAN, HEALTH_RISK, OTHER }

    enum Route { RECOMMENDATION, CHANGE_BATCH, MEAL_PLAN, HEALTH_RISK, OTHER, CONFIRM_PREFERENCES }

    enum Reason { RULE, MODEL, LOW_CONFIDENCE, SESSION_PREFERENCES }

    record Decision(Intent intent, Route route, Reason reason) {}
}
