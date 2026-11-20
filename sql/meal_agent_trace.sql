-- 每轮请求保存一条 Trace，事件按执行顺序记录在 events_json 中。
-- 首轮请求失败时可能尚未创建会话，因此 session_id 可以为空。
CREATE TABLE IF NOT EXISTS meal_agent_trace (
    trace_id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NULL,
    duration_ms BIGINT NOT NULL,
    events_json JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
