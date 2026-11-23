package com.diet.learning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AgentTraceController {
    private static final int MAX_NOTE_LENGTH = 500;

    private final AgentTraceService traceService;

    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/api/traces/{traceId}")
    public AgentTraceService.Detail findById(@PathVariable UUID traceId) {
        return traceService.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace 不存在"));
    }

    @PutMapping("/api/traces/{traceId}/label")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveLabel(@PathVariable UUID traceId, @RequestBody LabelRequest request) {
        String expectedStatus = request.expectedStatus();
        if (expectedStatus == null || !RecommendationService.RESULT_STATUSES.contains(expectedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "期望结果必须为 ASK、RECOMMEND 或 NO_MATCH");
        }
        String note = request.note() == null ? "" : request.note().strip();
        if (note.codePointCount(0, note.length()) > MAX_NOTE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标注备注最多 500 字符");
        }
        if (!traceService.saveLabel(traceId, expectedStatus, note.isEmpty() ? null : note)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace 不存在");
        }
    }

    @GetMapping("/api/traces/{traceId}/label")
    public AgentTraceService.Label findLabel(@PathVariable UUID traceId) {
        return traceService.findLabel(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该 Trace 的标注"));
    }

    @PutMapping("/api/traces/{traceId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveFeedback(@PathVariable UUID traceId, @RequestBody FeedbackRequest request) {
        JsonNode helpful = request.helpful();
        if (helpful == null || !helpful.isBoolean()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "helpful 必须为 true 或 false");
        }
        if (!traceService.saveFeedback(traceId, helpful.booleanValue())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace 不存在");
        }
    }

    @GetMapping("/api/traces/{traceId}/feedback")
    public AgentTraceService.Feedback findFeedback(@PathVariable UUID traceId) {
        return traceService.findFeedback(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该 Trace 的反馈"));
    }

    public record LabelRequest(String expectedStatus, String note) {}

    public record FeedbackRequest(JsonNode helpful) {}
}
