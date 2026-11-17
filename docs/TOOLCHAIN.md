# Toolchain fijado en P01 y verificado en P31

Fecha de verificación del entorno de entrega: 2026-09-16.

| Área | Versión/comando | Estado |
|---|---|---|
| Node.js | 24.19.0 | Verificado en el host |
| npm | 11.17.0 | Verificado en el host |
| Angular CLI | 21.2.2 | Fijado por `frontend/package.json` y lockfile |
| Angular | 21.2.x resuelto en `package-lock.json` | Instalado con `npm ci`/`npm install` de P01 |
| TypeScript | 5.9.x resuelto en lockfile | Typecheck exitoso |
| Java | 17 como target; 21.0.12 LTS en el host de esta verificación | Maven compila con `java.version=17`; Docker usa Temurin 17 |
| Spring Boot | 4.1.1 | Fijado en `backend/pom.xml` |
| Maven Wrapper | 3.3.4 script + distribución Maven 3.9.16 | `mvnw.cmd verify` exitoso |
| MariaDB | 10.4.32 en XAMPP | Verificado, sin conexión ni cambios en P01 |
| Docker Compose | 5.5.1 CLI | CLI y Engine disponibles en P31 |
| Playwright CLI | `@playwright/cli` vía `npx` | Capturas de origen exitosas |

## Reproducibilidad

- Frontend: `npm ci` es el comando reproducible y el Dockerfile lo usa. En esta ruta OneDrive puede fallar al eliminar DLL nativas con `EPERM`; si ocurre, cerrar procesos que usen `frontend/node_modules`, repetir `npm ci` y usar `npm install --force` solo como recuperación local documentada.
- Backend: ejecutar `backend\mvnw.cmd verify`; el wrapper descarga Maven 3.9.16 desde Maven Central.
- El primer uso requiere acceso a los registries Maven/npm. Los lockfiles y el wrapper son parte del proyecto.
- El shell backend usa Java 17 y no incorpora JDBC, Flyway ni configuración de base en P01.
- Los artefactos de build (`frontend/dist`, `backend/target`) quedan fuera de la entrega mediante `.gitignore`.
- Las imágenes de build fijan Node 24.19.0, Maven 3.9.16, Temurin 17 y Nginx 1.29-alpine; no se usa `latest` en archivos de infraestructura.

## Verificación ejecutada

| Comando | Resultado |
|---|---|
| `npm ci` | Comando reproducible | Requiere una carpeta de trabajo sin bloqueos de OneDrive |
| `npm run lint` | Aprobado en P30 | TypeScript y Prettier |
| `npm test -- --watch=false` | Aprobado en P30 | 8 archivos, 22 pruebas |
| `npm run build` | Aprobado en P30 | Bundle Angular de producción |
| `backend\mvnw.cmd -B verify` | Aprobado en P30 | 78 pruebas, 0 fallos |
| `docker compose config --quiet` | Aprobado en P31 | Configuración válida |
| `docker compose build api web` | Aprobado en P30 | Imágenes reconstruidas; digests registrados en `docs/PLAN_PROYECTO.md` |
| MariaDB/XAMPP + readiness | Aprobado en P30 | MariaDB 10.4.32, API `READY`, base `UP` |
| `scripts/package-delivery.ps1` | Aprobado en P31 | Lista blanca, inspección de rutas y SHA-256 del ZIP |
