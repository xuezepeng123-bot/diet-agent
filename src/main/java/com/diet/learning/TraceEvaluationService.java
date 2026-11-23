package com.diet.learning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TraceEvaluationService {
    private final AgentTraceService traceService;

    public TraceEvaluationService(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    public Report evaluate(UUID sessionId, int limit) {
        List<AgentTraceService.LabeledSample> samples = traceService.findLabeledSamples(sessionId, limit);
        List<Issue> issues = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int failed = 0;
        int unscorable = 0;
        double totalDurationMs = 0;

        for (var sample : samples) {
            totalDurationMs += sample.durationMs();
            Issue issue = inspect(sample);
            if (issue == null) {
                matched++;
                continue;
            }
            issues.add(issue);
            switch (issue.reason()) {
                case STATUS_MISMATCH -> mismatched++;
                case REQUEST_FAILED -> failed++;
                case MISSING_RESULT -> unscorable++;
            }
        }

        int scored = matched + mismatched + failed;
        Double accuracy = scored == 0 ? null : roundToTwoDecimals(100.0 * matched / scored);
        Double averageDuration = samples.isEmpty() ? null : roundToTwoDecimals(totalDurationMs / samples.size());
        return new Report(samples.size(), scored, matched, mismatched, failed, unscorable, accuracy,
                averageDuration, List.copyOf(issues));
    }

    private Issue inspect(AgentTraceService.LabeledSample sample) {
        JsonNode events = sample.events();
        if (events.isArray()) {
            for (int index = events.size() - 1; index >= 0; index--) {
                JsonNode event = events.get(index);
                String eventName = event.path("name").asText();
                if ("REQUEST_FAILED".equals(eventName)) {
                    return new Issue(sample.traceId(), sample.expectedStatus(), null, Reason.REQUEST_FAILED);
                }
                if ("REQUEST_FINISHED".equals(eventName)) {
                    String actual = event.path("details").path("resultStatus").textValue();
                    if (actual == null || !RecommendationService.RESULT_STATUSES.contains(actual)) {
                        break;
                    }
                    return sample.expectedStatus().equals(actual) ? null
                            : new Issue(sample.traceId(), sample.expectedStatus(), actual, Reason.STATUS_MISMATCH);
                }
            }
        }
        return new Issue(sample.traceId(), sample.expectedStatus(), null, Reason.MISSING_RESULT);
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record Report(int sampleCount, int scoredCount, int matchedCount, int mismatchCount,
            int failedCount, int unscorableCount, Double accuracyPercent, Double averageDurationMs,
            List<Issue> issues) {}

    public record Issue(UUID traceId, String expectedStatus, String actualStatus, Reason reason) {}

    public enum Reason { STATUS_MISMATCH, REQUEST_FAILED, MISSING_RESULT }
}
