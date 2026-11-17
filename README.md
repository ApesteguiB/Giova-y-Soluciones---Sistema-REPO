# GIOVA V1

GIOVA es una aplicación local para catálogo público y gestión interna de
ventas, contabilidad, tareas, metas y oportunidades de contratación pública.
La reconciliación local P31-final incorpora importación asistida de XML UBL,
historial comercial de precios y resultados de cotización. No automatiza
postulaciones, no firma documentos, no integra SUNAT y no habilita sincronización
externa de OECE por defecto. Para el procedimiento detallado de instalación,
consulta [INSTALACION.md](INSTALACION.md).

## Alcance

- Catálogo público con 52 productos y formularios de contacto/reclamaciones.
- Área privada `/operaciones` con roles fijos `ADMIN`, `ACCOUNTANT` y `SELLER`.
- Clientes, referencias de precio, cotizaciones, facturas internas, órdenes,
  entregas, garantías, tareas, metas y auditoría.
- Las cotizaciones registran tipo de contratación, resultado, OCAM y precios de
  competencia/compra; la factura admite previsualización e importación asistida
  de XML UBL, sin validación tributaria ni comunicación con SUNAT.
- Radar de licitaciones basado en datos OECE/OCDS, con fuente y frescura
  visibles. `GIOVA_OECE_ENABLED=false` es el valor predeterminado; ponerlo en
  `true` habilita la consulta a `https://contratacionesabiertas.oece.gob.pe/api/v1/releases`,
  el único endpoint del portal que devuelve un release package OCDS. V11 siembra
  un perfil de coincidencia base, necesario para que el radar muestre resultados.
- Documentos privados almacenados en el volumen Docker del API; Nginx no
  publica la carpeta física.

## Requisitos

- Windows 10/11, PowerShell 5.1 o PowerShell 7.
- Docker Desktop con Compose.
- XAMPP con MariaDB 10.4.32 escuchando en `127.0.0.1:3306`.
- Node.js 24.19.x y npm 11.x para comprobaciones del frontend.
- Java 17 o superior compatible para ejecución local del backend. Las imágenes
  Docker fijan Temurin 17 y Node 24.19.0.

La aplicación no crea un contenedor de MariaDB: usa la base `giova_v1` de
XAMPP mediante `host.docker.internal`.

## Arranque integrado

1. Copia `.env.example` como `.env` y completa únicamente las credenciales
   locales de `giova_app`, `giova_migrator` y, si corresponde, la configuración
   de OECE. No compartas `.env`.
2. Comprueba el entorno y la configuración:

```powershell
Set-Location C:\ruta\GIOVA-ANGULAR+BACKEND
.\scripts\check-environment.ps1
docker compose config --quiet
```

3. En una base nueva, crea el esquema y usuarios desde una sesión temporal. No
   pongas estas contraseñas en argumentos, scripts ni control de versiones:

```powershell
$env:GIOVA_ROOT_PASSWORD = '<contraseña-root-de-XAMPP>'
$env:GIOVA_MIGRATOR_PASSWORD = '<contraseña-migrator-nueva>'
$env:GIOVA_APP_PASSWORD = '<contraseña-app-nueva>'
.\scripts\bootstrap-xampp.ps1
Remove-Item Env:GIOVA_ROOT_PASSWORD,Env:GIOVA_MIGRATOR_PASSWORD,Env:GIOVA_APP_PASSWORD
```

4. Completa `.env` con las credenciales runtime y arranca:

```powershell
docker compose up -d
Invoke-WebRequest http://127.0.0.1:8088/health
Invoke-WebRequest http://127.0.0.1:8088/api/v1/health/readiness
```

La aplicación queda en `http://127.0.0.1:8088`. Flyway aplica las migraciones
disponibles V1–V11 y V13–V15 al primer arranque; el árbol no contiene V12.
V13/V15 incluyen datos de demostración sintéticos marcados `[DEMO]`. No borres
datos copiando SQL de una migración. La readiness debe mostrar `READY` y
`database: UP`.

## Trabajadores y roles

Crear un trabajador solicita la contraseña de forma interactiva y no la guarda
en el proyecto:

```powershell
.\scripts\create-worker.ps1 `
  -Username operador `
  -DisplayName 'Operador GIOVA' `
  -Role ADMIN
```

Los roles son exactamente `ADMIN`, `ACCOUNTANT` y `SELLER`. La sesión usa
cookie HttpOnly y las escrituras requieren CSRF. La cuenta `ADMIN` administra
catálogo, usuarios y operaciones; `ACCOUNTANT` gestiona módulos contables y
consultas permitidas; `SELLER` trabaja sobre sus registros y permanece sin
permisos de administración.

## Desarrollo y pruebas

```powershell
Push-Location frontend
npm ci
npm run lint
npm test -- --watch=false
npm run build
Pop-Location

Push-Location backend
.\mvnw.cmd -B verify
Pop-Location
```

Para la prueba visual se requiere un navegador local y el stack integrado.
Recorrer las rutas públicas y los módulos de cada rol en 390, 768 y 1440 px,
comprobando teclado, foco, estados vacíos/errores, ausencia de overflow y que
el radar muestre su fuente. No hay un `ng e2e` configurado.

## Backup y restauración

El checkpoint recomendado combina SQL y documentos privados:

```powershell
.\scripts\backup-local.ps1 -OutputDirectory .\backups\local-YYYYMMDD
```

La restauración siempre usa una base y un volumen desechables. Define la
contraseña solo en la sesión actual y verifica filas y SHA-256:

```powershell
$env:GIOVA_RESTORE_DB_PASSWORD = '<contraseña-del-destino>'
.\scripts\restore-local.ps1 `
  -BackupDirectory .\backups\local-YYYYMMDD `
  -TargetDatabase giova_restore_YYYYMMDD `
  -TargetDocumentVolume giova-restore-YYYYMMDD
Remove-Item Env:GIOVA_RESTORE_DB_PASSWORD
```

Consulta `docs/ENTORNO_LOCAL.md` para diagnóstico y `docs/IMPORTACION.md` para
la preparación de MariaDB.

## Entrega local

Genera un paquete reproducible mediante la lista blanca del script. El paquete
excluye secretos, backups, documentos, dependencias instaladas, builds,
Graphify y documentación interna de planificación:

```powershell
.\scripts\package-delivery.ps1
```

El script crea el ZIP y un archivo lateral `.sha256`. Revisa ambos antes de
compartirlos. No se hace push, publicación ni despliegue desde este proyecto.

## Documentación

- `docs/ENTORNO_LOCAL.md`: arranque, diagnóstico, backup y restauración.
- `docs/IMPORTACION.md`: MariaDB, Flyway y semilla del catálogo.
- `docs/DATOS_Y_API.md`: contrato de datos y rutas API.
- `docs/TOOLCHAIN.md`: versiones y comandos verificados.
- `docs/referencia/ocds-p27-fuentes.md`: contrato y límites del radar OECE/OCDS.
- `frontend/README.md`: desarrollo específico de Angular.
