# Requerimientos funcionales y no funcionales — GIOVA

## 0. Control del documento

- Proyecto: **GIOVA Angular + Spring Boot**.
- Ruta canónica: `C:\Users\moral\OneDrive\Documents\ACTIVIDAD_CAPSTONE\GIOVA-ANGULAR+BACKEND`.
- Elaborado: 2026-09-17.
- Alcance documentado: V1 (P01–P15), V2 (P17–P31) y las fases multiempresa/móvil aprobadas en `docs/PLAN_PROYECTO.md`; cada fase conserva su estado de implementación y verificación en `docs/ESTADO_TRABAJO.md`.
- Este documento **describe** el sistema construido; no lo amplía. La fuente de verdad de alcance y ejecución sigue siendo `docs/PLAN_PROYECTO.md`, y el contrato técnico `docs/DATOS_Y_API.md`.
- Cada requerimiento es verificable contra un endpoint, una tabla, una configuración o una prueba ya ejecutada y registrada en `docs/ESTADO_TRABAJO.md`.

### Documentos relacionados

| Documento | Aporte |
|---|---|
| `docs/PLAN_PROYECTO.md` | Alcance aprobado, matriz de permisos, fases y verificación final |
| `docs/DATOS_Y_API.md` | Contrato de datos, tablas, endpoints y códigos de error |
| `docs/GRAFOS.md` | Arquitectura, modelo entidad-relación, flujo de sesión y dependencias de fases |
| `docs/ESTADO_TRABAJO.md` | Evidencia de pruebas, hallazgos y decisiones duraderas |
| `docs/TOOLCHAIN.md` | Versiones fijadas y comandos verificados |
| `docs/referencia/ocds-p27-fuentes.md` | Contrato y límites de la fuente OECE/OCDS |

### Convenciones

- Roles fijos: `ADMIN` (Administrador), `ACCOUNTANT` (Contador) y `SELLER` (Vendedor) por membresía; `SUPERADMIN` administra la plataforma y no tiene membresía empresarial. No hay editor dinámico de permisos.
- "Propio" significa `owner_worker_id` igual al trabajador autenticado. `ADMIN` no está limitado por propiedad.
- Identificación: `RF-<MÓDULO>-<n>` para requerimientos funcionales, `RNF-<CATEGORÍA>-<n>` para no funcionales.
- Respuesta paginada estándar: `{items,totalItems,page,size,totalPages}`; `page` inicia en 0 y `size` va de 1 a 100.
- Formato de error estándar: `{code,message,requestId,fieldErrors?}`.

---

# Parte I — Requerimientos funcionales

## 1. Sitio público (RF-PUB)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-PUB-01 | El sistema debe publicar el catálogo de productos y servicios sin requerir autenticación. | Visitante | `GET /api/v1/catalog/products` responde 200 con las 52 entradas sembradas. |
| RF-PUB-02 | El sistema debe permitir navegar el catálogo por categoría y por marca. | Visitante | `GET /api/v1/catalog/categories` (8) y `GET /api/v1/catalog/brands` (14) responden 200. |
| RF-PUB-03 | El sistema debe registrar mensajes de contacto y devolver una referencia única al remitente. | Visitante | `POST /api/v1/contact-submissions` persiste en `contact_submissions` con `reference` y `request_id` únicos. |
| RF-PUB-04 | El sistema debe registrar reclamos del Libro de Reclamaciones con su referencia. | Visitante | `POST /api/v1/claims` persiste en `claims` con `reference` única. |
| RF-PUB-05 | Los envíos públicos deben ser idempotentes por `requestId` y estar limitados por frecuencia. | Visitante | Reenvío con el mismo `requestId` no duplica la fila; superar 20 envíos en 10 minutos devuelve 429 con `Retry-After`. |
| RF-PUB-06 | El contacto por WhatsApp debe ser un enlace de apertura manual, nunca un envío automático. | Visitante | El frontend genera el enlace; no existe integración de mensajería saliente. |

## 2. Autenticación, sesión y roles (RF-AUT)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-AUT-01 | El sistema debe autenticar trabajadores por usuario y contraseña contra `workers`. | Trabajador | `POST /api/v1/auth/login` devuelve 200 y cookie de sesión HttpOnly. |
| RF-AUT-02 | El sistema debe entregar y exigir token CSRF en toda escritura privada. | Trabajador | `GET /api/v1/auth/csrf`; una escritura sin token devuelve 403. |
| RF-AUT-03 | El sistema debe renovar el identificador de sesión al iniciar sesión. | Sistema | `sessionFixation().changeSessionId()` en `SecurityConfiguration`. |
| RF-AUT-04 | El sistema debe exponer la identidad, el rol y el contexto de empresa autorizado sin revelar datos sensibles. | Usuario autenticado | `GET /api/v1/auth/me` devuelve identidad y membresías permitidas; no incluye hash, authorities internas ni sessionId. SUPERADMIN no tiene membresías empresariales. |
| RF-AUT-05 | El sistema debe cerrar sesión invalidándola en el servidor. | Trabajador | `POST /api/v1/auth/logout` devuelve 204 y la sesión deja de ser válida. |
| RF-AUT-06 | El sistema debe soportar cuatro roles fijos, sin editor dinámico de permisos. | Sistema | `ADMIN`, `ACCOUNTANT` y `SELLER` se fijan en `worker_memberships`; `SUPERADMIN` se fija en `workers.role_code`; no existen tablas `roles` ni `permissions`. |
| RF-AUT-07 | Una cuenta deshabilitada, sin membresía empresarial activa ni rol de plataforma válido no debe iniciar sesión con permisos. | Sistema | Login fail-closed; no se asigna `ADMIN` por defecto. |
| RF-AUT-08 | El aprovisionamiento local debe crear una identidad SUPERADMIN solo con confirmación y sin empresa; los roles de empresa requieren una empresa activa. | Operador | `scripts/create-worker.ps1`; `scripts/set-worker-role.ps1` está deshabilitado desde V16 y no conecta a DB. La gestión web de ADMIN corresponde a P55 y la de ACCOUNTANT/SELLER a P66. |
| RF-AUT-09 | Los intentos fallidos de inicio de sesión deben limitarse por frecuencia. | Sistema | `LoginRateLimiter`: 5 intentos por 60 s; excedente devuelve 429 con `Retry-After`. |
| RF-AUT-10 | La autorización efectiva debe residir en el backend; los guards de Angular son solo UX. | Sistema | `authGuard` redirige a login con `returnUrl` y `roleGuard` a `/403`, pero la API devuelve 401/403 igual ante una URL escrita a mano. |

### 2.1 Administración de plataforma (RF-PLA, P55)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-PLA-01 | SUPERADMIN debe gestionar empresas y su marca sin acceso a datos de negocio. | SUPERADMIN | `/api/v1/platform/**` requiere SUPERADMIN; los endpoints de negocio rechazan SUPERADMIN con 403. |
| RF-PLA-02 | La empresa debe guardar código, slug, razón social, nombre visible, identificador fiscal opcional, dirección, correo, moneda, IGV y marca. | SUPERADMIN | `POST/PUT /api/v1/platform/tenants`; validaciones, unicidad e idempotencia por código documentadas en `DATOS_Y_API.md` §15.3. |
| RF-PLA-03 | Los logos de empresa deben almacenarse como imágenes rasterizadas en la carpeta privada. | SUPERADMIN | `PUT /api/v1/platform/tenants/{id}/logo` valida PNG/JPEG, limita tamaño/dimensiones, elimina metadatos mediante re-encodeado PNG y no sirve SVG. |
| RF-PLA-04 | SUPERADMIN debe administrar solo membresías ADMIN de una empresa. | SUPERADMIN | `/api/v1/platform/tenants/{id}/admins` crea/vincula identidades; estado y restablecimiento afectan la membresía del tenant indicado. Al crear una identidad o restablecer la contraseña, la temporal aparece una vez y queda marcado el cambio obligatorio para P66; vincular una identidad existente no genera contraseña. |

## 3. Catálogo interno (RF-CAT)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-CAT-01 | ADMIN debe poder crear, editar y activar o desactivar marcas, categorías y productos. | ADMIN | `/api/v1/admin/brands`, `/categories`, `/products` con GET/POST/PUT/PATCH. |
| RF-CAT-02 | ACCOUNTANT y SELLER deben tener solo lectura del catálogo. | ACCOUNTANT, SELLER | Cualquier mutación en `/api/v1/admin/**` devuelve 403 por `hasRole("ADMIN")`. |
| RF-CAT-03 | Las bajas deben ser lógicas, no físicas. | ADMIN | Columna `active`; la interfaz no ofrece borrado físico. |
| RF-CAT-04 | Toda edición debe usar bloqueo optimista por `version`. | ADMIN | UPDATE con `WHERE id=:id AND version=:version`; cero filas devuelve 409 `VERSION_CONFLICT` si el registro existe y 404 si no. |

## 4. Clientes (RF-CLI)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-CLI-01 | El sistema debe registrar clientes de tipo persona o empresa con tipo y número de documento. | ADMIN, SELLER | `POST /api/v1/sales/customers`; `customer_type` en `PERSON` o `COMPANY`. |
| RF-CLI-02 | El sistema debe validar el documento según su tipo. | Sistema | DNI exactamente 8 dígitos; RUC exactamente 11; CE u OTHER de 3 a 20 alfanuméricos o guion. |
| RF-CLI-03 | El sistema debe impedir clientes duplicados por tipo y número de documento. | Sistema | UNIQUE `(document_type, document_number)`; duplicado devuelve 409 `CUSTOMER_DOCUMENT_EXISTS`. |
| RF-CLI-04 | SELLER debe crear, leer y editar únicamente sus clientes, y no debe poder desactivarlos. | SELLER | Filtro por `owner_worker_id`; `PATCH /{publicId}/status` restringido a ADMIN. |
| RF-CLI-05 | ACCOUNTANT debe tener lectura de todos los clientes. | ACCOUNTANT | `GET /api/v1/sales/customers?owner=all` autorizado solo en lectura. |
| RF-CLI-06 | El propietario nunca debe aceptarse desde el navegador; se obtiene de la sesión. | Sistema | POST y PUT ignoran `ownerWorkerId`, `createdBy` y `updatedBy` enviados por el cliente. |
| RF-CLI-07 | El listado debe permitir búsqueda por texto, estado y alcance de propiedad, con paginación. | Todos | `?q=&active=&owner=me|all&page=&size=` con el contrato paginado estándar. |

## 5. Referencias de precio (RF-PRE)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-PRE-01 | El sistema debe registrar referencias históricas de precio por producto, con proveedor, moneda, impuesto y fecha de vigencia. | ADMIN, SELLER | `POST /api/v1/sales/price-references`. |
| RF-PRE-02 | El precio en USD debe exigir tipo de cambio mayor que cero; en PEN debe ser nulo. | Sistema | `exchange_rate_to_pen` obligatorio solo si `currency_code = 'USD'`. |
| RF-PRE-03 | El histórico debe ser inmutable para SELLER; solo ADMIN corrige por endpoint explícito. | SELLER, ADMIN | Sin UPDATE ni DELETE para SELLER; `PUT /api/v1/sales/price-references/{id}` es ADMIN. |
| RF-PRE-04 | Las referencias no deben alterar el catálogo ni comportarse como inventario. | Sistema | No se añade columna de precio a `products`; no existen movimientos de stock. |
| RF-PRE-05 | El listado debe filtrar por producto y rango de fechas, con paginación. | Todos | `?productId=&from=&to=&page=&size=`. |

## 6. Cotizaciones y PDF (RF-COT)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-COT-01 | El sistema debe permitir crear cotizaciones en borrador con una o más líneas. | ADMIN, SELLER | `POST /api/v1/sales/quotations` con `items[]`; estado inicial `DRAFT`. |
| RF-COT-02 | Solo el estado `DRAFT` debe ser editable. | Sistema | PUT sobre una cotización emitida o terminal devuelve 409 `INVALID_STATE_TRANSITION`. |
| RF-COT-03 | El backend debe calcular subtotal, impuesto y total, y nunca aceptarlos del cliente. | Sistema | Con `includesTax=false`: base = cantidad × precio, impuesto = base × tasa. Con `true`: base = total / (1 + tasa). Redondeo HALF_UP a 2 decimales. |
| RF-COT-04 | Las líneas deben guardar instantáneas de SKU, descripción y unidad. | Sistema | `sku_snapshot`, `description_snapshot`, `unit_snapshot`; cambiar el producto después no altera el documento. |
| RF-COT-05 | La emisión debe asignar un correlativo anual único con formato `COT-YYYY-000001`. | ADMIN, SELLER | Fila de `document_sequences` bloqueada con `SELECT ... FOR UPDATE`; prohibido `MAX(id)+1`, conteo de filas, memoria JVM o UUID como número visible. |
| RF-COT-06 | La emisión debe generar y almacenar el PDF de la cotización como documento privado. | Sistema | PDFBox 3.0.5; registro en `stored_documents` y vínculo `ISSUED_PDF`; UNIQUE `(quotation_id, document_role)` impide duplicarlo o reemplazarlo. |
| RF-COT-07 | La emisión debe ser idempotente. | ADMIN, SELLER | `POST /{publicId}/issue` sobre una cotización ya emitida devuelve la representación actual; desde otro estado devuelve 409. |
| RF-COT-08 | El sistema debe respetar el grafo `DRAFT → ISSUED → ACCEPTED \| REJECTED \| EXPIRED \| CANCELLED`, sin retroceso desde un estado terminal. | Sistema | `POST /{publicId}/status` recibe `{targetStatus, version, reason?}` y valida el grafo. |
| RF-COT-09 | La descarga del PDF debe autorizarse por rol y por propiedad. | Todos | `GET /{publicId}/pdf`: ADMIN y ACCOUNTANT acceden a todas; SELLER solo a las propias. |
| RF-COT-10 | El listado debe filtrar por texto, estado, cliente, propiedad y rango de fechas. | Todos | `?q=&status=&customerId=&owner=me|all&from=&to=&page=&size=`. |

## 7. Facturas y XML (RF-FAC)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-FAC-01 | El sistema debe registrar facturas internas con líneas manuales o copiadas desde una cotización. | ADMIN, ACCOUNTANT | `POST /api/v1/accounting/invoices` con `quotationPublicId` opcional. |
| RF-FAC-02 | Al copiar desde una cotización, el servidor debe validar que corresponda al cliente indicado y copiar los snapshots. | Sistema | Error si la cotización no pertenece al cliente enviado; los importes se recalculan en backend. |
| RF-FAC-03 | El número de factura debe ser único por tipo de documento. | Sistema | UNIQUE `(invoice_type, invoice_number)`; duplicado devuelve 409 `DUPLICATE_BUSINESS_KEY`. |
| RF-FAC-04 | Solo `DRAFT` debe admitir edición y adjuntos; `ISSUED` y `CANCELLED` son terminales. | Sistema | Verificado en P23 con 409 al intentar editar en estado terminal. |
| RF-FAC-05 | El sistema debe permitir adjuntar PDF y XML de respaldo a la factura. | ADMIN, ACCOUNTANT | `POST /{publicId}/documents` multipart con `role` y `version`; roles `PDF`, `XML`, `OTHER`. |
| RF-FAC-06 | Debe existir como máximo un vínculo XML activo; reemplazarlo crea un documento nuevo y desactiva el anterior sin sobrescribir el archivo físico. | Sistema | La transacción bloquea la factura y registra auditoría. |
| RF-FAC-07 | SELLER solo debe leer las facturas alcanzables desde sus cotizaciones propias. | SELLER | Acceso ajeno devuelve 404; cualquier escritura devuelve 403. |
| RF-FAC-08 | El sistema no debe firmar, transmitir ni validar tributariamente ante SUNAT. | Sistema | No existe endpoint SUNAT; el XML es evidencia adjunta y descargable. |

## 8. Órdenes de compra, entregas y garantías (RF-OEG)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-OEG-01 | El sistema debe registrar órdenes de compra con número único, cliente, líneas snapshot y relación opcional a cotización y factura. | ADMIN, ACCOUNTANT | `POST /api/v1/accounting/purchase-orders`. |
| RF-OEG-02 | La orden debe seguir `REGISTERED → IN_PROGRESS → DELIVERED`, con cancelación desde `REGISTERED` o `IN_PROGRESS`. | Sistema | `POST /{publicId}/status` valida el grafo; solo `REGISTERED` es editable. |
| RF-OEG-03 | El sistema debe registrar actas de entrega asociadas a una orden. | ADMIN, ACCOUNTANT | `POST /api/v1/accounting/deliveries`; `purchase_order_id` NOT NULL. |
| RF-OEG-04 | La entrega debe seguir `DRAFT → SIGNED \| CANCELLED`; `deliveredAt` no puede ser futura y firmar exige fecha de entrega. | Sistema | Validado en backend durante P24. |
| RF-OEG-05 | "Firmada" debe significar únicamente que se registró el acta firmada fuera del sistema. | Sistema | GIOVA no realiza firma digital certificada. |
| RF-OEG-06 | El sistema debe registrar garantías con vigencia, estado y vínculo opcional a orden, ítem y producto. | ADMIN, ACCOUNTANT | `POST /api/v1/accounting/warranties`; `ends_on >= starts_on`. |
| RF-OEG-07 | Una garantía activa cuya vigencia ya venció debe reflejarse como `EXPIRED` de forma determinista. | Sistema | Cálculo con fecha UTC al consultar y al listar. |
| RF-OEG-08 | Los tres módulos deben permitir adjuntar documentos PDF con roles definidos. | ADMIN, ACCOUNTANT | Órdenes: `ORDER_PDF`, `CUSTOMER_ATTACHMENT`, `OTHER`. Entregas: `SIGNED_ACT`, `PHOTO_PDF`, `OTHER_PDF`. Garantías: `CERTIFICATE_PDF`, `OTHER_PDF`. |
| RF-OEG-09 | SELLER solo debe leer los registros alcanzables desde sus cotizaciones propias, sin poder mutarlos. | SELLER | Interfaz sin botones de escritura; API responde 403 o 404. |
| RF-OEG-10 | Los listados deben filtrar por texto, estado, cliente, cotización y orden. | Todos | `?q=&status=&customerId=&quotationId=&purchaseOrderId=&page=&size=`. |

## 9. Documentos privados (RF-DOC)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-DOC-01 | Los archivos deben guardarse en una carpeta privada del backend; la base guarda solo clave relativa y metadatos. | Sistema | `storage_key` con formato `tipo/yyyy/MM/<uuid>.<ext>`; nunca BLOB ni ruta absoluta. |
| RF-DOC-02 | No debe existir un endpoint genérico de carga; cada entidad recibe su propio multipart. | Sistema | El único endpoint genérico es `GET /api/v1/documents/{publicId}/content`. |
| RF-DOC-03 | El PDF debe validarse por extensión, MIME y firma `%PDF-`, con tamaño máximo. | Sistema | Límite `GIOVA_DOCUMENTS_MAX_BYTES`; el exceso devuelve 413 `DOCUMENT_TOO_LARGE`. |
| RF-DOC-04 | El XML debe parsearse sin DTD ni entidades externas. | Sistema | XXE verificado en P31: multipart con entidad externa devolvió 400 `DOCUMENT_INVALID`. |
| RF-DOC-05 | El sistema debe rechazar nombres vacíos, doble extensión engañosa, `..`, separadores y NUL. | Sistema | El nombre físico es siempre UUID; el original solo se muestra saneado en `Content-Disposition`. |
| RF-DOC-06 | El sistema debe calcular y almacenar el SHA-256 de cada archivo. | Sistema | Columna `sha256_hex CHAR(64)`. |
| RF-DOC-07 | La escritura debe ser temporal y atómica, con compensación si falla la transacción. | Sistema | El rollback elimina el archivo movido. |
| RF-DOC-08 | La descarga debe autenticarse, autorizarse por rol y propiedad, y no redirigir a una ruta física. | Todos | Cabeceras `Content-Disposition: attachment`, `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`; ajeno devuelve 403 e inexistente o inactivo devuelve 404. |
| RF-DOC-09 | Nginx no debe publicar la carpeta de documentos. | Sistema | `scripts/verify-private-documents.ps1` aprobado en P30. |
| RF-DOC-10 | Un documento asociado no debe reemplazarse físicamente; se agrega una versión nueva y se marca la anterior. | Sistema | Vínculos documentales con columna `active`. |

## 10. Tareas y metas (RF-TRA)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-TRA-01 | El sistema debe gestionar tareas con título, descripción, prioridad, vencimiento y entidad relacionada opcional. | Todos | `GET\|POST /api/v1/tasks`; prioridad `LOW`, `MEDIUM` o `HIGH`. |
| RF-TRA-02 | La tarea debe seguir `PENDING → IN_PROGRESS → DONE \| CANCELLED`, con `completedAt` fijado por el servidor. | Sistema | `POST /api/v1/tasks/{publicId}/status`. |
| RF-TRA-03 | Cada usuario debe ver y actualizar sus tareas propias o asignadas; solo ADMIN ve todas y reasigna. | Todos | El cambio de asignado posterior al alta está restringido a ADMIN. |
| RF-TRA-04 | El sistema debe gestionar metas por trabajador, tipo y periodo. | ADMIN | Tipos `QUOTATIONS_ISSUED`, `QUOTATION_AMOUNT`, `TENDERS_FOLLOWED`, `TASKS_COMPLETED`; UNIQUE por trabajador, tipo y periodo. |
| RF-TRA-05 | El avance de la meta debe calcularse desde los datos y nunca editarse manualmente. | Sistema | Cálculo por SQL en backend; no existe campo de progreso editable. |
| RF-TRA-06 | Solo ADMIN crea y edita metas; cada trabajador lee las propias. | ADMIN, Todos | `POST\|PUT /api/v1/goals` restringido a ADMIN. |

## 11. Auditoría y reportes (RF-AUD)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-AUD-01 | El sistema debe registrar eventos funcionales críticos en una bitácora append-only. | Sistema | Tabla `audit_events`; no existen endpoints PUT ni DELETE. |
| RF-AUD-02 | Cada evento debe guardar actor, rol, acción de allowlist, tipo e id de entidad, resultado, `requestId` y marca de tiempo. | Sistema | `result` en `SUCCESS` o `FAILURE`; resumen saneado de hasta 2000 caracteres. |
| RF-AUD-03 | La auditoría debe cubrir login exitoso y fallido, asignación local de rol, altas y cambios de estado, emisión y anulación de cotización, carga, descarga y baja lógica de documento, exportación y sincronización manual. | Sistema | Acciones declaradas en el plan y verificadas en P26. |
| RF-AUD-04 | La bitácora no debe guardar contraseñas, cookies, token CSRF, documentos completos ni cuerpos de contacto o reclamación. | Sistema | Política de privacidad de auditoría del plan. |
| RF-AUD-05 | ADMIN debe consultar la auditoría paginada, sin exponer `ip_hash`. | ADMIN | `GET /api/v1/admin/audit-events`. |
| RF-AUD-06 | El sistema debe exportar reportes XLSX de cotizaciones y de contabilidad. | ADMIN, ACCOUNTANT | `GET /api/v1/reports/quotations.xlsx` y `GET /api/v1/reports/accounting.xlsx` con Apache POI OOXML 5.5.1. |
| RF-AUD-07 | ADMIN descarga ambos reportes, ACCOUNTANT solo el contable y SELLER ninguno. | Sistema | 403 para SELLER verificado con Playwright en P26. |
| RF-AUD-08 | Los reportes deben limitarse en intervalo y volumen. | Sistema | Máximo 366 días y 50 000 filas; el exceso devuelve 422 `REPORT_TOO_LARGE`. |
| RF-AUD-09 | El XLSX debe neutralizar fórmulas. | Sistema | Todo texto que inicie con `=`, `+`, `-` o `@` se escribe como texto seguro. |

## 12. Radar de licitaciones OECE/OCDS (RF-LIC)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-LIC-01 | El sistema debe consultar exclusivamente la API pública OCDS de Contrataciones Abiertas de OECE. | Sistema | `https://contratacionesabiertas.oece.gob.pe/api/v1/releases`, único endpoint del portal que devuelve un release package. |
| RF-LIC-02 | La integración debe estar deshabilitada por defecto y activarse solo por configuración. | Operador | `GIOVA_OECE_ENABLED=false`; el scheduler solo se ejecuta si está habilitada. |
| RF-LIC-03 | El sistema debe validar el paquete OCDS antes de persistir. | Sistema | Requiere `uri`, `version`, `publishedDate`, `publisher.name` y `releases[]` no vacío; cada release exige `ocid`, `id`, `date`, `tag`, `initiationType` y `tender.title` no vacío. |
| RF-LIC-04 | El sistema debe conservar el JSON original junto con los campos normalizados. | Sistema | `raw_json` y `source_hash`; límite de 10 MiB por respuesta y 2 MiB por release. |
| RF-LIC-05 | La sincronización debe ser idempotente por fuente y `release_id`, y consolidar procesos por OCID. | Sistema | UNIQUE `(source_id, release_id)`; los releases históricos no se pierden. |
| RF-LIC-06 | Cada ejecución debe registrarse con estado, cursor y métricas. | Sistema | `tender_sync_runs`: `RUNNING → SUCCEEDED \| FAILED`, con páginas, releases, upserts y coincidencias. |
| RF-LIC-07 | Debe existir una sola sincronización concurrente. | Sistema | `POST /api/v1/tenders/sync` con un run activo devuelve 409 `SYNC_ALREADY_RUNNING`. |
| RF-LIC-08 | La sincronización manual debe responder 202 con `runId`, sin mantener abierta la petición HTTP. | ADMIN, SELLER | `GET /api/v1/tenders/sync-runs/{runId}` permite consultar el avance. |
| RF-LIC-09 | Los errores transitorios deben reintentarse con backoff, y un fallo no debe borrar datos previos. | Sistema | Máximo 3 reintentos ante 429, 5xx o timeout respetando `Retry-After`; los 4xx contractuales no se reintentan. |
| RF-LIC-10 | El presupuesto de páginas debe acotar cada ejecución y guardar cursor de continuación. | Sistema | `GIOVA_OECE_MAX_PAGES` (60 por defecto); al agotarlo la ejecución termina `SUCCEEDED` y reanuda desde `cursor_after`. |
| RF-LIC-11 | Las coincidencias deben calcularse con perfiles configurables y puntuación determinista y explicable. | ADMIN | Palabras incluidas y excluidas, comprador, región, clasificación, monto y rango de cierre; `score` de 0 a 100 con `reasons` persistidas. |
| RF-LIC-12 | El cotejo de palabras debe ignorar mayúsculas y tildes. | Sistema | `TÓNER` coincide con `toner`. |
| RF-LIC-13 | La recalculación no debe borrar decisiones humanas. | Sistema | Solo se retira una coincidencia que siga en `NEW` y sin acciones registradas. |
| RF-LIC-14 | ADMIN y SELLER deben poder marcar coincidencias como `REVIEWING`, `FOLLOWING`, `DISMISSED` o `CLOSED`. | ADMIN, SELLER | `POST /api/v1/tenders/{publicId}/match-state`; acciones append-only en `tender_match_actions`. |
| RF-LIC-15 | ACCOUNTANT debe tener acceso de solo lectura al radar. | ACCOUNTANT | Sin trigger manual ni acciones de cambio de estado. |
| RF-LIC-16 | La interfaz debe mostrar la fuente, la frescura y la última sincronización exitosa, sin presentar datos viejos como actuales. | Todos | `GET /api/v1/tenders/source-status`; advertencia visible si la última ejecución falló, sin vaciar el histórico. |
| RF-LIC-17 | Cada oportunidad debe enlazar a la fuente oficial validada por HTTPS y host permitido. | Todos | Enlace `/proceso/{ocid}` con `target="_blank"` y `rel="noopener noreferrer"`. |
| RF-LIC-18 | El sistema no debe postular, enviar ofertas, iniciar sesión, resolver CAPTCHA ni automatizar SEACE. | Sistema | Sin endpoints ni interfaz para ello; verificado en P29 y P31. |
| RF-LIC-19 | Debe existir al menos un perfil de coincidencia activo para que el radar produzca resultados. | Operador | V11 siembra el perfil base `Tecnologia y equipamiento TI` de forma idempotente. |

## 13. Operación local (RF-OPE)

| ID | Requerimiento | Actor | Criterio verificable |
|---|---|---|---|
| RF-OPE-01 | El sistema debe exponer endpoints de salud y readiness. | Operador | `GET /health` y `GET /api/v1/health/readiness` responden 200 con `READY` y `database: UP`. |
| RF-OPE-02 | El esquema debe versionarse con migraciones Flyway solo hacia delante. | Operador | V1 a V11 aplicadas; nunca `clean`; el segundo arranque es idempotente. |
| RF-OPE-03 | El respaldo debe cubrir el mismo checkpoint lógico de base y documentos. | Operador | `scripts/backup-local.ps1` genera el dump SQL y la carpeta de documentos. |
| RF-OPE-04 | La restauración debe ejecutarse siempre sobre base y volumen desechables, verificando filas y SHA-256. | Operador | `scripts/restore-local.ps1`; P30 verificó 8 filas y 8 archivos con hashes coincidentes. |
| RF-OPE-05 | El empaquetado de entrega debe usar lista blanca y excluir secretos, respaldos, documentos y builds. | Operador | `scripts/package-delivery.ps1` genera el ZIP y su archivo `.sha256`. |

## 14. Exclusiones explícitas

Documentar lo excluido forma parte del requerimiento, porque delimita el sistema y evita expectativas incorrectas.

| Excluido | Motivo declarado |
|---|---|
| Inventario, stock y kardex | Fuera del alcance aprobado de V2; no se crean tablas, movimientos ni pantallas |
| Pagos, caja, bancos y conciliación | Solo se mantiene el estado documental de factura y pedido |
| Facturación electrónica y SUNAT | El XML se adjunta y consulta; no se firma, transmite ni valida |
| Correo, WhatsApp automático y SMS | No se envían mensajes externos |
| Portal de clientes y registro público | Las cuentas se crean por comando local seguro |
| Gestión web de trabajadores | La asignación de rol es una operación local auditada |
| Postulación automática a licitaciones y CAPTCHA | El radar es de solo lectura con enlace a la fuente oficial |
| Hosting público o despliegue | La entrega es local; publicar requiere autorización separada |
| Editor dinámico de roles y permisos | El conjunto de roles es fijo y está restringido en base |
| BI y dashboards financieros avanzados | Solo exportación XLSX de reportes autorizados |

---

# Parte II — Requerimientos no funcionales

## 15. Seguridad (RNF-SEG)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-SEG-01 | Autenticación por sesión con cookie HttpOnly y SameSite=Lax; prohibido JWT o tokens en `localStorage`. | Inspección de cookies y ausencia de token en el almacenamiento del navegador. |
| RNF-SEG-02 | Protección CSRF obligatoria en toda escritura privada. | Escritura sin token devuelve 403. |
| RNF-SEG-03 | Autorización en dos niveles: fronteras por prefijo en `SecurityConfiguration` y propiedad o transición en el servicio. | Una regla global `authenticated()` no basta; `@PreAuthorize` aplica la matriz de roles por endpoint. |
| RNF-SEG-04 | Toda consulta debe usar SQL parametrizado, sin `SELECT *` ni concatenación de entrada del cliente. | `NamedParameterJdbcTemplate` con columnas explícitas; `ORDER BY` por allowlist; `LIKE` con escape de `%` y `_`. |
| RNF-SEG-05 | Defensa comprobada contra IDOR: un recurso ajeno responde 403 o 404 sin revelar su existencia. | Matriz real de roles verificada en P24, P30 y P31. |
| RNF-SEG-06 | Defensa contra XXE y path traversal en toda carga de archivos. | XXE autenticado devolvió 400 `DOCUMENT_INVALID`, con evidencia en `output/p31/xxe-authenticated-response.txt`; las rutas se resuelven con `normalize()` dentro de la raíz configurada. |
| RNF-SEG-07 | Límite de frecuencia en login y en formularios públicos. | 5 intentos por minuto en login; 20 solicitudes por 10 minutos en formularios, ambos con `Retry-After`. |
| RNF-SEG-08 | Los errores nunca deben exponer SQL, stack trace, rutas físicas ni `rejectedValue`. | Formato de error `{code, message, requestId, fieldErrors?}`. |
| RNF-SEG-09 | Secretos fuera del código, la documentación, los logs, las imágenes Docker y los comandos visibles. | `.env` ignorado por Git; contraseñas solicitadas de forma interactiva; verificado en P31. |
| RNF-SEG-10 | Análisis estático sin hallazgos en el código propio antes de la entrega. | Semgrep P31: 255 archivos, 91 reglas, 0 hallazgos y 0 errores; `npm audit` con 0 vulnerabilidades. |
| RNF-SEG-11 | Límites de cuerpo y de página en la API. | JSON hasta 64 KiB, página hasta 100 elementos, multipart con límite propio. |
| RNF-SEG-12 | Cabeceras seguras en las descargas, con una sola aparición de `X-Content-Type-Options`. | Corregido con `proxy_hide_header` en Nginx y verificado en P31. |

## 16. Integridad y consistencia de datos (RNF-DAT)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-DAT-01 | Esquema único `giova_v1` en MariaDB 10.4.32, `utf8mb4` y marcas de tiempo en UTC. | No se crean bases adicionales ni se modifican otras bases. |
| RNF-DAT-02 | Todo importe usa `DECIMAL(19,4)` en base y `BigDecimal` en Java; prohibido `float` y `double`. | Redondeo `HALF_UP` a 2 decimales al totalizar, conservando hasta 4 decimales en precio y cantidad. |
| RNF-DAT-03 | Control de concurrencia optimista en toda entidad mutable. | Columna `version` en cada UPDATE; conflicto devuelve 409 `VERSION_CONFLICT`. |
| RNF-DAT-04 | Los correlativos no deben colisionar bajo concurrencia. | Fila de `document_sequences` bloqueada con `FOR UPDATE` dentro de la misma transacción. |
| RNF-DAT-05 | Los estados son cerrados, se validan en backend y un estado terminal no retrocede. | Siete grafos documentados: cotización, factura, orden, entrega, garantía, tarea y coincidencia de licitación. |
| RNF-DAT-06 | Bajas lógicas, sin borrado físico desde la interfaz. | Columna `active` o estado cerrado. |
| RNF-DAT-07 | Los documentos emitidos no se recalculan al cambiar precios o productos. | Snapshots persistidos en las líneas. |
| RNF-DAT-08 | Toda clave foránea y toda columna de estado o fecha usada en conjunto debe estar indexada; las claves de negocio llevan UNIQUE. | Revisión de DDL en cada migración. |
| RNF-DAT-09 | Los archivos nunca se guardan como BLOB ni con ruta absoluta. | Solo la clave relativa `tipo/yyyy/MM/uuid.ext`. |

## 17. Rendimiento y capacidad (RNF-REN)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-REN-01 | Toda lectura de colección debe ser paginada con tamaño acotado. | `size` entre 1 y 100 con el contrato paginado estándar. |
| RNF-REN-02 | La sincronización del radar debe completar un ciclo acotado sin bloquear la aplicación. | Ejecución real medida: 60 páginas, 1 200 releases y 26 coincidencias en 75 s, terminando `SUCCEEDED`. |
| RNF-REN-03 | Las llamadas al proveedor externo deben tener timeouts explícitos. | Conexión 10 s, lectura 30 s y página de 50 registros, configurables por variable. |
| RNF-REN-04 | La exportación debe estar acotada en volumen. | Hasta 366 días y 50 000 filas; el exceso devuelve 422. |
| RNF-REN-05 | El bundle inicial del frontend debe mantenerse acotado con carga diferida por funcionalidad. | Bundle inicial de aproximadamente 494 kB; el radar viaja en un chunk lazy de 33,7 kB. |
| RNF-REN-06 | Las cargas de archivo deben estar limitadas en tamaño. | `GIOVA_DOCUMENTS_MAX_BYTES` = 10 MiB en la configuración vigente; XML hasta 2 MiB. |

## 18. Fiabilidad y disponibilidad (RNF-FIA)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-FIA-01 | El contenedor web debe exponer un healthcheck automático. | `wget /health` con `grep UP` cada 10 s, timeout de 3 s y 6 reintentos; estado `healthy`. |
| RNF-FIA-02 | Los servicios deben reiniciarse automáticamente ante un fallo. | `restart: unless-stopped` en `api` y `web`. |
| RNF-FIA-03 | El apagado debe ser ordenado. | `server.shutdown: graceful`. |
| RNF-FIA-04 | Un fallo del proveedor externo no debe degradar ni borrar los datos ya persistidos. | El run queda `FAILED` con mensaje técnico saneado; 502 o 503 sin pérdida de información previa. |
| RNF-FIA-05 | Las transacciones que fallan deben compensar los archivos ya escritos. | Sincronización de rollback verificada en P20 y P23. |
| RNF-FIA-06 | El arranque repetido debe ser idempotente a nivel de esquema. | Segundo arranque sin migración nueva, verificado en cada fase con DDL. |
| RNF-FIA-07 | El respaldo debe ser restaurable y verificado antes de considerarse válido. | P30: conteos de filas, archivos y SHA-256 coincidentes en un destino desechable. |

## 19. Usabilidad y accesibilidad (RNF-USA)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-USA-01 | La interfaz debe funcionar sin desborde horizontal en 390, 768 y 1440 px. | Playwright P31: rutas públicas y privadas en los tres anchos, con `body` sin overflow y diálogos dentro del viewport. |
| RNF-USA-02 | La navegación debe ser operable por teclado, con foco visible. | Verificado en P31. |
| RNF-USA-03 | Las tablas deben ser semánticas y usar `caption` accesible oculto visualmente. | Clase `.sr-only` presente en todas las hojas de estilo, incluida la del radar. |
| RNF-USA-04 | Todo listado debe presentar estados de carga, vacío y error diferenciados. | Las cargas fallidas muestran el error del servidor sin falso éxito. |
| RNF-USA-05 | La consola del navegador debe quedar sin errores en el recorrido completo. | 0 errores de consola en P30 y P31. |
| RNF-USA-06 | El menú privado debe filtrarse por rol desde una configuración estática única y probada. | Una sola definición tipada de `RoleCode`; sin condiciones duplicadas por plantilla. |
| RNF-USA-07 | Idioma y nomenclatura: interfaz en español, JSON en `camelCase`, código y SQL en inglés. | Convención fijada en el contrato de datos. |

## 20. Mantenibilidad y calidad (RNF-MAN)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-MAN-01 | Arquitectura por capas obligatoria: controller, DTO, service y repository. | SQL solo en repositorios; el controller no calcula dinero, no decide transiciones ni abre archivos; el repository no decide permisos. |
| RNF-MAN-02 | Prohibido el CRUD genérico y los repositorios genéricos. | Un módulo por dominio bajo `pe.giova.*`. |
| RNF-MAN-03 | Los DTO de entrada no reutilizan el record de salida. | Revisión por módulo. |
| RNF-MAN-04 | Las suites de prueba deben estar verdes antes de cerrar cada fase. | Backend: 78 pruebas, 0 fallos. Frontend: 8 archivos, 22 pruebas, 0 fallos. Lint de TypeScript y Prettier aprobado. |
| RNF-MAN-05 | La pila tecnológica está congelada; ampliarla requiere aprobación explícita. | Prohibidos JPA/Hibernate, Lombok, MapStruct, NgRx, JWT, Redis, Kafka, microservicios, segunda base y proveedor cloud. |
| RNF-MAN-06 | Las dependencias nuevas solo se agregan en su fase, con versión fija y justificación. | Únicas admitidas: Apache PDFBox 3.0.5 en P21 y Apache POI OOXML 5.5.1 en P26. |
| RNF-MAN-07 | El código debe estar representado en un grafo actualizado al cierre de cada fase. | Graphify: 2 688 nodos, 7 085 relaciones y 133 comunidades; `graphify-out/graph.html`, `graph.json` y `GRAPH_REPORT.md`. |
| RNF-MAN-08 | La documentación debe mantener un orden de precedencia explícito ante contradicciones. | Usuario, `AGENTS.md`, plan, contrato de datos, checkpoint y código vivo, en ese orden. |

## 21. Portabilidad y compatibilidad (RNF-POR)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-POR-01 | Toda configuración sensible al entorno debe leerse de variables, sin rutas absolutas en base ni en código. | `GIOVA_DB_*`, `GIOVA_DOCUMENTS_ROOT` y `GIOVA_OECE_*`. |
| RNF-POR-02 | Las imágenes de infraestructura deben fijar versión; prohibido `latest`. | Temurin 17, Node 24.19.0, Maven 3.9.16 y Nginx 1.29-alpine. |
| RNF-POR-03 | La aplicación debe ejecutarse en Docker Compose con la base MariaDB externa en XAMPP. | Conexión por `host.docker.internal:3306`; Compose no crea un contenedor de base de datos. |
| RNF-POR-04 | El servicio debe exponerse solo en loopback. | Puerto publicado `127.0.0.1:8088:80`. |
| RNF-POR-05 | Frontend y API deben compartir origen; la API se consume por ruta relativa. | Sin CORS abierto; Nginx realiza el proxy interno. |
| RNF-POR-06 | La construcción debe ser reproducible con lockfiles y wrapper. | `npm ci` y `backend\mvnw.cmd -B verify`; la limitación conocida de `EPERM` en OneDrive queda documentada. |
| RNF-POR-07 | Objetivo de compilación Java 17, Angular 21.2 y TypeScript estricto. | Fijado en `backend/pom.xml`, `frontend/package.json` y el lockfile. |

## 22. Privacidad, cumplimiento y ética (RNF-PRI)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-PRI-01 | La auditoría debe registrar lo mínimo necesario y nunca contenido sensible completo. | Sin contraseñas, cookies, token CSRF, PDF o XML completos, ni cuerpos de contacto y reclamación. |
| RNF-PRI-02 | La IP debe almacenarse normalizada o como `ip_hash`, y no exponerse en las consultas. | La API de auditoría no devuelve `ip_hash`. |
| RNF-PRI-03 | El uso de datos públicos de contratación debe limitarse a consulta y enlace a la fuente oficial. | Sin scraping de zonas autenticadas ni descargas restringidas. |
| RNF-PRI-04 | El sistema no debe prometer probabilidad de adjudicación ni usar IA en la puntuación. | Score determinista con razones explicables persistidas. |
| RNF-PRI-05 | No se deben inventar datos legales de la empresa. | Sin RUC, dirección legal, firma, cuenta bancaria ni condición tributaria ficticios; textos neutrales marcados como internos. |
| RNF-PRI-06 | El JSON externo almacenado nunca debe renderizarse como HTML. | Prevención de XSS almacenado en el radar. |

## 23. Operación y gobernanza (RNF-OPG)

| ID | Requerimiento | Métrica o criterio de aceptación |
|---|---|---|
| RNF-OPG-01 | Antes de escribir en la base deben verificarse host, versión, esquema, respaldo y autorización de fase. | `scripts/check-environment.ps1`; prohibido Flyway `clean`. |
| RNF-OPG-02 | Las cuentas de base de datos deben separarse por función. | `giova_app` para runtime y `giova_migrator` para DDL. |
| RNF-OPG-03 | No debe realizarse commit, push, PR, publicación ni despliegue sin autorización explícita. | Estado registrado en el checkpoint del proyecto. |
| RNF-OPG-04 | Cada fase debe cerrar con un estado global declarado y evidencia reproducible. | Estados posibles: Aprobado, Aprobado con observaciones, Fallido, Bloqueado o No ejecutado. |
| RNF-OPG-05 | Las limitaciones de herramientas deben declararse en lugar de darse por aprobadas. | Declarado: `osv-scanner`, `trivy`, `k6` y `tree_sitter_sql` no instalados; sin `ng e2e` configurado. |

---

# Parte III — Trazabilidad

## 24. Matriz de módulo, fase, migración y API

| Módulo | Fases | Migración | Prefijo API | Requerimientos |
|---|---|---|---|---|
| Web pública y catálogo | P01–P11 | V1, V2 | `/api/v1/catalog`, `/contact-submissions`, `/claims` | RF-PUB |
| Autenticación y roles | P05, P12, P17 | V3 | `/api/v1/auth` | RF-AUT |
| Shell privado y navegación | P18 | — | — | RF-AUT-10, RNF-USA-06 |
| Catálogo interno | P06, P07, P13, P14 | V1 | `/api/v1/admin/...` | RF-CAT |
| Clientes y referencias de precio | P19 | V4 | `/api/v1/sales/customers`, `/sales/price-references` | RF-CLI, RF-PRE |
| Documentos privados | P20 | V5 | `/api/v1/documents` | RF-DOC |
| Cotizaciones y PDF | P21, P22 | V6 | `/api/v1/sales/quotations` | RF-COT |
| Facturas y XML | P23 | V7 | `/api/v1/accounting/invoices` | RF-FAC |
| Órdenes, entregas y garantías | P24, P25 | V8 | `/api/v1/accounting/purchase-orders`, `/deliveries`, `/warranties` | RF-OEG |
| Tareas, metas, auditoría y XLSX | P26 | V9 | `/api/v1/tasks`, `/goals`, `/admin/audit-events`, `/reports` | RF-TRA, RF-AUD |
| Radar de licitaciones | P27, P28, P29 | V10, V11 | `/api/v1/tenders` | RF-LIC |
| Integración, seguridad y entrega | P30, P31 | — | `/health`, `/api/v1/health/readiness` | RF-OPE, RNF-OPG |

## 25. Matriz de permisos por recurso

| Recurso o acción | ADMIN | ACCOUNTANT | SELLER |
|---|---|---|---|
| Catálogo público | Leer | Leer | Leer |
| Marcas, categorías y productos internos | CRUD | Leer | Leer |
| Clientes | CRUD todos | Leer todos | Crear, leer y editar propios; sin desactivar |
| Cotizaciones | CRUD, emitir y anular todas | Leer todas | Crear, editar, emitir y anular propias |
| Descargar PDF de cotización | Sí | Sí | Solo propias |
| Referencias de precio | CRUD | Leer | Crear y leer; sin editar ni borrar el histórico |
| Facturas y XML | CRUD y estados | CRUD y estados | Leer las asociadas a sus cotizaciones |
| Órdenes de compra | CRUD y estados | CRUD y estados | Leer las asociadas a sus cotizaciones |
| Actas, garantías y documentos contables | CRUD | CRUD | Leer las asociadas a sus cotizaciones |
| Tareas | Todas | Propias o asignadas | Propias o asignadas |
| Metas | CRUD y evaluar | Leer propias | Leer propias |
| Auditoría y exportaciones | Leer y exportar | Exportar el reporte contable | No |
| Configuración del radar | CRUD | Leer | Leer |
| Sincronizar radar manualmente | Sí | No | Sí |
| Coincidencias de licitación | Todas | Leer | Leer, seguir o descartar las asignadas o propias |

## 26. Evidencia de verificación registrada

| Comprobación | Resultado | Evidencia |
|---|---|---|
| `backend\mvnw.cmd -B verify` | Aprobado | 78 pruebas, 0 fallos |
| `npm run lint` | Aprobado | TypeScript y Prettier |
| `npm test -- --watch=false` | Aprobado | 8 archivos, 22 pruebas, 0 fallos |
| `npm run build` | Aprobado | Bundle inicial 494,72 kB; radar 33,67 kB |
| Semgrep P31 | Aprobado | 255 archivos, 91 reglas, 0 hallazgos |
| `npm audit` | Aprobado | 0 vulnerabilidades |
| Playwright público y privado P31 | Aprobado | 390, 768 y 1440 px; tres roles, radar, 403 administrativo, foco de teclado y 0 errores de consola |
| XXE autenticado P31 | Aprobado | HTTP 400 `DOCUMENT_INVALID`; salida en `output/p31/xxe-authenticated-response.txt` |
| Cabecera de descarga P31 | Aprobado | Un único `X-Content-Type-Options: nosniff` |
| Backup y restauración P30 | Aprobado | 8 filas y 8 archivos con hashes coincidentes en destinos desechables |
| Readiness y Compose P31 | Aprobado | API activa, web `healthy`, readiness `READY` y `database: UP` |
| Migraciones Flyway | Aprobado | V1 a V11 aplicadas; segundo arranque sin cambios de esquema |
| Sincronización real OECE | Aprobado | 60 páginas, 1 200 releases y 26 coincidencias en 75 s, `SUCCEEDED` con cursor de continuación |

### Limitaciones declaradas

- `osv-scanner`, `trivy` y `k6` no están instalados en el entorno; no se reportan como pruebas aprobadas ni como hallazgos de seguridad.
- No existe una suite `ng e2e` configurada; la verificación de interfaz se realiza con Playwright contra la API real.
- Graphify omite 11 archivos SQL por ausencia del parser opcional `tree_sitter_sql`; esas relaciones se verifican directamente en los archivos de migración y en MariaDB.
- La reextracción semántica de documentos e imágenes del grafo no se ejecutó por falta de backend LLM configurado.

## 27. Observaciones de coherencia documental

Dos puntos donde el plan y la implementación difieren. Este documento describe **la implementación vigente**:

1. **Límite de tamaño de PDF.** `docs/PLAN_PROYECTO.md` sección 7.3 indica 15 MiB, pero la configuración efectiva usa `GIOVA_DOCUMENTS_MAX_BYTES = 10485760` (10 MiB) tanto en `application.yml` como en `compose.yaml`. RF-DOC-03 y RNF-REN-06 se redactan sobre el valor efectivo.
2. **Estados de factura.** La sección 6.3 del plan menciona `REGISTERED → PENDING | PAID | VOID`, mientras que la implementación de P23, la sección 17 del mismo plan y la migración V7 usan `DRAFT → ISSUED | CANCELLED`, con los dos últimos como terminales. RF-FAC-04 se redacta sobre este último, que es el correcto.

Corregir ambos puntos en `docs/PLAN_PROYECTO.md` requiere aprobación explícita del propietario del proyecto, conforme a la regla de precedencia del propio plan.
