CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_worker_id BIGINT NOT NULL,
    customer_type VARCHAR(15) NOT NULL,
    document_type VARCHAR(10) NOT NULL,
    document_number VARCHAR(20) NOT NULL,
    business_name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(140) NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(30) NULL,
    address VARCHAR(300) NULL,
    notes VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_public_id (public_id),
    UNIQUE KEY uk_customers_document (document_type, document_number),
    KEY ix_customers_owner_active (owner_worker_id, active, updated_at, id),
    KEY ix_customers_business_name (business_name),
    CONSTRAINT fk_customers_owner FOREIGN KEY (owner_worker_id) REFERENCES workers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_customers_type CHECK (customer_type IN ('PERSON','COMPANY')),
    CONSTRAINT ck_customers_document_type CHECK (document_type IN ('DNI','RUC','CE','OTHER')),
    CONSTRAINT ck_customers_document_format CHECK (
        (document_type = 'DNI' AND document_number REGEXP '^[0-9]{8}$') OR
        (document_type = 'RUC' AND document_number REGEXP '^[0-9]{11}$') OR
        (document_type IN ('CE','OTHER') AND document_number REGEXP '^[A-Za-z0-9-]{3,20}$')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_price_references (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    recorded_by_worker_id BIGINT NOT NULL,
    supplier_name VARCHAR(180) NULL,
    source_reference VARCHAR(300) NULL,
    currency_code CHAR(3) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    includes_tax BOOLEAN NOT NULL DEFAULT FALSE,
    tax_rate DECIMAL(9,6) NOT NULL DEFAULT 0,
    exchange_rate_to_pen DECIMAL(19,6) NULL,
    effective_at DATETIME(6) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_price_references_product_effective (product_id, effective_at, id),
    KEY ix_price_references_recorded_by (recorded_by_worker_id, created_at, id),
    CONSTRAINT fk_price_references_product FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_price_references_worker FOREIGN KEY (recorded_by_worker_id) REFERENCES workers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_price_references_currency CHECK (currency_code IN ('PEN','USD')),
    CONSTRAINT ck_price_references_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_price_references_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_price_references_exchange CHECK (
        (currency_code = 'PEN' AND exchange_rate_to_pen IS NULL) OR
        (currency_code = 'USD' AND exchange_rate_to_pen > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
