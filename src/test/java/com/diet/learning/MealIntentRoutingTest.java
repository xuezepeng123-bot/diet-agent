package com.diet.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
@Transactional
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
class MealIntentRoutingTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    @Sql(statements = "INSERT INTO meal_item (name, meal_time, taste) VALUES ('测试小米粥', '早餐', '清淡')")
    @Sql(statements = "DELETE FROM meal_item WHERE name = '测试小米粥'", executionPhase = AFTER_TEST_METHOD)
    void returnsAPlanInMealOrderUsingTheRequestedTaste() throws Exception {
        var response = recommend(Map.of("message", "帮我规划三餐，口味清淡"))
                .andExpect(jsonPath("$.result.status").value("RECOMMEND"))
                .andExpect(jsonPath("$.result.meals[*].mealTime", contains("早餐", "午餐", "晚餐")))
                .andExpect(jsonPath("$.result.meals[*].taste", contains("清淡", "清淡", "清淡")))
                .andReturn().getResponse();

        mvc.perform(get("/api/traces/{traceId}", response.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[1].details.intent").value("MEAL_PLAN"))
                .andExpect(jsonPath("$.events[3].details.mealCount").value(3));
    }

    @Test
    @Sql(statements = """
            INSERT INTO meal_item (name, meal_time, taste, preference_tags) VALUES
              ('测试清蒸虾', '晚餐', '清淡', '{"cuisine":["粤菜"],"convenience":["快手"],"healthGoal":["控油"]}'),
              ('测试白灼虾', '晚餐', '清淡', '{"mood":["放松"],"scene":["独食"],"cuisine":["粤菜"],"convenience":["快手"],"healthGoal":["控油"]}'),
              ('测试香辣虾', '晚餐', '微辣', '{"mood":["放松"],"scene":["独食"],"cuisine":["粤菜"],"convenience":["快手"],"healthGoal":["控油"]}');
            """)
    @Sql(statements = "DELETE FROM meal_item WHERE name IN ('测试清蒸虾', '测试白灼虾', '测试香辣虾')",
            executionPhase = AFTER_TEST_METHOD)
    void mergesOptionalPreferencesAndRanksMealsWhenRecommendingAndChangingBatch() throws Exception {
        recommend(Map.of("message", "晚餐想吃粤菜"))
                .andExpect(jsonPath("$.result.status").value("RECOMMEND"))
                .andExpect(jsonPath("$.result.meals[*].name", contains("测试清蒸虾")))
                .andExpect(jsonPath("$.result.missingSlots").doesNotExist());

        var clarification = recommend(Map.of("message", "想放松，独食"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andExpect(jsonPath("$.result.missingSlots", contains("mealTime")))
                .andReturn().getResponse();
        String session = json.readTree(clarification.getContentAsString()).path("sessionId").asText();

        recommend(Map.of("sessionId", session, "message", "晚餐清淡，想吃粤菜，最好快手和控油"))
                .andExpect(jsonPath("$.result.meals[*].name", contains("测试白灼虾")));
        recommend(Map.of("sessionId", session, "message", "再来一批"))
                .andExpect(jsonPath("$.result.meals[*].name", contains("测试清蒸虾")));
        recommend(Map.of("sessionId", session, "message", "再来一批"))
                .andExpect(jsonPath("$.result.meals[*].name", contains("清蒸鲈鱼")));

        recommend(Map.of("sessionId", session, "message", "帮我规划三餐"))
                .andExpect(jsonPath("$.result.meals[?(@.mealTime == '晚餐')].name", contains("测试白灼虾")));
    }

    @Test
    void asksForCorePreferencesAndAcceptsAHealthGoalWithoutTaste() throws Exception {
        var clarification = recommend(Map.of("message", "想放松一下，推荐一点吃的"))
                .andExpect(jsonPath("$.result.missingSlots", contains("mealTime", "healthGoal")))
                .andReturn().getResponse();
        String session = json.readTree(clarification.getContentAsString()).path("sessionId").asText();

        recommend(Map.of("sessionId", session, "message", "午餐"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andExpect(jsonPath("$.result.missingSlots", contains("healthGoal")));
        recommend(Map.of("sessionId", session, "message", "想控油"))
                .andExpect(jsonPath("$.result.status").value("RECOMMEND"))
                .andExpect(jsonPath("$.result.meals[*].name", contains("杂粮饭")));
    }

    @Test
    void routesHealthRiskAndOffTopicRequestsToGuidance() throws Exception {
        var risk = recommend(Map.of("message", "帮我规划三餐治好糖尿病"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andExpect(jsonPath("$.result.meals").isEmpty())
                .andExpect(jsonPath("$.result.reply", containsString("请咨询医生")))
                .andReturn().getResponse();
        mvc.perform(get("/api/traces/{traceId}", risk.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].name", contains("REQUEST_RECEIVED", "ROUTE_SELECTED", "REQUEST_FINISHED")))
                .andExpect(jsonPath("$.events[1].details.intent").value("HEALTH_RISK"));

        var other = recommend(Map.of("message", "你是谁？"))
                .andExpect(jsonPath("$.result.status").value("ASK"))
                .andExpect(jsonPath("$.result.meals").isEmpty())
                .andReturn().getResponse();
        mvc.perform(get("/api/traces/{traceId}", other.getHeader("X-Trace-Id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[1].details.intent").value("OTHER"));
    }

    private ResultActions recommend(Map<String, String> request) throws Exception {
        return mvc.perform(post("/api/conversations/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }
}
