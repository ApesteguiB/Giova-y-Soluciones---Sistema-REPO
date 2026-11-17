CREATE TABLE stored_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_kind VARCHAR(30) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    extension VARCHAR(10) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256_hex CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by_worker_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stored_documents_public_id (public_id),
    UNIQUE KEY uk_stored_documents_storage_key (storage_key),
    KEY ix_stored_documents_worker_active (created_by_worker_id, active, created_at, id),
    CONSTRAINT fk_stored_documents_worker FOREIGN KEY (created_by_worker_id) REFERENCES workers(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_stored_documents_kind CHECK (document_kind IN (
        'QUOTATION_PDF','INVOICE_PDF','INVOICE_XML','PURCHASE_ORDER_PDF',
        'DELIVERY_PDF','WARRANTY_PDF','OTHER_PDF'
    )),
    CONSTRAINT ck_stored_documents_size CHECK (size_bytes > 0),
    CONSTRAINT ck_stored_documents_hash CHECK (sha256_hex REGEXP '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_stored_documents_extension CHECK (extension IN ('pdf','xml'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
