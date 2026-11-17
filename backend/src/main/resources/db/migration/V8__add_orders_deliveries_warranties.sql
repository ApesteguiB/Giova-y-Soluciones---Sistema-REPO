CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    customer_id BIGINT NOT NULL,
    quotation_id BIGINT NULL,
    invoice_id BIGINT NULL,
    responsible_worker_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    issue_date DATE NOT NULL,
    expected_delivery_date DATE NULL,
    delivered_date DATE NULL,
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
    UNIQUE KEY uk_purchase_orders_public_id (public_id),
    UNIQUE KEY uk_purchase_orders_number (order_number),
    KEY ix_purchase_orders_customer_status (customer_id, status, updated_at, id),
    KEY ix_purchase_orders_quotation_status (quotation_id, status, updated_at, id),
    KEY ix_purchase_orders_responsible_status (responsible_worker_id, status, updated_at, id),
    CONSTRAINT fk_purchase_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_responsible FOREIGN KEY (responsible_worker_id) REFERENCES workers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('REGISTERED','IN_PROGRESS','DELIVERED','CANCELLED')),
    CONSTRAINT ck_purchase_orders_currency CHECK (currency_code IN ('PEN','USD')),
    CONSTRAINT ck_purchase_orders_exchange CHECK (exchange_rate_to_pen > 0),
    CONSTRAINT ck_purchase_orders_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_purchase_orders_amounts CHECK (subtotal_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_purchase_orders_expected_date CHECK (expected_delivery_date IS NULL OR expected_delivery_date >= issue_date),
    CONSTRAINT ck_purchase_orders_delivered_date CHECK (delivered_date IS NULL OR delivered_date >= issue_date),
    CONSTRAINT ck_purchase_orders_delivered_status CHECK (status <> 'DELIVERED' OR delivered_date IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_purchase_order_items_line (purchase_order_id, line_number),
    KEY ix_purchase_order_items_product (product_id),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_purchase_order_items_line CHECK (line_number > 0),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_purchase_order_items_amounts CHECK (line_subtotal >= 0 AND line_tax >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_order_documents (
    purchase_order_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (purchase_order_id, document_id),
    KEY ix_purchase_order_documents_active (purchase_order_id, document_role, active, created_at),
    CONSTRAINT fk_purchase_order_documents_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_documents_document FOREIGN KEY (document_id) REFERENCES stored_documents(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_purchase_order_documents_role CHECK (document_role IN ('ORDER_PDF','CUSTOMER_ATTACHMENT','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    delivery_number VARCHAR(40) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    delivered_at DATETIME(6) NULL,
    received_by_name VARCHAR(160) NULL,
    received_by_document VARCHAR(40) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(1500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_records_public_id (public_id),
    UNIQUE KEY uk_delivery_records_number (delivery_number),
    KEY ix_delivery_records_order_status (purchase_order_id, status, updated_at, id),
    KEY ix_delivery_records_customer_status (customer_id, status, updated_at, id),
    CONSTRAINT fk_delivery_records_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_records_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_delivery_records_status CHECK (status IN ('DRAFT','SIGNED','CANCELLED')),
    CONSTRAINT ck_delivery_records_signed_at CHECK (status <> 'SIGNED' OR delivered_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_documents (
    delivery_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (delivery_id, document_id),
    KEY ix_delivery_documents_active (delivery_id, document_role, active, created_at),
    CONSTRAINT fk_delivery_documents_delivery FOREIGN KEY (delivery_id) REFERENCES delivery_records(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_documents_document FOREIGN KEY (document_id) REFERENCES stored_documents(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_delivery_documents_role CHECK (document_role IN ('SIGNED_ACT','PHOTO_PDF','OTHER_PDF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE warranties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    customer_id BIGINT NOT NULL,
    purchase_order_id BIGINT NULL,
    purchase_order_item_id BIGINT NULL,
    product_id BIGINT NULL,
    serial_number VARCHAR(120) NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    conditions VARCHAR(1500) NULL,
    notes VARCHAR(1500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_warranties_public_id (public_id),
    KEY ix_warranties_customer_status (customer_id, status, ends_on, updated_at, id),
    KEY ix_warranties_order_status (purchase_order_id, status, ends_on, id),
    KEY ix_warranties_product_serial (product_id, serial_number),
    CONSTRAINT fk_warranties_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_warranties_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_warranties_order_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_warranties_product FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_warranties_status CHECK (status IN ('ACTIVE','EXPIRED','CLAIMED','CANCELLED')),
    CONSTRAINT ck_warranties_dates CHECK (ends_on >= starts_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE warranty_documents (
    warranty_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (warranty_id, document_id),
    KEY ix_warranty_documents_active (warranty_id, document_role, active, created_at),
    CONSTRAINT fk_warranty_documents_warranty FOREIGN KEY (warranty_id) REFERENCES warranties(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_warranty_documents_document FOREIGN KEY (document_id) REFERENCES stored_documents(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_warranty_documents_role CHECK (document_role IN ('CERTIFICATE_PDF','OTHER_PDF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
