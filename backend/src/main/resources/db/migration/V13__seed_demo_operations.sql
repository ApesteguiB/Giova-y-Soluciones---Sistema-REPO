-- ============================================================================
-- V13 · Datos de prueba para recorrer el sistema completo
--
-- Carga entidades del Estado reales (MINSA, Marina de Guerra, INEI, SUNAT…) y
-- clientes privados, con cotizaciones que cubren los tres caminos que usa GIOVA:
--
--   * PERÚ COMPRAS (Acuerdo Marco): la entidad publica, se cotiza con plazo
--     corto (1 a 3 días hábiles) y, si se gana, llega la OCAM.
--   * CONTRATACIÓN DIRECTA: se cotiza a pedido de la entidad, con validez que
--     fija el vendedor.
--   * CONTRATACIÓN INDIRECTA: llega por un intermediario o distribuidor.
--
-- También deja facturas, órdenes de compra, actas, garantías, precios de compra
-- y tareas para que cada módulo tenga algo que mostrar.
--
-- TODO lo que inserta este archivo lleva la marca [DEMO] en sus notas, así que
-- para dejar la base limpia antes de entregar basta con:
--
--   DELETE FROM quotation_items WHERE quotation_id IN (SELECT id FROM quotations WHERE notes LIKE '%[DEMO]%');
--   DELETE FROM quotations      WHERE notes LIKE '%[DEMO]%';
--   DELETE FROM invoice_items   WHERE invoice_id IN (SELECT id FROM invoices WHERE notes LIKE '%[DEMO]%');
--   DELETE FROM invoices        WHERE notes LIKE '%[DEMO]%';
--   DELETE FROM customers       WHERE notes LIKE '%[DEMO]%';
--
-- La migración es idempotente: cada INSERT comprueba antes que el registro no
-- exista, de modo que volver a aplicarla no duplica nada. Por eso convive sin
-- problema con cualquier V12 de datos de prueba que ya se haya aplicado: si una
-- entidad ya existe (mismo RUC), simplemente se reutiliza.
-- ============================================================================

-- El vendedor es el dueño de las cotizaciones. Si todavía no se creó ninguno con
-- rol SELLER, se usa el primer trabajador disponible para no romper la FK.
SET @seller_id = (
    SELECT id FROM workers WHERE role_code = 'SELLER' ORDER BY id LIMIT 1
);
SET @accountant_id = (
    SELECT id FROM workers WHERE role_code = 'ACCOUNTANT' ORDER BY id LIMIT 1
);
SET @any_worker_id = (SELECT id FROM workers ORDER BY id LIMIT 1);
SET @seller_id = COALESCE(@seller_id, @any_worker_id);
SET @accountant_id = COALESCE(@accountant_id, @any_worker_id);

-- ----------------------------------------------------------------------------
-- 1. Entidades del Estado y clientes privados
-- ----------------------------------------------------------------------------
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT * FROM (
    SELECT UUID() AS public_id, @seller_id AS owner_worker_id, 'COMPANY' AS customer_type,
           'RUC' AS document_type, '20131373237' AS document_number,
           'MINISTERIO DE SALUD' AS business_name, 'Oficina de Logística' AS contact_name,
           'logistica@minsa.gob.pe' AS email, '(01) 315 6600' AS phone,
           'Av. Salaverry 801, Jesús María, Lima' AS address,
           '[DEMO] Entidad del Estado. Compra por Perú Compras (Acuerdo Marco).' AS notes,
           TRUE AS active
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20154995514',
        'MARINA DE GUERRA DEL PERU', 'Dirección de Abastecimiento',
        'abastecimiento@marina.mil.pe', '(01) 209 8100',
        'Av. La Marina 1180, Callao',
        '[DEMO] Entidad del Estado. Requiere acta de conformidad firmada.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131379944',
        'INSTITUTO NACIONAL DE ESTADISTICA E INFORMATICA', 'Oficina Técnica de Informática',
        'logistica@inei.gob.pe', '(01) 652 0000',
        'Av. General Garzón 654, Jesús María, Lima',
        '[DEMO] Entidad del Estado. Compras de equipamiento informático.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131312955',
        'SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA',
        'Intendencia de Administración', 'compras@sunat.gob.pe', '(01) 315 0730',
        'Av. Garcilaso de la Vega 1472, Lima',
        '[DEMO] Entidad del Estado. Procedimiento por catálogo electrónico.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131367938',
        'MINISTERIO DE EDUCACION', 'Unidad de Abastecimiento',
        'abastecimiento@minedu.gob.pe', '(01) 615 5800',
        'Calle Del Comercio 193, San Borja, Lima',
        '[DEMO] Entidad del Estado. Compras por contratación directa.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131257750',
        'MUNICIPALIDAD PROVINCIAL DEL CALLAO', 'Subgerencia de Logística',
        'logistica@municallao.gob.pe', '(01) 429 6096',
        'Av. Sáenz Peña 261, Callao',
        '[DEMO] Entidad del Estado. Cliente cercano a la oficina.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20297868790',
        'UNIVERSIDAD SAN IGNACIO DE LOYOLA S.R.L.', 'Jefatura de Compras',
        'compras@usil.edu.pe', '(01) 317 1000',
        'Av. La Fontana 550, La Molina, Lima',
        '[DEMO] Cliente privado. Compra recurrente de tóner Xerox.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20100047218',
        'BANCO DE CREDITO DEL PERU', 'Gerencia de Compras',
        'proveedores@bcp.com.pe', '(01) 311 9898',
        'Calle Centenario 156, La Molina, Lima',
        '[DEMO] Cliente privado. Solo requiere orden de compra, sin OCAM.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20601030013',
        'SOLUCIONES INTEGRALES DEL NORTE S.A.C.', 'Área Comercial',
        'contacto@sinorte.com.pe', '(044) 221 100',
        'Av. España 1450, Trujillo',
        '[DEMO] Intermediario. Llega por contratación indirecta.', TRUE
    UNION ALL SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20553708137',
        'CORPORACION TECNOLOGICA ANDINA S.A.C.', 'Compras',
        'compras@ctandina.pe', '(01) 480 2211',
        'Av. Argentina 2085, Lima',
        '[DEMO] Cliente privado. Distribuidor asociado.', TRUE
) AS demo
WHERE NOT EXISTS (
    SELECT 1 FROM customers existing
    WHERE existing.document_type = demo.document_type
      AND existing.document_number = demo.document_number
);

-- ----------------------------------------------------------------------------
-- 2. Cotizaciones
--
-- Una por escenario. Los importes se calculan desde las líneas más abajo, así
-- que aquí solo se define la cabecera: estado, moneda, vigencia y la nota que
-- explica el procedimiento (Perú Compras / directa / indirecta), que es la forma
-- de registrarlo mientras la tabla no tenga una columna propia.
-- ----------------------------------------------------------------------------
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status, issued_at,
                        valid_until, currency_code, exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), demo.quotation_number,
       (SELECT id FROM customers WHERE document_number = demo.ruc ORDER BY id LIMIT 1),
       @seller_id, demo.status, demo.issued_at, demo.valid_until, demo.currency_code,
       demo.exchange_rate, FALSE, 0.180000, demo.notes
FROM (
    -- --- PERÚ COMPRAS (Acuerdo Marco): plazo de 1 a 3 días hábiles -----------
    SELECT 'COT-2026-0101' AS quotation_number, '20131373237' AS ruc, 'ACCEPTED' AS status,
           TIMESTAMP(CURRENT_DATE - INTERVAL 24 DAY, '09:15:00') AS issued_at,
           CURRENT_DATE - INTERVAL 21 DAY AS valid_until, 'PEN' AS currency_code,
           1.000000 AS exchange_rate,
           '[DEMO] PERU COMPRAS · Acuerdo Marco IM-CE-2024-11. Plazo de cotización: 3 días hábiles. Resultado: SELECCIONADA. OCAM 00123-2026 recibida. Plazo de entrega ofrecido: 10 días. Garantía: 12 meses.' AS notes
    UNION ALL SELECT 'COT-2026-0102', '20154995514', 'ISSUED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 2 DAY, '10:40:00'), CURRENT_DATE + INTERVAL 1 DAY, 'PEN', 1.000000,
        '[DEMO] PERU COMPRAS · Acuerdo Marco. Publicada hace 2 días, vence mañana. Esperando selección de la entidad. Plazo de entrega ofrecido: 15 días. Garantía: 12 meses.'
    UNION ALL SELECT 'COT-2026-0103', '20131379944', 'REJECTED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 12 DAY, '08:30:00'), CURRENT_DATE - INTERVAL 9 DAY, 'PEN', 1.000000,
        '[DEMO] PERU COMPRAS · Resultado: NO SELECCIONADA. Motivo: la entidad eligió a otro proveedor con precio menor (diferencia de S/ 380). Rechazo de la ENTIDAD, no nuestro.'
    UNION ALL SELECT 'COT-2026-0104', '20131312955', 'EXPIRED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 20 DAY, '11:05:00'), CURRENT_DATE - INTERVAL 17 DAY, 'PEN', 1.000000,
        '[DEMO] PERU COMPRAS · Resultado: DESIERTA. La entidad no adjudicó a ningún proveedor dentro del plazo.'
    -- --- CONTRATACIÓN DIRECTA ----------------------------------------------
    UNION ALL SELECT 'COT-2026-0105', '20131367938', 'ACCEPTED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 18 DAY, '15:20:00'), CURRENT_DATE - INTERVAL 3 DAY, 'PEN', 1.000000,
        '[DEMO] CONTRATACION DIRECTA · Solicitud directa de la entidad (menor a 8 UIT). Validez ofrecida: 15 días. Resultado: SELECCIONADA. Orden de compra 4500123987. Plazo de entrega: 7 días.'
    UNION ALL SELECT 'COT-2026-0106', '20131257750', 'ISSUED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 5 DAY, '09:00:00'), CURRENT_DATE + INTERVAL 10 DAY, 'PEN', 1.000000,
        '[DEMO] CONTRATACION DIRECTA · Validez ofrecida: 15 días. En evaluación por la Subgerencia de Logística. Plazo de entrega ofrecido: 12 días.'
    UNION ALL SELECT 'COT-2026-0107', '20100047218', 'ACCEPTED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 30 DAY, '16:45:00'), CURRENT_DATE - INTERVAL 15 DAY, 'USD', 3.720000,
        '[DEMO] EMPRESA PRIVADA · No requiere OCAM, solo orden de compra. Tipo de cambio del día de la cotización: 3.72. Resultado: ACEPTADA. Plazo de entrega: 20 días (importación).'
    -- --- CONTRATACIÓN INDIRECTA --------------------------------------------
    UNION ALL SELECT 'COT-2026-0108', '20601030013', 'ISSUED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 7 DAY, '12:10:00'), CURRENT_DATE + INTERVAL 8 DAY, 'USD', 3.745000,
        '[DEMO] CONTRATACION INDIRECTA · Llega por intermediario para una entidad de Trujillo. Tipo de cambio guardado: 3.745. Validez: 15 días.'
    UNION ALL SELECT 'COT-2026-0109', '20553708137', 'CANCELLED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 9 DAY, '14:30:00'), CURRENT_DATE - INTERVAL 4 DAY, 'PEN', 1.000000,
        '[DEMO] CONTRATACION INDIRECTA · ANULADA POR NOSOTROS. Motivo: el proveedor subió el precio de compra y el margen quedaba negativo. No se pudo sostener el precio ofrecido.'
    UNION ALL SELECT 'COT-2026-0110', '20297868790', 'ACCEPTED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 26 DAY, '10:00:00'), CURRENT_DATE - INTERVAL 11 DAY, 'USD', 3.710000,
        '[DEMO] EMPRESA PRIVADA · Compra recurrente de tóner Xerox. Facturada con F001-796. Plazo de entrega: 5 días. Garantía: 6 meses.'
    UNION ALL SELECT 'COT-2026-0111', '20131373237', 'DRAFT', NULL, NULL, 'PEN', 1.000000,
        '[DEMO] PERU COMPRAS · Borrador en preparación. Falta confirmar el precio de compra con el proveedor antes de publicar.'
    UNION ALL SELECT 'COT-2026-0112', '20154995514', 'ACCEPTED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 45 DAY, '09:50:00'), CURRENT_DATE - INTERVAL 42 DAY, 'PEN', 1.000000,
        '[DEMO] PERU COMPRAS · Cotización histórica del mes pasado. SELECCIONADA, entregada y con acta de conformidad firmada.'
) AS demo
WHERE NOT EXISTS (
    SELECT 1 FROM quotations existing WHERE existing.quotation_number = demo.quotation_number
) AND EXISTS (
    SELECT 1 FROM customers c WHERE c.document_number = demo.ruc
);

-- ----------------------------------------------------------------------------
-- 3. Líneas de las cotizaciones
--
-- line_subtotal = cantidad × precio, line_tax = 18 %, line_total = suma.
-- Se enlazan al catálogo por SKU cuando el producto existe.
-- ----------------------------------------------------------------------------
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
                             unit_snapshot, quantity, unit_price, line_subtotal, line_tax, line_total)
SELECT q.id, demo.line_number,
       (SELECT p.id FROM products p WHERE p.sku = demo.sku ORDER BY p.id LIMIT 1),
       demo.sku, demo.description, 'unidad', demo.quantity, demo.unit_price,
       ROUND(demo.quantity * demo.unit_price, 4),
       ROUND(demo.quantity * demo.unit_price * 0.18, 4),
       ROUND(demo.quantity * demo.unit_price * 1.18, 4)
FROM (
    SELECT 'COT-2026-0101' AS quotation_number, 1 AS line_number, 'CF258A' AS sku,
           'Tóner HP 58A (CF258A) Negro' AS description, 20.0000 AS quantity, 285.0000 AS unit_price
    UNION ALL SELECT 'COT-2026-0101', 2, 'CF259X', 'Tóner HP 59X (CF259X) Alto Rendimiento', 10.0000, 610.0000
    UNION ALL SELECT 'COT-2026-0102', 1, '450-G10', 'Laptop HP ProBook 450 G10', 8.0000, 3950.0000
    UNION ALL SELECT 'COT-2026-0102', 2, 'MON-24FHD', 'Monitor LED 24" Full HD', 8.0000, 520.0000
    UNION ALL SELECT 'COT-2026-0103', 1, 'M2040DN', 'Multifuncional Kyocera ECOSYS M2040dn', 5.0000, 2480.0000
    UNION ALL SELECT 'COT-2026-0104', 1, 'PI-65-4K', 'Pantalla interactiva 65" 4K', 3.0000, 7800.0000
    UNION ALL SELECT 'COT-2026-0105', 1, 'W1A53A', 'Impresora HP LaserJet Pro M404dn', 12.0000, 1290.0000
    UNION ALL SELECT 'COT-2026-0105', 2, 'SRV-INSTAL', 'Instalación y configuración', 12.0000, 85.0000
    UNION ALL SELECT 'COT-2026-0106', 1, 'CAM-IP-4MP', 'Cámara IP domo 4MP', 16.0000, 395.0000
    UNION ALL SELECT 'COT-2026-0106', 2, 'NVR-8CH', 'NVR 8 canales PoE', 2.0000, 1150.0000
    UNION ALL SELECT 'COT-2026-0106', 3, 'SRV-CABLE', 'Cableado estructurado', 1.0000, 2400.0000
    UNION ALL SELECT 'COT-2026-0107', 1, 'CAT-1000-24', 'Switch Cisco Catalyst 24 puertos', 4.0000, 890.0000
    UNION ALL SELECT 'COT-2026-0107', 2, 'RTR-CISCO-RV', 'Router empresarial Cisco', 2.0000, 640.0000
    UNION ALL SELECT 'COT-2026-0108', 1, 'SSD-512NVME', 'Disco SSD 512 GB NVMe', 40.0000, 42.5000
    UNION ALL SELECT 'COT-2026-0108', 2, 'RAM-16DDR4', 'Memoria RAM 16 GB DDR4', 40.0000, 38.9000
    UNION ALL SELECT 'COT-2026-0109', 1, 'E14-G5', 'Laptop Lenovo ThinkPad E14', 6.0000, 4350.0000
    UNION ALL SELECT 'COT-2026-0110', 1, '6R01646', 'TONER XEROX VERSANT 80/180 6R01646 BLACK', 2.0000, 156.1441
    UNION ALL SELECT 'COT-2026-0110', 2, '6R01647', 'TONER XEROX VERSANT 80/180 6R01647 CYAN', 2.0000, 189.4068
    UNION ALL SELECT 'COT-2026-0110', 3, '6R01649', 'TONER XEROX VERSANT 80/180 6R01649 YELLOW', 2.0000, 189.4068
    UNION ALL SELECT 'COT-2026-0110', 4, '6R01648', 'TONER XEROX VERSANT 80/180 6R01648 MAGENTA', 2.0000, 189.4068
    UNION ALL SELECT 'COT-2026-0111', 1, 'TK-3402', 'Tóner Kyocera TK-3402 Negro', 25.0000, 178.0000
    UNION ALL SELECT 'COT-2026-0112', 1, 'W1Y45A', 'Impresora HP Color LaserJet Pro M454dw', 6.0000, 2180.0000
    UNION ALL SELECT 'COT-2026-0112', 2, 'FUSOR-HP', 'Kit de fusor HP LaserJet', 6.0000, 430.0000
) AS demo
JOIN quotations q ON q.quotation_number = demo.quotation_number
WHERE NOT EXISTS (
    SELECT 1 FROM quotation_items existing
    WHERE existing.quotation_id = q.id AND existing.line_number = demo.line_number
);

-- Totales de cabecera recalculados desde las líneas ya insertadas.
UPDATE quotations q
SET q.subtotal_amount = COALESCE((SELECT SUM(i.line_subtotal) FROM quotation_items i WHERE i.quotation_id = q.id), 0),
    q.tax_amount      = COALESCE((SELECT SUM(i.line_tax)      FROM quotation_items i WHERE i.quotation_id = q.id), 0),
    q.total_amount    = COALESCE((SELECT SUM(i.line_total)    FROM quotation_items i WHERE i.quotation_id = q.id), 0)
WHERE q.notes LIKE '%[DEMO]%';

-- ----------------------------------------------------------------------------
-- 4. Facturas emitidas de las cotizaciones ganadas
-- ----------------------------------------------------------------------------
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
                      registered_by_worker_id, status, issue_date, due_date, currency_code,
                      exchange_rate_to_pen, includes_tax, tax_rate, subtotal_amount, tax_amount,
                      total_amount, notes)
SELECT UUID(), demo.invoice_number, 'INVOICE',
       q.customer_id, q.id, @accountant_id, demo.status,
       demo.issue_date, demo.due_date, q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount, demo.notes
FROM (
    SELECT 'F001-0801' AS invoice_number, 'COT-2026-0101' AS quotation_number, 'ISSUED' AS status,
           CURRENT_DATE - INTERVAL 14 DAY AS issue_date, CURRENT_DATE + INTERVAL 16 DAY AS due_date,
           '[DEMO] Factura de la OCAM 00123-2026 (MINSA). Entregada con acta firmada.' AS notes
    UNION ALL SELECT 'F001-0802', 'COT-2026-0105', 'ISSUED',
        CURRENT_DATE - INTERVAL 8 DAY, CURRENT_DATE + INTERVAL 22 DAY,
        '[DEMO] Factura de la orden de compra 4500123987 (MINEDU).'
    UNION ALL SELECT 'F001-0803', 'COT-2026-0112', 'ISSUED',
        CURRENT_DATE - INTERVAL 38 DAY, CURRENT_DATE - INTERVAL 8 DAY,
        '[DEMO] Factura del mes pasado (Marina de Guerra). Cobrada.'
) AS demo
JOIN quotations q ON q.quotation_number = demo.quotation_number
WHERE NOT EXISTS (
    SELECT 1 FROM invoices existing
    WHERE existing.invoice_type = 'INVOICE' AND existing.invoice_number = demo.invoice_number
);

INSERT INTO invoice_items (invoice_id, line_number, product_id, sku_snapshot, description_snapshot,
                           unit_snapshot, quantity, unit_price, line_subtotal, line_tax, line_total)
SELECT i.id, qi.line_number, qi.product_id, qi.sku_snapshot, qi.description_snapshot,
       qi.unit_snapshot, qi.quantity, qi.unit_price, qi.line_subtotal, qi.line_tax, qi.line_total
FROM invoices i
JOIN quotation_items qi ON qi.quotation_id = i.quotation_id
WHERE i.notes LIKE '%[DEMO]%'
  AND NOT EXISTS (
    SELECT 1 FROM invoice_items existing
    WHERE existing.invoice_id = i.id AND existing.line_number = qi.line_number
);

-- ----------------------------------------------------------------------------
-- 5. Precios de compra de referencia
--
-- Es lo que ve el vendedor al cotizar: cuánto costó la última vez y a quién.
-- ----------------------------------------------------------------------------
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
                                      currency_code, unit_price, includes_tax, tax_rate,
                                      exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, demo.supplier, demo.source, demo.currency_code, demo.unit_price,
       FALSE, 0.180000, demo.exchange_rate, TIMESTAMP(CURRENT_DATE - INTERVAL demo.days_ago DAY, '10:00:00'),
       demo.notes
FROM (
    SELECT 'CF258A' AS sku, 'DELTRON S.A.' AS supplier, 'FACTURA E001-45211' AS source,
           'PEN' AS currency_code, 198.0000 AS unit_price, NULL AS exchange_rate, 30 AS days_ago,
           '[DEMO] Precio de compra tomado de factura del proveedor.' AS notes
    UNION ALL SELECT 'CF259X', 'DELTRON S.A.', 'FACTURA E001-45211', 'PEN', 430.0000, NULL, 30,
        '[DEMO] Precio de compra tomado de factura del proveedor.'
    UNION ALL SELECT '450-G10', 'INGRAM MICRO PERU', 'FACTURA F520-9981', 'USD', 745.0000, 3.735000, 12,
        '[DEMO] Precio en dólares. Convertir con el tipo de cambio del día de la cotización.'
    UNION ALL SELECT 'M2040DN', 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA F001-3320', 'PEN', 1780.0000, NULL, 22,
        '[DEMO] Precio de lista del distribuidor autorizado.'
    UNION ALL SELECT 'W1A53A', 'DELTRON S.A.', 'FACTURA E001-46002', 'PEN', 935.0000, NULL, 9,
        '[DEMO] Última compra registrada.'
    UNION ALL SELECT 'CAM-IP-4MP', 'SECURITY IMPORT S.A.C.', 'COTIZACION 2026-118', 'PEN', 268.0000, NULL, 6,
        '[DEMO] Precio de proveedor, pendiente de confirmar con factura.'
    UNION ALL SELECT 'CAT-1000-24', 'INGRAM MICRO PERU', 'FACTURA F520-10034', 'USD', 612.0000, 3.720000, 31,
        '[DEMO] Equipo de red importado.'
    UNION ALL SELECT 'SSD-512NVME', 'TECNOMUNDO S.A.C.', 'FACTURA F002-7788', 'USD', 29.5000, 3.745000, 4,
        '[DEMO] Componente de rotación rápida.'
    UNION ALL SELECT 'TK-3402', 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA F001-3401', 'PEN', 121.0000, NULL, 2,
        '[DEMO] Precio actualizado esta semana.'
    UNION ALL SELECT 'E14-G5', 'INTCOMEX PERU', 'FACTURA F300-2214', 'USD', 1055.0000, 3.750000, 8,
        '[DEMO] Subió respecto de la compra anterior; por eso se anuló la COT-2026-0109.'
) AS demo
JOIN products p ON p.sku = demo.sku
WHERE NOT EXISTS (
    SELECT 1 FROM product_price_references existing
    WHERE existing.product_id = p.id AND existing.source_reference = demo.source
);

-- ----------------------------------------------------------------------------
-- 6. Tareas del día (las que no se marcan quedan pendientes y suben al tope)
-- ----------------------------------------------------------------------------
INSERT INTO tasks (public_id, title, description, assigned_to_worker_id, created_by_worker_id,
                   priority, status, due_at)
SELECT UUID(), demo.title, demo.description,
       CASE demo.owner WHEN 'SELLER' THEN @seller_id ELSE @accountant_id END,
       @seller_id, demo.priority, demo.status, demo.due_at
FROM (
    SELECT 'Revisar plazo de la cotización COT-2026-0102' AS title,
           '[DEMO] Vence mañana. Confirmar con la Marina si ya hay resultado.' AS description,
           'SELLER' AS owner, 'HIGH' AS priority, 'OPEN' AS status,
           TIMESTAMP(CURRENT_DATE, '17:00:00') AS due_at
    UNION ALL SELECT 'Subir el XML de la factura F001-0802',
        '[DEMO] Registrar la factura del MINEDU en el sistema.', 'ACCOUNTANT', 'MEDIUM', 'OPEN',
        TIMESTAMP(CURRENT_DATE, '18:00:00')
    UNION ALL SELECT 'Confirmar precio de compra del TK-3402',
        '[DEMO] Falta el precio para publicar la COT-2026-0111. Quedó sin marcar de ayer.',
        'SELLER', 'HIGH', 'OPEN', TIMESTAMP(CURRENT_DATE - INTERVAL 1 DAY, '17:00:00')
    UNION ALL SELECT 'Coordinar la entrega de la OCAM 00123-2026',
        '[DEMO] Plazo de entrega: 10 días desde la orden.', 'SELLER', 'MEDIUM', 'IN_PROGRESS',
        TIMESTAMP(CURRENT_DATE + INTERVAL 2 DAY, '12:00:00')
    UNION ALL SELECT 'Archivar el acta de conformidad de la Marina',
        '[DEMO] Acta firmada del mes pasado.', 'ACCOUNTANT', 'LOW', 'COMPLETED',
        TIMESTAMP(CURRENT_DATE - INTERVAL 5 DAY, '16:00:00')
) AS demo
WHERE NOT EXISTS (
    SELECT 1 FROM tasks existing WHERE existing.title = demo.title
);

UPDATE tasks SET completed_at = updated_at
WHERE status = 'COMPLETED' AND completed_at IS NULL AND description LIKE '%[DEMO]%';
