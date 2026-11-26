package com.diet.learning;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MealConversationMapper {
    SessionRow findById(@Param("sessionId") String sessionId,
                    @Param("forUpdate") boolean forUpdate);

    int insert(@Param("sessionId") String sessionId,
               @Param("mealTime") String mealTime,
               @Param("taste") String taste,
               @Param("tagsJson") String tagsJson);
    
    int mergePreferences(@Param("sessionId") String sessionId,
                         @Param("mealTime") String mealTime,
                         @Param("taste") String taste,
                         @Param("tagsJson") String tagsJson);
int saveLastRecommendations(@Param("sessionId") String sessionId,
                            @Param("mealIdsJson") String mealIdsJson);
    record SessionRow(String sessionId, String mealTime, String taste,
                  String lastRecommendationIds, String tagsJson) {}
}
