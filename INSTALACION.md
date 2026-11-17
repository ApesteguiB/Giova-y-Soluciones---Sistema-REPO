# Sistema GIOVA · cómo levantarlo desde cero

Guía para dejar el sistema corriendo en una computadora nueva. Todo en
PowerShell de Windows (no hace falta la terminal de VS Code, aunque también
sirve).

Tiempo aproximado: 20 minutos la primera vez, casi todo esperando descargas.

## Qué hay que instalar antes

| Programa | Para qué | Dónde |
|---|---|---|
| **XAMPP** | Trae MariaDB, que es la base de datos | apachefriends.org |
| **Docker Desktop** | Levanta el backend y el frontend | docker.com |

Docker Desktop necesita que la virtualización esté activada en la BIOS. Si al
abrirlo dice *"Virtualization support not detected"*, hay que entrar a la BIOS y
activar **VT-x** (Intel) o **SVM** (AMD).

No hace falta instalar Java ni Node: Docker los trae dentro.

---

## Primero: ¿de dónde sale la base de datos?

Hay dos caminos. **Elige uno.**

### Camino A — importar el respaldo del compañero (recomendado)

Es lo que conviene si quieres ver el sistema con los datos reales con los que
han estado trabajando: 84 productos, 35 clientes, 72 cotizaciones, 28 facturas
y el radar de licitaciones ya cargado.

El respaldo trae las migraciones `V1` a `V16` aplicadas, así que `V17` (los
sitios por empresa) se aplica sola al levantar la API.

Con MySQL encendido en XAMPP:

```powershell
cd C:\ruta\donde\descomprimiste\el-proyecto

$env:GIOVA_ROOT_PASSWORD = "la-contraseña-de-root-de-tu-XAMPP"
.\scripts\import-backup.ps1 -SqlPath C:\ruta\al\giova_v1.sql
```

El script guarda un respaldo de lo que tengas ahora, borra `giova_v1`, importa
el archivo y al final te dice cuántas tablas quedaron y hasta qué migración
llega. Pide escribir `giova_v1` para confirmar, porque borra.

El archivo no trae usuarios de base de datos, así que después hay que volver a
crearlos y darles permisos. Eso lo hace el paso 1 de abajo, que es idempotente:
córrelo igual.

Las cuentas para entrar al sistema **sí** vienen (`admin@gmail.com`,
`vendedor@gmail.com`, `contador@gmail.com`, `superadmin@gmail.com`,
`admin2@gmail.com`), pero las contraseñas son hashes: pídeselas a tu compañero,
o créate las tuyas con otro nombre de usuario en el paso 3.

### Camino B — base nueva desde cero

Flyway construye todo desde las migraciones y carga los datos de demostración.
No necesitas que nadie te mande nada. Si ya tenías una base `giova_v1` de un
paquete anterior, revisa primero qué migraciones tiene:

```powershell
& "C:\xampp\mysql\bin\mysql.exe" -u root -p giova_v1 -e "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank;"
```

Si aparece una versión que **no** está en
`backend\src\main\resources\db\migration` (por ejemplo una `V12` de un
paquete viejo), Flyway aborta con *"Detected applied migration not resolved
locally"*. Respalda y borra la base: `DROP DATABASE giova_v1;`

---

## 1 · Preparar la base de datos

Abre el **panel de XAMPP** y dale **Start** a MySQL. Debe quedar en verde.

Primero crea la configuración local y guarda en `.env` contraseñas propias
(usa al menos 12 caracteres para cada usuario; el archivo no se distribuye):

```powershell
cd C:\ruta\donde\descomprimiste\GIOVA-V1-P31-final
Copy-Item .env.example .env
notepad .env
```

Después, carga temporalmente las contraseñas en esta sesión. `Read-Host` las
recibe como texto protegido y el bloque no las imprime ni las guarda en el
historial de comandos:

```powershell
function Set-GiovaSecret([string]$Name, [string]$Prompt) {
  $secure = Read-Host -Prompt $Prompt -AsSecureString
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {
    [Environment]::SetEnvironmentVariable($Name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr), 'Process')
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
  }
}

Set-GiovaSecret GIOVA_ROOT_PASSWORD 'Contraseña root actual de XAMPP'
Set-GiovaSecret GIOVA_MIGRATOR_PASSWORD 'Contraseña local para giova_migrator'
Set-GiovaSecret GIOVA_APP_PASSWORD 'Contraseña local para giova_app'

.\scripts\bootstrap-xampp.ps1
```

Debe terminar diciendo *"giova_v1 preparado con usuarios giova_migrator y
giova_app"*.

> Si PowerShell se niega a correr el script porque "no está firmado
> digitalmente", corre antes esto en la misma ventana:
> `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force`

El `.env` debe tener los mismos valores privados en estas variables:

```
GIOVA_DB_PASSWORD=<contraseña-local-de-giova_app>
GIOVA_FLYWAY_PASSWORD=<contraseña-local-de-giova_migrator>
```

Sustituye los textos entre `< >` en tu `.env` local; no los copies literalmente.
El `.env` no se sube a Git.

## 2 · Levantar el sistema

```powershell
docker compose up -d --build
```

La primera vez tarda varios minutos: compila Java y Angular. Cuando termine:

```powershell
docker compose ps
docker compose logs api --tail 20
```

Tiene que aparecer **`Started GiovaBackendApplication`**. Si en vez de eso hay
un `Caused by:`, ahí está el problema.

## 3 · Crear los usuarios

Ahora el sistema es multiempresa, así que **cada cuenta pertenece a una
empresa** y hay que indicarla con `-TenantCode`. Las empresas que vienen
cargadas son `GIOVA` y `SOLUCIONES_TI`.

Además existe el rol `SUPERADMIN`, que administra la plataforma (crea empresas)
y **no** ve los datos de ninguna de ellas. Ese no lleva `-TenantCode` y pide una
confirmación escrita.

```powershell
# Administrador de plataforma: te va a pedir escribir  CREATE SUPERADMIN
.\scripts\create-worker.ps1 -Username super -DisplayName 'Plataforma' -Role SUPERADMIN

# Los tres roles de GIOVA
.\scripts\create-worker.ps1 -Username admin    -DisplayName 'Administrador' -Role ADMIN      -TenantCode GIOVA
.\scripts\create-worker.ps1 -Username contador -DisplayName 'Contadora'     -Role ACCOUNTANT -TenantCode GIOVA
.\scripts\create-worker.ps1 -Username vendedor -DisplayName 'Vendedor'      -Role SELLER     -TenantCode GIOVA

# Un administrador de la segunda empresa, para comprobar el aislamiento
.\scripts\create-worker.ps1 -Username admin.ti -DisplayName 'Administrador TI' -Role ADMIN -TenantCode SOLUCIONES_TI
```

Cada ejecución te pedirá una contraseña de **mínimo 12 caracteres**. El script
usa `127.0.0.1` para la conexión local y restaura el valor previo de
`GIOVA_DB_HOST` al terminar.

> **Si importaste el respaldo del compañero (camino A)**, esas cuentas ya
> existen con otros nombres y el script fallará por usuario repetido. Usa
> nombres distintos (`jose.admin`, `jose.vendedor`…) o pídele las contraseñas.
> En ese respaldo ya hay un `SUPERADMIN` y un `ADMIN` de la segunda empresa, así
> que la primera y la última línea del bloque de arriba te las puedes saltar.

Si olvidas el `-TenantCode`, el script se detiene con *"TenantCode es
obligatorio para ADMIN, ACCOUNTANT y SELLER"*. Es intencional: una cuenta sin
empresa no podría ver nada.

El contenedor de la API usa `host.docker.internal`; el script local de alta usa
`127.0.0.1`. No cambies el host de Docker para crear trabajadores.

## 4 · Entrar

Abre **http://127.0.0.1:8088** e inicia sesión con cualquiera de los usuarios.

## 5 · Ver los dos sitios públicos

Cada empresa tiene su propia portada. El sistema decide cuál mostrar según el
**dominio** con el que entras, no según un parámetro que elija el visitante.

En local, sin tocar nada, Chrome y Edge resuelven cualquier `*.localhost`:

| Dirección | Qué se ve |
|---|---|
| http://localhost:8088 | Portada de GIOVA |
| http://soluciones.localhost:8088 | Portada de Soluciones Informática TI |
| http://127.0.0.1:8088/?empresa=soluciones-ti | Lo mismo, por si el navegador no resuelve `*.localhost` |

Para el parámetro sirve tanto el código de la empresa (`SOLUCIONES_TI`) como su
slug (`soluciones-ti`, `soluciones-informaticas-ti`): el guion medio y el bajo
se tratan igual.

El parámetro `?empresa=` **solo** funciona desde `localhost`; desde un dominio
real el backend lo ignora. Para apagarlo del todo, pon
`GIOVA_SITE_ALLOW_LOCAL_OVERRIDE=false` en el `.env`.

El detalle de cómo dar de alta un dominio real y cómo editar los textos,
colores y datos de contacto de cada sitio está en
`docs\SITIOS_POR_EMPRESA.md`.

---

## Qué debe verse si todo salió bien

```powershell
& "C:\xampp\mysql\bin\mysql.exe" -u root -p giova_v1 -e "SELECT 'Productos' AS dato, COUNT(*) AS total FROM products UNION ALL SELECT 'Entidades', COUNT(*) FROM customers UNION ALL SELECT 'Cotizaciones', COUNT(*) FROM quotations UNION ALL SELECT 'Facturas', COUNT(*) FROM invoices UNION ALL SELECT 'Precios de compra', COUNT(*) FROM product_price_references;"
```

La consulta sirve para comprobar conexión y presencia de tablas; los totales
dependen de los datos existentes. En `giova_v1`, al 2026-10-06, devolvió 82
productos, 32 clientes, 72 cotizaciones, 25 facturas y 89 referencias de precio.
No uses esos totales como condición para borrar o recrear datos.

---

## Qué hace cada rol

| Rol | Puede | No puede |
|---|---|---|
| **Vendedor** | Cotizar, registrar resultados, OCAM, ver precios de referencia | Registrar facturas |
| **Contadora** | Subir facturas XML, órdenes, actas, garantías | Cotizar |
| **Administrador** | Ver todas las cotizaciones, administrar el catálogo y los usuarios de su empresa, auditoría, reportes | **Cotizar** |
| **Superadmin** | Crear empresas y la primera cuenta ADMIN de cada una | Ver cotizaciones, facturas o clientes de cualquier empresa |

Que el administrador no cotice es a propósito: en GIOVA el que cotiza es el
vendedor, y el administrador supervisa. Está bloqueado en el servidor, no solo
escondiendo botones.

---

## Recorrido de demostración (5 minutos)

### Como **vendedor** — el historial de precios

1. Cotizaciones → **Nueva cotización**.
2. Elige una entidad y deja el procedimiento en **Perú Compras**. Fíjate en los
   botones **1 · 2 · 3**: calculan la fecha límite contando **días hábiles**. Si
   hoy es jueves, "2" cae lunes, no sábado.
3. En la línea, escribe `toner` o `CF258A`. La lista filtra mientras escribes.
4. Al elegir el producto se abre solo el panel **Historial de precios**. Ahí
   sale:
   - **Precio sugerido**, con el motivo de la sugerencia. El botón *Usar* lo
     copia al campo.
   - **Última compra**: cuánto nos costó, a qué proveedor y cuándo.
   - **Se vendió a**: a qué precio se ganó antes y a qué entidad. Clic en el
     precio y se copia.
   - **Nos ganaron con**: el precio del competidor que se llevó la orden, y
     cuánto habíamos ofrecido nosotros.
5. Activa **"Mostrar precios de compra y competencia"** para ver el margen por
   línea en verde, ámbar o rojo.

### Como **vendedor** — cerrar un proceso

En el listado, **Registrar resultado** sobre una cotización emitida. Elige *"No
nos seleccionaron"* e intenta guardar sin escribir el motivo: no deja. Es una
regla de la base de datos, no solo de la pantalla.

### Como **contadora** — leer una factura XML

Facturas → **Subir factura XML** → usa `docs\ejemplos\ejemplo-factura-F001-796.xml`.

El sistema lee el archivo y **antes de guardar** te muestra número, fecha,
entidad con su RUC y el detalle línea por línea. Al confirmar: registra la
factura, adjunta el XML como respaldo y guarda los precios de compra, que es lo
que después ve el vendedor en el historial.

Súbela una segunda vez: la rechaza por repetida.

### Como **administrador**

Entra a Cotizaciones: ves todo lo que cotizaron los vendedores, pero no hay
botón de crear. En el menú tampoco están Precios ni Garantías. Sí están
Productos, Marcas y Categorías.

### El aislamiento entre empresas

Entra con `admin.ti` (la segunda empresa) y recorre el menú: clientes,
cotizaciones, facturas y tareas salen **vacíos**, y el catálogo es el suyo, no
el de GIOVA. La numeración también es independiente: la primera cotización de
Soluciones vuelve a empezar en 0001.

Entra con `super`: solo existe la pantalla de plataforma. Si escribes a mano una
ruta como `/operaciones/ventas/cotizaciones`, el servidor responde 403, no es
solo que el menú esté oculto.

---

## Los datos de prueba

Las migraciones `V13`, `V14` y `V15` incluyen escenarios **sintéticos** para
probar cotizaciones, resultados, facturas y precios. Los estados y precios no
son evidencia de operaciones reales, aunque algunos ejemplos usen nombres de
entidades públicas. Las notas llevan la marca `[DEMO]`.

No ejecutes sentencias `DELETE` copiadas de una migración para limpiar la base.
No hay un limpiador general validado para estos datos. Antes de retirar datos,
haz un respaldo y define el alcance exacto; para una instalación de prueba,
restaura una base desechable desde un respaldo limpio.

---

## Para programar con recarga en vivo

Reconstruir Docker con cada cambio son minutos. Para desarrollar conviene dejar
el backend en Docker y correr solo el frontend:

```powershell
# Terminal 1 — déjala abierta
docker compose up -d

# Terminal 2
cd frontend
npm install
npm run dev
```

Se abre `http://localhost:4200` y al guardar un archivo el navegador se refresca
en un segundo. Para la revisión final usa el **8088**, que corre como en
producción.

## Correr los tests

```powershell
cd frontend
npm test

cd ..\backend
.\mvnw.cmd test
```

En el frontend pasan **55 de 56**. La que falla, `accounting-ui.spec.ts`, es
anterior a estos cambios: da por hecha la zona horaria de Lima, así que en una
máquina configurada en UTC no coincide. Si tu Windows está en hora de Perú,
debería pasar también.

`npm run lint` además ejecuta Prettier y hay archivos que venían sin ese
formato, así que un `npm test` en verde no implica que el lint completo pase.

---

## Si algo sale mal

| Síntoma | Qué pasó |
|---|---|
| `502 Bad Gateway` | El backend no arrancó. `docker compose logs api` |
| En el log: `Connection refused to 127.0.0.1` | Quedó la variable del paso 4. Cierra esa ventana de PowerShell y haz `docker compose up -d` desde una nueva. |
| `UnknownHostException: host.docker.internal` al crear trabajadores | Falta el `$env:GIOVA_DB_HOST = "127.0.0.1"` del paso 4. |
| `no configuration file provided` | No estás parado en la carpeta del proyecto. |
| Docker: *"Virtualization support not detected"* | Falta activar VT-x o SVM en la BIOS. |
| El script no corre: *"no está firmado digitalmente"* | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force` |
| Radar de licitaciones vacío | Es normal: está apagado por defecto. Ver abajo. |
| `Detected applied migration not resolved locally` | La base tiene una migración que este paquete no trae. Ver el aviso del inicio. |
| `TenantCode es obligatorio` al crear un usuario | Falta `-TenantCode GIOVA` (paso 3). |
| La portada de Soluciones sale con la marca de GIOVA | El navegador no resolvió `soluciones.localhost`. Usa `127.0.0.1:8088/?empresa=soluciones-ti` o agrega la línea al archivo `hosts`. |
| Una empresa nueva entra y no ve productos | Correcto: el catálogo es por empresa. Se carga desde Operaciones → Catálogo. |

### Sobre el radar de licitaciones

Viene **apagado** (`GIOVA_OECE_ENABLED=false`). El portal
`contratacionesabiertas.oece.gob.pe` está detrás de una protección anti-bots: en
el navegador entras sin problema, pero desde un programa devuelve una página de
verificación en vez de los datos. Encenderlo sin resolver eso solo llena el log
de errores.

Para que funcione de verdad hay que pedirle a la OECE acceso programático, o
alimentar el radar con las descargas masivas que publique el portal.
