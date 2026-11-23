package com.diet.learning;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TraceEvaluationController {
    private final TraceEvaluationService evaluationService;

    public TraceEvaluationController(TraceEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/api/evaluations/traces")
    public TraceEvaluationService.Report evaluate(@RequestParam(required = false) UUID sessionId,
            @RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评估样本数量应在 1 到 500 之间");
        }
        return evaluationService.evaluate(sessionId, limit);
    }
}
