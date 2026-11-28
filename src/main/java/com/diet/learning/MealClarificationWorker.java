package com.diet.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class MealClarificationWorker {
    private static final Set<String> PREFERENCE_SLOTS = Set.of("healthGoal", "cuisine", "scene", "convenience");

    Optional<RecommendationService.Result> clarify(MealPreferenceExtractor.Preferences preferences) {
        List<String> missing = missingSlots(preferences);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        String question = missing.contains("mealTime") ? "你想吃早餐、午餐还是晚餐？"
                : "这顿想按口味来，还是更看重均衡、控油或高蛋白？";
        return Optional.of(new RecommendationService.Result("ASK", question, List.of(), missing));
    }

    static List<String> missingSlots(MealPreferenceExtractor.Preferences preferences) {
        List<String> missing = new ArrayList<>(2);
        if (preferences.mealTime() == null) {
            missing.add("mealTime");
        }
        if (preferences.taste() == null && PREFERENCE_SLOTS.stream().noneMatch(preferences.tags()::containsKey)) {
            missing.add("healthGoal");
        }
        return List.copyOf(missing);
    }
}
