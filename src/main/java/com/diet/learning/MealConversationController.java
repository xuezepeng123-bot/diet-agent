package com.diet.learning;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MealConversationController {
    private final MealConversationService conversationService;
    private final MealPreferenceExtractor preferenceExtractor;
    
    public MealConversationController(MealConversationService conversationService,
            MealPreferenceExtractor preferenceExtractor) {
        this.conversationService = conversationService;
        this.preferenceExtractor = preferenceExtractor;
       
    }

    @PostMapping("/api/conversations/recommendations")
    public Turn recommend(@RequestBody Request request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入饮食需求");
        }

        conversationService.checkSession(request.sessionId());
        var latest = preferenceExtractor.extract(request.message());
        var saved = conversationService.recommendTurn(request.sessionId(), latest);
return new Turn(saved.sessionId(), saved.result());
    }

    public record Request(UUID sessionId, String message) {}
    public record Turn(UUID sessionId, RecommendationService.Result result) {}
}