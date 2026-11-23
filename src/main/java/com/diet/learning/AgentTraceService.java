package com.diet.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceService {
    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AgentTraceService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(AgentTrace trace, UUID sessionId) {
        long durationMs = trace.elapsedMs();
        try {
            jdbc.update("""
                    INSERT INTO meal_agent_trace (trace_id, session_id, duration_ms, events_json)
                    VALUES (?, ?, ?, ?)
                    """, trace.id().toString(), sessionId == null ? null : sessionId.toString(),
                    durationMs, json.writeValueAsString(trace.events()));
        } catch (DataAccessException | JsonProcessingException error) {
            // Trace 写入失败时保留推荐请求原有的返回结果或异常。
            log.warn("Could not save agent trace {}", trace.id(), error);
        }
    }

    public Optional<Detail> findById(UUID traceId) {
        return jdbc.query("""
                SELECT session_id, duration_ms, events_json, created_at
                FROM meal_agent_trace
                WHERE trace_id = ?
                """, (row, rowNumber) -> {
            String sessionId = row.getString("session_id");
            return new Detail(traceId, sessionId == null ? null : UUID.fromString(sessionId),
                    row.getLong("duration_ms"), parseEvents(row.getString("events_json")),
                    row.getTimestamp("created_at").toInstant());
        }, traceId.toString()).stream().findFirst();
    }

    public boolean saveLabel(UUID traceId, String expectedStatus, String note) {
        int changed = jdbc.update("""
                INSERT INTO meal_agent_trace_label (trace_id, expected_status, note)
                SELECT trace_id, ?, ?
                FROM meal_agent_trace
                WHERE trace_id = ?
                ON DUPLICATE KEY UPDATE expected_status = ?, note = ?
                """, expectedStatus, note, traceId.toString(), expectedStatus, note);
        // 相同请求重试时，MySQL 可能返回 0 条变更，标注仍然存在。
        return changed > 0 || findLabel(traceId).isPresent();
    }

    public Optional<Label> findLabel(UUID traceId) {
        return jdbc.query("""
                SELECT expected_status, note, updated_at
                FROM meal_agent_trace_label
                WHERE trace_id = ?
                """, (row, rowNumber) -> new Label(traceId, row.getString("expected_status"),
                row.getString("note"), row.getTimestamp("updated_at").toInstant()),
                traceId.toString()).stream().findFirst();
    }

    public boolean saveFeedback(UUID traceId, boolean helpful) {
        int changed = jdbc.update("""
                INSERT INTO meal_agent_trace_feedback (trace_id, helpful)
                SELECT trace_id, ?
                FROM meal_agent_trace
                WHERE trace_id = ?
                ON DUPLICATE KEY UPDATE helpful = ?
                """, helpful, traceId.toString(), helpful);
        return changed > 0 || findFeedback(traceId).isPresent();
    }

    public Optional<Feedback> findFeedback(UUID traceId) {
        return jdbc.query("""
                SELECT helpful, updated_at
                FROM meal_agent_trace_feedback
                WHERE trace_id = ?
                """, (row, rowNumber) -> new Feedback(traceId, row.getBoolean("helpful"),
                row.getTimestamp("updated_at").toInstant()), traceId.toString()).stream().findFirst();
    }

    List<LabeledSample> findLabeledSamples(UUID sessionId, int limit) {
        String session = sessionId == null ? null : sessionId.toString();
        return jdbc.query("""
                SELECT t.trace_id, t.duration_ms, t.events_json, l.expected_status, f.helpful
                FROM meal_agent_trace_label l
                JOIN meal_agent_trace t ON t.trace_id = l.trace_id
                LEFT JOIN meal_agent_trace_feedback f ON f.trace_id = l.trace_id
                WHERE (? IS NULL OR t.session_id = ?)
                ORDER BY t.created_at DESC, t.trace_id DESC
                LIMIT ?
                """, (row, rowNumber) -> new LabeledSample(UUID.fromString(row.getString("trace_id")),
                row.getString("expected_status"), row.getLong("duration_ms"),
                parseEvents(row.getString("events_json")), row.getObject("helpful", Boolean.class)),
                session, session, limit);
    }

    private JsonNode parseEvents(String eventsJson) {
        try {
            return json.readTree(eventsJson);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("会话 Trace 格式无效", error);
        }
    }

    public record Detail(UUID traceId, UUID sessionId, long durationMs, JsonNode events, Instant createdAt) {}

    public record Label(UUID traceId, String expectedStatus, String note, Instant updatedAt) {}

    public record Feedback(UUID traceId, boolean helpful, Instant updatedAt) {}

    record LabeledSample(UUID traceId, String expectedStatus, long durationMs, JsonNode events, Boolean helpful) {}
}
