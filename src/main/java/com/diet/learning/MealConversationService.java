package com.diet.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MealConversationService {
    private final MealConversationMapper mapper;
    private final RecommendationService recommendationService;
    private final ObjectMapper json;

    public MealConversationService(MealConversationMapper mapper,
            RecommendationService recommendationService, ObjectMapper json) {
        this.mapper = mapper;
        this.recommendationService = recommendationService;
        this.json = json;
    }

    public void checkSession(UUID sessionId) {
        if (sessionId != null) {
            findRequired(sessionId.toString());
        }
    }

    @Transactional
    public SavedTurn recommendTurn(UUID sessionId, MealPreferenceExtractor.Preferences latest) {
        UUID id = sessionId == null ? UUID.randomUUID() : sessionId;
        String key = id.toString();

        if (sessionId == null) {
            mapper.insert(key, latest.mealTime(), latest.taste());
        } else {
            mapper.mergePreferences(key, latest.mealTime(), latest.taste());
        }

        var row = findRequired(key);
        var preferences = new MealPreferenceExtractor.Preferences(row.mealTime(), row.taste());
        var result = recommendationService.recommendForPreferences(preferences);

        if (!result.meals().isEmpty()) {
            var mealIds = result.meals().stream().map(Meal::id).toList();
            mapper.saveLastRecommendations(key, json.valueToTree(mealIds).toString());
        }
        return new SavedTurn(id, result);
    }

    private MealConversationMapper.SessionRow findRequired(String sessionId) {
        var row = mapper.findById(sessionId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return row;
    }

    public record SavedTurn(UUID sessionId, RecommendationService.Result result) {}
}