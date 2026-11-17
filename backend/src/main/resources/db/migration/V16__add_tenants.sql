-- P51: convierte los datos existentes en la empresa GIOVA y agrega aislamiento por tenant.
-- MariaDB 10.4 no ejecuta estos ALTER como una transacción; ante un fallo, restaurar el respaldo.

CREATE TABLE tenants (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    code VARCHAR(30) NOT NULL,
    slug VARCHAR(40) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    tax_id VARCHAR(20) NULL,
    address VARCHAR(255) NULL,
    contact_email VARCHAR(254) NULL,
    default_currency CHAR(3) NOT NULL,
    tax_rate DECIMAL(9,6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenants_public_id (public_id),
    UNIQUE KEY uk_tenants_code (code),
    UNIQUE KEY uk_tenants_slug (slug),
    CONSTRAINT ck_tenants_currency CHECK (default_currency IN ('PEN','USD')),
    CONSTRAINT ck_tenants_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tenants (
    id, public_id, code, slug, legal_name, display_name, default_currency, tax_rate,
    active, version, created_at, updated_at
) VALUES (
    1, UUID(), 'GIOVA', 'giova', 'GIOVA', 'GIOVA', 'PEN', 0.180000,
    TRUE, 0, NOW(6), NOW(6)
);

CREATE TABLE tenant_branding (
    tenant_id BIGINT UNSIGNED NOT NULL,
    primary_color CHAR(7) NOT NULL,
    secondary_color CHAR(7) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    logo_storage_key VARCHAR(255) NULL,
    pdf_header_text VARCHAR(120) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_branding_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_tenant_branding_colors CHECK (
        primary_color REGEXP '^#[0-9A-Fa-f]{6}$' AND
        secondary_color REGEXP '^#[0-9A-Fa-f]{6}$' AND
        accent_color REGEXP '^#[0-9A-Fa-f]{6}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tenant_branding (
    tenant_id, primary_color, secondary_color, accent_color,
    logo_storage_key, pdf_header_text, version, created_at, updated_at
) VALUES (
    1, '#0a2342', '#14a3a8', '#2ec8dd', NULL, NULL, 0, NOW(6), NOW(6)
);

CREATE TABLE worker_memberships (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    role_code VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_worker_id BIGINT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_memberships_worker_tenant (worker_id, tenant_id),
    KEY ix_worker_memberships_tenant_enabled (tenant_id, enabled),
    CONSTRAINT fk_worker_memberships_worker FOREIGN KEY (worker_id) REFERENCES workers(id),
    CONSTRAINT fk_worker_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_worker_memberships_created_by FOREIGN KEY (created_by_worker_id) REFERENCES workers(id),
    CONSTRAINT ck_worker_memberships_role CHECK (role_code IN ('ADMIN','ACCOUNTANT','SELLER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE workers
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at DATETIME(6) NULL,
    ADD COLUMN created_by_worker_id BIGINT NULL,
    ADD CONSTRAINT fk_workers_created_by FOREIGN KEY (created_by_worker_id) REFERENCES workers(id);

ALTER TABLE workers DROP CONSTRAINT ck_workers_role_code;

INSERT INTO worker_memberships (
    worker_id, tenant_id, role_code, enabled, created_by_worker_id, version, created_at, updated_at
)
SELECT id, 1, role_code, enabled, NULL, 0, created_at, updated_at
FROM workers
WHERE role_code IS NOT NULL AND role_code IN ('ADMIN','ACCOUNTANT','SELLER');

UPDATE workers
SET role_code = NULL
WHERE role_code IS NOT NULL AND role_code IN ('ADMIN','ACCOUNTANT','SELLER');

ALTER TABLE workers
    ADD CONSTRAINT ck_workers_role_code CHECK (role_code IS NULL OR role_code = 'SUPERADMIN');

-- Tablas con tenant_id obligatorio: agregar nullable, backfill y luego fijar NOT NULL/FK.
ALTER TABLE brands ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE brands SET tenant_id = 1;
ALTER TABLE brands MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_brands_tenant (tenant_id), ADD CONSTRAINT fk_brands_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE categories ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE categories SET tenant_id = 1;
ALTER TABLE categories MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_categories_tenant (tenant_id), ADD CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE products ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE products SET tenant_id = 1;
ALTER TABLE products MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_products_tenant (tenant_id), ADD CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE product_price_references ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE product_price_references SET tenant_id = 1;
ALTER TABLE product_price_references MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_product_price_references_tenant (tenant_id), ADD CONSTRAINT fk_product_price_references_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE customers ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE customers SET tenant_id = 1;
ALTER TABLE customers MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_customers_tenant (tenant_id), ADD CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE quotations ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE quotations SET tenant_id = 1;
ALTER TABLE quotations MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_quotations_tenant (tenant_id), ADD CONSTRAINT fk_quotations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE stored_documents ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE stored_documents SET tenant_id = 1;
ALTER TABLE stored_documents MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_stored_documents_tenant (tenant_id), ADD CONSTRAINT fk_stored_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE invoices ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE invoices SET tenant_id = 1;
ALTER TABLE invoices MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_invoices_tenant (tenant_id), ADD CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE purchase_orders ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE purchase_orders SET tenant_id = 1;
ALTER TABLE purchase_orders MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_purchase_orders_tenant (tenant_id), ADD CONSTRAINT fk_purchase_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE delivery_records ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE delivery_records SET tenant_id = 1;
ALTER TABLE delivery_records MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_delivery_records_tenant (tenant_id), ADD CONSTRAINT fk_delivery_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE warranties ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE warranties SET tenant_id = 1;
ALTER TABLE warranties MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_warranties_tenant (tenant_id), ADD CONSTRAINT fk_warranties_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE tasks ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE tasks SET tenant_id = 1;
ALTER TABLE tasks MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_tasks_tenant (tenant_id), ADD CONSTRAINT fk_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE goals ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE goals SET tenant_id = 1;
ALTER TABLE goals MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_goals_tenant (tenant_id), ADD CONSTRAINT fk_goals_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE document_sequences ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE document_sequences SET tenant_id = 1;
ALTER TABLE document_sequences MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_document_sequences_tenant (tenant_id), ADD CONSTRAINT fk_document_sequences_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE tender_match_profiles ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE tender_match_profiles SET tenant_id = 1;
ALTER TABLE tender_match_profiles MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_tender_match_profiles_tenant (tenant_id), ADD CONSTRAINT fk_tender_match_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE tender_matches ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE tender_matches SET tenant_id = 1;
ALTER TABLE tender_matches MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_tender_matches_tenant (tenant_id), ADD CONSTRAINT fk_tender_matches_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE tender_match_actions ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE tender_match_actions SET tenant_id = 1;
ALTER TABLE tender_match_actions MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_tender_match_actions_tenant (tenant_id), ADD CONSTRAINT fk_tender_match_actions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE contact_submissions ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE contact_submissions SET tenant_id = 1;
ALTER TABLE contact_submissions MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_contact_submissions_tenant (tenant_id), ADD CONSTRAINT fk_contact_submissions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE claims ADD COLUMN tenant_id BIGINT UNSIGNED NULL;
UPDATE claims SET tenant_id = 1;
ALTER TABLE claims MODIFY tenant_id BIGINT UNSIGNED NOT NULL, ADD KEY ix_claims_tenant (tenant_id), ADD CONSTRAINT fk_claims_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

-- Login fallido sin cuenta puede quedar sin empresa; las filas históricas se asignan a GIOVA.
ALTER TABLE audit_events
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL,
    ADD KEY ix_audit_events_tenant (tenant_id),
    ADD CONSTRAINT fk_audit_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
UPDATE audit_events SET tenant_id = 1 WHERE tenant_id IS NULL;

-- Crear todos los índices únicos compuestos antes de retirar sus equivalentes globales.
ALTER TABLE brands
    ADD UNIQUE KEY uk_brands_tenant_name (tenant_id, name),
    ADD UNIQUE KEY uk_brands_tenant_slug (tenant_id, slug);
ALTER TABLE categories
    ADD UNIQUE KEY uk_categories_tenant_name (tenant_id, name),
    ADD UNIQUE KEY uk_categories_tenant_slug (tenant_id, slug);
ALTER TABLE products
    ADD UNIQUE KEY uk_products_tenant_sku (tenant_id, sku),
    ADD UNIQUE KEY uk_products_tenant_slug (tenant_id, slug);
ALTER TABLE customers
    ADD UNIQUE KEY uk_customers_tenant_document (tenant_id, document_type, document_number);
ALTER TABLE quotations
    ADD UNIQUE KEY uk_quotations_tenant_number (tenant_id, quotation_number);
ALTER TABLE invoices
    ADD UNIQUE KEY uk_invoices_tenant_type_number (tenant_id, invoice_type, invoice_number);
ALTER TABLE purchase_orders
    ADD UNIQUE KEY uk_purchase_orders_tenant_number (tenant_id, order_number);
ALTER TABLE delivery_records
    ADD UNIQUE KEY uk_delivery_records_tenant_number (tenant_id, delivery_number);
ALTER TABLE goals
    ADD UNIQUE KEY uk_goals_tenant_worker_type_period (tenant_id, worker_id, goal_type, period_start, period_end);
ALTER TABLE tender_matches
    ADD UNIQUE KEY uk_tender_matches_tenant_process_profile (tenant_id, process_id, profile_id);

-- El nuevo índice de goals ya está cubierto por ix_goals_worker_active_period; tender_matches
-- requiere índice propio de process_id para conservar fk_tender_matches_process.
ALTER TABLE tender_matches ADD KEY ix_tender_matches_process (process_id);

ALTER TABLE document_sequences
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (tenant_id, document_type, year);

ALTER TABLE brands DROP INDEX uk_brands_name, DROP INDEX uk_brands_slug;
ALTER TABLE categories DROP INDEX uk_categories_name, DROP INDEX uk_categories_slug;
ALTER TABLE products DROP INDEX uk_products_sku, DROP INDEX uk_products_slug;
ALTER TABLE customers DROP INDEX uk_customers_document;
ALTER TABLE quotations DROP INDEX uk_quotations_number;
ALTER TABLE invoices DROP INDEX uk_invoices_type_number;
ALTER TABLE purchase_orders DROP INDEX uk_purchase_orders_number;
ALTER TABLE delivery_records DROP INDEX uk_delivery_records_number;
ALTER TABLE goals DROP INDEX uk_goals_worker_type_period;
ALTER TABLE tender_matches DROP INDEX uk_tender_matches_process_profile;

-- Retirar los índices temporales de tenant ahora que los índices compuestos los cubren.
ALTER TABLE brands DROP INDEX ix_brands_tenant;
ALTER TABLE categories DROP INDEX ix_categories_tenant;
ALTER TABLE products DROP INDEX ix_products_tenant;
ALTER TABLE customers DROP INDEX ix_customers_tenant;
ALTER TABLE quotations DROP INDEX ix_quotations_tenant;
ALTER TABLE invoices DROP INDEX ix_invoices_tenant;
ALTER TABLE purchase_orders DROP INDEX ix_purchase_orders_tenant;
ALTER TABLE delivery_records DROP INDEX ix_delivery_records_tenant;
ALTER TABLE goals DROP INDEX ix_goals_tenant;
ALTER TABLE document_sequences DROP INDEX ix_document_sequences_tenant;
ALTER TABLE tender_matches DROP INDEX ix_tender_matches_tenant;
