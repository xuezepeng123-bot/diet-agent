package com.diet.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"DASHSCOPE_API_KEY=", "DASHSCOPE_BASE_URL="})
@AutoConfigureMockMvc
@Transactional
class MealIntentRoutingTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void returnsAPlanInMealOrderUsingTheRequestedTaste() throws Exception {
        jdbc.update("INSERT INTO meal_item (name, meal_time, taste) VALUES (?, ?, ?)", "小米粥", "早餐", "清淡");
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
