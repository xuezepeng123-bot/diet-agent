CREATE TABLE IF NOT EXISTS meal_agent_trace_label (
    trace_id CHAR(36) NOT NULL PRIMARY KEY,
    expected_status VARCHAR(16) NOT NULL,
    note VARCHAR(500) NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_meal_agent_trace_label_trace FOREIGN KEY (trace_id)
        REFERENCES meal_agent_trace (trace_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
