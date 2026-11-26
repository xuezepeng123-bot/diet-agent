package com.diet.learning;
import java.util.LinkedHashSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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

    SessionContext readContext(UUID sessionId) {
        if (sessionId == null) {
            return new SessionContext(new MealPreferenceExtractor.Preferences(null, null), false);
        }
        var row = findRequired(sessionId.toString(), false);
        return new SessionContext(readPreferences(row), row.lastRecommendationIds() != null);
    }

    @Transactional//声明式事务
    public SavedTurn recommendTurn(UUID sessionId, MealPreferenceExtractor.Preferences latest) {
        UUID id = sessionId == null ? UUID.randomUUID() : sessionId;
        String key = id.toString();
        String tagsJson = latest.tags().isEmpty() ? null : json.valueToTree(latest.tags()).toString();

        if (sessionId == null) {
            mapper.insert(key, latest.mealTime(), latest.taste(), tagsJson);
        } else {
            mapper.mergePreferences(key, latest.mealTime(), latest.taste(), tagsJson);
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

        Set<Long> excludedIds = readRecommendationIds(row.lastRecommendationIds());
        return recommendAndSave(sessionId, row, excludedIds);
    }
    private Set<Long> readRecommendationIds(String mealIdsJson) {
        if (mealIdsJson == null) {
            return Set.of();
        }
        try {
            return json.readValue(mealIdsJson, new TypeReference<LinkedHashSet<Long>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("会话推荐历史格式无效", ex);
        }
    }

    private SavedTurn recommendAndSave(UUID id, MealConversationMapper.SessionRow row,
            Set<Long> excludedIds) {
        var result = recommendationService.recommendForPreferences(readPreferences(row), excludedIds, BATCH_SIZE);

                if (!result.meals().isEmpty()) {
                    Set<Long> history = new LinkedHashSet<>(readRecommendationIds(row.lastRecommendationIds()));
                    for (Meal meal : result.meals()) {
                        history.add(meal.id());
                    }
                    mapper.saveLastRecommendations(id.toString(), json.valueToTree(history).toString());
                }
        return new SavedTurn(id, result);
    }

    private MealPreferenceExtractor.Preferences readPreferences(MealConversationMapper.SessionRow row) {
        Map<String, String> tags = Map.of();
        if (row.tagsJson() != null) {
            try {
                tags = MealSlotDictionary.fromJson(json.readTree(row.tagsJson()));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("会话标签格式无效", error);
            }
        }
        return new MealPreferenceExtractor.Preferences(row.mealTime(), row.taste(), tags);
    }

    private MealConversationMapper.SessionRow findRequired(String sessionId, boolean forUpdate) {
        var row = mapper.findById(sessionId, forUpdate);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return row;
    }

    public record SavedTurn(UUID sessionId, RecommendationService.Result result) {}

    record SessionContext(MealPreferenceExtractor.Preferences preferences, boolean hasRecommendations) {}
}
