package com.diet.search;

import com.diet.learning.Meal;
import com.diet.learning.MealController;
import com.diet.learning.MealSearchService;
import com.diet.learning.MealSearchTool;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MealLookupRpcTest {
    @Test
    void keepsEmptyResultsDistinctFromAStoppedService() throws Exception {
        var mapper = mock(MealMapper.class);
        var meal = new Meal(1L, "清蒸鲈鱼", "晚餐", "清淡");
        when(mapper.search("晚餐", "清淡", Map.of())).thenReturn(List.of(meal));
        when(mapper.search("早餐", "清淡", Map.of())).thenReturn(List.of());

        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new MealLookupEndpoint(mapper)).build().start();
        var client = new MealSearchService("127.0.0.1", server.getPort(), 2000);
        try {
            var tool = new MealSearchTool(client);
            assertThat(tool.searchMeals(" 晚餐 ", "清淡")).containsExactly(meal);
            assertThat(tool.searchMeals("早餐", "清淡")).isEmpty();

            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> tool.searchMeals("晚餐", "清淡"))
                    .satisfies(error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
            MockMvcBuilders.standaloneSetup(new MealController(client)).build()
                    .perform(get("/api/meals").param("mealTime", "晚餐").param("taste", "清淡"))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            client.close();
            server.shutdownNow();
        }
    }
}
