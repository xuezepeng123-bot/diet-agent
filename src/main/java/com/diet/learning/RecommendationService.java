package com.diet.learning;
import java.util.Set;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private final MealSearchService mealSearchService;

   

    public Result recommend(String message) {
    return recommendForPreferences(preferenceExtractor.extract(message));
}

public Result recommendForPreferences(MealPreferenceExtractor.Preferences preferences) {
    return recommendForPreferences(preferences, Set.of(), Integer.MAX_VALUE);
}

public Result recommendForPreferences(MealPreferenceExtractor.Preferences preferences,
        Set<Long> excludedIds, int limit) {
    String mealTime = preferences.mealTime();
    String taste = preferences.taste();

    if (mealTime == null) {
        return new Result("ASK", "你想吃早餐、午餐还是晚餐？", List.of());
    }
    if (taste == null) {
        return new Result("ASK", "你喜欢清淡还是微辣？", List.of());
    }

    List<Meal> meals = mealSearchService.search(mealTime, taste).stream()
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
    private final MealPreferenceExtractor preferenceExtractor;

public RecommendationService(MealSearchService mealSearchService,
        MealPreferenceExtractor preferenceExtractor) {
    this.mealSearchService = mealSearchService;
    this.preferenceExtractor = preferenceExtractor;
}
}