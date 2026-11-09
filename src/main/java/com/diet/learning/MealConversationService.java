package com.diet.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MealConversationService {
    private static final int BATCH_SIZE = 1;

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
            findRequired(sessionId.toString(), false);
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

        return recommendAndSave(id, findRequired(key, false), Set.of());
    }

    @Transactional
    public SavedTurn changeBatch(UUID sessionId) {
        if (sessionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "换一批需要当前会话的 sessionId");
        }

        var row = findRequired(sessionId.toString(), true);
        if (row.lastRecommendationIds() == null) {
            var result = new RecommendationService.Result(
                    "ASK", "请先完成一次餐食推荐，再试试换一批。", List.of());
            return new SavedTurn(sessionId, result);
        }

        Set<Long> excludedIds;
        try {
            excludedIds = json.readValue(
                    row.lastRecommendationIds(), new TypeReference<Set<Long>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("会话推荐历史格式无效", ex);
        }
        return recommendAndSave(sessionId, row, excludedIds);
    }

    private SavedTurn recommendAndSave(UUID id, MealConversationMapper.SessionRow row,
            Set<Long> excludedIds) {
        var preferences = new MealPreferenceExtractor.Preferences(row.mealTime(), row.taste());
        var result = recommendationService.recommendForPreferences(
                preferences, excludedIds, BATCH_SIZE);

        if (!result.meals().isEmpty()) {
            var mealIds = result.meals().stream().map(Meal::id).toList();
            mapper.saveLastRecommendations(id.toString(), json.valueToTree(mealIds).toString());
        }
        return new SavedTurn(id, result);
    }

    private MealConversationMapper.SessionRow findRequired(String sessionId, boolean forUpdate) {
        var row = mapper.findById(sessionId, forUpdate);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return row;
    }

    public record SavedTurn(UUID sessionId, RecommendationService.Result result) {}
}