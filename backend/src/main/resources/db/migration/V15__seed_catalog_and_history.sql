-- ============================================================================
-- V15 · Catálogo, cartera e historial comercial ampliados
--
-- Esta migración carga el volumen de datos necesario para que el sistema se
-- pueda mostrar funcionando de verdad:
--
--   * 30 productos nuevos (incluidos los tóners Xerox de las facturas reales,
--     para que la importación de XML los reconozca).
--   * 12 entidades y empresas nuevas.
--   * Precio de compra de referencia para TODOS los productos del catálogo.
--   * Un historial de cotizaciones ganadas y perdidas repartido sobre los
--     últimos ocho meses, con el precio del competidor que se llevó la orden.
--   * Facturas emitidas de las cotizaciones ganadas.
--
-- Ese historial es lo que alimenta el panel "Historial de precios" que aparece
-- al cotizar: a cuánto se vendió antes, con qué precio nos ganaron y cuánto
-- costó la última vez.
--
-- Todo lleva la marca [DEMO] en sus notas. Para limpiar antes de entregar:
--
--   DELETE FROM invoice_items   WHERE invoice_id IN (SELECT id FROM invoices WHERE notes LIKE '%[DEMO]%');
--   DELETE FROM invoices        WHERE notes LIKE '%[DEMO]%';
--   DELETE FROM quotation_items WHERE quotation_id IN (SELECT id FROM quotations WHERE notes LIKE '%[DEMO]%');
--   DELETE FROM quotations      WHERE notes LIKE '%[DEMO]%';
--   DELETE FROM product_price_references WHERE notes LIKE '%[DEMO]%';
--   DELETE FROM customers       WHERE notes LIKE '%[DEMO]%';
--
-- Idempotente: cada INSERT comprueba antes que el registro no exista.
-- ============================================================================

SET @seller_id = (SELECT id FROM workers WHERE role_code = 'SELLER' ORDER BY id LIMIT 1);
SET @accountant_id = (SELECT id FROM workers WHERE role_code = 'ACCOUNTANT' ORDER BY id LIMIT 1);
SET @any_worker_id = (SELECT id FROM workers ORDER BY id LIMIT 1);
SET @seller_id = COALESCE(@seller_id, @any_worker_id);
SET @accountant_id = COALESCE(@accountant_id, @any_worker_id);

-- ----------------------------------------------------------------------------
-- 1. Productos nuevos
-- ----------------------------------------------------------------------------
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '6r01646', '6R01646', 'Tóner Xerox Versant 80/180 6R01646 Negro', 'Tóner Xerox Versant 80/180 6R01646 Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '6R01646');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '6r01647', '6R01647', 'Tóner Xerox Versant 80/180 6R01647 Cian', 'Tóner Xerox Versant 80/180 6R01647 Cian — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '6R01647');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '6r01648', '6R01648', 'Tóner Xerox Versant 80/180 6R01648 Magenta', 'Tóner Xerox Versant 80/180 6R01648 Magenta — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '6R01648');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '6r01649', '6R01649', 'Tóner Xerox Versant 80/180 6R01649 Amarillo', 'Tóner Xerox Versant 80/180 6R01649 Amarillo — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '6R01649');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '106r03623', '106R03623', 'Tóner Xerox WorkCentre 3335 Negro', 'Tóner Xerox WorkCentre 3335 Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '106R03623');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '013r00591', '013R00591', 'Cilindro Xerox WorkCentre 5325', 'Cilindro Xerox WorkCentre 5325 — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'xerox'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '013R00591');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'tk-5230k', 'TK-5230K', 'Tóner Kyocera TK-5230 Negro', 'Tóner Kyocera TK-5230 Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kyocera'), (SELECT id FROM categories WHERE slug = 'toner-kyocera'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'TK-5230K');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'tk-5230c', 'TK-5230C', 'Tóner Kyocera TK-5230 Cian', 'Tóner Kyocera TK-5230 Cian — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kyocera'), (SELECT id FROM categories WHERE slug = 'toner-kyocera'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'TK-5230C');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'tk-5230m', 'TK-5230M', 'Tóner Kyocera TK-5230 Magenta', 'Tóner Kyocera TK-5230 Magenta — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kyocera'), (SELECT id FROM categories WHERE slug = 'toner-kyocera'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'TK-5230M');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'tk-5230y', 'TK-5230Y', 'Tóner Kyocera TK-5230 Amarillo', 'Tóner Kyocera TK-5230 Amarillo — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kyocera'), (SELECT id FROM categories WHERE slug = 'toner-kyocera'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'TK-5230Y');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'w1106a', 'W1106A', 'Tóner HP 106A (W1106A) Negro', 'Tóner HP 106A (W1106A) Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'hp'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'W1106A');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'w1107a', 'W1107A', 'Tóner HP 107A (W1107A) Negro', 'Tóner HP 107A (W1107A) Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'hp'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'W1107A');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'cf410a', 'CF410A', 'Tóner HP 410A (CF410A) Negro', 'Tóner HP 410A (CF410A) Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'hp'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'CF410A');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'cf411a', 'CF411A', 'Tóner HP 410A (CF411A) Cian', 'Tóner HP 410A (CF411A) Cian — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'hp'), (SELECT id FROM categories WHERE slug = 'toner-hp'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'CF411A');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 't544120', 'T544120', 'Tinta Epson T544 Negro', 'Tinta Epson T544 Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'epson'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'T544120');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'tn-660', 'TN-660', 'Tóner Brother TN-660 Alto Rendimiento', 'Tóner Brother TN-660 Alto Rendimiento — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'brother'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'TN-660');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'dr-630', 'DR-630', 'Unidad de cilindro Brother DR-630', 'Unidad de cilindro Brother DR-630 — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'brother'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DR-630');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT '054h-bk', '054H-BK', 'Tóner Canon 054H Negro', 'Tóner Canon 054H Negro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'canon'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = '054H-BK');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'e15-g4', 'E15-G4', 'Laptop Lenovo ThinkPad E15 Gen 4', 'Laptop Lenovo ThinkPad E15 Gen 4 — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'lenovo'), (SELECT id FROM categories WHERE slug = 'equipos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'E15-G4');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'opt-7090', 'OPT-7090', 'Laptop Dell OptiPlex 7090 Micro', 'Laptop Dell OptiPlex 7090 Micro — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'dell'), (SELECT id FROM categories WHERE slug = 'equipos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'OPT-7090');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'prod-405g9', 'PROD-405G9', 'Desktop HP ProDesk 405 G9', 'Desktop HP ProDesk 405 G9 — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'hp'), (SELECT id FROM categories WHERE slug = 'equipos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'PROD-405G9');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'mon-27qhd', 'MON-27QHD', 'Monitor LED 27" QHD', 'Monitor LED 27" QHD — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'giova'), (SELECT id FROM categories WHERE slug = 'equipos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'MON-27QHD');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'ups-1500', 'UPS-1500', 'UPS 1500VA con regulador', 'UPS 1500VA con regulador — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'giova'), (SELECT id FROM categories WHERE slug = 'equipos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'UPS-1500');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'sw-2960x', 'SW-2960X', 'Switch Cisco Catalyst 2960-X 48 puertos', 'Switch Cisco Catalyst 2960-X 48 puertos — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'cisco'), (SELECT id FROM categories WHERE slug = 'redes'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SW-2960X');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'ap-wifi6', 'AP-WIFI6', 'Access Point WiFi 6 empresarial', 'Access Point WiFi 6 empresarial — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'cisco'), (SELECT id FROM categories WHERE slug = 'redes'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'AP-WIFI6');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'ssd-1tnvme', 'SSD-1TNVME', 'Disco SSD 1 TB NVMe', 'Disco SSD 1 TB NVMe — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kingston'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SSD-1TNVME');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'ram-32ddr4', 'RAM-32DDR4', 'Memoria RAM 32 GB DDR4', 'Memoria RAM 32 GB DDR4 — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'kingston'), (SELECT id FROM categories WHERE slug = 'repuestos'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RAM-32DDR4');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'cam-ip-8mp', 'CAM-IP-8MP', 'Cámara IP domo 8MP', 'Cámara IP domo 8MP — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'giova'), (SELECT id FROM categories WHERE slug = 'seguridad'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'CAM-IP-8MP');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'nvr-16ch', 'NVR-16CH', 'NVR 16 canales PoE', 'NVR 16 canales PoE — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'giova'), (SELECT id FROM categories WHERE slug = 'seguridad'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'NVR-16CH');
INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order, active)
SELECT 'srv-mant-anual', 'SRV-MANT-ANUAL', 'Mantenimiento preventivo anual', 'Mantenimiento preventivo anual — carga de datos de demostración.',
       (SELECT id FROM brands WHERE slug = 'giova'), (SELECT id FROM categories WHERE slug = 'servicios'),
       NULL, NULL, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SRV-MANT-ANUAL');

-- ----------------------------------------------------------------------------
-- 2. Entidades y empresas nuevas
-- ----------------------------------------------------------------------------
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131380951', 'SEGURO SOCIAL DE SALUD - ESSALUD', 'Central de Compras',
       'logistica@essalud.gob.pe', '(01) 265 6000', 'Av. Domingo Cueto 120, Jesús María, Lima', '[DEMO] Entidad del Estado. Alto volumen en tóner.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20131380951');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131371617', 'MINISTERIO DE ECONOMIA Y FINANZAS', 'Oficina de Abastecimiento',
       'abastecimiento@mef.gob.pe', '(01) 311 5930', 'Jr. Junín 319, Lima', '[DEMO] Entidad del Estado.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20131371617');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20131370645', 'MINISTERIO DEL INTERIOR', 'Dirección de Logística',
       'logistica@mininter.gob.pe', '(01) 418 4030', 'Plaza 30 de Agosto s/n, San Isidro, Lima', '[DEMO] Entidad del Estado.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20131370645');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20504743307', 'BANCO DE LA NACION', 'Gerencia de Logística',
       'compras@bn.com.pe', '(01) 519 2000', 'Av. Javier Prado Este 2499, San Borja, Lima', '[DEMO] Banca estatal.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20504743307');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20100017491', 'PONTIFICIA UNIVERSIDAD CATOLICA DEL PERU', 'Oficina de Abastecimiento',
       'abastecimiento@pucp.edu.pe', '(01) 626 2000', 'Av. Universitaria 1801, San Miguel, Lima', '[DEMO] Cliente privado educativo.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20100017491');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20154637035', 'UNIVERSIDAD NACIONAL MAYOR DE SAN MARCOS', 'Unidad de Logística',
       'logistica@unmsm.edu.pe', '(01) 619 7000', 'Av. Germán Amézaga 375, Lima', '[DEMO] Universidad pública.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20154637035');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20100128056', 'BACKUS Y JOHNSTON S.A.A.', 'Compras Corporativas',
       'proveedores@backus.pe', '(01) 311 3000', 'Av. Nicolás Ayllón 3986, Ate, Lima', '[DEMO] Cliente privado corporativo.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20100128056');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20331066703', 'CLINICA INTERNACIONAL S.A.', 'Jefatura de Compras',
       'compras@clinicainternacional.com.pe', '(01) 619 6161', 'Av. Guardia Civil 385, San Borja, Lima', '[DEMO] Cliente privado salud.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20331066703');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20382875799', 'GOBIERNO REGIONAL DE LA LIBERTAD', 'Sub Gerencia de Logística',
       'logistica@regionlalibertad.gob.pe', '(044) 604 000', 'Los Brillantes 650, Trujillo', '[DEMO] Entidad regional.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20382875799');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20147796254', 'MUNICIPALIDAD DISTRITAL DE MIRAFLORES', 'Subgerencia de Logística',
       'logistica@miraflores.gob.pe', '(01) 617 7300', 'Av. Larco 400, Miraflores, Lima', '[DEMO] Municipalidad.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20147796254');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20604011836', 'INVERSIONES TECNOLOGICAS DEL SUR S.A.C.', 'Gerencia Comercial',
       'ventas@intecsur.pe', '(054) 285 400', 'Av. Ejército 710, Arequipa', '[DEMO] Intermediario en el sur.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20604011836');
INSERT INTO customers (public_id, owner_worker_id, customer_type, document_type, document_number,
                       business_name, contact_name, email, phone, address, notes, active)
SELECT UUID(), @seller_id, 'COMPANY', 'RUC', '20512345678', 'SERVICIOS INFORMATICOS ANDINOS E.I.R.L.', 'Administración',
       'admin@siandinos.pe', '(084) 240 100', 'Av. La Cultura 1200, Cusco', '[DEMO] Intermediario en Cusco.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE document_type = 'RUC' AND document_number = '20512345678');

-- ----------------------------------------------------------------------------
-- 3. Precio de compra de referencia para cada producto
--
-- Es lo que el vendedor ve como costo al armar el precio. Sale de las facturas
-- que sube el contador; aquí se carga directamente.
-- ----------------------------------------------------------------------------
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E100-40000', 'PEN', 890.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 5 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '013R00591'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-40000');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E101-40137', 'PEN', 195.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 12 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '054H-BK'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-40137');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E102-40274', 'PEN', 310.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 19 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '106R03623'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-40274');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E103-40411', 'PEN', 620.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 26 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '1F3Y2A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-40411');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E104-40548', 'PEN', 1180.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 33 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '1MR78A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-40548');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E105-40685', 'PEN', 2780.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 40 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '450-G10'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-40685');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E106-40822', 'PEN', 520.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 47 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '6R01646'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-40822');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E107-40959', 'PEN', 640.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 54 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '6R01647'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-40959');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E108-41096', 'PEN', 640.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 61 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '6R01648'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-41096');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E100-41233', 'PEN', 640.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 68 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '6R01649'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-41233');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E101-41370', 'PEN', 395.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 75 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '7MD68A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-41370');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E102-41507', 'PEN', 890.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 82 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = '9YG09A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-41507');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E103-41644', 'PEN', 540.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 89 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'AP-AIO22'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-41644');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E104-41781', 'PEN', 780.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 96 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'AP-WIFI6'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-41781');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E105-41918', 'PEN', 740.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 103 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CA-BIO'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-41918');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E106-42055', 'PEN', 268.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 110 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CAM-IP-4MP'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-42055');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E107-42192', 'PEN', 620.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 117 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CAM-IP-8MP'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-42192');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E108-42329', 'PEN', 2280.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 124 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CAT-1000-24'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-42329');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E100-42466', 'PEN', 345.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 11 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CC364A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-42466');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E101-42603', 'PEN', 128.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 18 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CE285A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-42603');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E102-42740', 'PEN', 135.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 25 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF217A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-42740');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E103-42877', 'PEN', 198.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 32 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF258A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-42877');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E104-43014', 'PEN', 430.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 39 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF258X'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-43014');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E105-43151', 'PEN', 205.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 46 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF259A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-43151');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E106-43288', 'PEN', 455.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 53 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF259X'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-43288');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E107-43425', 'PEN', 168.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 60 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF280A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-43425');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E108-43562', 'PEN', 142.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 67 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF283A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-43562');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E100-43699', 'PEN', 385.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 74 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF410A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-43699');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E101-43836', 'PEN', 425.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 81 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'CF411A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-43836');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E102-43973', 'PEN', 215.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 88 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'DR-630'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-43973');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E103-44110', 'PEN', 3950.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 95 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'E14-G5'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-44110');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E104-44247', 'PEN', 3680.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 102 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'E15-G4'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-44247');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E105-44384', 'PEN', 340.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 109 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'FUSOR-HP'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-44384');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E106-44521', 'PEN', 4200.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 116 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'FW-PERIM'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-44521');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E107-44658', 'PEN', 810.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 123 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'L2540DW'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-44658');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E108-44795', 'PEN', 690.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 10 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'L3250'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-44795');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E100-44932', 'PEN', 3120.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 17 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'LAT-3540'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-44932');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E101-45069', 'PEN', 1780.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 24 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'M2040DN'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-45069');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E102-45206', 'PEN', 1980.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 31 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'M428FDW'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-45206');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E103-45343', 'PEN', 265.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 38 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'MB-H610M'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-45343');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E104-45480', 'PEN', 385.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 45 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'MON-24FHD'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-45480');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E105-45617', 'PEN', 690.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 52 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'MON-27QHD'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-45617');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E106-45754', 'PEN', 1850.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 59 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'NVR-16CH'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-45754');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E107-45891', 'PEN', 920.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 66 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'NVR-8CH'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-45891');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E108-46028', 'PEN', 3250.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 73 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'OPT-7090'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-46028');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E100-46165', 'PEN', 420.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 80 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'PA2001W'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-46165');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E101-46302', 'PEN', 6200.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 87 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'PI-65-4K'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-46302');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E102-46439', 'PEN', 2480.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 94 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'PROD-405G9'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-46439');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E103-46576', 'PEN', 148.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 101 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'RAM-16DDR4'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-46576');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E104-46713', 'PEN', 290.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 108 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'RAM-32DDR4'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-46713');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E105-46850', 'PEN', 1650.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 115 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'RTR-CISCO-RV'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-46850');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E106-46987', 'PEN', 1800.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 122 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'SRV-CABLE'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-46987');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E101-47535', 'PEN', 235.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 30 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'SSD-1TNVME'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-47535');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E102-47672', 'PEN', 112.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 37 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'SSD-512NVME'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-47672');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E103-47809', 'PEN', 2950.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 44 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'SW-2960X'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-47809');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E104-47946', 'PEN', 38.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 51 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'T544120'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-47946');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E105-48083', 'PEN', 108.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 58 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-1147'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-48083');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E106-48220', 'PEN', 112.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 65 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-1160'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-48220');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E107-48357', 'PEN', 115.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 72 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-1175'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-48357');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E108-48494', 'PEN', 88.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 79 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-1248'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-48494');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E100-48631', 'PEN', 168.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 86 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-3182'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-48631');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E101-48768', 'PEN', 121.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 93 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-3402'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-48768');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E102-48905', 'PEN', 148.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 100 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5230C'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-48905');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E103-49042', 'PEN', 132.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 107 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5230K'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-49042');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E104-49179', 'PEN', 148.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 114 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5230M'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-49179');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E105-49316', 'PEN', 148.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 121 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5230Y'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-49316');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E106-49453', 'PEN', 158.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 8 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5242C'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-49453');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E107-49590', 'PEN', 142.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 15 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-5242K'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-49590');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E108-49727', 'PEN', 225.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 22 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-6115'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-49727');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E100-49864', 'PEN', 410.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 29 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TK-8345K'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-49864');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E101-50001', 'PEN', 118.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 36 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'TN-660'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E101-50001');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E102-50138', 'PEN', 520.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 43 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'UPS-1500'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E102-50138');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'XEROX DEL PERU S.A.', 'FACTURA E103-50275', 'PEN', 2150.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 50 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'V30A-24'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E103-50275');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'MAXIMA INTERNACIONAL S.A.', 'FACTURA E104-50412', 'PEN', 96.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 57 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1105A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E104-50412');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'DELTRON S.A.', 'FACTURA E105-50549', 'PEN', 98.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 64 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1106A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E105-50549');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INGRAM MICRO PERU', 'FACTURA E106-50686', 'PEN', 92.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 71 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1107A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E106-50686');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'INTCOMEX PERU', 'FACTURA E107-50823', 'PEN', 1950.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 78 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1A30A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E107-50823');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'TECNOMUNDO S.A.C.', 'FACTURA E108-50960', 'PEN', 935.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 85 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1A53A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E108-50960');
INSERT INTO product_price_references (product_id, recorded_by_worker_id, supplier_name, source_reference,
        currency_code, unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
SELECT p.id, @accountant_id, 'KYOCERA DOCUMENT SOLUTIONS', 'FACTURA E100-51097', 'PEN', 1720.0000, FALSE, 0.180000, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 92 DAY, '10:00:00'),
       '[DEMO] Costo de referencia cargado con los datos de prueba.'
FROM products p WHERE p.sku = 'W1Y45A'
  AND NOT EXISTS (SELECT 1 FROM product_price_references r WHERE r.product_id = p.id AND r.source_reference = 'FACTURA E100-51097');

-- ----------------------------------------------------------------------------
-- 4. Historial de cotizaciones
--
-- 56 procesos repartidos sobre los últimos ocho meses. Cada uno con su
-- resultado real y, cuando se perdió, el precio con el que nos ganaron: eso es
-- lo que se ve después al cotizar el mismo producto.
-- ----------------------------------------------------------------------------
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0201', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 238 DAY, '16:00:00'),
       5, 0, 'OCAM 00100-2026', DATE(CURRENT_DATE - INTERVAL 236 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 240 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 237 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131380951'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0201')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 1072.4500, NULL, 890.0000,
       ROUND(2 * 1072.4500, 4), ROUND(2 * 1072.4500 * 0.18, 4), ROUND(2 * 1072.4500 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '013R00591'
WHERE q.quotation_number = 'COT-2025-0201'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0202', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 234 DAY, '16:00:00'),
       7, 6, 'OCAM 00107-2026', DATE(CURRENT_DATE - INTERVAL 232 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 236 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 233 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131371617'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0202')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 3.0000, 3472.9984, 3611.9183, 2780.0000,
       ROUND(3 * 3472.9984, 4), ROUND(3 * 3472.9984 * 0.18, 4), ROUND(3 * 3472.9984 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '450-G10'
WHERE q.quotation_number = 'COT-2025-0202'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 4.0000, 765.7000, 807.8135, 620.0000,
       ROUND(4 * 765.7000, 4), ROUND(4 * 765.7000 * 0.18, 4), ROUND(4 * 765.7000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CAM-IP-8MP'
WHERE q.quotation_number = 'COT-2025-0202'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0203', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 230 DAY, '16:00:00'),
       10, 12, 'OCAM 00114-2026', DATE(CURRENT_DATE - INTERVAL 228 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 232 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 229 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131370645'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0203')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 4.0000, 499.5328, 519.5141, 395.0000,
       ROUND(4 * 499.5328, 4), ROUND(4 * 499.5328 * 0.18, 4), ROUND(4 * 499.5328 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '7MD68A'
WHERE q.quotation_number = 'COT-2025-0203'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 5.0000, 253.4400, NULL, 198.0000,
       ROUND(5 * 253.4400, 4), ROUND(5 * 253.4400 * 0.18, 4), ROUND(5 * 253.4400 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF258A'
WHERE q.quotation_number = 'COT-2025-0203'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 6.0000, 440.4224, 471.2520, 340.0000,
       ROUND(6 * 440.4224, 4), ROUND(6 * 440.4224 * 0.18, 4), ROUND(6 * 440.4224 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'FUSOR-HP'
WHERE q.quotation_number = 'COT-2025-0203'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0204', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 226 DAY, '16:00:00'),
       12, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 228 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 213 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20504743307'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0204')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 5.0000, 343.0400, NULL, 268.0000,
       ROUND(5 * 343.0400, 4), ROUND(5 * 343.0400 * 0.18, 4), ROUND(5 * 343.0400 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CAM-IP-4MP'
WHERE q.quotation_number = 'COT-2025-0204'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0205', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 222 DAY, '16:00:00'),
       15, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 224 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 209 DAY, 'USD', 3.748000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100017491'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0205')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 6.0000, 47.0090, 48.8894, 36.2903,
       ROUND(6 * 47.0090, 4), ROUND(6 * 47.0090 * 0.18, 4), ROUND(6 * 47.0090 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF217A'
WHERE q.quotation_number = 'COT-2025-0205'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 8.0000, 1195.3273, 1261.0703, 989.2473,
       ROUND(8 * 1195.3273, 4), ROUND(8 * 1195.3273 * 0.18, 4), ROUND(8 * 1195.3273 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'E15-G4'
WHERE q.quotation_number = 'COT-2025-0205'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0206', c.id, @seller_id, 'ACCEPTED', 'INDIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 218 DAY, '16:00:00'),
       20, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 220 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 205 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20154637035'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0206')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 8.0000, 220.2010, 229.0090, 168.0000,
       ROUND(8 * 220.2010, 4), ROUND(8 * 220.2010 * 0.18, 4), ROUND(8 * 220.2010 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF280A'
WHERE q.quotation_number = 'COT-2025-0206'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 10.0000, 4137.3696, NULL, 3120.0000,
       ROUND(10 * 4137.3696, 4), ROUND(10 * 4137.3696 * 0.18, 4), ROUND(10 * 4137.3696 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'LAT-3540'
WHERE q.quotation_number = 'COT-2025-0206'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 12.0000, 2844.9568, 3044.1038, 2480.0000,
       ROUND(12 * 2844.9568, 4), ROUND(12 * 2844.9568 * 0.18, 4), ROUND(12 * 2844.9568 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'PROD-405G9'
WHERE q.quotation_number = 'COT-2025-0206'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0207', c.id, @seller_id, 'ACCEPTED', 'PRIVADA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 214 DAY, '16:00:00'),
       25, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 216 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 201 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100128056'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0207')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 10.0000, 5238.0160, NULL, 3950.0000,
       ROUND(10 * 5238.0160, 4), ROUND(10 * 5238.0160 * 0.18, 4), ROUND(10 * 5238.0160 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'E14-G5'
WHERE q.quotation_number = 'COT-2025-0207'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0208', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 210 DAY, '16:00:00'),
       5, 6, 'OCAM 00149-2026', DATE(CURRENT_DATE - INTERVAL 208 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 212 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 209 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20331066703'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0208')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 12.0000, 851.4048, 885.4610, 690.0000,
       ROUND(12 * 851.4048, 4), ROUND(12 * 851.4048 * 0.18, 4), ROUND(12 * 851.4048 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'L3250'
WHERE q.quotation_number = 'COT-2025-0208'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 15.0000, 7745.5360, 8171.5405, 6200.0000,
       ROUND(15 * 7745.5360, 4), ROUND(15 * 7745.5360 * 0.18, 4), ROUND(15 * 7745.5360 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'PI-65-4K'
WHERE q.quotation_number = 'COT-2025-0208'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0209', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 206 DAY, '16:00:00'),
       7, 12, 'OCAM 00156-2026', DATE(CURRENT_DATE - INTERVAL 204 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 208 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 205 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20382875799'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0209')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 15.0000, 480.9728, 500.2117, 385.0000,
       ROUND(15 * 480.9728, 4), ROUND(15 * 480.9728 * 0.18, 4), ROUND(15 * 480.9728 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MON-24FHD'
WHERE q.quotation_number = 'COT-2025-0209'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 20.0000, 2276.3520, NULL, 1800.0000,
       ROUND(20 * 2276.3520, 4), ROUND(20 * 2276.3520 * 0.18, 4), ROUND(20 * 2276.3520 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SRV-CABLE'
WHERE q.quotation_number = 'COT-2025-0209'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 25.0000, 112.6400, 120.5248, 88.0000,
       ROUND(25 * 112.6400, 4), ROUND(25 * 112.6400 * 0.18, 4), ROUND(25 * 112.6400 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1248'
WHERE q.quotation_number = 'COT-2025-0209'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0210', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 202 DAY, '16:00:00'),
       10, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 204 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 201 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20147796254'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0210')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 20.0000, 531.1488, 467.4109, 420.0000,
       ROUND(20 * 531.1488, 4), ROUND(20 * 531.1488 * 0.18, 4), ROUND(20 * 531.1488 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'PA2001W'
WHERE q.quotation_number = 'COT-2025-0210'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0211', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'La entidad seleccionó a otro proveedor con precio menor.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 198 DAY, '16:00:00'),
       12, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 200 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 185 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20604011836'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0211')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 25.0000, 2112.0000, 1858.5600, 1650.0000,
       ROUND(25 * 2112.0000, 4), ROUND(25 * 2112.0000 * 0.18, 4), ROUND(25 * 2112.0000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'RTR-CISCO-RV'
WHERE q.quotation_number = 'COT-2025-0211'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 2.0000, 148.9664, 134.0698, 115.0000,
       ROUND(2 * 148.9664, 4), ROUND(2 * 148.9664 * 0.18, 4), ROUND(2 * 148.9664 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1175'
WHERE q.quotation_number = 'COT-2025-0211'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0212', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'Nos ganaron por precio: la diferencia fue de menos de 3 % en el total.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 194 DAY, '16:00:00'),
       15, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 196 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 181 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20512345678'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0212')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 304.4096, 267.8804, 235.0000,
       ROUND(2 * 304.4096, 4), ROUND(2 * 304.4096 * 0.18, 4), ROUND(2 * 304.4096 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SSD-1TNVME'
WHERE q.quotation_number = 'COT-2025-0212'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 3.0000, 178.4218, 160.5796, 132.0000,
       ROUND(3 * 178.4218, 4), ROUND(3 * 178.4218 * 0.18, 4), ROUND(3 * 178.4218 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5230K'
WHERE q.quotation_number = 'COT-2025-0212'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 4.0000, 136.0475, 125.1637, 98.0000,
       ROUND(4 * 136.0475, 4), ROUND(4 * 136.0475 * 0.18, 4), ROUND(4 * 136.0475 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1106A'
WHERE q.quotation_number = 'COT-2025-0212'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0213', c.id, @seller_id, 'REJECTED', 'INDIRECTA', 'NO_SELECCIONADA', 'El competidor ofreció el mismo producto con plazo de entrega más corto.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 190 DAY, '16:00:00'),
       20, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 192 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 177 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131373237'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0213')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 3.0000, 146.8006, 129.1845, 112.0000,
       ROUND(3 * 146.8006, 4), ROUND(3 * 146.8006 * 0.18, 4), ROUND(3 * 146.8006 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1160'
WHERE q.quotation_number = 'COT-2025-0213'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2025-0214', c.id, @seller_id, 'REJECTED', 'PRIVADA', 'NO_SELECCIONADA', 'Perdimos por precio unitario en las líneas de mayor cantidad.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 186 DAY, '16:00:00'),
       25, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 188 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 173 DAY, 'USD', 3.772000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20154995514'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2025-0214')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 4.0000, 54.4067, 47.8779, 39.7849,
       ROUND(4 * 54.4067, 4), ROUND(4 * 54.4067 * 0.18, 4), ROUND(4 * 54.4067 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5230C'
WHERE q.quotation_number = 'COT-2025-0214'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 5.0000, 31.8431, 28.6588, 25.8065,
       ROUND(5 * 31.8431, 4), ROUND(5 * 31.8431 * 0.18, 4), ROUND(5 * 31.8431 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1105A'
WHERE q.quotation_number = 'COT-2025-0214'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0215', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 182 DAY, '16:00:00'),
       5, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 184 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 181 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131379944'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0215')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 5.0000, 175.2166, 154.1906, 142.0000,
       ROUND(5 * 175.2166, 4), ROUND(5 * 175.2166 * 0.18, 4), ROUND(5 * 175.2166 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5242K'
WHERE q.quotation_number = 'COT-2026-0215'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 6.0000, 2148.7616, 1933.8854, 1720.0000,
       ROUND(6 * 2148.7616, 4), ROUND(6 * 2148.7616 * 0.18, 4), ROUND(6 * 2148.7616 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1Y45A'
WHERE q.quotation_number = 'COT-2026-0215'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 8.0000, 499.5328, 459.5702, 395.0000,
       ROUND(8 * 499.5328, 4), ROUND(8 * 499.5328 * 0.18, 4), ROUND(8 * 499.5328 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '7MD68A'
WHERE q.quotation_number = 'COT-2026-0215'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0216', c.id, @seller_id, 'EXPIRED', 'PERU_COMPRAS', 'DESIERTA', 'El proceso quedó desierto: ninguna oferta cumplió las especificaciones técnicas.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 178 DAY, '16:00:00'),
       7, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 180 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 177 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131312955'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0216')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 6.0000, 2685.9520, NULL, 2150.0000,
       ROUND(6 * 2685.9520, 4), ROUND(6 * 2685.9520 * 0.18, 4), ROUND(6 * 2685.9520 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'V30A-24'
WHERE q.quotation_number = 'COT-2026-0216'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0217', c.id, @seller_id, 'EXPIRED', 'PERU_COMPRAS', 'DESIERTA', 'La entidad no adjudicó a ningún proveedor dentro del plazo.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 174 DAY, '16:00:00'),
       10, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 176 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 173 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131367938'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0217')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 8.0000, 1182.4384, NULL, 935.0000,
       ROUND(8 * 1182.4384, 4), ROUND(8 * 1182.4384 * 0.18, 4), ROUND(8 * 1182.4384 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1A53A'
WHERE q.quotation_number = 'COT-2026-0217'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 10.0000, 819.2000, NULL, 640.0000,
       ROUND(10 * 819.2000, 4), ROUND(10 * 819.2000 * 0.18, 4), ROUND(10 * 819.2000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '6R01649'
WHERE q.quotation_number = 'COT-2026-0217'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0218', c.id, @seller_id, 'CANCELLED', 'DIRECTA', 'ANULADA', 'Nos retiramos: la entidad cambió las especificaciones después de publicar.', 'NOSOTROS',
       TIMESTAMP(CURRENT_DATE - INTERVAL 170 DAY, '16:00:00'),
       12, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 172 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 157 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131257750'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0218')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 10.0000, 793.6000, NULL, 620.0000,
       ROUND(10 * 793.6000, 4), ROUND(10 * 793.6000 * 0.18, 4), ROUND(10 * 793.6000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '1F3Y2A'
WHERE q.quotation_number = 'COT-2026-0218'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 12.0000, 958.5664, NULL, 740.0000,
       ROUND(12 * 958.5664, 4), ROUND(12 * 958.5664 * 0.18, 4), ROUND(12 * 958.5664 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CA-BIO'
WHERE q.quotation_number = 'COT-2026-0218'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 15.0000, 220.2010, NULL, 168.0000,
       ROUND(15 * 220.2010, 4), ROUND(15 * 220.2010 * 0.18, 4), ROUND(15 * 220.2010 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF280A'
WHERE q.quotation_number = 'COT-2026-0218'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0219', c.id, @seller_id, 'CANCELLED', 'DIRECTA', 'ANULADA', 'El proveedor subió el precio de compra y el margen quedaba negativo.', 'NOSOTROS',
       TIMESTAMP(CURRENT_DATE - INTERVAL 166 DAY, '16:00:00'),
       15, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 168 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 153 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20297868790'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0219')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 12.0000, 829.0304, NULL, 640.0000,
       ROUND(12 * 829.0304, 4), ROUND(12 * 829.0304 * 0.18, 4), ROUND(12 * 829.0304 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '6R01648'
WHERE q.quotation_number = 'COT-2026-0219'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0220', c.id, @seller_id, 'ISSUED', 'INDIRECTA', 'PENDIENTE', NULL, NULL,
       NULL,
       20, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 2 DAY, '09:30:00'),
       CURRENT_DATE + INTERVAL 10 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100047218'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0220')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 15.0000, 974.4384, NULL, 780.0000,
       ROUND(15 * 974.4384, 4), ROUND(15 * 974.4384 * 0.18, 4), ROUND(15 * 974.4384 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'AP-WIFI6'
WHERE q.quotation_number = 'COT-2026-0220'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 20.0000, 603.3664, NULL, 455.0000,
       ROUND(20 * 603.3664, 4), ROUND(20 * 603.3664 * 0.18, 4), ROUND(20 * 603.3664 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF259X'
WHERE q.quotation_number = 'COT-2026-0220'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0221', c.id, @seller_id, 'ACCEPTED', 'PRIVADA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 158 DAY, '16:00:00'),
       25, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 160 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 145 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20601030013'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0221')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 20.0000, 457.4976, 475.7975, 345.0000,
       ROUND(20 * 457.4976, 4), ROUND(20 * 457.4976 * 0.18, 4), ROUND(20 * 457.4976 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CC364A'
WHERE q.quotation_number = 'COT-2026-0221'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 25.0000, 269.4380, NULL, 215.0000,
       ROUND(25 * 269.4380, 4), ROUND(25 * 269.4380 * 0.18, 4), ROUND(25 * 269.4380 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'DR-630'
WHERE q.quotation_number = 'COT-2026-0221'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 2.0000, 480.9728, 514.6409, 385.0000,
       ROUND(2 * 480.9728, 4), ROUND(2 * 480.9728 * 0.18, 4), ROUND(2 * 480.9728 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MON-24FHD'
WHERE q.quotation_number = 'COT-2026-0221'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0222', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 154 DAY, '16:00:00'),
       5, 12, 'OCAM 00247-2026', DATE(CURRENT_DATE - INTERVAL 152 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 156 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 153 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20553708137'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0222')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 25.0000, 252.9536, NULL, 205.0000,
       ROUND(25 * 252.9536, 4), ROUND(25 * 252.9536 * 0.18, 4), ROUND(25 * 252.9536 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF259A'
WHERE q.quotation_number = 'COT-2026-0222'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0223', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 150 DAY, '16:00:00'),
       7, 24, 'OCAM 00254-2026', DATE(CURRENT_DATE - INTERVAL 148 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 152 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 149 DAY, 'USD', 3.712000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131380951'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0223')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 141.6118, 147.2763, 114.2473,
       ROUND(2 * 141.6118, 4), ROUND(2 * 141.6118 * 0.18, 4), ROUND(2 * 141.6118 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF411A'
WHERE q.quotation_number = 'COT-2026-0223'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 3.0000, 90.0886, 95.0435, 71.2366,
       ROUND(3 * 90.0886, 4), ROUND(3 * 90.0886 * 0.18, 4), ROUND(3 * 90.0886 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MB-H610M'
WHERE q.quotation_number = 'COT-2026-0223'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0224', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 146 DAY, '16:00:00'),
       10, 36, 'OCAM 00261-2026', DATE(CURRENT_DATE - INTERVAL 144 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 148 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 145 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131371617'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0224')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 3.0000, 5311.4880, 5523.9475, 4200.0000,
       ROUND(3 * 5311.4880, 4), ROUND(3 * 5311.4880 * 0.18, 4), ROUND(3 * 5311.4880 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'FW-PERIM'
WHERE q.quotation_number = 'COT-2026-0224'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 4.0000, 3835.0000, NULL, 3250.0000,
       ROUND(4 * 3835.0000, 4), ROUND(4 * 3835.0000 * 0.18, 4), ROUND(4 * 3835.0000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'OPT-7090'
WHERE q.quotation_number = 'COT-2026-0224'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 5.0000, 304.4096, 325.7183, 235.0000,
       ROUND(5 * 304.4096, 4), ROUND(5 * 304.4096 * 0.18, 4), ROUND(5 * 304.4096 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SSD-1TNVME'
WHERE q.quotation_number = 'COT-2026-0224'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0225', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 142 DAY, '16:00:00'),
       12, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 144 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 129 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131370645'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0225')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 4.0000, 2534.4000, NULL, 1980.0000,
       ROUND(4 * 2534.4000, 4), ROUND(4 * 2534.4000 * 0.18, 4), ROUND(4 * 2534.4000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'M428FDW'
WHERE q.quotation_number = 'COT-2026-0225'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0226', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 138 DAY, '16:00:00'),
       15, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 140 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 125 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20504743307'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0226')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 5.0000, 1191.7312, 1239.4004, 920.0000,
       ROUND(5 * 1191.7312, 4), ROUND(5 * 1191.7312 * 0.18, 4), ROUND(5 * 1191.7312 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'NVR-8CH'
WHERE q.quotation_number = 'COT-2026-0226'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 6.0000, 222.7200, 234.9696, 150.0000,
       ROUND(6 * 222.7200, 4), ROUND(6 * 222.7200 * 0.18, 4), ROUND(6 * 222.7200 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SRV-SOPORTE'
WHERE q.quotation_number = 'COT-2026-0226'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0227', c.id, @seller_id, 'ACCEPTED', 'INDIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 134 DAY, '16:00:00'),
       20, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 136 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 121 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100017491'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0227')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 6.0000, 193.9866, 201.7461, 148.0000,
       ROUND(6 * 193.9866, 4), ROUND(6 * 193.9866 * 0.18, 4), ROUND(6 * 193.9866 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'RAM-16DDR4'
WHERE q.quotation_number = 'COT-2026-0227'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 8.0000, 143.2166, NULL, 108.0000,
       ROUND(8 * 143.2166, 4), ROUND(8 * 143.2166 * 0.18, 4), ROUND(8 * 143.2166 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1147'
WHERE q.quotation_number = 'COT-2026-0227'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 10.0000, 175.2166, 187.4818, 142.0000,
       ROUND(10 * 175.2166, 4), ROUND(10 * 175.2166 * 0.18, 4), ROUND(10 * 175.2166 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5242K'
WHERE q.quotation_number = 'COT-2026-0227'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0228', c.id, @seller_id, 'ACCEPTED', 'PRIVADA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 130 DAY, '16:00:00'),
       25, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 132 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 117 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20154637035'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0228')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 8.0000, 225.3300, NULL, 150.0000,
       ROUND(8 * 225.3300, 4), ROUND(8 * 225.3300 * 0.18, 4), ROUND(8 * 225.3300 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SRV-MANT-ANUAL'
WHERE q.quotation_number = 'COT-2026-0228'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0229', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 126 DAY, '16:00:00'),
       5, 24, 'OCAM 00296-2026', DATE(CURRENT_DATE - INTERVAL 124 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 128 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 125 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20100128056'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0229')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 10.0000, 50.5522, 52.5743, 38.0000,
       ROUND(10 * 50.5522, 4), ROUND(10 * 50.5522 * 0.18, 4), ROUND(10 * 50.5522 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'T544120'
WHERE q.quotation_number = 'COT-2026-0229'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 12.0000, 197.3862, 208.2424, 158.0000,
       ROUND(12 * 197.3862, 4), ROUND(12 * 197.3862 * 0.18, 4), ROUND(12 * 197.3862 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5242C'
WHERE q.quotation_number = 'COT-2026-0229'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0230', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 122 DAY, '16:00:00'),
       7, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 124 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 121 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20331066703'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0230')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 12.0000, 209.8790, 184.6935, 168.0000,
       ROUND(12 * 209.8790, 4), ROUND(12 * 209.8790 * 0.18, 4), ROUND(12 * 209.8790 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-3182'
WHERE q.quotation_number = 'COT-2026-0230'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 15.0000, 647.3376, 582.6038, 520.0000,
       ROUND(15 * 647.3376, 4), ROUND(15 * 647.3376 * 0.18, 4), ROUND(15 * 647.3376 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'UPS-1500'
WHERE q.quotation_number = 'COT-2026-0230'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 20.0000, 793.6000, 730.1120, 620.0000,
       ROUND(20 * 793.6000, 4), ROUND(20 * 793.6000 * 0.18, 4), ROUND(20 * 793.6000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '1F3Y2A'
WHERE q.quotation_number = 'COT-2026-0230'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0231', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad seleccionó a otro proveedor con precio menor.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 118 DAY, '16:00:00'),
       10, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 120 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 117 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20382875799'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0231')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 15.0000, 193.0157, 169.8538, 148.0000,
       ROUND(15 * 193.0157, 4), ROUND(15 * 193.0157 * 0.18, 4), ROUND(15 * 193.0157 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5230Y'
WHERE q.quotation_number = 'COT-2026-0231'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0232', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'Nos ganaron por precio: la diferencia fue de menos de 3 % en el total.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 114 DAY, '16:00:00'),
       12, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 116 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 101 DAY, 'USD', 3.736000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20147796254'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0232')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 20.0000, 42.1882, 37.1256, 31.7204,
       ROUND(20 * 42.1882, 4), ROUND(20 * 42.1882 * 0.18, 4), ROUND(20 * 42.1882 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TN-660'
WHERE q.quotation_number = 'COT-2026-0232'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 25.0000, 109.6333, 98.6700, 83.3333,
       ROUND(25 * 109.6333, 4), ROUND(25 * 109.6333 * 0.18, 4), ROUND(25 * 109.6333 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '106R03623'
WHERE q.quotation_number = 'COT-2026-0232'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0233', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'El competidor ofreció el mismo producto con plazo de entrega más corto.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 110 DAY, '16:00:00'),
       15, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 112 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 97 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20604011836'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0233')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 25.0000, 124.7594, 109.7883, 92.0000,
       ROUND(25 * 124.7594, 4), ROUND(25 * 124.7594 * 0.18, 4), ROUND(25 * 124.7594 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1107A'
WHERE q.quotation_number = 'COT-2026-0233'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 2.0000, 838.8608, 754.9747, 640.0000,
       ROUND(2 * 838.8608, 4), ROUND(2 * 838.8608 * 0.18, 4), ROUND(2 * 838.8608 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '6R01647'
WHERE q.quotation_number = 'COT-2026-0233'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 3.0000, 457.4976, 420.8978, 345.0000,
       ROUND(3 * 457.4976, 4), ROUND(3 * 457.4976 * 0.18, 4), ROUND(3 * 457.4976 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CC364A'
WHERE q.quotation_number = 'COT-2026-0233'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0234', c.id, @seller_id, 'REJECTED', 'INDIRECTA', 'NO_SELECCIONADA', 'Perdimos por precio unitario en las líneas de mayor cantidad.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 106 DAY, '16:00:00'),
       20, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 108 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 93 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20512345678'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0234')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 261.5808, 230.1911, 195.0000,
       ROUND(2 * 261.5808, 4), ROUND(2 * 261.5808 * 0.18, 4), ROUND(2 * 261.5808 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '054H-BK'
WHERE q.quotation_number = 'COT-2026-0234'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0235', c.id, @seller_id, 'REJECTED', 'PRIVADA', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 102 DAY, '16:00:00'),
       25, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 104 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 89 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131373237'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0235')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 3.0000, 689.5616, 606.8142, 520.0000,
       ROUND(3 * 689.5616, 4), ROUND(3 * 689.5616 * 0.18, 4), ROUND(3 * 689.5616 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '6R01646'
WHERE q.quotation_number = 'COT-2026-0235'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 4.0000, 2813.3376, 2532.0038, 2280.0000,
       ROUND(4 * 2813.3376, 4), ROUND(4 * 2813.3376 * 0.18, 4), ROUND(4 * 2813.3376 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CAT-1000-24'
WHERE q.quotation_number = 'COT-2026-0235'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0236', c.id, @seller_id, 'EXPIRED', 'PERU_COMPRAS', 'DESIERTA', 'El proceso quedó desierto: ninguna oferta cumplió las especificaciones técnicas.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 98 DAY, '16:00:00'),
       5, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 100 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 97 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20154995514'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0236')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 4.0000, 1098.1888, NULL, 890.0000,
       ROUND(4 * 1098.1888, 4), ROUND(4 * 1098.1888 * 0.18, 4), ROUND(4 * 1098.1888 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '9YG09A'
WHERE q.quotation_number = 'COT-2026-0236'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 5.0000, 537.1904, NULL, 430.0000,
       ROUND(5 * 537.1904, 4), ROUND(5 * 537.1904 * 0.18, 4), ROUND(5 * 537.1904 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF258X'
WHERE q.quotation_number = 'COT-2026-0236'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 6.0000, 5311.4880, NULL, 4200.0000,
       ROUND(6 * 5311.4880, 4), ROUND(6 * 5311.4880 * 0.18, 4), ROUND(6 * 5311.4880 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'FW-PERIM'
WHERE q.quotation_number = 'COT-2026-0236'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0237', c.id, @seller_id, 'EXPIRED', 'PERU_COMPRAS', 'DESIERTA', 'La entidad no adjudicó a ningún proveedor dentro del plazo.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 94 DAY, '16:00:00'),
       7, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 96 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 93 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131379944'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0237')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 5.0000, 756.4000, NULL, 620.0000,
       ROUND(5 * 756.4000, 4), ROUND(5 * 756.4000 * 0.18, 4), ROUND(5 * 756.4000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CAM-IP-8MP'
WHERE q.quotation_number = 'COT-2026-0237'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0238', c.id, @seller_id, 'CANCELLED', 'PERU_COMPRAS', 'ANULADA', 'No había stock disponible para cumplir el plazo de entrega ofrecido.', 'NOSOTROS',
       TIMESTAMP(CURRENT_DATE - INTERVAL 90 DAY, '16:00:00'),
       10, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 92 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 89 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131312955'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0238')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 6.0000, 250.3987, NULL, 198.0000,
       ROUND(6 * 250.3987, 4), ROUND(6 * 250.3987 * 0.18, 4), ROUND(6 * 250.3987 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF258A'
WHERE q.quotation_number = 'COT-2026-0238'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 8.0000, 435.2000, NULL, 340.0000,
       ROUND(8 * 435.2000, 4), ROUND(8 * 435.2000 * 0.18, 4), ROUND(8 * 435.2000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'FUSOR-HP'
WHERE q.quotation_number = 'COT-2026-0238'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0239', c.id, @seller_id, 'CANCELLED', 'DIRECTA', 'ANULADA', 'Nos retiramos: la entidad cambió las especificaciones después de publicar.', 'NOSOTROS',
       TIMESTAMP(CURRENT_DATE - INTERVAL 86 DAY, '16:00:00'),
       12, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 88 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 73 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131367938'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0239')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 8.0000, 181.7600, NULL, 142.0000,
       ROUND(8 * 181.7600, 4), ROUND(8 * 181.7600 * 0.18, 4), ROUND(8 * 181.7600 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF283A'
WHERE q.quotation_number = 'COT-2026-0239'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 10.0000, 2305.7408, NULL, 1780.0000,
       ROUND(10 * 2305.7408, 4), ROUND(10 * 2305.7408 * 0.18, 4), ROUND(10 * 2305.7408 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'M2040DN'
WHERE q.quotation_number = 'COT-2026-0239'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 12.0000, 193.9866, NULL, 148.0000,
       ROUND(12 * 193.9866, 4), ROUND(12 * 193.9866 * 0.18, 4), ROUND(12 * 193.9866 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'RAM-16DDR4'
WHERE q.quotation_number = 'COT-2026-0239'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0240', c.id, @seller_id, 'ISSUED', 'DIRECTA', 'PENDIENTE', NULL, NULL,
       NULL,
       15, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 1 DAY, '09:30:00'),
       CURRENT_DATE + INTERVAL 11 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131257750'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0240')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 10.0000, 4394.5088, NULL, 3680.0000,
       ROUND(10 * 4394.5088, 4), ROUND(10 * 4394.5088 * 0.18, 4), ROUND(10 * 4394.5088 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'E15-G4'
WHERE q.quotation_number = 'COT-2026-0240'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0241', c.id, @seller_id, 'ACCEPTED', 'INDIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 78 DAY, '16:00:00'),
       20, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 80 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 65 DAY, 'USD', 3.760000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20297868790'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0241')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 12.0000, 1099.3135, 1143.2860, 838.7097,
       ROUND(12 * 1099.3135, 4), ROUND(12 * 1099.3135 * 0.18, 4), ROUND(12 * 1099.3135 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'LAT-3540'
WHERE q.quotation_number = 'COT-2026-0241'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 15.0000, 821.8933, 867.0974, 666.6667,
       ROUND(15 * 821.8933, 4), ROUND(15 * 821.8933 * 0.18, 4), ROUND(15 * 821.8933 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'PROD-405G9'
WHERE q.quotation_number = 'COT-2026-0241'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0242', c.id, @seller_id, 'ACCEPTED', 'PRIVADA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 74 DAY, '16:00:00'),
       25, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 76 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 61 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100047218'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0242')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 15.0000, 886.4016, 921.8577, 690.0000,
       ROUND(15 * 886.4016, 4), ROUND(15 * 886.4016 * 0.18, 4), ROUND(15 * 886.4016 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MON-27QHD'
WHERE q.quotation_number = 'COT-2026-0242'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 20.0000, 209.6700, NULL, 150.0000,
       ROUND(20 * 209.6700, 4), ROUND(20 * 209.6700 * 0.18, 4), ROUND(20 * 209.6700 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SRV-INSTAL'
WHERE q.quotation_number = 'COT-2026-0242'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 25.0000, 209.8790, 224.5705, 168.0000,
       ROUND(25 * 209.8790, 4), ROUND(25 * 209.8790 * 0.18, 4), ROUND(25 * 209.8790 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-3182'
WHERE q.quotation_number = 'COT-2026-0242'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0243', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 70 DAY, '16:00:00'),
       5, 0, 'OCAM 00394-2026', DATE(CURRENT_DATE - INTERVAL 68 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 72 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 69 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20601030013'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0243')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 20.0000, 7650.3040, NULL, 6200.0000,
       ROUND(20 * 7650.3040, 4), ROUND(20 * 7650.3040 * 0.18, 4), ROUND(20 * 7650.3040 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'PI-65-4K'
WHERE q.quotation_number = 'COT-2026-0243'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0244', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 66 DAY, '16:00:00'),
       7, 6, 'OCAM 00401-2026', DATE(CURRENT_DATE - INTERVAL 64 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 68 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 65 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20553708137'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0244')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 25.0000, 2248.7040, 2338.6522, 1800.0000,
       ROUND(25 * 2248.7040, 4), ROUND(25 * 2248.7040 * 0.18, 4), ROUND(25 * 2248.7040 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SRV-CABLE'
WHERE q.quotation_number = 'COT-2026-0244'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 2.0000, 111.2883, 117.4092, 88.0000,
       ROUND(2 * 111.2883, 4), ROUND(2 * 111.2883 * 0.18, 4), ROUND(2 * 111.2883 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1248'
WHERE q.quotation_number = 'COT-2026-0244'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0245', c.id, @seller_id, 'ACCEPTED', 'PERU_COMPRAS', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 62 DAY, '16:00:00'),
       10, 12, 'OCAM 00408-2026', DATE(CURRENT_DATE - INTERVAL 60 DAY),
       TIMESTAMP(CURRENT_DATE - INTERVAL 64 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 61 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20131380951'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0245')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 141.6397, 147.3053, 112.0000,
       ROUND(2 * 141.6397, 4), ROUND(2 * 141.6397 * 0.18, 4), ROUND(2 * 141.6397 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'SSD-512NVME'
WHERE q.quotation_number = 'COT-2026-0245'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 3.0000, 195.3600, NULL, 148.0000,
       ROUND(3 * 195.3600, 4), ROUND(3 * 195.3600 * 0.18, 4), ROUND(3 * 195.3600 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5230M'
WHERE q.quotation_number = 'COT-2026-0245'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 4.0000, 124.7594, 133.4926, 92.0000,
       ROUND(4 * 124.7594, 4), ROUND(4 * 124.7594 * 0.18, 4), ROUND(4 * 124.7594 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1107A'
WHERE q.quotation_number = 'COT-2026-0245'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0246', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 58 DAY, '16:00:00'),
       12, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 60 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 45 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131371617'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0246')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 3.0000, 147.2000, NULL, 115.0000,
       ROUND(3 * 147.2000, 4), ROUND(3 * 147.2000 * 0.18, 4), ROUND(3 * 147.2000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-1175'
WHERE q.quotation_number = 'COT-2026-0246'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0247', c.id, @seller_id, 'ACCEPTED', 'DIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 54 DAY, '16:00:00'),
       15, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 56 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 41 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20131370645'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0247')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 4.0000, 176.3309, 183.3841, 132.0000,
       ROUND(4 * 176.3309, 4), ROUND(4 * 176.3309 * 0.18, 4), ROUND(4 * 176.3309 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-5230K'
WHERE q.quotation_number = 'COT-2026-0247'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 5.0000, 134.4717, 141.8676, 98.0000,
       ROUND(5 * 134.4717, 4), ROUND(5 * 134.4717 * 0.18, 4), ROUND(5 * 134.4717 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1106A'
WHERE q.quotation_number = 'COT-2026-0247'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0248', c.id, @seller_id, 'ACCEPTED', 'INDIRECTA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 50 DAY, '16:00:00'),
       20, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 52 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 37 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20504743307'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0248')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 5.0000, 294.9120, 306.7085, 225.0000,
       ROUND(5 * 294.9120, 4), ROUND(5 * 294.9120 * 0.18, 4), ROUND(5 * 294.9120 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'TK-6115'
WHERE q.quotation_number = 'COT-2026-0248'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 6.0000, 1152.5500, NULL, 890.0000,
       ROUND(6 * 1152.5500, 4), ROUND(6 * 1152.5500 * 0.18, 4), ROUND(6 * 1152.5500 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '013R00591'
WHERE q.quotation_number = 'COT-2026-0248'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 8.0000, 1098.1888, 1175.0620, 890.0000,
       ROUND(8 * 1098.1888, 4), ROUND(8 * 1098.1888 * 0.18, 4), ROUND(8 * 1098.1888 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '9YG09A'
WHERE q.quotation_number = 'COT-2026-0248'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0249', c.id, @seller_id, 'ACCEPTED', 'PRIVADA', 'SELECCIONADA', NULL, 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 46 DAY, '16:00:00'),
       25, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 48 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 33 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20100017491'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0249')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 6.0000, 127.3037, NULL, 96.0000,
       ROUND(6 * 127.3037, 4), ROUND(6 * 127.3037 * 0.18, 4), ROUND(6 * 127.3037 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1105A'
WHERE q.quotation_number = 'COT-2026-0249'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0250', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 42 DAY, '16:00:00'),
       5, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 44 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 41 DAY, 'USD', 3.700000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20154637035'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0250')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 8.0000, 570.5222, 502.0595, 462.3656,
       ROUND(8 * 570.5222, 4), ROUND(8 * 570.5222 * 0.18, 4), ROUND(8 * 570.5222 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'W1Y45A'
WHERE q.quotation_number = 'COT-2026-0250'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 10.0000, 132.6520, 119.3868, 106.1828,
       ROUND(10 * 132.6520, 4), ROUND(10 * 132.6520 * 0.18, 4), ROUND(10 * 132.6520 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '7MD68A'
WHERE q.quotation_number = 'COT-2026-0250'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0251', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'La entidad seleccionó a otro proveedor con precio menor.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 38 DAY, '16:00:00'),
       7, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 40 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 37 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20100128056'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0251')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 10.0000, 1474.1504, 1297.2524, 1180.0000,
       ROUND(10 * 1474.1504, 4), ROUND(10 * 1474.1504 * 0.18, 4), ROUND(10 * 1474.1504 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '1MR78A'
WHERE q.quotation_number = 'COT-2026-0251'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 12.0000, 338.9235, 305.0312, 268.0000,
       ROUND(12 * 338.9235, 4), ROUND(12 * 338.9235 * 0.18, 4), ROUND(12 * 338.9235 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CAM-IP-4MP'
WHERE q.quotation_number = 'COT-2026-0251'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 15.0000, 181.7600, 167.2192, 142.0000,
       ROUND(15 * 181.7600, 4), ROUND(15 * 181.7600 * 0.18, 4), ROUND(15 * 181.7600 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF283A'
WHERE q.quotation_number = 'COT-2026-0251'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0252', c.id, @seller_id, 'REJECTED', 'PERU_COMPRAS', 'NO_SELECCIONADA', 'Nos ganaron por precio: la diferencia fue de menos de 3 % en el total.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 34 DAY, '16:00:00'),
       10, 12, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 36 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 33 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] PERU COMPRAS · historial de prueba.'
FROM customers c WHERE c.document_number = '20331066703'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0252')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 12.0000, 809.3696, 712.2452, 640.0000,
       ROUND(12 * 809.3696, 4), ROUND(12 * 809.3696 * 0.18, 4), ROUND(12 * 809.3696 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = '6R01649'
WHERE q.quotation_number = 'COT-2026-0252'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0253', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'El competidor ofreció el mismo producto con plazo de entrega más corto.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 30 DAY, '16:00:00'),
       12, 24, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 32 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 17 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20382875799'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0253')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 15.0000, 947.2000, 833.5360, 740.0000,
       ROUND(15 * 947.2000, 4), ROUND(15 * 947.2000 * 0.18, 4), ROUND(15 * 947.2000 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CA-BIO'
WHERE q.quotation_number = 'COT-2026-0253'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 20.0000, 217.6205, 195.8585, 168.0000,
       ROUND(20 * 217.6205, 4), ROUND(20 * 217.6205 * 0.18, 4), ROUND(20 * 217.6205 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF280A'
WHERE q.quotation_number = 'COT-2026-0253'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0254', c.id, @seller_id, 'REJECTED', 'DIRECTA', 'NO_SELECCIONADA', 'Perdimos por precio unitario en las líneas de mayor cantidad.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 26 DAY, '16:00:00'),
       15, 36, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 28 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 13 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION DIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20147796254'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0254')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 20.0000, 165.8061, 145.9094, 128.0000,
       ROUND(20 * 165.8061, 4), ROUND(20 * 165.8061 * 0.18, 4), ROUND(20 * 165.8061 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CE285A'
WHERE q.quotation_number = 'COT-2026-0254'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 25.0000, 5177.3440, 4659.6096, 3950.0000,
       ROUND(25 * 5177.3440, 4), ROUND(25 * 5177.3440 * 0.18, 4), ROUND(25 * 5177.3440 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'E14-G5'
WHERE q.quotation_number = 'COT-2026-0254'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 3, p.id, p.sku, p.name, 'unidad', 2.0000, 886.4016, 815.4895, 690.0000,
       ROUND(2 * 886.4016, 4), ROUND(2 * 886.4016 * 0.18, 4), ROUND(2 * 886.4016 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MON-27QHD'
WHERE q.quotation_number = 'COT-2026-0254'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 3);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0255', c.id, @seller_id, 'REJECTED', 'INDIRECTA', 'NO_SELECCIONADA', 'La entidad prefirió a un proveedor con stock inmediato.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 22 DAY, '16:00:00'),
       20, 0, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 24 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 9 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] CONTRATACION INDIRECTA · historial de prueba.'
FROM customers c WHERE c.document_number = '20604011836'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0255')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 25.0000, 596.3776, 524.8123, 455.0000,
       ROUND(25 * 596.3776, 4), ROUND(25 * 596.3776 * 0.18, 4), ROUND(25 * 596.3776 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'CF259X'
WHERE q.quotation_number = 'COT-2026-0255'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotations (public_id, quotation_number, customer_id, owner_worker_id, status,
        procurement_type, outcome, outcome_reason, outcome_source, outcome_at, delivery_term_days,
        warranty_months, ocam_number, ocam_date, issued_at, valid_until, currency_code,
        exchange_rate_to_pen, includes_tax, tax_rate, notes)
SELECT UUID(), 'COT-2026-0256', c.id, @seller_id, 'EXPIRED', 'PRIVADA', 'DESIERTA', 'El proceso quedó desierto: ninguna oferta cumplió las especificaciones técnicas.', 'ENTIDAD',
       TIMESTAMP(CURRENT_DATE - INTERVAL 18 DAY, '16:00:00'),
       25, 6, NULL, NULL,
       TIMESTAMP(CURRENT_DATE - INTERVAL 20 DAY, '09:30:00'),
       CURRENT_DATE - INTERVAL 5 DAY, 'PEN', 1.000000, FALSE, 0.180000, '[DEMO] EMPRESA PRIVADA · historial de prueba.'
FROM customers c WHERE c.document_number = '20512345678'
  AND NOT EXISTS (SELECT 1 FROM quotations q WHERE q.quotation_number = 'COT-2026-0256')
LIMIT 1;
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 1, p.id, p.sku, p.name, 'unidad', 2.0000, 289.5620, NULL, 215.0000,
       ROUND(2 * 289.5620, 4), ROUND(2 * 289.5620 * 0.18, 4), ROUND(2 * 289.5620 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'DR-630'
WHERE q.quotation_number = 'COT-2026-0256'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 1);
INSERT INTO quotation_items (quotation_id, line_number, product_id, sku_snapshot, description_snapshot,
        unit_snapshot, quantity, unit_price, competitor_price, purchase_price, line_subtotal, line_tax, line_total)
SELECT q.id, 2, p.id, p.sku, p.name, 'unidad', 3.0000, 475.0592, NULL, 385.0000,
       ROUND(3 * 475.0592, 4), ROUND(3 * 475.0592 * 0.18, 4), ROUND(3 * 475.0592 * 1.18, 4)
FROM quotations q JOIN products p ON p.sku = 'MON-24FHD'
WHERE q.quotation_number = 'COT-2026-0256'
  AND NOT EXISTS (SELECT 1 FROM quotation_items i WHERE i.quotation_id = q.id AND i.line_number = 2);

-- Totales de cabecera, calculados desde las líneas.
UPDATE quotations q
SET q.subtotal_amount = COALESCE((SELECT SUM(i.line_subtotal) FROM quotation_items i WHERE i.quotation_id = q.id), 0),
    q.tax_amount      = COALESCE((SELECT SUM(i.line_tax)      FROM quotation_items i WHERE i.quotation_id = q.id), 0),
    q.total_amount    = COALESCE((SELECT SUM(i.line_total)    FROM quotation_items i WHERE i.quotation_id = q.id), 0)
WHERE q.notes LIKE '%[DEMO]%';

-- ----------------------------------------------------------------------------
-- 5. Facturas de las cotizaciones ganadas
-- ----------------------------------------------------------------------------
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-901', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 230 DAY), DATE(CURRENT_DATE - INTERVAL 200 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2025-0201.'
FROM quotations q WHERE q.quotation_number = 'COT-2025-0201'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-901');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-902', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 222 DAY), DATE(CURRENT_DATE - INTERVAL 192 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2025-0203.'
FROM quotations q WHERE q.quotation_number = 'COT-2025-0203'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-902');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-903', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 214 DAY), DATE(CURRENT_DATE - INTERVAL 184 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2025-0205.'
FROM quotations q WHERE q.quotation_number = 'COT-2025-0205'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-903');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-904', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 206 DAY), DATE(CURRENT_DATE - INTERVAL 176 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2025-0207.'
FROM quotations q WHERE q.quotation_number = 'COT-2025-0207'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-904');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-905', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 198 DAY), DATE(CURRENT_DATE - INTERVAL 168 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2025-0209.'
FROM quotations q WHERE q.quotation_number = 'COT-2025-0209'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-905');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-906', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 150 DAY), DATE(CURRENT_DATE - INTERVAL 120 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0221.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0221'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-906');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-907', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 142 DAY), DATE(CURRENT_DATE - INTERVAL 112 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0223.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0223'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-907');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-908', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 134 DAY), DATE(CURRENT_DATE - INTERVAL 104 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0225.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0225'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-908');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-909', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 126 DAY), DATE(CURRENT_DATE - INTERVAL 96 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0227.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0227'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-909');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-910', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 118 DAY), DATE(CURRENT_DATE - INTERVAL 88 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0229.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0229'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-910');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-911', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 70 DAY), DATE(CURRENT_DATE - INTERVAL 40 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0241.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0241'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-911');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-912', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 62 DAY), DATE(CURRENT_DATE - INTERVAL 32 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0243.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0243'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-912');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-913', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 54 DAY), DATE(CURRENT_DATE - INTERVAL 24 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0245.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0245'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-913');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-914', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 46 DAY), DATE(CURRENT_DATE - INTERVAL 16 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0247.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0247'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-914');
INSERT INTO invoices (public_id, invoice_number, invoice_type, customer_id, quotation_id,
        registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
        includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
SELECT UUID(), 'F001-915', 'INVOICE', q.customer_id, q.id, @accountant_id, 'ISSUED',
       DATE(CURRENT_DATE - INTERVAL 38 DAY), DATE(CURRENT_DATE - INTERVAL 8 DAY),
       q.currency_code, q.exchange_rate_to_pen, FALSE, 0.180000,
       q.subtotal_amount, q.tax_amount, q.total_amount,
       '[DEMO] Factura emitida de la cotización COT-2026-0249.'
FROM quotations q WHERE q.quotation_number = 'COT-2026-0249'
  AND NOT EXISTS (SELECT 1 FROM invoices v WHERE v.invoice_type = 'INVOICE' AND v.invoice_number = 'F001-915');

INSERT INTO invoice_items (invoice_id, line_number, product_id, sku_snapshot, description_snapshot,
                           unit_snapshot, quantity, unit_price, line_subtotal, line_tax, line_total)
SELECT i.id, qi.line_number, qi.product_id, qi.sku_snapshot, qi.description_snapshot,
       qi.unit_snapshot, qi.quantity, qi.unit_price, qi.line_subtotal, qi.line_tax, qi.line_total
FROM invoices i
JOIN quotation_items qi ON qi.quotation_id = i.quotation_id
WHERE i.notes LIKE '%[DEMO] Factura emitida de la cotización%'
  AND NOT EXISTS (SELECT 1 FROM invoice_items e WHERE e.invoice_id = i.id AND e.line_number = qi.line_number);

-- ----------------------------------------------------------------------------
-- 6. Correlativo de cotizaciones
--
-- Los números de arriba son de demostración. Se adelanta el correlativo para
-- que las cotizaciones que emita el sistema nunca choquen con ellos.
-- ----------------------------------------------------------------------------
INSERT INTO document_sequences (document_type, year, last_value, version)
VALUES ('COT', YEAR(CURRENT_DATE), 500, 0)
ON DUPLICATE KEY UPDATE last_value = GREATEST(last_value, 500);
