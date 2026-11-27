package com.diet.search;

import com.diet.learning.Meal;
import com.diet.learning.MealSlotDictionary;
import com.diet.search.rpc.MealItem;
import com.diet.search.rpc.MealLookupGrpc;
import com.diet.search.rpc.SearchMealsRequest;
import com.diet.search.rpc.SearchMealsResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

final class MealLookupEndpoint extends MealLookupGrpc.MealLookupImplBase {
    private static final Logger log = LoggerFactory.getLogger(MealLookupEndpoint.class);
    private final MealMapper mapper;

    MealLookupEndpoint(MealMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void searchMeals(SearchMealsRequest request, StreamObserver<SearchMealsResponse> response) {
        var tags = request.getTagsMap();
        if (tags.entrySet().stream().anyMatch(tag -> !MealSlotDictionary.supports(tag.getKey(), tag.getValue()))) {
            response.onError(Status.INVALID_ARGUMENT.withDescription("Unsupported meal preference tag").asRuntimeException());
            return;
        }

        List<Meal> meals;
        try {
            meals = mapper.search(normalize(request.getMealTime()), normalize(request.getTaste()), tags);
        } catch (DataAccessException error) {
            log.warn("Meal lookup failed ({})", error.getClass().getSimpleName());
            response.onError(Status.UNAVAILABLE.withDescription("Meal database unavailable").asRuntimeException());
            return;
        }

        var result = SearchMealsResponse.newBuilder();
        for (Meal meal : meals) {
            result.addMeals(MealItem.newBuilder().setId(meal.id()).setName(meal.name())
                    .setMealTime(meal.mealTime()).setTaste(meal.taste()));
        }
        response.onNext(result.build());
        response.onCompleted();
    }

    private static String normalize(String value) {
        return value.isBlank() ? null : value.strip();
    }
}
