CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1500) NULL,
    assigned_to_worker_id BIGINT NOT NULL,
    created_by_worker_id BIGINT NOT NULL,
    related_entity_type VARCHAR(60) NULL,
    related_entity_id VARCHAR(80) NULL,
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tasks_public_id (public_id),
    KEY ix_tasks_assigned_status_due (assigned_to_worker_id, status, due_at),
    KEY ix_tasks_created_status (created_at, status),
    CONSTRAINT fk_tasks_assigned_worker FOREIGN KEY (assigned_to_worker_id) REFERENCES workers(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_created_worker FOREIGN KEY (created_by_worker_id) REFERENCES workers(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_tasks_due_dates CHECK (completed_at IS NULL OR due_at IS NULL OR completed_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE goals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    worker_id BIGINT NOT NULL,
    goal_type VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    target_value DECIMAL(19,4) NOT NULL,
    notes VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goals_public_id (public_id),
    UNIQUE KEY uk_goals_worker_type_period (worker_id, goal_type, period_start, period_end),
    KEY ix_goals_worker_active_period (worker_id, active, period_start, period_end),
    CONSTRAINT fk_goals_worker FOREIGN KEY (worker_id) REFERENCES workers(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_goals_type CHECK (goal_type IN ('QUOTATIONS_ISSUED','QUOTATION_AMOUNT','TENDERS_FOLLOWED','TASKS_COMPLETED')),
    CONSTRAINT ck_goals_period CHECK (period_end >= period_start),
    CONSTRAINT ck_goals_target CHECK (target_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE audit_events
    ADD KEY ix_audit_events_action_created (action, created_at, id),
    ADD KEY ix_audit_events_result_created (result, created_at, id),
    ADD KEY ix_audit_events_actor_created (actor_worker_id, created_at, id);
