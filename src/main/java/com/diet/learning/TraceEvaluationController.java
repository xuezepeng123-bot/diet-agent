package com.diet.learning;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TraceEvaluationController {
    private final TraceEvaluationService evaluationService;
    private final TraceJudgeService judgeService;
    private final AgentTraceService traceService;

    public TraceEvaluationController(TraceEvaluationService evaluationService, TraceJudgeService judgeService,
            AgentTraceService traceService) {
        this.evaluationService = evaluationService;
        this.judgeService = judgeService;
        this.traceService = traceService;
    }

    @GetMapping("/api/evaluations/traces")
    public TraceEvaluationService.Report evaluate(@RequestParam(required = false) UUID sessionId,
            @RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评估样本数量应在 1 到 500 之间");
        }
        return evaluationService.evaluate(sessionId, limit);
    }

    @PostMapping("/api/evaluations/traces/{traceId}/judge")
    public AgentTraceService.Judgment judge(@PathVariable UUID traceId) {
        return judgeService.evaluate(traceId);
    }

    @GetMapping("/api/evaluations/traces/{traceId}/judge")
    public AgentTraceService.Judgment findJudgment(@PathVariable UUID traceId) {
        return traceService.findJudgment(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该 Trace 的模型评分"));
    }
}
