CREATE TABLE IF NOT EXISTS meal_agent_trace (
    trace_id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NULL,
    duration_ms BIGINT NOT NULL,
    events_json JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
