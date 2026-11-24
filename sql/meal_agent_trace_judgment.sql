CREATE TABLE IF NOT EXISTS meal_agent_trace_judgment (
    trace_id CHAR(36) NOT NULL PRIMARY KEY,
    score TINYINT UNSIGNED NOT NULL,
    reason VARCHAR(300) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    evaluated_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_meal_agent_trace_judgment_score CHECK (score <= 100),
    CONSTRAINT fk_meal_agent_trace_judgment_trace FOREIGN KEY (trace_id)
        REFERENCES meal_agent_trace (trace_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
