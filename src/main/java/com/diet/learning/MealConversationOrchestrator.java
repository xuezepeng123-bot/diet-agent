package com.diet.learning;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MealConversationOrchestrator {
    private final MealConversationService conversationService;
    private final MealPreferenceExtractor preferenceExtractor;
    private final MealReplyAgent replyAgent;
    private final MealIntentRouter intentRouter;
    private final RecommendationService recommendationService;

    public MealConversationOrchestrator(MealConversationService conversationService,
            MealPreferenceExtractor preferenceExtractor, MealReplyAgent replyAgent,
            MealIntentRouter intentRouter, RecommendationService recommendationService) {
        this.conversationService = conversationService;
        this.preferenceExtractor = preferenceExtractor;
        this.replyAgent = replyAgent;
        this.intentRouter = intentRouter;
        this.recommendationService = recommendationService;
    }

    public MealConversationService.SavedTurn recommend(UUID sessionId, String message, AgentTrace trace) {
        var decision = intentRouter.direct(message);
        MealConversationService.SavedTurn saved;
        if (decision != null && decision.route() != MealIntentRouter.Route.MEAL_PLAN) {
            recordRoute(trace, decision);
            if (decision.route() == MealIntentRouter.Route.CHANGE_BATCH) {
                saved = conversationService.changeBatch(sessionId);
            } else {
                conversationService.readContext(sessionId);
                saved = guidance(sessionId, decision.route());
            }
        } else {
            var context = conversationService.readContext(sessionId);
            long extractionStartedAt = trace.elapsedMs();
            var extraction = preferenceExtractor.extractWithDetails(message);
            long extractionDurationMs = trace.elapsedMs() - extractionStartedAt;
            decision = intentRouter.resolve(decision, extraction, context);
            recordRoute(trace, decision);
            trace.record("PREFERENCES_EXTRACTED", Map.of(
                    "durationMs", extractionDurationMs, "extraction", extraction));
            saved = switch (decision.route()) {
                case RECOMMENDATION -> conversationService.recommendTurn(sessionId, extraction.preferences());
                case CHANGE_BATCH -> conversationService.changeBatch(sessionId);
                case MEAL_PLAN -> new MealConversationService.SavedTurn(sessionId,
                        recommendationService.planForTaste(extraction.preferences().taste() == null
                                ? context.taste() : extraction.preferences().taste()));
                case HEALTH_RISK, OTHER, CONFIRM_PREFERENCES -> guidance(sessionId, decision.route());
            };
        }

        // 会话事务先提交，再调用回复 Agent。
        var outcome = saved.result();
        long replyStartedAt = trace.elapsedMs();
        var reply = switch (decision.route()) {
            case RECOMMENDATION, CHANGE_BATCH -> replyAgent.render(outcome);
            case MEAL_PLAN, HEALTH_RISK, OTHER, CONFIRM_PREFERENCES ->
                    new MealReplyAgent.Reply(outcome.reply(), MealReplyAgent.Source.TEMPLATE);
        };
        var result = new RecommendationService.Result(outcome.status(), reply.text(), outcome.meals());
        trace.record("REQUEST_FINISHED", Map.of(
                "resultStatus", result.status(), "mealCount", result.meals().size(), "reply", result.reply(),
                "replySource", reply.source(), "replyDurationMs", trace.elapsedMs() - replyStartedAt));
        return new MealConversationService.SavedTurn(saved.sessionId(), result);
    }

    private void recordRoute(AgentTrace trace, MealIntentRouter.Decision decision) {
        trace.record("ROUTE_SELECTED", Map.of(
                "route", decision.route(), "intent", decision.intent(), "reason", decision.reason()));
    }

    private MealConversationService.SavedTurn guidance(UUID sessionId, MealIntentRouter.Route route) {
        String reply = switch (route) {
            case HEALTH_RISK -> "涉及诊疗的饮食需求请咨询医生或注册营养师。我可以帮你查找日常餐食。";
            case OTHER -> "我可以推荐餐食或规划三餐。你想吃哪一餐，口味偏清淡还是微辣？";
            case CONFIRM_PREFERENCES -> "请再说一次想吃哪一餐，口味偏清淡还是微辣？";
            default -> throw new IllegalArgumentException("该路由需要执行业务处理");
        };
        return new MealConversationService.SavedTurn(sessionId,
                new RecommendationService.Result("ASK", reply, List.of()));
    }
}
