-- P52: cada empresa tiene su propio sitio público (dominio, marca y catálogo)
-- sobre el mismo sistema. El tenant del sitio se resuelve por el nombre de host
-- de la petición, nunca por un parámetro que pueda elegir el visitante.

CREATE TABLE tenant_sites (
    tenant_id BIGINT UNSIGNED NOT NULL,
    template_key VARCHAR(30) NOT NULL,
    site_title VARCHAR(140) NOT NULL,
    brand_name VARCHAR(120) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    tagline VARCHAR(220) NULL,
    logo_asset VARCHAR(160) NULL,
    monogram_text VARCHAR(4) NULL,
    phone VARCHAR(60) NULL,
    whatsapp_number VARCHAR(20) NULL,
    whatsapp_message VARCHAR(220) NULL,
    email VARCHAR(254) NULL,
    address VARCHAR(255) NULL,
    maps_url VARCHAR(500) NULL,
    hours VARCHAR(140) NULL,
    footer_note VARCHAR(220) NULL,
    facebook_url VARCHAR(500) NULL,
    instagram_url VARCHAR(500) NULL,
    linkedin_url VARCHAR(500) NULL,
    claims_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_sites_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_tenant_sites_template CHECK (template_key IN ('GIOVA','SOLUCIONES','GENERICO')),
    CONSTRAINT ck_tenant_sites_whatsapp CHECK (whatsapp_number IS NULL OR whatsapp_number REGEXP '^[0-9]{9,15}$'),
    CONSTRAINT ck_tenant_sites_email CHECK (email IS NULL OR email LIKE '%@%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_hosts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    hostname VARCHAR(190) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_hosts_hostname (hostname),
    KEY ix_tenant_hosts_tenant (tenant_id, is_primary),
    CONSTRAINT fk_tenant_hosts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    -- Solo minúsculas: el resolutor normaliza el Host antes de buscar.
    CONSTRAINT ck_tenant_hosts_hostname CHECK (hostname REGEXP '^[a-z0-9][a-z0-9.-]{0,188}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- GIOVA: los datos que hoy están escritos a mano en el frontend.
-- ---------------------------------------------------------------------------
INSERT INTO tenant_sites (
    tenant_id, template_key, site_title, brand_name, legal_name, tagline,
    logo_asset, monogram_text, phone, whatsapp_number, whatsapp_message, email,
    address, maps_url, hours, footer_note, claims_enabled, catalog_enabled
) VALUES (
    1, 'GIOVA', 'Giova Representaciones | Tecnología', 'Giova Representaciones',
    'GIOVA REPRESENTACIONES S.A.C.',
    'Equipos informáticos, impresoras y tóners originales HP y Kyocera para el sector público y privado',
    'assets/logo.svg', 'GR', '(01) 903 9891 · 968 359 290', '51968359290',
    'Hola, deseo recibir información sobre los productos y servicios tecnológicos que ofrecen.',
    'ventas@giova.pe', 'Calle Pedro Donofrio 171 Int. 302, Bellavista, Callao',
    'https://maps.app.goo.gl/BgsorZ1sQWj4tub56', 'Lunes a viernes de 9:00 a 18:00',
    'Proveedor habilitado en OSCE | Empresa Homologada', TRUE, TRUE
);

-- Agregar aquí el dominio real cuando se publique, por ejemplo:
--   INSERT INTO tenant_hosts(tenant_id, hostname, is_primary)
--   VALUES (1, 'giovarepresentaciones.com', TRUE), (1, 'www.giovarepresentaciones.com', FALSE);
INSERT INTO tenant_hosts (tenant_id, hostname, is_primary) VALUES
    (1, 'localhost', TRUE),
    (1, '127.0.0.1', FALSE),
    (1, 'giova.localhost', FALSE),
    (1, 'www.giova.localhost', FALSE);

-- ---------------------------------------------------------------------------
-- Soluciones Informática TI: segunda empresa, mismo sistema, sitio propio.
-- Falta el teléfono y el WhatsApp: quedan en NULL y el sitio los oculta.
-- ---------------------------------------------------------------------------
-- Si la empresa ya fue creada desde la pantalla de plataforma, estas sentencias
-- no la duplican: todas se apoyan en el código SOLUCIONES_TI. Si allí se usó
-- otro código, cambiarlo en este archivo antes de migrar.
INSERT INTO tenants (
    public_id, code, slug, legal_name, display_name, tax_id, default_currency,
    tax_rate, active, version, created_at, updated_at
)
SELECT UUID(), 'SOLUCIONES_TI', 'soluciones-ti', 'SOLUCIONES INFORMATICA TI E.I.R.L.',
       'Soluciones Informática TI', NULL, 'PEN', 0.180000, TRUE, 0, NOW(6), NOW(6)
FROM (SELECT 1) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM tenants WHERE code = 'SOLUCIONES_TI' OR slug = 'soluciones-ti'
);

INSERT INTO tenant_branding (
    tenant_id, primary_color, secondary_color, accent_color, logo_storage_key,
    pdf_header_text, version, created_at, updated_at
)
SELECT t.id, '#f26b21', '#1f2937', '#f6b847', NULL,
       'SOLUCIONES INFORMATICA TI E.I.R.L.', 0, NOW(6), NOW(6)
FROM tenants t
WHERE t.code = 'SOLUCIONES_TI'
  AND NOT EXISTS (SELECT 1 FROM tenant_branding b WHERE b.tenant_id = t.id);

INSERT INTO tenant_sites (
    tenant_id, template_key, site_title, brand_name, legal_name, tagline,
    logo_asset, monogram_text, phone, whatsapp_number, whatsapp_message, email,
    address, maps_url, hours, footer_note, claims_enabled, catalog_enabled
)
SELECT t.id, 'SOLUCIONES', 'Soluciones Informática TI | Infraestructura y soporte',
       'Soluciones Informática TI', 'SOLUCIONES INFORMATICA TI E.I.R.L.',
       'Infraestructura, redes y soporte informático para empresas e instituciones',
       'assets/soluciones-informaticas-ti.svg', 'TI', NULL, NULL,
       'Hola, quisiera cotizar servicios de infraestructura y soporte informático.',
       'aramirez@solucionesinformatica.com.pe', 'AV. LIMA 3959 URB. PERU - SMP, San Martín de Porres',
       'https://www.google.com/maps/search/?api=1&query=AV.%20LIMA%203959%20URB.%20PERU%20SMP%20Lima',
       'Lunes a viernes de 9:00 a 18:00',
       'Infraestructura · Redes · Videovigilancia · Soporte', TRUE, TRUE
FROM tenants t
WHERE t.code = 'SOLUCIONES_TI';

INSERT INTO tenant_hosts (tenant_id, hostname, is_primary)
SELECT t.id, h.hostname, h.is_primary
FROM tenants t
JOIN (
    SELECT 'soluciones.localhost' AS hostname, TRUE AS is_primary
    UNION ALL SELECT 'www.soluciones.localhost', FALSE
) AS h
WHERE t.code = 'SOLUCIONES_TI';

-- Catálogo propio de la segunda empresa. Los ids se dejan automáticos: las
-- claves únicas ahora son (tenant_id, slug) y (tenant_id, sku), así que no
-- chocan con el catálogo de GIOVA aunque se repita un SKU.
INSERT INTO categories (tenant_id, name, slug, icon_key, display_order, active)
SELECT t.id, d.name, d.slug, d.icon_key, d.display_order, TRUE
FROM tenants t
JOIN (
    SELECT 'Redes y conectividad' AS name, 'redes' AS slug, 'redes' AS icon_key, 1 AS display_order
    UNION ALL SELECT 'Videovigilancia', 'videovigilancia', 'seguridad', 2
    UNION ALL SELECT 'Equipos y energía', 'equipos', 'equipos', 3
    UNION ALL SELECT 'Servicios de TI', 'servicios', 'servicios', 4
) AS d
WHERE t.code = 'SOLUCIONES_TI';

INSERT INTO brands (tenant_id, name, slug, display_color, featured, display_order, active)
SELECT t.id, d.name, d.slug, d.display_color, d.featured, d.display_order, TRUE
FROM tenants t
JOIN (
    SELECT 'Ubiquiti' AS name, 'ubiquiti' AS slug, '#0559c9' AS display_color, TRUE AS featured, 1 AS display_order
    UNION ALL SELECT 'TP-Link', 'tp-link', '#4acbd6', TRUE, 2
    UNION ALL SELECT 'Hikvision', 'hikvision', '#e3000f', TRUE, 3
    UNION ALL SELECT 'Dahua', 'dahua', '#0068b7', FALSE, 4
    UNION ALL SELECT 'APC', 'apc', '#00843d', TRUE, 5
    UNION ALL SELECT 'Lenovo', 'lenovo', '#e1140a', TRUE, 6
    UNION ALL SELECT 'Soluciones TI', 'soluciones-ti', '#f26b21', FALSE, 7
) AS d
WHERE t.code = 'SOLUCIONES_TI';

INSERT INTO products (tenant_id, slug, sku, name, description, brand_id, category_id,
                      badge, display_order, active)
SELECT t.id, d.slug, d.sku, d.name, d.description,
       (SELECT b.id FROM brands b WHERE b.tenant_id = t.id AND b.slug = d.brand_slug),
       (SELECT c.id FROM categories c WHERE c.tenant_id = t.id AND c.slug = d.category_slug),
       d.badge, d.display_order, TRUE
FROM tenants t
JOIN (
    SELECT 'access-point-unifi-u6-lite' AS slug, 'U6-LITE' AS sku,
           'Access Point UniFi U6 Lite' AS name,
           'Punto de acceso Wi-Fi 6 de doble banda para oficinas, con administración centralizada UniFi y alimentación PoE.' AS description,
           'ubiquiti' AS brand_slug, 'redes' AS category_slug, 'Wi-Fi 6' AS badge, 1 AS display_order
    UNION ALL SELECT 'switch-unifi-usw-24-poe', 'USW-24-POE', 'Switch UniFi 24 puertos PoE',
           'Switch administrable de 24 puertos Gigabit con 16 puertos PoE+, pensado para cableado estructurado de oficinas y sedes.',
           'ubiquiti', 'redes', NULL, 2
    UNION ALL SELECT 'router-tp-link-er605', 'ER605', 'Router TP-Link Omada ER605',
           'Router multi-WAN con balanceo de carga y VPN, para enlaces redundantes de internet en sedes pequeñas.',
           'tp-link', 'redes', NULL, 3
    UNION ALL SELECT 'switch-tp-link-sg1016d', 'TL-SG1016D', 'Switch TP-Link 16 puertos Gigabit',
           'Switch no administrable de 16 puertos Gigabit en gabinete metálico, para ampliar puntos de red sin configuración.',
           'tp-link', 'redes', NULL, 4
    UNION ALL SELECT 'camara-hikvision-ds-2cd1043g2', 'DS-2CD1043G2', 'Cámara IP Hikvision 4 MP',
           'Cámara tipo bullet de 4 MP con visión nocturna de 30 m, apta para exteriores con grado de protección IP67.',
           'hikvision', 'videovigilancia', 'Más vendido', 5
    UNION ALL SELECT 'nvr-hikvision-ds-7608ni', 'DS-7608NI', 'NVR Hikvision 8 canales',
           'Grabador de red de 8 canales con soporte para disco de 10 TB y acceso remoto desde aplicación móvil.',
           'hikvision', 'videovigilancia', NULL, 6
    UNION ALL SELECT 'camara-dahua-ipc-hdw1239', 'IPC-HDW1239', 'Cámara IP Dahua domo 2 MP',
           'Domo IP de 2 MP con luz full color para interiores, ideal para recepciones y pasillos.',
           'dahua', 'videovigilancia', NULL, 7
    UNION ALL SELECT 'ups-apc-br1500ms2', 'BR1500MS2', 'UPS APC Back-UPS Pro 1500 VA',
           'UPS interactivo de 1500 VA con pantalla LCD y regulación automática de voltaje, para servidores pequeños y puestos críticos.',
           'apc', 'equipos', NULL, 8
    UNION ALL SELECT 'ups-apc-be600m1', 'BE600M1', 'UPS APC Back-UPS 600 VA',
           'Respaldo de energía de 600 VA para computadora de escritorio, monitor y router.',
           'apc', 'equipos', NULL, 9
    UNION ALL SELECT 'laptop-lenovo-thinkpad-e14', 'THINKPAD-E14', 'Laptop Lenovo ThinkPad E14',
           'Laptop empresarial de 14 pulgadas con procesador Intel Core i5, 16 GB de RAM y SSD de 512 GB.',
           'lenovo', 'equipos', NULL, 10
    UNION ALL SELECT 'servidor-lenovo-st50-v2', 'ST50-V2', 'Servidor Lenovo ThinkSystem ST50 V2',
           'Servidor torre de entrada para archivos, respaldos o controlador de dominio en oficinas de hasta 30 usuarios.',
           'lenovo', 'equipos', NULL, 11
    UNION ALL SELECT 'servicio-cableado-estructurado', 'SRV-CABLEADO', 'Cableado estructurado categoría 6',
           'Instalación certificada de puntos de red categoría 6, con canaletas, patch panel, etiquetado y pruebas de certificación.',
           'soluciones-ti', 'servicios', 'Servicio', 12
    UNION ALL SELECT 'servicio-soporte-mensual', 'SRV-SOPORTE', 'Soporte informático mensual',
           'Mantenimiento preventivo y correctivo con atención remota y visitas programadas, bajo acuerdo de nivel de servicio.',
           'soluciones-ti', 'servicios', 'Servicio', 13
    UNION ALL SELECT 'servicio-implementacion-camaras', 'SRV-CCTV', 'Implementación de videovigilancia',
           'Diseño e instalación del sistema de cámaras: canalización, configuración del grabador y capacitación de uso.',
           'soluciones-ti', 'servicios', 'Servicio', 14
) AS d
WHERE t.code = 'SOLUCIONES_TI';

-- ---------------------------------------------------------------------------
-- Datos legales de las dos empresas, confirmados por el propietario.
-- Van como UPDATE y no como parte de un INSERT porque las filas de `tenants`
-- pueden venir de V16 o de un respaldo donde la segunda empresa ya se creó
-- desde la pantalla de plataforma.
-- ---------------------------------------------------------------------------
UPDATE tenants SET
    legal_name = 'GIOVA REPRESENTACIONES S.A.C.',
    tax_id = '20524947707',
    address = 'Calle Pedro Donofrio 171 Int. 302 Urb. El Aguila, Bellavista, Callao',
    contact_email = 'ventas@giova.pe',
    updated_at = NOW(6)
WHERE code = 'GIOVA';

UPDATE tenants SET
    legal_name = 'SOLUCIONES INFORMATICA TI E.I.R.L.',
    tax_id = '20604792216',
    address = 'AV. LIMA 3959 URB. PERU - SMP',
    contact_email = 'aramirez@solucionesinformatica.com.pe',
    updated_at = NOW(6)
WHERE code = 'SOLUCIONES_TI';

-- Cabecera de las cotizaciones en PDF. El generador reemplaza los caracteres
-- fuera de ASCII, así que la línea va sin tildes ni viñetas.
UPDATE tenant_branding b
JOIN tenants t ON t.id = b.tenant_id
SET b.pdf_header_text = 'RUC 20524947707 - Calle Pedro Donofrio 171 Int. 302, Bellavista, Callao',
    b.version = b.version + 1,
    b.updated_at = NOW(6)
WHERE t.code = 'GIOVA';

UPDATE tenant_branding b
JOIN tenants t ON t.id = b.tenant_id
SET b.pdf_header_text = 'RUC 20604792216 - Av. Lima 3959 Urb. Peru, San Martin de Porres',
    b.version = b.version + 1,
    b.updated_at = NOW(6)
WHERE t.code = 'SOLUCIONES_TI';
