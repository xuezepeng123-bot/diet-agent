package com.diet.learning;

import com.diet.search.rpc.MealLookupGrpc;
import com.diet.search.rpc.SearchMealsRequest;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MealSearchService {
    private final ManagedChannel channel;
    private final MealLookupGrpc.MealLookupBlockingStub client;
    private final long deadlineMs;

    public MealSearchService(@Value("${diet.meal-search.host:127.0.0.1}") String host,
            @Value("${diet.meal-search.port:50051}") int port,
            @Value("${diet.meal-search.deadline-ms:2000}") long deadlineMs) {
        this.deadlineMs = deadlineMs;
        channel = Grpc.newChannelBuilderForAddress(host, port, InsecureChannelCredentials.create()).build();
        client = MealLookupGrpc.newBlockingStub(channel);
    }

    public List<Meal> search(String mealTime, String taste) {
        return search(mealTime, taste, Map.of());
    }

    List<Meal> search(String mealTime, String taste, Map<String, String> tags) {
        var request = SearchMealsRequest.newBuilder().putAllTags(tags);
        if (mealTime != null) {
            request.setMealTime(mealTime);
        }
        if (taste != null) {
            request.setTaste(taste);
        }
        try {
            return client.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).searchMeals(request.build())
                    .getMealsList().stream()
                    .map(meal -> new Meal(meal.getId(), meal.getName(), meal.getMealTime(), meal.getTaste()))
                    .toList();
        } catch (StatusRuntimeException error) {
            throw switch (error.getStatus().getCode()) {
                case UNAVAILABLE, DEADLINE_EXCEEDED ->
                        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "餐食查询暂时不可用", error);
                default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "餐食查询失败", error);
            };
        }
    }

    @PreDestroy
    public void close() {
        channel.shutdownNow();
    }
}
