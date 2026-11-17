# Contrato de datos y API — GIOVA V2

## 0. Uso del contrato

- Ruta canónica: `C:\Users\moral\OneDrive\Documents\ACTIVIDAD_CAPSTONE\GIOVA-ANGULAR+BACKEND`.
- Actualizado: 2026-10-08. Se integró el paquete local `GIOVA-V1-P31-final`; la evidencia de esta reconciliación y la ejecución de P51/P52 está en `docs/ESTADO_TRABAJO.md`.
- Contrato multiempresa y membresías integrado en §15 (P50, aprobado por el propietario 2026-10-07). V16 se aplicó a `giova_v1` en P51; P52 implementó el contexto de empresa y fue auditada con la observación de que la matriz HTTP multiempresa completa no se ejecutó. P53 incorporó branding por empresa en endpoints y PDFs; auditoría APROBADO y 137 pruebas (ver `docs/ESTADO_TRABAJO.md`).
- V1 (P01–P15) está implementada. Este documento conserva sus rutas y define la ampliación P17–P31.
- No implementar todas las secciones juntas: cada tabla/endpoints nace únicamente en la fase indicada.
- JSON usa `camelCase`; SQL/Java usan inglés; interfaz usa español.
- Respuesta paginada: `{items,totalItems,page,size,totalPages}`. `page` inicia en 0; `size` 1–100.
- Error: `{code,message,requestId,fieldErrors?}`. Nunca devolver SQL, stack trace, rutas físicas ni `rejectedValue`.
- Todas las escrituras privadas requieren sesión, rol y CSRF. Todas las consultas usan SQL parametrizado.

## 1. Convenciones SQL

- Esquema único: `giova_v1`; motor MariaDB 10.4.32; `utf8mb4`.
- PK: `id BIGINT UNSIGNED AUTO_INCREMENT`.
- Fechas técnicas: `created_at DATETIME(6)`, `updated_at DATETIME(6)` en UTC (tipo real de V1–V16; ver §15.1 para la decisión de P51).
- Baja lógica: `active BOOLEAN NOT NULL DEFAULT TRUE` o estado cerrado; no borrar físicamente desde UI.
- Bloqueo optimista: `version BIGINT UNSIGNED NOT NULL DEFAULT 0`; UPDATE siempre incluye `WHERE id=:id AND version=:version` e incrementa. Cero filas → 409 `VERSION_CONFLICT` si existe, 404 si no existe.
- Dinero: `DECIMAL(19,4)`; porcentajes/tasa `DECIMAL(9,6)`; Java `BigDecimal`.
- Códigos de moneda: `PEN`, `USD`.
- Identificadores públicos de documentos: UUID textual `CHAR(36)` cuando no conviene exponer PK.
- Checks de enum se agregan en DDL y se repiten en Java. No confiar solo en CHECK de MariaDB.
- Índices: toda FK; columnas de estado/fecha usadas conjuntamente; claves de idempotencia/negocio únicas.
- No modificar V1–V15 aplicadas. Siguiente migración: V16 (ver §13 y §15).
- Multiempresa: toda tabla de negocio de §15.1 lleva `tenant_id` y las consultas lo filtran (§15.3).

## 2. Tablas V1 existentes

Conservar sin reinterpretar: `brands`, `categories`, `products`, `workers`, `contact_submissions`, `claims`, `flyway_schema_history`. Desde V17, las rutas públicas de catálogo y los formularios públicos se resuelven por el dominio de la petición (`tenant_hosts`), no por `tenant_id=1` fijo. El catálogo inicial sigue teniendo 52 productos/servicios, 8 categorías y 14 marcas.

Rutas V1 conservadas:

```text
GET  /api/v1/catalog/products
GET  /api/v1/catalog/categories
GET  /api/v1/catalog/brands
GET  /api/v1/catalog/site       (V17: marca y datos del sitio de la empresa del dominio)
GET  /api/v1/catalog/site/logo  (V17: logo subido por esa empresa)
GET  /api/v1/auth/csrf
POST /api/v1/auth/login
GET  /api/v1/auth/me
POST /api/v1/auth/logout
GET|POST|PUT|PATCH /api/v1/admin/brands/**
GET|POST|PUT|PATCH /api/v1/admin/categories/**
GET|POST|PUT|PATCH /api/v1/admin/products/**
POST /api/v1/contact-submissions
POST /api/v1/claims
```

Al completar P17, mutaciones `/admin/**` requieren ADMIN. ACCOUNTANT/SELLER usan los GET de catálogo público para lectura.

## 3. V3 — rol de trabajador (P17)

Agregar a `workers`:

| Columna | Tipo/regla |
|---|---|
| `role_code` | En V1–V15 admitía `ADMIN`,`ACCOUNTANT`,`SELLER`; desde V16 solo identifica `SUPERADMIN` de plataforma. |

La descripción anterior corresponde al modelo de V1–V15. Desde V16, `ADMIN`, `ACCOUNTANT` y `SELLER` se almacenan en `worker_memberships`; `workers.role_code` queda NULL para esas identidades y solo contiene `SUPERADMIN` cuando es una cuenta de plataforma. La autenticación rechaza identidades sin membresía activa o rol de plataforma. `create-worker.ps1` exige empresa para los tres roles de empresa y confirmación local exacta para crear `SUPERADMIN`; `set-worker-role.ps1` está deshabilitado desde V16 y no se conecta a la base.

Desde P50 (contrato §15), `role_code` admite también `SUPERADMIN` (plataforma, sin membresía). ADMIN, ACCOUNTANT y SELLER de empresa pasan a `worker_memberships` en P51; `workers.role_code` queda solo para SUPERADMIN.

Auth worker DTO desde P17:

```json
{
  "id": 1,
  "username": "usuario",
  "displayName": "Nombre visible",
  "roleCode": "SELLER",
  "roleLabel": "Vendedor"
}
```

No devolver `passwordHash`, `enabled`, authorities internas, session ID ni token como campo JSON.

### 3.1 Shell privado P18

La interfaz Angular usa una sola definición tipada de roles y una configuración estática de navegación:

```text
/trabajadores/login
/operaciones                         # shell autenticado, inicio por rol
/operaciones/catalogo/productos      # CRUD ADMIN en la UI actual
/operaciones/catalogo/marcas          # CRUD ADMIN en la UI actual
/operaciones/catalogo/categorias      # CRUD ADMIN en la UI actual
/operaciones/ventas/clientes          # CRUD según rol P19
/operaciones/ventas/precios            # referencias históricas según rol P19
/operaciones/ventas/cotizaciones       # interfaz de cotizaciones según rol P22
/403                                  # acceso autenticado sin permiso
```

`/admin`, `/admin/productos`, `/admin/marcas`, `/admin/categorias`, `/admin/clientes` y `/admin/precios` se mantienen como aliases y redirigen al shell canónico. El `authGuard` resuelve 401 hacia el login con `returnUrl`; `roleGuard` resuelve una ruta no permitida hacia `/403`. Estas protecciones son UX: la API conserva la autorización backend y devuelve 403 para un rol no autorizado.

## 4. V4 — clientes y referencias de precio (P19)

### 4.1 `customers`

| Columna | Tipo/regla |
|---|---|
| `id` | PK |
| `public_id` | `CHAR(36) UNIQUE NOT NULL` |
| `owner_worker_id` | FK workers, NOT NULL |
| `customer_type` | `VARCHAR(15)`, `PERSON` o `COMPANY` |
| `document_type` | `VARCHAR(10)`, `DNI`,`RUC`,`CE`,`OTHER` |
| `document_number` | `VARCHAR(20) NOT NULL` |
| `business_name` | `VARCHAR(180) NOT NULL` |
| `contact_name` | `VARCHAR(140) NULL` |
| `email` | `VARCHAR(254) NULL` normalizado lowercase |
| `phone` | `VARCHAR(30) NULL` |
| `address` | `VARCHAR(300) NULL` |
| `notes` | `VARCHAR(1000) NULL` |
| `active`,`version`,timestamps | convención |

Unique `(document_type, document_number)` entre registros; si negocio necesita reutilizar uno desactivado, se reactiva. DNI exactamente 8 dígitos; RUC exactamente 11; CE/OTHER 3–20 alfanumérico/guion. Backend normaliza espacios periféricos, no altera razón social. Seller solo accede a `owner_worker_id` propio; ADMIN todos; ACCOUNTANT lectura todos.

### 4.2 `product_price_references`

| Columna | Tipo/regla |
|---|---|
| `id` | PK |
| `product_id` | FK products NOT NULL |
| `recorded_by_worker_id` | FK workers NOT NULL |
| `supplier_name` | `VARCHAR(180) NULL` |
| `source_reference` | `VARCHAR(300) NULL`, no URL ejecutable en frontend sin validar |
| `currency_code` | `CHAR(3)` PEN/USD |
| `unit_price` | `DECIMAL(19,4) > 0` |
| `includes_tax` | BOOLEAN |
| `tax_rate` | `DECIMAL(9,6)`, 0..1 |
| `exchange_rate_to_pen` | `DECIMAL(19,6) NULL`; obligatorio y >0 si USD; NULL si PEN |
| `effective_at` | `DATETIME(6) NOT NULL` UTC |
| `notes` | `VARCHAR(500) NULL` |
| `created_at` | timestamp; inmutable |

No UPDATE/DELETE para SELLER. ADMIN puede corregir por endpoint explícito; la auditoría funcional se incorporará con la tabla prevista en P21. Se recomienda añadir una nueva referencia. No se añade precio a `products`.

### 4.3 API P19

```text
GET    /api/v1/sales/customers?q=&active=&owner=me|all&page=&size=
GET    /api/v1/sales/customers/{publicId}
POST   /api/v1/sales/customers
PUT    /api/v1/sales/customers/{publicId}
PATCH  /api/v1/sales/customers/{publicId}/status
GET    /api/v1/sales/price-references?productId=&from=&to=&page=&size=
POST   /api/v1/sales/price-references
PUT    /api/v1/sales/price-references/{id}        # ADMIN
```

POST/PUT customer no acepta owner desde cliente. `PATCH status` ADMIN. Duplicado → 409 `CUSTOMER_DOCUMENT_EXISTS`.

Implementación P19: los controllers `CustomerController` y `PriceReferenceController` aplican `@PreAuthorize` por matriz de roles; los services validan propiedad, documentos, moneda, tasas y existencia de producto; los repositories usan `NamedParameterJdbcTemplate` con columnas explícitas y bloqueo optimista. La UI Angular reutiliza `SalesApiService`, ofrece filtros y formularios responsive, y no expone borrado físico ni edición de precios a SELLER.

## 5. V5 — documentos privados (P20)

### 5.1 `stored_documents`

| Columna | Tipo/regla |
|---|---|
| `id` | PK |
| `public_id` | UUID UNIQUE |
| `document_kind` | allowlist `QUOTATION_PDF`,`INVOICE_PDF`,`INVOICE_XML`,`PURCHASE_ORDER_PDF`,`DELIVERY_PDF`,`WARRANTY_PDF`,`OTHER_PDF` |
| `storage_key` | `VARCHAR(255) UNIQUE NOT NULL`, ruta relativa generada |
| `original_name` | `VARCHAR(255) NOT NULL`, saneado para display |
| `media_type` | `VARCHAR(100) NOT NULL` |
| `extension` | `VARCHAR(10) NOT NULL` |
| `size_bytes` | `BIGINT UNSIGNED NOT NULL` |
| `sha256_hex` | `CHAR(64) NOT NULL` |
| `created_by_worker_id` | FK workers NOT NULL |
| `active`,`version`,timestamps | convención |

No almacenar `C:\...` ni `/var/lib/...`; solo `tipo/yyyy/MM/uuid.ext`. Resolver con `root.resolve(key).normalize()` y comprobar que comienza con root normalizada.

API:

```text
GET /api/v1/documents/{publicId}/content
```

No existe endpoint genérico de upload: cada entidad recibe multipart y llama internamente a `DocumentStorageService`. El servicio público reutilizable valida PDF/XML, sanea el nombre, calcula SHA-256, escribe temporalmente y mueve a la raíz configurada. Descargar requiere ADMIN/ACCOUNTANT o el SELLER que creó el documento; responde `Content-Type` guardado, `Content-Length`, `Content-Disposition: attachment`, `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`; nunca redirect a ruta. Un documento inexistente/inactivo o con archivo ausente responde 404; un documento ajeno responde 403.

Implementación P20: V5 está aplicada en `giova_v1`. `GIOVA_DOCUMENTS_ROOT` permite una raíz local o el volumen Docker `/var/lib/giova/documents`; `GIOVA_DOCUMENTS_MAX_BYTES` limita el cuerpo (10 MiB por defecto). La firma `%PDF-`, la extensión y el MIME deben coincidir; XML usa parser seguro sin DTD ni entidades externas. La clave nunca proviene del cliente y se resuelve con `normalize()` dentro de la raíz; los montajes no se sirven desde Nginx. En una transacción fallida el archivo movido se elimina mediante compensación y sincronización de rollback.

## 6. V6 — cotizaciones (P21/P22)

### 6.1 `document_sequences`

`document_type VARCHAR(30)`, `year SMALLINT UNSIGNED`, `last_value BIGINT UNSIGNED`, `version` y timestamps; PK `(document_type,year)`. Para emitir, bloquear la fila exacta con `SELECT ... FOR UPDATE`, incrementar dentro de la misma transacción y formatear `COT-YYYY-` + seis dígitos. No usar `MAX(id)+1`, conteo de filas, memoria JVM ni UUID como número visible. La misma tabla puede generar `FAC`, `OC` y `ENT` con tipos diferentes cuando nazcan sus módulos.

### 6.2 `quotations`

| Columna | Tipo/regla |
|---|---|
| `id`, `public_id` | PK/UUID unique |
| `quotation_number` | `VARCHAR(30) UNIQUE NULL` hasta emisión; formato `COT-YYYY-000001` |
| `customer_id` | FK customers NOT NULL |
| `owner_worker_id` | FK workers NOT NULL |
| `status` | enum de plan; default DRAFT |
| `issued_at` | UTC NULL; obligatorio desde ISSUED |
| `valid_until` | DATE NULL en draft; >= fecha emisión |
| `procurement_type` | `PERU_COMPRAS`,`DIRECTA`,`INDIRECTA`,`PRIVADA` |
| `outcome` | `PENDIENTE`,`SELECCIONADA`,`NO_SELECCIONADA`,`DESIERTA`,`RECHAZADA`,`ANULADA` |
| `outcome_reason`,`outcome_source`,`outcome_at` | motivo requerido para cierres negativos; fuente `ENTIDAD`/`NOSOTROS`; fecha de cierre |
| `delivery_term_days`,`warranty_months` | plazo ofrecido y garantía opcionales, con límites de V14 |
| `ocam_number`,`ocam_date` | referencia y fecha de OCAM, si aplica |
| `currency_code` | PEN/USD |
| `exchange_rate_to_pen` | regla moneda |
| `includes_tax` | BOOLEAN |
| `tax_rate` | decimal 0..1 |
| `subtotal_amount`,`tax_amount`,`total_amount` | `DECIMAL(19,4)`, calculado servidor |
| `notes` | `VARCHAR(1500) NULL` |
| `version`,timestamps | convención |

### 6.3 `quotation_items`

| Columna | Tipo/regla |
|---|---|
| `id`,`quotation_id` | PK/FK cascade restringido al service mientras DRAFT |
| `line_number` | INT >0, UNIQUE con quotation |
| `product_id` | FK products NULL para concepto manual autorizado |
| `sku_snapshot` | `VARCHAR(80) NULL` |
| `description_snapshot` | `VARCHAR(500) NOT NULL` |
| `unit_snapshot` | `VARCHAR(30) NOT NULL` default `unidad` |
| `quantity` | `DECIMAL(19,4) >0` |
| `unit_price` | `DECIMAL(19,4) >=0` |
| `competitor_price`,`purchase_price` | `DECIMAL(19,4) >=0 NULL`; precios de competencia y costo de compra |
| `line_subtotal`,`line_tax`,`line_total` | calculado servidor |

### 6.4 `quotation_documents`

`quotation_id`, `document_id`, `document_role` (`ISSUED_PDF`,`ATTACHMENT`), `created_at`; PK por pareja y restricción única `(quotation_id, document_role)` para impedir duplicar el PDF emitido en P21. La ampliación de adjuntos adicionales queda para una migración posterior.

### 6.5 Cálculo

Si `includesTax=false`: base línea = quantity×unitPrice; tax = base×taxRate; total=base+tax. Si true: total=quantity×unitPrice; base=total/(1+taxRate); tax=total-base. Calcular con precisión amplia y redondear montos mostrados/guardados a 2 decimales HALF_UP. El total del documento es suma de líneas redondeadas. La respuesta del backend es autoritativa.

### 6.6 API

```text
GET    /api/v1/sales/quotations?q=&status=&procurementType=&outcome=&customerId=&owner=me|all&from=&to=&page=&size=
GET    /api/v1/sales/quotations/{publicId}
POST   /api/v1/sales/quotations
PUT    /api/v1/sales/quotations/{publicId}
POST   /api/v1/sales/quotations/{publicId}/issue
POST   /api/v1/sales/quotations/{publicId}/status
POST   /api/v1/sales/quotations/{publicId}/outcome
POST   /api/v1/sales/quotations/{publicId}/ocam
GET    /api/v1/sales/quotations/{publicId}/pdf
```

POST/PUT recibe customerPublicId, moneda, tasa, impuesto, vigencia, notas, version (PUT) y `items[]`. No acepta totales, número, owner, status ni snapshot de marca. `issue` recibe `{version}` y es idempotente: si ya ISSUED devuelve representación actual; desde otro estado → 409. Cambio de estado recibe `{targetStatus,version,reason?}` y valida grafo.

Implementación P21: V6 está aplicada en `giova_v1`. `QuotationService` mantiene el cálculo autoritativo y el control de propiedad en backend; los ítems guardan SKU, descripción y unidad como snapshots. La secuencia `COT` crea/bloquea la fila anual exacta con `FOR UPDATE`, por lo que dos emisiones concurrentes no comparten número. Al emitir se genera un PDF de texto extraíble con PDFBox 3.0.5, se registra en `stored_documents` y se vincula como `ISSUED_PDF`; la restricción única `(quotation_id, document_role)` impide reemplazar o duplicar el PDF emitido en P21. Las acciones de cotización se insertan en `audit_events`, sin endpoint de modificación/borrado.

Implementación P22: `QuotationsPageComponent` consume estas rutas mediante `SalesApiService` y mantiene el editor de líneas, filtros, detalle, confirmaciones y descarga. La previsualización de la interfaz usa el mismo redondeo visible del backend solo como ayuda; la respuesta persistida del servidor prevalece. Los borradores se guardan temporalmente en `sessionStorage` con moneda, tasa, impuesto, vigencia e ítems; no se guardan cliente, notas ni credenciales. ADMIN y SELLER pueden crear/editar/emitir/anular según ownership; ACCOUNTANT dispone de lectura global. El listado ADMIN acepta `owner=all` sin filtro y conserva respuesta paginada vacía como 200.

V14 añade a `quotations` el tipo de contratación, resultado, motivo/fuente/fecha del resultado, plazo de entrega, garantía y datos de OCAM. A `quotation_items` añade precio de competencia y precio de compra. Los cierres negativos requieren motivo; SELLER registra el resultado y la OCAM de sus cotizaciones. Los filtros nuevos son `procurementType` y `outcome`. El cálculo de fechas hábiles omite sábados y domingos; no incorpora feriados.

## 7. V7 — facturas (P23)

### 7.1 Tablas

`invoices`: `id`, UUID, `invoice_number VARCHAR(40) NOT NULL`, `invoice_type` (`INVOICE`,`RECEIPT`,`OTHER`), `customer_id`, `quotation_id NULL`, `registered_by_worker_id`, `status` (`DRAFT`,`ISSUED`,`CANCELLED`), `issue_date`, `due_date NULL`, moneda/tasa/impuesto/totales como cotización, `notes`, `version`, timestamps. Unique `(invoice_type, invoice_number)`. `ISSUED` y `CANCELLED` son terminales: no se edita el encabezado ni se agregan adjuntos.

`invoice_items`: estructura snapshot equivalente a `quotation_items`; no copiar por referencia mutable. Al crear desde cotización, el servidor copia líneas y calcula los importes en backend. Las líneas manuales y el encabezado se pueden ajustar solo mientras la factura está en `DRAFT`.

`invoice_documents`: `invoice_id`, `document_id`, `document_role` (`PDF`,`XML`,`OTHER`), `active`, timestamps. La operación transaccional bloquea la factura y mantiene como máximo un enlace XML activo; reemplazar crea un nuevo `stored_document`, desactiva el vínculo anterior y registra auditoría. El archivo físico anterior no se sobrescribe.

### 7.2 API

```text
GET|POST /api/v1/accounting/invoices
GET|PUT  /api/v1/accounting/invoices/{publicId}
POST     /api/v1/accounting/invoices/{publicId}/status
POST     /api/v1/accounting/invoices/import/xml/preview   # multipart file; no persiste
POST     /api/v1/accounting/invoices/import/xml           # multipart file; registra y adjunta
POST     /api/v1/accounting/invoices/{publicId}/documents   # multipart file + role + version
GET      /api/v1/accounting/invoices/{publicId}/documents
GET      /api/v1/accounting/invoices/{publicId}/documents/{documentPublicId}/content
```

`POST`, `PUT`, cambio de estado y multipart requieren ADMIN o ACCOUNTANT. El alta acepta líneas manuales o `quotationPublicId`; en el segundo caso el servidor copia los snapshots de la cotización y exige que corresponda al cliente enviado. Solo `DRAFT` admite edición y adjuntos. `GET` de factura, listado documental y descarga autorizan a ADMIN/ACCOUNTANT; SELLER solo si `quotation.owner_worker_id` es propio. El XML exige extensión/MIME coherentes, sintaxis XML segura sin DTD ni entidades externas, y hereda el hash, límites, raíz privada y headers de descarga de P20. No existe endpoint SUNAT ni validación tributaria.

Implementación P23: V7 está aplicada en `giova_v1`; el cálculo usa `BigDecimal` y redondeo a dos decimales por línea, la versión evita actualizaciones perdidas y las acciones de alta/cambio de estado/carga/reemplazo/descarga se auditan como entidad `INVOICE`. La carga opcional admite `INVOICE_PDF`, `INVOICE_XML` u `OTHER_PDF` según el rol documental; no genera ni firma documentos electrónicos.

La importación XML nueva requiere ADMIN o ACCOUNTANT. `preview` valida y muestra los datos extraídos sin guardar; `import/xml` permite crear el cliente cuando falta, adjuntar el XML original y registrar referencias de compra. El parser UBL deshabilita DTD y entidades externas; el contenido extraído es una ayuda de captura y no constituye validación tributaria ni consulta SUNAT. El número de factura duplicado se rechaza.

## 8. V8 — órdenes, entregas y garantías (P24; interfaz en P25)

### 8.1 `purchase_orders` / `purchase_order_items`

Encabezado: UUID, `order_number` unique, customer FK, quotation/invoice nullable, responsible worker, status, issue/expected/delivered dates, moneda/totales, notes, version/timestamps. Ítems snapshot igual patrón. `purchase_order_documents` roles `ORDER_PDF`,`CUSTOMER_ATTACHMENT`,`OTHER`.

### 8.2 `delivery_records`

UUID, `delivery_number` unique, purchase_order FK NOT NULL, customer FK redundante validada para consulta, deliveredAt nullable, receivedBy/name/document nullable, status, notes, version/timestamps. `delivery_documents` roles `SIGNED_ACT`,`PHOTO_PDF`,`OTHER_PDF`; V2 solo admite PDF, no imágenes sueltas.

### 8.3 `warranties`

UUID, customer FK, purchase_order/item opcional, product FK opcional, `serial_number VARCHAR(120) NULL`, `starts_on`, `ends_on` (>= inicio), status, conditions `VARCHAR(1500)`, notes, version/timestamps. `warranty_documents` rol `CERTIFICATE_PDF` u `OTHER_PDF`.

### 8.4 API

```text
GET|POST /api/v1/accounting/purchase-orders
GET|PUT|POST(status) /api/v1/accounting/purchase-orders/{publicId}/**
POST /api/v1/accounting/purchase-orders/{publicId}/documents
GET|POST /api/v1/accounting/deliveries
GET|PUT|POST(status) /api/v1/accounting/deliveries/{publicId}/**
POST /api/v1/accounting/deliveries/{publicId}/documents
GET|POST /api/v1/accounting/warranties
GET|PUT|POST(status) /api/v1/accounting/warranties/{publicId}/**
POST /api/v1/accounting/warranties/{publicId}/documents
```

Usar rutas concretas `/{id}/status`, no controller comodín real `/**`. ADMIN/ACCOUNTANT escriben; SELLER lectura vinculada a cotización propia.

### 8.5 Reglas y filtros implementados en P24

Los listados aceptan `q`, `status`, `customerId`, `quotationId` (órdenes), `purchaseOrderId` (entregas y garantías), `page` y `size` según corresponda. Las órdenes también permiten relación opcional con factura; al copiar una cotización, el servidor conserva snapshots de producto, descripción, cantidad, precio e importe y valida que la cotización pertenezca al cliente indicado. Los importes se calculan con `BigDecimal` y se actualizan con `version` para evitar escrituras perdidas.

Solo `REGISTERED` admite edición de orden; sus transiciones válidas son `REGISTERED → IN_PROGRESS → DELIVERED` y la cancelación desde `REGISTERED` o `IN_PROGRESS`. La entrega inicia en `DRAFT` y solo puede pasar a `SIGNED` o `CANCELLED`; `deliveredAt` no puede ser futuro y la firma exige fecha de entrega. La garantía inicia en `ACTIVE`; puede pasar a `EXPIRED`, `CLAIMED` o `CANCELLED`, y una garantía activa cuya fecha `ends_on` ya venció se refleja como `EXPIRED` al consultar o listar usando la fecha UTC.

Las cargas documentales requieren ADMIN o ACCOUNTANT, PDF válido, límite y versión vigentes. Los roles son `ORDER_PDF`, `CUSTOMER_ATTACHMENT`, `OTHER` para órdenes; `SIGNED_ACT`, `PHOTO_PDF`, `OTHER_PDF` para entregas; y `CERTIFICATE_PDF`, `OTHER_PDF` para garantías. Se reutiliza `DocumentStorageService` de P20: archivo privado con UUID, hash SHA-256, vínculo activo/inactivo y descarga con autorización; Nginx no expone la carpeta. SELLER obtiene únicamente órdenes, entregas y garantías relacionadas con una cotización propia, y no puede mutarlas.

Implementación P24: V8 fue aplicada en `giova_v1`, con FK, índices, restricciones `CHECK`, baja lógica documental y control de concurrencia. La API real verificó CRUD, filtros por cliente/cotización/estado, asociación orden→entrega/garantía, adjuntos y descargas, además de respuestas 401, 403, 404 y 409. No se incorpora inventario, pagos, caja, bancos ni facturación electrónica SUNAT.

### 8.6 Interfaz contable implementada en P25

La interfaz utiliza rutas lazy `/operaciones/contabilidad/facturas`, `/ordenes`, `/entregas` y `/garantias`, protegidas para `ADMIN`, `ACCOUNTANT` y `SELLER`. `AccountingApiService` mantiene los contratos tipados de listados paginados, detalle, alta/edición, transiciones, documentos y descargas; las cargas usan progreso y muestran errores del servidor sin falso éxito. Cada módulo presenta una tabla semántica, filtros y un diálogo de detalle/formulario específico. `ADMIN` y `ACCOUNTANT` escriben según las reglas de P23/P24; `SELLER` solo consulta registros alcanzables desde sus cotizaciones y no ve botones de escritura. No se agregó migración en P25.

Durante la verificación se corrigió en `InvoiceRepository` el literal SQL del carácter de escape usado por MariaDB en el listado de facturas; el contrato HTTP y el esquema V7 permanecen sin cambios.

## 9. V9 — tareas, metas, auditoría y reportes (P26)

### 9.1 `tasks`

UUID, `title VARCHAR(180)`, description 1500, `assigned_to_worker_id`, `created_by_worker_id`, related entity type/id nullable, priority `LOW|MEDIUM|HIGH`, status, dueAt nullable, completedAt nullable, version/timestamps. Usuario lee/actualiza propias; ADMIN todas/asigna. Solo ADMIN cambia asignado después de crear.

`POST /api/v1/tasks` es exclusivo de ADMIN desde P65 (Q-I); ACCOUNTANT y SELLER conservan lectura, edición permitida y cambio de estado según las reglas de propiedad y asignación existentes.

### 9.2 `goals`

UUID, `worker_id`, `goal_type` (`QUOTATIONS_ISSUED`,`QUOTATION_AMOUNT`,`TENDERS_FOLLOWED`,`TASKS_COMPLETED`), periodStart/periodEnd, targetValue decimal, notes, active/version/timestamps. Unique worker+type+period. Solo ADMIN crea/edita; usuario lee propia. Avance se calcula desde datos, no se edita manualmente.

### 9.3 `audit_events`

La tabla se crea inicialmente en V6 porque la emisión de cotizaciones ya debe auditarse; V9 agrega los índices de consulta que falten y la pantalla/API de lectura. Columnas: `id`, event UUID, actor worker nullable para login fallido, role snapshot nullable cuando la identidad aún no tiene una empresa activa, action allowlist, entityType/id nullable, result `SUCCESS|FAILURE`, requestId, `ip_hash` o IP normalizada según política local, summary JSON/text saneado máximo 2000, createdAt. `tenant_id` puede ser NULL para login fallido sin cuenta, acciones de plataforma de SUPERADMIN y acciones de identidad anteriores a seleccionar una membresía. Append-only desde aplicación; sin PUT/DELETE API.

### 9.4 API/reportes

```text
GET|POST /api/v1/tasks
GET|PUT  /api/v1/tasks/{publicId}
POST     /api/v1/tasks/{publicId}/status
GET|POST /api/v1/goals
GET|PUT  /api/v1/goals/{publicId}
GET      /api/v1/admin/audit-events
GET      /api/v1/reports/quotations.xlsx
GET      /api/v1/reports/accounting.xlsx
```

Filtros de reporte: `from`,`to`, estados y moneda. Intervalo máximo 366 días; máximo 50 000 filas; exceder → 422 `REPORT_TOO_LARGE`. ADMIN descarga ambos; ACCOUNTANT solo accounting; SELLER ninguno. Nombre de archivo fijo con fecha UTC. Neutralizar fórmulas.

Implementación P26: V9 está aplicada en `giova_v1` y el arranque posterior no repite la migración. Las tareas validan asignación, ownership, transiciones y `version`; los cambios de estado establecen `completedAt` en el servidor. Las metas conservan su avance calculado por SQL y no aceptan progreso manual. La consulta de auditoría es solo lectura, paginada y no expone `ip_hash`. Los reportes usan Apache POI OOXML 5.5.1, columnas fijas y celdas numéricas/fechas tipadas; el texto que pudiera interpretarse como fórmula se neutraliza antes de escribirlo. No se guardan credenciales ni secretos en el XLSX.

La interfaz lazy expone `/operaciones/trabajo/tareas`, `/metas`, `/auditoria` y `/reportes`; las tablas usan el contrato paginado y los formularios se muestran en diálogos accesibles. El guard de Angular solo mejora la UX: la autorización efectiva permanece en los controllers y servicios backend.

## 10. V10–V11 — licitaciones (P27–P29)

V10 quedó aplicada en `giova_v1` durante P28. La fuente inicial `OECE_OCDS` se crea con la URL configurable documentada y `enabled=true` como configuración persistida; la propiedad efectiva `GIOVA_OECE_ENABLED` permanece `false` por defecto y es la que habilita red y scheduler.

`GIOVA_OECE_BASE_URL` apunta ahora a `https://contratacionesabiertas.oece.gob.pe/api/v1/releases`, el único endpoint del portal que devuelve un release package OCDS verificado (`/api` y `/api/v1` sirven el HTML del portal). `TenderService` propaga la URL configurada a `tender_sources.base_url` en cada consulta de estado, así que basta cambiar la variable de entorno.

V11 siembra el perfil de coincidencia base `Tecnologia y equipamiento TI` (idempotente: solo inserta si `tender_match_profiles` está vacía). Sin al menos un perfil activo el motor no genera coincidencias y `/operaciones/licitaciones` queda vacía aunque la sincronización termine en `SUCCEEDED`.

### 10.1 Tablas

- `tender_sources`: code unique (`OECE_OCDS`), base URL, enabled, lastSuccessfulAt, cursor, version/timestamps. No secretos; API pública.
- `tender_sync_runs`: source FK, trigger `SCHEDULED|MANUAL`, requestedBy nullable, started/finished, status `RUNNING|SUCCEEDED|FAILED`, cursorBefore/After, pages/releases/upserts/matches counts, errorCode/message saneado.
- `tender_releases`: source FK, `release_id`, `ocid`, publishedDate, releaseTag nullable, sourceUrl nullable HTTPS, `source_hash CHAR(64)`, `raw_json LONGTEXT`, firstSeen/lastSeen; unique source+release_id.
- `tender_processes`: source+ocid unique, title, description, buyerName/id, region, classificationCodes JSON/text, procurementMethod, status, currency, amount, tenderStart/end, officialUrl HTTPS, latestReleaseDate, sourceHash, version/timestamps.
- `tender_match_profiles`: UUID, name, include/exclude keywords, buyers, regions, classification codes, min/max amount, active/version y timestamps. Obligatoria; no codificar palabras de GIOVA en Java.
- `tender_matches`: process FK, profile FK, owner worker nullable, score 0..100, reasons JSON/text, state, matchedAt, version; unique `(process_id,profile_id)`.
- `tender_match_actions`: append-only match FK, actor, action, note 500, createdAt.

Guardar JSON original permite reprocesar, pero nunca mostrarlo sin autorización ni insertarlo como HTML. Limitar tamaño por release; si excede, fallar el elemento con métrica, no truncar JSON inválido silenciosamente.

### 10.2 API

```text
GET    /api/v1/tenders/source-status
GET    /api/v1/tenders?state=&q=&buyer=&region=&from=&to=&minScore=&page=&size=
GET    /api/v1/tenders/{publicId}
POST   /api/v1/tenders/sync                       # ADMIN, SELLER; 202 + runId
GET    /api/v1/tenders/sync-runs/{runId}
GET    /api/v1/tenders/match-profiles
POST   /api/v1/tenders/match-profiles             # ADMIN
PUT    /api/v1/tenders/match-profiles/{id}        # ADMIN
POST   /api/v1/tenders/{publicId}/match-state      # ADMIN/SELLER autorizados
```

`sync` devuelve 409 `SYNC_ALREADY_RUNNING` si existe run activo. No mantiene una petición HTTP abierta durante toda la sincronización. Scheduler solo corre si enabled. URL oficial se valida HTTPS y host permitido antes de mostrar.

### 10.3 Contrato P27 verificado antes de persistir

P27 implementa el parser aislado `OcdsReleasePackageParser` y fixtures sintéticos offline. En P28 el mismo parser conserva `rawJson`, extrae clasificación/método/región y acepta `next` textual o `links.next` para paginación. El paquete requiere `uri`, `version`, `publishedDate`, `publisher.name` y un array `releases` no vacío. Cada release requiere `ocid`, `id`, `date`, `tag`, `initiationType`, `tender` y un `tender.title` no vacío como mínimo adicional de búsqueda de GIOVA. `tender.status`, comprador, monto, moneda, periodo y URL de origen son opcionales y se normalizan cuando existen.

El parser ignora propiedades desconocidas y registra las URI de `extensions`. P28 añade el cliente HTTP, pero lo mantiene separado del parser y no realiza red cuando `GIOVA_OECE_ENABLED=false`. El cliente sigue enlaces `next` solo dentro del host configurado, aplica solapamiento de 24 horas cuando no hay cursor, reintenta solo errores transitorios y limita la respuesta a 10 MiB y cada release a 2 MiB. Las coincidencias se recalculan con palabras incluidas/excluidas, comprador, región, clasificación, monto y cierre; las razones se guardan como JSON y nunca se ejecuta IA.

Ajustes verificados contra el portal real:

- OECE acepta `limit`/`since` pero no filtra por ellos y ordena de la release más reciente a la más antigua, así que el cliente deja de seguir `next` en la primera página cuyas releases son todas anteriores a la marca `since` pedida.
- `GIOVA_OECE_MAX_PAGES` (60 por defecto) acota cada ejecución. Al agotar el presupuesto la ejecución termina `SUCCEEDED` y guarda el `next` pendiente en `cursor_after`/`tender_sources.cursor_value`; la siguiente sincronización reanuda desde ahí en lugar de fallar.
- El cotejo de palabras ignora mayúsculas y tildes (`TÓNER` coincide con `toner`), porque los avisos SEACE llegan en mayúsculas con acentuación irregular.
- Solo se persisten las coincidencias que el motor acepta. Una evaluación negativa retira la coincidencia previa únicamente si sigue en `NEW` y nadie registró acciones sobre ella; las decisiones humanas nunca se borran.
- Las releases OECE no traen `url`; la fuente oficial se construye como `/proceso/{ocid}` sobre el host configurado y se valida igual que cualquier enlace externo. La matriz de fuentes, mapeo, paginación, errores/rate limit y resultado de la consulta controlada está en [`docs/referencia/ocds-p27-fuentes.md`](referencia/ocds-p27-fuentes.md).

### 10.4 Interfaz P29

La ruta lazy `/operaciones/licitaciones` está protegida para `ADMIN`, `ACCOUNTANT` y `SELLER` y se incorpora al menú privado. `TenderApiService` conserva los DTOs del contrato P28 para estado de fuente, listado paginado, detalle, ejecuciones y cambios de estado. La pantalla permite buscar por texto, estado, comprador, región, rango de cierre y score mínimo; muestra score/razones, releases, frescura y advertencias de sincronización fallida sin vaciar el histórico. ADMIN y SELLER ven las acciones humanas permitidas por el grafo (`REVIEWING`, `FOLLOWING`, `DISMISSED`, `CLOSED`) y el trigger manual; ACCOUNTANT solo consulta. Los enlaces de fuente usan HTTPS validado por backend, `target="_blank"` y `rel="noopener noreferrer"`. La interfaz no ofrece postulación, envío de ofertas ni automatización SEACE.

## 11. Códigos HTTP y errores

| HTTP | Uso |
|---:|---|
| 200 | lectura/edición/idempotencia ya completada |
| 201 | creación persistida; incluir `Location` |
| 202 | sincronización aceptada en background |
| 204 | logout o acción sin cuerpo |
| 400 | sintaxis/parámetro/archivo mal formado |
| 401 | sin sesión o credenciales genéricamente inválidas |
| 403 | sesión sin rol/permiso o CSRF inválido |
| 404 | id inexistente o ajeno que no debe revelarse |
| 409 | unique, versión, transición o sync concurrente |
| 413 | multipart/cuerpo demasiado grande |
| 415 | media type no admitido |
| 422 | operación válida sintácticamente pero imposible por límites de negocio |
| 429 | rate limit; incluir `Retry-After` |
| 502/503 | proveedor OECE no disponible; no borrar datos anteriores |

Códigos estables sugeridos: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `ACCESS_DENIED`, `NOT_FOUND`, `VERSION_CONFLICT`, `INVALID_STATE_TRANSITION`, `DUPLICATE_BUSINESS_KEY`, `DOCUMENT_TOO_LARGE`, `UNSUPPORTED_DOCUMENT`, `DOCUMENT_STORAGE_ERROR`, `REPORT_TOO_LARGE`, `SYNC_ALREADY_RUNNING`, `TENDER_PROVIDER_ERROR`.

## 12. Reglas transversales de repositorio/service

1. Listas seleccionan columnas explícitas; nunca `SELECT *`.
2. Sort se traduce con allowlist; jamás concatenar valor del cliente como SQL.
3. `LIKE` escapa `%`, `_` y carácter escape.
4. Crear/editar verifica FK activa y permisos en la misma transacción cuando pueda cambiar.
5. Owner/actor se obtiene de sesión.
6. El controller no calcula dinero, decide transiciones ni abre archivos.
7. El repository no decide permisos de rol.
8. DTO de entrada nunca reutiliza entidad/record de salida.
9. Relaciones a documentos se crean solo después de validar metadatos/archivo; compensar fallos.
10. Timestamps de negocio se reciben ISO-8601 con zona cuando son instantes; fechas civiles usan `YYYY-MM-DD`.

### 10.5 Historial comercial de precios

```text
GET /api/v1/sales/price-history/products/{productId}
GET /api/v1/sales/price-history/by-sku?sku=...
```

ADMIN, ACCOUNTANT y SELLER pueden consultar hasta cinco ventas ganadas/perdidas por producto, precio de compra más reciente, estadísticas y una sugerencia explicada. El servicio no decide el precio por el vendedor; evita sugerir un precio por debajo del costo conocido. Las consultas usan datos de cotizaciones, líneas y referencias de compra.

## 13. Orden de migraciones

| Migración | Fase | Contenido |
|---|---|---|
| V1/V2 | completadas | base y catálogo |
| V3 | P17 | `workers.role_code` |
| V4 | P19 | customers y price references |
| V5 | P20 | stored documents (aplicada) |
| V6 | P21 | document sequences, quotations/items/doc links y audit_events mínima (aplicada) |
| V7 | P23 | invoices/items/doc links |
| V8 | P24 | orders/items, deliveries, warranties y doc links |
| V9 | P26 | tasks, goals, índices/consulta de auditoría y reportes |
| V10 | P28 | tender source/runs/releases/processes/profiles/matches/actions |
| V11 | P29 | perfil base de coincidencia |
| V13 | entrega P31-final | datos sintéticos de operaciones de demostración |
| V14 | entrega P31-final | campos de contratación, resultados, OCAM y precios por línea |
| V15 | entrega P31-final | catálogo y datos sintéticos de historial comercial |
| V16 | P51 | multiempresa: tenants, tenant_branding, worker_memberships, tenant_id y backfill a GIOVA (§15); aplicada en `giova_v1` el 2026-10-08 |

No hay una migración V12 en el árbol fuente; se conserva el salto de versiones registrado por Flyway. V13–V15 aparecen aplicadas con éxito en `giova_v1` (consulta de solo lectura del 2026-10-06; MariaDB 10.4.32). No volver a aplicar ni editar migraciones ejecutadas. V16 se aplicó con éxito en `giova_v1` el 2026-10-08 después de un dump verificado; Flyway validó las versiones anteriores, el backfill conservó los conteos existentes y el segundo arranque informó que el esquema estaba al día. Antes de cualquier migración futura contra XAMPP: respaldo, verificación `127.0.0.1:3306`, MariaDB 10.4.32, esquema `giova_v1`, usuario migrador limitado y ausencia de otra instancia.

## 14. Contratos que requieren decisión explícita para cambiar

- Códigos y matriz de roles: ADMIN, ACCOUNTANT y SELLER por empresa, y SUPERADMIN de plataforma (aprobado 2026-10-07).
- Una sola DB `giova_v1` con `tenant_id` y membresías por persona (aprobado 2026-10-07, Q2 y S7).
- Logos y datos fiscales por empresa; sin HTML ni CSS libre subido por la empresa.
- Archivos privados en carpeta, clave relativa en DB y no BLOB.
- Sesión/CSRF, Spring JDBC y MariaDB XAMPP.
- Estados y transiciones documentados.
- Sin stock, pagos, SUNAT, correo ni postulación automática a licitaciones.
- Propiedad seller y lectura contable descritas.
- P17–P31 secuenciales, una fase por ejecución.

## 15. Multiempresa, membresías y superadministrador (contrato P50)

### 15.1 Tablas nuevas y columnas (ejecución en P51; V16)

Los tipos y restricciones siguientes quedan fijados como contrato para P51. Ninguna estructura de esta sección existe en la base antes de aplicar V16.

- `tenants`: `id BIGINT UNSIGNED AUTO_INCREMENT` PK; `public_id CHAR(36)` UNIQUE; `code VARCHAR(30)` UNIQUE (ej. `GIOVA`); `slug VARCHAR(40)` UNIQUE; `legal_name VARCHAR(200)` NOT NULL; `display_name VARCHAR(120)` NOT NULL; `tax_id VARCHAR(20)` NULL; `address VARCHAR(255)` NULL; `contact_email VARCHAR(254)` NULL; `default_currency CHAR(3)` CHECK `PEN`/`USD`; `tax_rate DECIMAL(9,6)`; `active BOOLEAN NOT NULL DEFAULT TRUE`; `version BIGINT UNSIGNED NOT NULL DEFAULT 0`; `created_at`, `updated_at` DATETIME(6) (ver nota de tipos al final de esta sección). Fila inicial: `id=1`, `code='GIOVA'`.
- `tenant_branding`: `tenant_id BIGINT UNSIGNED` PK y FK a `tenants.id`; `primary_color`, `secondary_color`, `accent_color` `CHAR(7)` NOT NULL, validados en Java como `#RRGGBB`; `logo_storage_key VARCHAR(255)` NULL (clave relativa, nunca ruta absoluta); `pdf_header_text VARCHAR(120)` NULL; `version`, `created_at`, `updated_at`.
- `worker_memberships`: `id BIGINT UNSIGNED AUTO_INCREMENT` PK; `worker_id BIGINT` FK a `workers.id` (OJO: `workers.id` es BIGINT con signo desde V1; no usar UNSIGNED); `tenant_id BIGINT UNSIGNED` FK a `tenants.id`; `role_code VARCHAR(20)` CHECK en `ADMIN`,`ACCOUNTANT`,`SELLER`; `enabled BOOLEAN NOT NULL DEFAULT TRUE`; `created_by_worker_id BIGINT` NULL FK a `workers.id`; `version`, `created_at`, `updated_at`. UNIQUE `(worker_id, tenant_id)`; índice `(tenant_id, enabled)`.
- Columnas nuevas en `workers` (P51): `must_change_password BOOLEAN NOT NULL DEFAULT FALSE`; `password_changed_at DATETIME(6)` NULL; `created_by_worker_id BIGINT` NULL FK a `workers.id`. `workers.role_code` amplía su CHECK con `SUPERADMIN` y, tras el backfill, queda NULL salvo SUPERADMIN.
- `tenant_id BIGINT UNSIGNED` se agrega a las tablas de §25.4 del plan (`customers`, `product_price_references`, `products`, `brands`, `categories`, `document_sequences`, `quotations`, `stored_documents`, `invoices`, `purchase_orders`, `delivery_records`, `warranties`, `tasks`, `goals`, `audit_events` [NULL para login fallido sin cuenta, acciones SUPERADMIN de plataforma y acciones de identidad antes de elegir membresía], `tender_match_profiles`, `tender_matches`, `tender_match_actions`, `contact_submissions`, `claims`). Las tablas hijas (`quotation_items`, `invoice_items`, `purchase_order_items`, `*_documents`) heredan el tenant por su cabecera; se agrega `tenant_id` solo si una consulta la necesita directamente. `tender_sources`, `tender_sync_runs`, `tender_releases` y `tender_processes` son compartidas (sin tenant). Las filas existentes reciben `tenant_id = 1` (GIOVA) en el backfill de P51.
- Uniques compuestos aplicados en V16: `customers(tenant_id, document_type, document_number)`; `products(tenant_id, slug)` y `(tenant_id, sku)`; `brands` y `categories` con `(tenant_id, name)` y `(tenant_id, slug)`; `quotations(tenant_id, quotation_number)`; `invoices(tenant_id, invoice_type, invoice_number)`; `purchase_orders(tenant_id, order_number)`; `delivery_records(tenant_id, delivery_number)`; `goals(tenant_id, worker_id, goal_type, period_start, period_end)`; `tender_matches(tenant_id, process_id, profile_id)` con índice propio en `process_id` para conservar su FK; `document_sequences` PK `(tenant_id, document_type, year)`. `warranties` no tiene número correlativo en V8: se identifica por `public_id` y no lleva unique compuesto.
- Tipos de fecha: las tablas de V1–V15 y las de V16 usan `DATETIME(6)` (valores por defecto `CURRENT_TIMESTAMP(6)`), no `TIMESTAMP(6)`. Así se evita el límite de 2038 de `TIMESTAMP` y se mantiene la coherencia con `workers`. Esta decisión, tomada en P51 y aceptada en la auditoría del 2026-10-07, prevalece sobre la convención de §1.

### 15.2 Reglas de aislamiento

1. Empresa activa y rol salen de la sesión (membresía activa). Ningún endpoint de negocio acepta `tenantId` del cliente; si llega en un DTO, se rechaza o se ignora igual que `ownerWorkerId`.
2. Orden de decisión: 401 sin sesión; 403 por rol; 404 para recurso de otra empresa; propiedad dentro de la empresa según §4 (404 o 403).
3. Documentos y logos: el tenant se guarda como columna; la descarga valida tenant antes que rol. Rutas de documentos existentes `tipo/yyyy/MM/uuid.ext` sin mover archivos. Logos nuevos bajo `tenants/{tenant_id}/branding/logo-{uuid}.{ext}`.
4. `contact_submissions` y `claims` pertenecen a GIOVA (`tenant_id = 1`) y la landing no cambia.
5. Auditoría: `/api/v1/admin/audit-events` filtra por empresa activa; `audit_events.tenant_id` solo puede ser NULL en los casos de plataforma o identidad sin empresa activa indicados en §9.3.
6. Reportes XLSX: filtro por tenant antes de la consulta.
7. PDF de cotización: los PDF ya emitidos son instantáneas y no cambian de marca.

### 15.3 Endpoints

- P52: `GET /api/v1/auth/me` devuelve `id`, `username`, `displayName`, membresía activa (`membershipId`, `tenantId`, `tenantCode`, `tenantName`, `roleCode`, `roleLabel`), `membershipSelectionRequired`, `memberships` con esos mismos campos y `mustChangePassword`, sin datos de negocio. Una membresía activa entra directamente; varias dejan rol y empresa activa en `null`, devuelven `membershipSelectionRequired=true` y permiten únicamente consultar `/me`, seleccionar membresía o cerrar sesión hasta elegir. SUPERADMIN no tiene membresías de empresa. `POST /api/v1/auth/membership` acepta `{"membershipId": n}`; el id debe pertenecer a la cuenta y estar habilitado junto con la empresa. El cambio renueva ID de sesión y CSRF, audita y devuelve la identidad con la nueva empresa activa. Una membresía no propia o desactivada responde 401. Ningún endpoint de negocio acepta un `tenantId` del cliente.
- P53: `GET /api/v1/tenant/branding` (ADMIN, ACCOUNTANT, SELLER de empresa; `tenantId`, `displayName`, colores, `pdfHeaderText`, `hasLogo`; `Cache-Control: no-store`, sin ruta de almacenamiento) y `GET /api/v1/tenant/branding/logo` (archivo privado de la empresa activa; 401/403/404; responde PNG rasterizado con `nosniff` y `no-store`). Se admiten solo claves `tenants/{tenantId}/branding/logo-{uuid}.png|jpg|jpeg`; el servidor valida el formato real, limita el archivo a 5 MiB y la imagen a 4096×4096/16 megapíxeles, elimina metadatos al re-encodear a PNG y nunca sirve SVG. La plantilla de cotización toma nombre, cabecera, colores y logo de la empresa activa al emitir; los PDF ya emitidos no se regeneran.
- P55: todos los endpoints `/api/v1/platform/**` requieren `SUPERADMIN` y usan `Cache-Control: no-store`; las respuestas JSON añaden `Pragma: no-cache`. `GET /tenants` lista empresas. `POST /tenants` crea empresa y su marca; el mismo código con el mismo contenido es idempotente (201 al crear, 200 al repetir) y el mismo código con datos distintos devuelve conflicto. `PUT /tenants/{tenantId}` edita datos legales, nombre visible, moneda (`PEN|USD`), IGV (0–1), colores y texto de documentos con `version`; `PATCH /tenants/{tenantId}/status` activa/desactiva con `version`; `PUT /tenants/{tenantId}/logo` recibe PNG/JPEG multipart (máx. 5 MiB; 4096 px/16 MP), se re-encodea como PNG sin metadatos y se almacena en la carpeta privada; `GET /tenants/{tenantId}/logo` entrega el PNG privado con `nosniff` y `no-store`. `GET /tenants/{tenantId}/admins` lista sus ADMIN; `POST /tenants/{tenantId}/admins` crea una identidad nueva o vincula una existente sin revelar memberships de otras empresas; `PATCH /tenants/{tenantId}/admins/{workerId}/status` activa/desactiva su membresía; `POST /tenants/{tenantId}/admins/{workerId}/password-reset` restablece su contraseña. Las altas nuevas y los restablecimientos devuelven la contraseña temporal una sola vez, no la guardan ni registran, y marcan `must_change_password`; una vinculación no genera otra contraseña. La pantalla/cambio obligatorio se completa en P66. Desactivar una empresa impide autenticarse a sus membresías. ADMIN, ACCOUNTANT y SELLER reciben 403 en la API de plataforma; SUPERADMIN no accede a endpoints de negocio.
- P66: ADMIN gestiona únicamente trabajadores de la empresa activa: `GET /api/v1/admin/workers` lista ACCOUNTANT/SELLER; `POST` acepta `{"username":"...","displayName":"...","roleCode":"ACCOUNTANT|SELLER"}` y devuelve 201 con `{worker, temporaryPassword}`; `PUT /{workerId}` recibe `{displayName, version}`; `PATCH /{workerId}/status` recibe `{enabled, version}`; `POST /{workerId}/password-reset` genera otra temporal. Todas las respuestas de administración usan `Cache-Control: no-store` y `Pragma: no-cache`. La contraseña temporal solo aparece en la respuesta de alta/restablecimiento y fuerza el primer cambio; los duplicados devuelven un 409 genérico, las cuentas fuera del tenant un 404 y el ADMIN no puede desactivarse a sí mismo. ACCOUNTANT, SELLER y SUPERADMIN reciben 403. La identidad comparte una contraseña entre empresas; restablecerla fuerza el cambio en el siguiente inicio de sesión desde cualquiera de sus membresías.
- P66: `POST /api/v1/auth/password` recibe `{currentPassword, newPassword}` (12–200 caracteres), valida la contraseña actual, impide reutilizarla y rota la sesión y CSRF; la respuesta marca `mustChangePassword=false`. Mientras ese campo sea verdadero, backend y web permiten solo consultar `/me`, cambiar contraseña o cerrar sesión.
- P56: las rutas de radar no cambian; la respuesta se filtra por empresa activa.
- Errores: se reutilizan `ACCESS_DENIED`, `NOT_FOUND`, `UNAUTHENTICATED`, `VALIDATION_ERROR`, `VERSION_CONFLICT`. No se crean códigos nuevos sin justificarlo en el plan.

### 15.4 Migraciones

- V16 es la única migración prevista para la ampliación multiempresa (P51). Contiene tablas, columnas, backfill a GIOVA, CHECK, FK, índices y uniques compuestos, y el SUPERADMIN en el CHECK. V17 o posteriores solo si se justifica con evidencia y la fase lo autoriza.
- Las migraciones se aplican solo tras respaldo, verificación de `127.0.0.1:3306`, MariaDB 10.4.32, esquema `giova_v1` y autorización de la fase.

### 15.5 Pruebas exigidas por el contrato (se ejecutan en las fases correspondientes)

- Matriz tenant × módulo × rol con 401, 403, 404 cruzado y 200 propio.
- ID de otra empresa en URL, cuerpo y descarga de documentos; intento de cambiar `tenantId` en un DTO.
- Identidad con dos membresías ve solo la empresa activa; cambio de empresa renueva sesión y purga datos; membresía desactivada responde 401 al activarla.
