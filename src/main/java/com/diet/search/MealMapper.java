package com.diet.search;

import com.diet.learning.Meal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface MealMapper {
    List<Meal> search(@Param("mealTime") String mealTime,
                      @Param("taste") String taste,
                      @Param("tags") Map<String, String> tags);
}
