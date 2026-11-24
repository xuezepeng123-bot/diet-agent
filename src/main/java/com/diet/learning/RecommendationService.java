package com.diet.learning;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    static final Set<String> RESULT_STATUSES = Set.of("ASK", "RECOMMEND", "NO_MATCH");

    private final MealSearchService mealSearchService;
    private final MealPreferenceExtractor preferenceExtractor;
    private final MealClarificationWorker clarificationWorker;

    public RecommendationService(MealSearchService mealSearchService, MealPreferenceExtractor preferenceExtractor,
            MealClarificationWorker clarificationWorker) {
        this.mealSearchService = mealSearchService;
        this.preferenceExtractor = preferenceExtractor;
        this.clarificationWorker = clarificationWorker;
    }

    public Result recommend(String message) {
        return recommendForPreferences(preferenceExtractor.extract(message));
    }

    public Result recommendForPreferences(MealPreferenceExtractor.Preferences preferences) {
        return recommendForPreferences(preferences, Set.of(), Integer.MAX_VALUE);
    }

    public Result recommendForPreferences(MealPreferenceExtractor.Preferences preferences,
            Set<Long> excludedIds, int limit) {
        var clarification = clarificationWorker.clarify(preferences);
        if (clarification.isPresent()) {
            return clarification.get();
        }

        List<Meal> meals = mealSearchService.search(preferences.mealTime(), preferences.taste()).stream()
                .filter(meal -> !excludedIds.contains(meal.id()))
                .limit(limit)
                .toList();

        if (meals.isEmpty()) {
            String reply = excludedIds.isEmpty()
                    ? "没有找到符合条件的餐食。"
                    : "当前条件下没有其他餐食，试试换个口味吧。";
            return new Result("NO_MATCH", reply, meals);
        }
        return new Result("RECOMMEND", "找到了符合条件的餐食。", meals);
    }

    public record Result(String status, String reply, List<Meal> meals) {}
}
