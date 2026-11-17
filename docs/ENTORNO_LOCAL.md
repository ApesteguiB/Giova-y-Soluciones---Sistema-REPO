# Entorno local P02–P31

La entrega usa MariaDB 10.4.32 de XAMPP como base persistente. Docker ejecuta únicamente `api` y `web`; no crea un contenedor de base de datos de runtime.

## Comprobación

```powershell
Set-Location C:\ruta\GIOVA-ANGULAR+BACKEND
.\scripts\check-environment.ps1
docker compose config --quiet
```

MariaDB debe escuchar en `127.0.0.1:3306`. El acceso desde Docker usa `host.docker.internal`. La aplicación no modifica `my.ini`, firewall ni otras bases.

## Arranque y respaldo

```powershell
.\scripts\backup-xampp.ps1
docker compose build
docker compose up -d
```

La web queda en `http://127.0.0.1:8088`. Nginx publica `/api/*` mediante el servicio interno `api`; el backend no publica un puerto al host.

Estado de esta entrega: `giova_v1` se crea y migra en XAMPP; `.env` usa `giova_app` para runtime y `giova_migrator` para Flyway. No ejecutar la aplicación con `root`.

El API usa el volumen Docker `giova-v1-documents` montado en `/var/lib/giova/documents`. Aunque `.env` conserva `GIOVA_DOCUMENTS_ROOT=storage/documents` para ejecución local directa, `compose.yaml` fija la ruta interna del contenedor y no expone `/storage/documents` mediante Nginx.

Para un checkpoint conjunto de base y documentos, usar `./scripts/backup-local.ps1`; `./scripts/backup-xampp.ps1` queda como respaldo SQL legado y no sustituye el checkpoint conjunto P30:

```powershell
./scripts/backup-local.ps1 -OutputDirectory ./backups/p30-YYYYMMDD
```

La restauración exige un destino desechable y no sobrescribe esquemas ni volúmenes existentes:

```powershell
$env:GIOVA_RESTORE_DB_PASSWORD = '<contraseña-local-del-servidor-destino>'
./scripts/restore-local.ps1 `
  -BackupDirectory ./backups/p30-YYYYMMDD `
  -TargetDatabase giova_p30_restore_YYYYMMDD `
  -TargetDocumentVolume giova-p30-restore-YYYYMMDD
Remove-Item Env:GIOVA_RESTORE_DB_PASSWORD
```

La restauración debe hacerse en MariaDB de desarrollo, verificando las filas de `stored_documents`, los archivos y sus SHA-256. No guardar la contraseña en documentación, scripts ni control de versiones.

## Diagnóstico seguro

```powershell
docker compose ps
docker compose logs --tail=100 api
Invoke-WebRequest http://127.0.0.1:8088/health
Invoke-WebRequest http://127.0.0.1:8088/api/v1/health
Invoke-WebRequest http://127.0.0.1:8088/api/v1/health/readiness
```

La readiness devuelve `200 READY` solo cuando la aplicación puede consultar MariaDB. Si se solicita una ruta API inexistente, Nginx conserva la respuesta JSON `404`; no entrega `index.html`.

Si MariaDB no está disponible, no se deben borrar volúmenes ni reiniciar XAMPP con datos. Corrige el servicio y vuelve a ejecutar `docker compose up -d`; Flyway reanuda desde la última migración registrada.

Para crear el primer trabajador sin guardar la contraseña en el repositorio. El rol es obligatorio y está limitado al conjunto fijo:

```powershell
.\scripts\create-worker.ps1 -Username operador -DisplayName 'Operador GIOVA' -Role ADMIN
```

El login usa sesión de servidor y CSRF. Los trabajadores habilitados usan los roles fijos `ADMIN`, `ACCOUNTANT` y `SELLER`; las escrituras privadas exigen rol autorizado y token CSRF.

Rutas de la versión: `/`, `/productos`, `/contacto`, `/reclamaciones` (también `/libro-reclamaciones`), `/trabajadores/login`, `/admin`, `/admin/productos`, `/admin/marcas` y `/admin/categorias`. El acceso interno aparece de forma discreta como “Área de operaciones” al final del footer. Las rutas privadas P17–P29 cuelgan de `/operaciones` y requieren sesión, rol y CSRF para escrituras.

## Entrega y verificación P31

El paquete local se genera con una lista blanca y no incluye `.env`, backups,
documentos privados, `node_modules`, `target`, `dist`, `graphify-out` ni los
documentos internos de planificación:

```powershell
.\scripts\package-delivery.ps1
```

El comando produce el ZIP y su archivo lateral `.sha256`. Antes de compartirlo,
verifica el contenido del ZIP y el hash. La aceptación de interfaz debe recorrer
las rutas públicas y el área privada con cada rol en 390, 768 y 1440 px; revisa
también teclado, foco, estados vacíos/errores, overflow y la fuente visible del
radar de licitaciones.
