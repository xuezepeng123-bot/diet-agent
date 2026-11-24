package com.diet.learning;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MealConversationOrchestrator {
    private final MealConversationService conversationService;
    private final MealPreferenceExtractor preferenceExtractor;
    private final MealReplyAgent replyAgent;

    public MealConversationOrchestrator(MealConversationService conversationService,
            MealPreferenceExtractor preferenceExtractor, MealReplyAgent replyAgent) {
        this.conversationService = conversationService;
        this.preferenceExtractor = preferenceExtractor;
        this.replyAgent = replyAgent;
    }

    public MealConversationService.SavedTurn recommend(UUID sessionId, String message, AgentTrace trace) {
        MealConversationService.SavedTurn saved;
        if ("换一批".equals(message.strip())) {
            trace.record("ROUTE_SELECTED", Map.of("route", "CHANGE_BATCH"));
            saved = conversationService.changeBatch(sessionId);
        } else {
            trace.record("ROUTE_SELECTED", Map.of("route", "RECOMMENDATION"));
            conversationService.checkSession(sessionId);
            long extractionStartedAt = trace.elapsedMs();
            var extraction = preferenceExtractor.extractWithDetails(message);
            trace.record("PREFERENCES_EXTRACTED", Map.of(
                    "durationMs", trace.elapsedMs() - extractionStartedAt, "extraction", extraction));
            saved = conversationService.recommendTurn(sessionId, extraction.preferences());
        }

        // 会话事务先提交，再调用回复 Agent。
        var outcome = saved.result();
        long replyStartedAt = trace.elapsedMs();
        var reply = replyAgent.render(outcome);
        var result = new RecommendationService.Result(outcome.status(), reply.text(), outcome.meals());
        trace.record("REQUEST_FINISHED", Map.of(
                "resultStatus", result.status(), "mealCount", result.meals().size(), "reply", result.reply(),
                "replySource", reply.source(), "replyDurationMs", trace.elapsedMs() - replyStartedAt));
        return new MealConversationService.SavedTurn(saved.sessionId(), result);
    }
}
