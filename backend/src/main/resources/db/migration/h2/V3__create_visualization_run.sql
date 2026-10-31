-- One row per "Run & visualize" click by a signed-in user. Source code is not stored (privacy).
CREATE TABLE visualization_run (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000) NULL,
    error_detail VARCHAR(4000) NULL,
    step_count INT NOT NULL,
    truncated BOOLEAN NOT NULL,
    code_length INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    CONSTRAINT fk_visualization_run_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_visualization_run_user_created ON visualization_run (user_id, created_at DESC);
