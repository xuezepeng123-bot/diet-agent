package com.diet.learning;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MealConversationController {
    private final MealConversationOrchestrator orchestrator;
    private final AgentTraceService traceService;

    public MealConversationController(MealConversationOrchestrator orchestrator, AgentTraceService traceService) {
        this.orchestrator = orchestrator;
        this.traceService = traceService;
    }

    @PostMapping("/api/conversations/recommendations")
    public Turn recommend(@RequestBody Request request, HttpServletResponse response) {
        var trace = new AgentTrace();
        response.setHeader("X-Trace-Id", trace.id().toString());
        UUID sessionId = request.sessionId();
        trace.record("REQUEST_RECEIVED", Map.of("hasSessionId", sessionId != null));

        try {
            if (request.message() == null || request.message().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入饮食需求");
            }

            var turn = orchestrator.recommend(sessionId, request.message(), trace);
            sessionId = turn.sessionId();
            return new Turn(sessionId, turn.result());
        } catch (RuntimeException error) {
            int httpStatus = error instanceof ResponseStatusException rejection
                    ? rejection.getStatusCode().value() : 500;
            trace.record("REQUEST_FAILED", Map.of("httpStatus", httpStatus,
                    "errorType", error.getClass().getSimpleName()));
            throw error;
        } finally {
            traceService.save(trace, sessionId);
        }
    }

    public record Request(UUID sessionId, String message) {}
    public record Turn(UUID sessionId, RecommendationService.Result result) {}
}
