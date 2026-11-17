CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invoice_number VARCHAR(40) NOT NULL,
    invoice_type VARCHAR(20) NOT NULL,
    customer_id BIGINT NOT NULL,
    quotation_id BIGINT NULL,
    registered_by_worker_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    issue_date DATE NOT NULL,
    due_date DATE NULL,
    currency_code CHAR(3) NOT NULL,
    exchange_rate_to_pen DECIMAL(19,6) NOT NULL DEFAULT 1,
    includes_tax BOOLEAN NOT NULL DEFAULT FALSE,
    tax_rate DECIMAL(9,6) NOT NULL DEFAULT 0,
    subtotal_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    notes VARCHAR(1500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_public_id (public_id),
    UNIQUE KEY uk_invoices_type_number (invoice_type, invoice_number),
    KEY ix_invoices_customer_status (customer_id, status, updated_at, id),
    KEY ix_invoices_quotation_status (quotation_id, status, updated_at, id),
    KEY ix_invoices_registered_status (registered_by_worker_id, status, updated_at, id),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_registered_by FOREIGN KEY (registered_by_worker_id) REFERENCES workers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_invoices_type CHECK (invoice_type IN ('INVOICE','RECEIPT','OTHER')),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT','ISSUED','CANCELLED')),
    CONSTRAINT ck_invoices_currency CHECK (currency_code IN ('PEN','USD')),
    CONSTRAINT ck_invoices_exchange CHECK (exchange_rate_to_pen > 0),
    CONSTRAINT ck_invoices_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_invoices_amounts CHECK (subtotal_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_invoices_due_date CHECK (due_date IS NULL OR due_date >= issue_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoice_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    line_number INT NOT NULL,
    product_id BIGINT NULL,
    sku_snapshot VARCHAR(80) NULL,
    description_snapshot VARCHAR(500) NOT NULL,
    unit_snapshot VARCHAR(30) NOT NULL DEFAULT 'unidad',
    quantity DECIMAL(19,4) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    line_subtotal DECIMAL(19,4) NOT NULL,
    line_tax DECIMAL(19,4) NOT NULL,
    line_total DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_items_line (invoice_id, line_number),
    KEY ix_invoice_items_product (product_id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_product FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_invoice_items_line CHECK (line_number > 0),
    CONSTRAINT ck_invoice_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_invoice_items_amounts CHECK (line_subtotal >= 0 AND line_tax >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoice_documents (
    invoice_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (invoice_id, document_id),
    KEY ix_invoice_documents_active (invoice_id, document_role, active, created_at),
    CONSTRAINT fk_invoice_documents_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_documents_document FOREIGN KEY (document_id) REFERENCES stored_documents(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_invoice_documents_role CHECK (document_role IN ('PDF','XML','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
