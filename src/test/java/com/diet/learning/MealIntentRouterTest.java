package com.diet.learning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MealIntentRouterTest {
    private final MealIntentRouter router = new MealIntentRouter();

    @Test
    void requestsConfirmationWhenACompletePredictionHasLowConfidence() {
        var extraction = prediction(MealIntentRouter.Intent.MEAL_RECOMMENDATION, 0.4, "晚餐", "清淡");
        var decision = router.resolve(null, extraction, session(null, null, false));

        assertThat(decision.intent()).isEqualTo(MealIntentRouter.Intent.CLARIFY_NEEDED);
        assertThat(decision.route()).isEqualTo(MealIntentRouter.Route.CONFIRM_PREFERENCES);
        assertThat(decision.reason()).isEqualTo(MealIntentRouter.Reason.LOW_CONFIDENCE);
    }

    @Test
    void usesSessionHistoryToCorrectModelIntent() {
        var continuation = prediction(MealIntentRouter.Intent.CLARIFY_NEEDED, 0.9, null, "清淡");
        var pending = session("晚餐", null, false);
        assertThat(router.resolve(null, continuation, pending).intent())
                .isEqualTo(MealIntentRouter.Intent.MEAL_RECOMMENDATION);

        var adjustment = prediction(MealIntentRouter.Intent.MEAL_ADJUST, 0.9, null, null);
        assertThat(router.resolve(null, adjustment, pending).intent()).isEqualTo(MealIntentRouter.Intent.CLARIFY_NEEDED);
        var recommended = session("晚餐", "清淡", true);
        assertThat(router.resolve(null, adjustment, recommended).route()).isEqualTo(MealIntentRouter.Route.CHANGE_BATCH);
    }

    private static MealConversationService.SessionContext session(String mealTime, String taste, boolean hasRecommendations) {
        return new MealConversationService.SessionContext(new MealPreferenceExtractor.Preferences(mealTime, taste),
                hasRecommendations);
    }

    private static MealPreferenceExtractor.Extraction prediction(MealIntentRouter.Intent intent, double confidence,
            String mealTime, String taste) {
        return new MealPreferenceExtractor.Extraction(new MealPreferenceExtractor.Preferences(mealTime, taste),
                MealPreferenceExtractor.Source.MODEL, null, new MealPreferenceExtractor.IntentPrediction(intent, confidence));
    }
}
