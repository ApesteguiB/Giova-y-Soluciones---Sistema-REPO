-- ============================================================================
-- V14 · Campos propios del proceso de venta de GIOVA
--
-- Hasta ahora el tipo de procedimiento, el motivo de un rechazo y la OCAM se
-- escribían dentro de las notas de la cotización. Eso sirve para leerlo, pero
-- no para filtrar ni para sacar indicadores. Esta migración les da columna.
--
--  * procurement_type  — PERU_COMPRAS | DIRECTA | INDIRECTA | PRIVADA
--  * outcome           — resultado real del proceso, separado del estado técnico
--  * outcome_reason    — el motivo, obligatorio cuando el resultado es negativo
--  * outcome_source    — quién cerró el proceso: nosotros o la entidad
--  * ocam_number/date  — la Orden de Compra de Acuerdo Marco (opcional)
--  * competitor_price  — por línea, el precio con el que compite la propuesta
-- ============================================================================

ALTER TABLE quotations
    ADD COLUMN procurement_type VARCHAR(20) NOT NULL DEFAULT 'DIRECTA' AFTER status,
    ADD COLUMN outcome VARCHAR(20) NULL AFTER procurement_type,
    ADD COLUMN outcome_reason VARCHAR(500) NULL AFTER outcome,
    ADD COLUMN outcome_source VARCHAR(20) NULL AFTER outcome_reason,
    ADD COLUMN outcome_at DATETIME(6) NULL AFTER outcome_source,
    ADD COLUMN delivery_term_days INT NULL AFTER outcome_at,
    ADD COLUMN warranty_months INT NULL AFTER delivery_term_days,
    ADD COLUMN ocam_number VARCHAR(60) NULL AFTER warranty_months,
    ADD COLUMN ocam_date DATE NULL AFTER ocam_number;

ALTER TABLE quotations
    ADD CONSTRAINT ck_quotations_procurement_type
        CHECK (procurement_type IN ('PERU_COMPRAS','DIRECTA','INDIRECTA','PRIVADA')),
    ADD CONSTRAINT ck_quotations_outcome
        CHECK (outcome IS NULL OR outcome IN ('PENDIENTE','SELECCIONADA','NO_SELECCIONADA','DESIERTA','RECHAZADA','ANULADA')),
    ADD CONSTRAINT ck_quotations_outcome_source
        CHECK (outcome_source IS NULL OR outcome_source IN ('ENTIDAD','NOSOTROS')),
    -- Regla de negocio: un cierre negativo SIEMPRE lleva motivo escrito.
    ADD CONSTRAINT ck_quotations_outcome_reason
        CHECK (outcome IS NULL
               OR outcome IN ('PENDIENTE','SELECCIONADA')
               OR (outcome_reason IS NOT NULL AND CHAR_LENGTH(TRIM(outcome_reason)) >= 5)),
    ADD CONSTRAINT ck_quotations_delivery_term
        CHECK (delivery_term_days IS NULL OR (delivery_term_days > 0 AND delivery_term_days <= 365)),
    ADD CONSTRAINT ck_quotations_warranty
        CHECK (warranty_months IS NULL OR (warranty_months >= 0 AND warranty_months <= 120));

CREATE INDEX ix_quotations_procurement ON quotations (procurement_type, status, updated_at);
CREATE INDEX ix_quotations_outcome ON quotations (outcome, outcome_at);

-- Precio con el que compite cada línea. Se guarda siempre; mostrarlo o no es
-- decisión de la pantalla, no del dato.
ALTER TABLE quotation_items
    ADD COLUMN competitor_price DECIMAL(19,4) NULL AFTER unit_price,
    ADD COLUMN purchase_price DECIMAL(19,4) NULL AFTER competitor_price;

ALTER TABLE quotation_items
    ADD CONSTRAINT ck_quotation_items_competitor CHECK (competitor_price IS NULL OR competitor_price >= 0),
    ADD CONSTRAINT ck_quotation_items_purchase CHECK (purchase_price IS NULL OR purchase_price >= 0);

-- ----------------------------------------------------------------------------
-- Rellenar lo que ya existe leyendo las notas de las cotizaciones de prueba,
-- para que la pantalla nueva no salga vacía.
-- ----------------------------------------------------------------------------
UPDATE quotations SET procurement_type = 'PERU_COMPRAS' WHERE notes LIKE '%PERU COMPRAS%';
UPDATE quotations SET procurement_type = 'DIRECTA'      WHERE notes LIKE '%CONTRATACION DIRECTA%';
UPDATE quotations SET procurement_type = 'INDIRECTA'    WHERE notes LIKE '%CONTRATACION INDIRECTA%';
UPDATE quotations SET procurement_type = 'PRIVADA'      WHERE notes LIKE '%EMPRESA PRIVADA%';

-- El resultado se deduce del estado y de lo que dice la nota.
UPDATE quotations SET outcome = 'SELECCIONADA', outcome_source = 'ENTIDAD', outcome_at = issued_at
WHERE status = 'ACCEPTED' AND outcome IS NULL;

UPDATE quotations SET outcome = 'PENDIENTE'
WHERE status IN ('DRAFT','ISSUED') AND outcome IS NULL;

UPDATE quotations
SET outcome = 'NO_SELECCIONADA', outcome_source = 'ENTIDAD', outcome_at = issued_at,
    outcome_reason = 'La entidad seleccionó a otro proveedor. Motivo registrado en las notas.'
WHERE status = 'REJECTED' AND outcome IS NULL;

UPDATE quotations
SET outcome = 'DESIERTA', outcome_source = 'ENTIDAD', outcome_at = issued_at,
    outcome_reason = 'La entidad no adjudicó a ningún proveedor dentro del plazo.'
WHERE status = 'EXPIRED' AND outcome IS NULL;

UPDATE quotations
SET outcome = 'ANULADA', outcome_source = 'NOSOTROS', outcome_at = issued_at,
    outcome_reason = 'Anulada internamente. Motivo registrado en las notas.'
WHERE status = 'CANCELLED' AND outcome IS NULL;

-- Plazo de entrega y garantía de los datos de prueba.
UPDATE quotations SET delivery_term_days = 10, warranty_months = 12 WHERE quotation_number = 'COT-2026-0101';
UPDATE quotations SET delivery_term_days = 15, warranty_months = 12 WHERE quotation_number = 'COT-2026-0102';
UPDATE quotations SET delivery_term_days = 7                        WHERE quotation_number = 'COT-2026-0105';
UPDATE quotations SET delivery_term_days = 12                       WHERE quotation_number = 'COT-2026-0106';
UPDATE quotations SET delivery_term_days = 20                       WHERE quotation_number = 'COT-2026-0107';
UPDATE quotations SET delivery_term_days = 5, warranty_months = 6    WHERE quotation_number = 'COT-2026-0110';

-- OCAM de la cotización ganada por Acuerdo Marco.
UPDATE quotations SET ocam_number = 'OCAM 00123-2026', ocam_date = DATE(issued_at) + INTERVAL 4 DAY
WHERE quotation_number = 'COT-2026-0101';

-- Precios de competencia de ejemplo: la propuesta gana por poco en unas y
-- pierde en otras, que es justo lo que el vendedor necesita ver.
UPDATE quotation_items i
JOIN quotations q ON q.id = i.quotation_id
SET i.competitor_price = ROUND(i.unit_price * 1.06, 4)
WHERE q.quotation_number IN ('COT-2026-0101','COT-2026-0105','COT-2026-0110','COT-2026-0112');

UPDATE quotation_items i
JOIN quotations q ON q.id = i.quotation_id
SET i.competitor_price = ROUND(i.unit_price * 0.93, 4)
WHERE q.quotation_number = 'COT-2026-0103';

UPDATE quotation_items i
JOIN quotations q ON q.id = i.quotation_id
SET i.purchase_price = ROUND(i.unit_price * 0.72, 4)
WHERE q.notes LIKE '%[DEMO]%';
