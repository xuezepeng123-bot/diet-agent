package com.diet.learning;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MealSearchService {
    private final MealMapper mealMapper;

    public MealSearchService(MealMapper mealMapper) {
        this.mealMapper = mealMapper;
    }

    public List<Meal> search(String mealTime, String taste) {
        return search(mealTime, taste, Map.of());
    }

    List<Meal> search(String mealTime, String taste, Map<String, String> tags) {
        return mealMapper.search(normalize(mealTime), normalize(taste), tags);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
