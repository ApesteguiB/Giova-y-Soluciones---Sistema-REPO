ALTER TABLE workers
    ADD COLUMN role_code VARCHAR(20) NULL AFTER enabled,
    ADD KEY ix_workers_role_code (role_code),
    ADD CONSTRAINT ck_workers_role_code CHECK (role_code IN ('ADMIN', 'ACCOUNTANT', 'SELLER'));
