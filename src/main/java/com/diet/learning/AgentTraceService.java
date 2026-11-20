package com.diet.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

    private JsonNode parseEvents(String eventsJson) {
        try {
            return json.readTree(eventsJson);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("会话 Trace 格式无效", error);
        }
    }

    public record Detail(UUID traceId, UUID sessionId, long durationMs, JsonNode events, Instant createdAt) {}
}
