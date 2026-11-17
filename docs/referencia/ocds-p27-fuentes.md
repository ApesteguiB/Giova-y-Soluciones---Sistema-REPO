# P27/P28 — Fuentes y contrato OECE/OCDS

Fecha de verificación: **2026-09-15**. Las fuentes listadas son públicas y se consultaron para fijar el spike; no se guardan credenciales ni respuestas reales en el repositorio.

## Fuentes primarias

| Fuente | Uso en P27 |
|---|---|
| [OCDS 1.1.5 — esquema de release](https://standard.open-contracting.org/latest/en/schema/release/) | Campos requeridos y tipos de una release. |
| [OCDS 1.1.5 — referencia de release](https://standard.open-contracting.org/latest/en/schema/reference/) | Envolvente de release package, `tender`, `buyer` y `publisher`. |
| [OCDS 1.1.5 — hosting y paginación](https://standard.open-contracting.org/latest/en/guidance/build/hosting/) | `next`, cursor, `since`, `offset`, `page` y `limit`. |
| [OECE — Portal de Contrataciones Abiertas OCDS](https://www.gob.pe/52005-acceder-al-portal-de-contrataciones-abiertas-de-la-compra-publica) | Alcance oficial del portal y acceso a su API/documentación. |
| [OECE — registro de datos OCP](https://data.open-contracting.org/es/publication/135) | Cobertura publicada por OECE/SEACE y formatos de descarga. |
| [OECE — política de publicación OCDS](https://contratacionesabiertas.osce.gob.pe/downloads/politica_publicacion.pdf) | Frecuencia de actualización, origen SEACE y prefijo OCID documentados por el publicador. |

## Resultado de la consulta controlada

Se hizo un `GET` sin autenticación a `https://contratacionesabiertas.oece.gob.pe/api` el 2026-09-15. El host respondió `200 OK` con la página HTML del portal, no con un paquete JSON autodetectable. La documentación pública localizada todavía referencia el endpoint histórico `contratacionesabiertas.osce.gob.pe/api`; desde este entorno esa URL no resolvió. P28 dejó la URL parametrizada, mantuvo `GIOVA_OECE_ENABLED=false` y no inventó una ruta de consulta.

### Endpoint JSON verificado (2026-09-16)

`https://contratacionesabiertas.oece.gob.pe/api/v1/releases` responde `200 application/json` con un **release package** OCDS 1.1 que cumple el contrato mínimo adoptado: `uri`, `version` `"1.1"`, `publishedDate`, `publisher.name` (`Organismo Supervisor de las Contrataciones del Estado`), `releases` no vacío y `links.next`. `/api` y `/api/v1` siguen devolviendo el HTML del portal, así que la URL base configurada debe incluir la ruta `/api/v1/releases`; ese es ahora el valor predeterminado de `GIOVA_OECE_BASE_URL`.

Observaciones de la ejecución real:

- El portal acepta `limit` y `since` en la query y los arrastra a `links.next`, pero **no filtra por ellos**: siempre entrega 20 releases por página, de la más reciente a la más antigua.
- Por eso `OeceOcdsClient` corta la paginación en la primera página cuyas releases son todas anteriores a la marca `since` solicitada, y `GIOVA_OECE_MAX_PAGES` (60 por defecto) acota cada ejecución. Al agotar el presupuesto la ejecución termina en `SUCCEEDED` y guarda el `next` pendiente como cursor, de modo que la siguiente sincronización continúa donde quedó.
- Las releases no traen `url`. El portal publica cada proceso en `/proceso/{ocid}` sobre el mismo host, así que ese enlace se usa como fuente oficial y sigue pasando por `TenderUrlValidator`.
- Los documentos de cada release apuntan a `prod1.seace.gob.pe`; al ser otro host, el validador los descarta y no se almacenan como enlace oficial.

## Contrato mínimo adoptado

El spike acepta un **release package** OCDS. Los record packages quedan identificados como variante de contrato para P28, sin persistencia ni parser activo en P27.

Campos requeridos del paquete:

- `uri` como URI con esquema.
- `version` con formato `major.minor`.
- `publishedDate` como fecha ISO-8601; se normaliza a `Instant`.
- `publisher.name` no vacío.
- `releases` como array no vacío.

Campos requeridos por release:

- `ocid`, `id`, `date`, `tag` no vacío e `initiationType`.
- `tender` como objeto y `tender.title` no vacío. El estándar permite que el título sea nulo, pero GIOVA lo trata como mínimo adicional porque P28 necesita listar y buscar oportunidades.

Mapeo normalizado por `OcdsReleasePackageParser`:

| OCDS | GIOVA |
|---|---|
| `uri`, `version`, `publishedDate`, `publisher.name` | Metadatos del paquete |
| `ocid`, `id`, `date`, `tag`, `initiationType` | Identidad y etapa de la release |
| `tender.title`, `tender.description`, `tender.status` | Título, descripción y estado del proceso |
| `buyer.id`, `buyer.name` | Comprador |
| `tender.value` o `value` (`amount`, `currency`) | Monto y moneda |
| `tender.tenderPeriod.startDate/endDate` | Inicio y cierre de la oportunidad |
| `url` | Enlace de fuente, si el publicador lo entrega |

`extensions` se conserva como lista de URI en el sobre. Las propiedades desconocidas, incluidas las de objetos de extensión, se ignoran de forma deliberada para tolerar futuras extensiones sin insertar JSON arbitrario como HTML. P28 conserva además el JSON original de cada release, limitado por tamaño y asociado a un hash SHA-256.

## Paginación, errores y límites

- Se preferirá seguir el enlace opaco `next`; si el proveedor no lo entrega, el adaptador podrá usar `cursor` o `since`. `offset`/`page` son alternativas documentadas por OCDS y no se asumirán sin evidencia del endpoint.
- Tamaño solicitado inicial: `limit=50`, sujeto al máximo que publique OECE. `next` nulo o ausente termina el lote.
- `429` se normaliza como `RATE_LIMITED` y respeta `Retry-After`. No se inventa una cuota numérica no publicada.
- `408`, timeout y `5xx` se normalizan como `TRANSIENT_PROVIDER_ERROR`, con máximo de tres reintentos en P28.
- `401`/`403` son `PROVIDER_ACCESS_DENIED`; `404` es `PROVIDER_NOT_FOUND`; `400`/`422` son `CONTRACT_INVALID` y no se reintentan.
- P28 limita concurrencia a una sincronización mediante lock MariaDB y conserva la última data válida ante fallo. El scheduler y las llamadas automáticas solo existen si `GIOVA_OECE_ENABLED=true`.

## Fixtures y código

Los fixtures `minimal-release-package.json`, `full-release-package.json`, `malformed-release-package.json` y `missing-required-release-package.json` usan únicamente datos sintéticos bajo `example.test`; no son una copia de un expediente real.

El parser está en `backend/src/main/java/pe/giova/tender/contract/OcdsReleasePackageParser.java` y sus cinco contract tests offline en `backend/src/test/java/pe/giova/tender/contract/OcdsReleasePackageParserTest.java`. El cliente P28 está en `backend/src/main/java/pe/giova/tender/OeceOcdsClient.java`, sus pruebas locales en `backend/src/test/java/pe/giova/tender/OeceOcdsClientTest.java`, y no realiza red con la propiedad desactivada. V10 persiste el radar en `tender_sources`, `tender_sync_runs`, `tender_releases`, `tender_processes`, `tender_match_profiles`, `tender_matches` y `tender_match_actions`. La sincronización real queda pendiente de un endpoint JSON OECE verificable.
