# Importación, respaldo y migraciones P03–P31

Flyway está embebido en el backend; no hay que instalarlo aparte. Aplica `V1__create_schema.sql` y `V2__seed_catalog.sql` en el esquema exclusivo `giova_v1`. La semilla se genera desde `docs/referencia/catalogo-original.json` con `node scripts/generate-seed.mjs`; una vez aplicada, las migraciones se consideran inmutables.

## XAMPP

1. Comprueba que MariaDB 10.4.32 escucha en `127.0.0.1:3306`.
2. Genera un respaldo antes de cualquier escritura:

```powershell
.\scripts\backup-xampp.ps1
```

3. Define las credenciales solo en la sesión actual:

```powershell
$env:GIOVA_ROOT_PASSWORD = '<contraseña-root-de-XAMPP>'
$env:GIOVA_MIGRATOR_PASSWORD = '<contraseña-migrator-nueva>'
$env:GIOVA_APP_PASSWORD = '<contraseña-app-nueva>'
.\scripts\bootstrap-xampp.ps1
```

4. Completa `.env` con las credenciales de `giova_app` y `giova_migrator`, y ejecuta `docker compose up -d`.

El script concede `ALL` únicamente a `giova_migrator` sobre `giova_v1` para DDL de Flyway y solo `SELECT, INSERT, UPDATE, DELETE` a `giova_app`. No crea `root@%`, no cambia otras bases y no guarda contraseñas en archivos.

En esta sesión se verificó `root@localhost` contra `10.4.32-MariaDB`, se creó exclusivamente `giova_v1`, se generaron `giova_app` y `giova_migrator` con privilegios acotados, y Flyway aplicó V1/V2. Resultado: 14 marcas, 8 categorías, 52 productos y cero trabajadores/envíos iniciales. Se generó respaldo SQL antes y después de la migración; las demás bases no se modificaron.

## Verificación aislada ejecutada

Una MariaDB `10.4.32` efímera se migró correctamente: 14 marcas, 8 categorías y 52 productos; 52 SKU únicos; Unicode conservado; dos migraciones exitosas. Un segundo arranque no duplicó filas. La API pública devolvió 52 productos, una coincidencia para `CF258A`, 8 categorías, 12 marcas destacadas y 400 para `size=101`.
