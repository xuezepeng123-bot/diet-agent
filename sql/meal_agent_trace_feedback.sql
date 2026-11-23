CREATE TABLE IF NOT EXISTS meal_agent_trace_feedback (
    trace_id CHAR(36) NOT NULL PRIMARY KEY,
    helpful BOOLEAN NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_meal_agent_trace_feedback_helpful CHECK (helpful IN (0, 1)),
    CONSTRAINT fk_meal_agent_trace_feedback_trace FOREIGN KEY (trace_id)
        REFERENCES meal_agent_trace (trace_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
