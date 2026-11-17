# Un sistema, varias empresas, un sitio público para cada una

Las empresas comparten el backend, la base de datos y el panel de operaciones.
Lo que no comparten es la cara pública: cada una tiene su dominio, su marca y su
catálogo.

## Cómo se decide qué sitio mostrar

Toda petición pública trae una cabecera `Host` con el dominio que el visitante
escribió. Nginx la pasa tal cual al backend, y `PublicSiteResolver` la busca en
la tabla `tenant_hosts`:

```
giova.pe            → empresa 1 (GIOVA)
solucionesti.pe     → empresa 2 (SOLUCIONES_TI)
```

Con esa empresa resuelta se sirven la portada, el catálogo, el formulario de
contacto y el libro de reclamaciones. Un visitante **no puede** cambiar de
empresa: el parámetro `?empresa=` solo se acepta cuando el host es de bucle
invertido (`localhost`, `127.0.0.1`), y se puede apagar con
`GIOVA_SITE_ALLOW_LOCAL_OVERRIDE=false`.

Si el dominio no está dado de alta —por ejemplo alguien entra por la IP del
servidor— se muestra la empresa de `GIOVA_SITE_DEFAULT_TENANT` (por defecto
`GIOVA`), de modo que nunca queda una página en blanco.

El resultado se guarda en memoria cinco minutos. La caché está acotada a 256
dominios y se vacía al llegar al tope: una cabecera `Host` inventada no puede
hacerla crecer sin límite.

## Las dos tablas

`tenant_hosts` — qué dominios apuntan a qué empresa.

| Columna | Para qué |
|---|---|
| `hostname` | Dominio en minúsculas, sin `http://` ni puerto. Único en todo el sistema. |
| `is_primary` | Marca el dominio principal; hoy es informativo. |

`tenant_sites` — el contenido del sitio.

| Columna | Para qué |
|---|---|
| `template_key` | `GIOVA` usa la portada hecha a medida. `SOLUCIONES` y `GENERICO` usan la plantilla por datos. |
| `site_title` | Título de la pestaña del navegador. |
| `brand_name` / `legal_name` | Nombre comercial y razón social. |
| `tagline` | Frase bajo el nombre, en la portada y en el pie. |
| `logo_asset` | Ruta del logo dentro de `frontend/public/`. Si la empresa sube un logo desde Operaciones → Marca, ese tiene prioridad. |
| `monogram_text` | Dos o tres letras para el cargador y el cursor. |
| `phone`, `email`, `address`, `maps_url`, `hours` | Datos de contacto. **Lo que esté en `NULL` simplemente no se muestra**, no sale un hueco. |
| `whatsapp_number`, `whatsapp_message` | Si no hay número, los botones de WhatsApp se convierten en "Consultar" y llevan al formulario. |
| `footer_note` | La línea de respaldo del pie (en GIOVA, lo de OSCE y homologación). |
| `claims_enabled` | Oculta el libro de reclamaciones si esa empresa no lo necesita. |
| `catalog_enabled` | Oculta las secciones de productos. |

## Dar de alta un dominio real

```sql
INSERT INTO tenant_hosts (tenant_id, hostname, is_primary)
SELECT id, 'solucionesti.pe', TRUE FROM tenants WHERE code = 'SOLUCIONES_TI';

INSERT INTO tenant_hosts (tenant_id, hostname, is_primary)
SELECT id, 'www.solucionesti.pe', FALSE FROM tenants WHERE code = 'SOLUCIONES_TI';
```

El cambio tarda hasta cinco minutos en tomar efecto por la caché, o es inmediato
si se reinicia la API.

Si el dominio solo está dado de alta sin el `www.`, el resolutor igual lo
encuentra: ante `www.algo.pe` que no existe, prueba `algo.pe`.

En el servidor hace falta además que el DNS del dominio apunte a la máquina.
Nginx no necesita cambios para HTTP porque su `server_name` es `_` y acepta
cualquier dominio; para HTTPS sí hay que agregar un bloque por dominio con su
certificado.

## Si la base viene del respaldo del compañero

En ese respaldo la empresa 2 ya existe, creada desde la pantalla de plataforma:

| Dato | Valor en el respaldo |
|---|---|
| `code` | `SOLUCIONES_TI` |
| `slug` | `soluciones-informaticas-ti` |
| Razón social | Soluciones Informáticas TI |
| Colores | primario `#E87835`, secundario `#FFFFFF`, acento `#F3B247` |
| Logo | hay uno cargado, pero el archivo **no viaja en el respaldo** |

`V17` detecta que esa empresa ya existe —se guía por el código— y no la
duplica: solo le agrega su sitio, sus dominios y su catálogo. Tampoco toca los
colores que ya eligieron.

Dos consecuencias:

- **El logo cargado no se ve.** El PNG vive en la carpeta privada del backend
  (el volumen de Docker), no en el `.sql`. El sitio lo pide, recibe un 404 y
  cae al logo de `logo_asset`, que sí está en el proyecto. Si quieres el
  original, pídele a tu compañero el archivo de
  `storage/documents/tenants/2/branding/`, o vuelve a subirlo desde
  Operaciones → Marca.
- **El secundario es blanco**, y un fondo oscuro no puede salir de ahí. En ese
  caso la portada usa un gris azulado neutro y deja la marca en los acentos. Si
  prefieres que el fondo tome el color de la empresa, cambia el secundario por
  uno oscuro desde Operaciones → Marca.

## Completar los datos de Soluciones Informática TI

La migración `V17` dejó a propósito en `NULL` el RUC, el teléfono, el correo y
la dirección, porque no estaban confirmados. Para completarlos:

```sql
UPDATE tenants SET tax_id = '20XXXXXXXXX' WHERE code = 'SOLUCIONES_TI';

UPDATE tenant_sites s
JOIN tenants t ON t.id = s.tenant_id
SET s.phone = '(01) XXX XXXX',
    s.email = 'ventas@solucionesti.pe',
    s.address = 'Dirección fiscal, distrito, provincia',
    s.maps_url = 'https://maps.app.goo.gl/...',
    s.whatsapp_number = '51XXXXXXXXX'
WHERE t.code = 'SOLUCIONES_TI';
```

El sitio recoge los cambios al recargar la página; no hay que reconstruir nada.

## Agregar una tercera empresa

1. Crear la empresa desde la pantalla de plataforma, con una cuenta
   `SUPERADMIN`. Eso crea la empresa, su marca y su primer `ADMIN`.
2. Insertar su fila en `tenant_sites` con `template_key = 'GENERICO'` y sus
   dominios en `tenant_hosts`.
3. Su `ADMIN` carga el catálogo desde Operaciones → Catálogo.

No hace falta tocar código: la plantilla `GENERICO` arma la portada con el
nombre, la frase, los colores, las categorías y los productos de esa empresa.
Solo tiene sentido escribir una plantilla nueva si una empresa quiere una
portada distinta a mano, como la de GIOVA.

## Los colores

Los colores salen de `tenant_branding` (los mismos que usa el panel interno).
La portada los aplica así:

- Las superficies oscuras se calculan a partir del color secundario y se
  oscurecen hasta que el texto blanco encima cumpla contraste. Si ese secundario
  es demasiado claro para servir de fondo (blanco, un amarillo), se usa un gris
  azulado neutro en su lugar.
- El color principal y el de acento se usan en botones, enlaces y detalles.
- GIOVA es la excepción: su portada conserva su paleta original tal cual.

## El endpoint

```
GET /api/v1/catalog/site        → datos del sitio de la empresa del dominio
GET /api/v1/catalog/site/logo   → logo subido por esa empresa, si tiene
```

Es público, como el resto de `/api/v1/catalog`. No devuelve identificadores
internos ni la clave de almacenamiento del logo. Si la API no responde, el
frontend usa los datos de GIOVA como respaldo para que la portada siga en pie.
