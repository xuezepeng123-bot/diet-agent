package com.diet.learning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MealSlotDictionary {
    static final Map<String, List<String>> OPTIONS = Map.of(
            "mood", List.of("放松", "提振", "慰藉"),
            "scene", List.of("独食", "家庭", "聚餐"),
            "healthGoal", List.of("均衡", "控油", "高蛋白"),
            "cuisine", List.of("粤菜", "川菜", "家常", "日式"),
            "convenience", List.of("快手", "可外带", "可备餐"));

    private MealSlotDictionary() {}

    static Map<String, String> fromText(String text) {
        var tags = new HashMap<String, String>();
        OPTIONS.forEach((key, values) -> values.stream().filter(text::contains).findFirst()
                .ifPresent(value -> tags.put(key, value)));
        return tags;
    }

    static Map<String, String> fromJson(JsonNode json) {
        if (json == null || !json.isObject()) {
            return Map.of();
        }
        var tags = new HashMap<String, String>();
        OPTIONS.forEach((key, values) -> {
            String value = json.path(key).textValue();
            if (value != null && values.contains(value)) {
                tags.put(key, value);
            }
        });
        return tags;
    }
}
