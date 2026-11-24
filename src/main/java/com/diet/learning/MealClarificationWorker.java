package com.diet.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class MealClarificationWorker {
    Optional<RecommendationService.Result> clarify(MealPreferenceExtractor.Preferences preferences) {
        if (preferences.mealTime() == null) {
            return Optional.of(new RecommendationService.Result("ASK", "你想吃早餐、午餐还是晚餐？", List.of()));
        }
        if (preferences.taste() == null) {
            return Optional.of(new RecommendationService.Result("ASK", "你喜欢清淡还是微辣？", List.of()));
        }
        return Optional.empty();
    }
}
