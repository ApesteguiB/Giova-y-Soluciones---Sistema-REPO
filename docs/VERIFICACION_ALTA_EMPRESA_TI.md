# Verificación del alta de Soluciones Informáticas TI

Fecha local: 2026-10-08. Estado: **Aprobado con observaciones**. Petición: reproducir el fallo de registro, corregirlo y registrar la segunda empresa con datos genéricos autorizados. Codex implementa y verifica por instrucción expresa de trabajar sin Claude; Antigravity aprobó la corrección en solo lectura.

## Especificación y alcance

Corrección acotada a los cuatro archivos `frontend/src/app/platform/platform-page.component.{ts,html,css,spec.ts}`. No cambia contratos HTTP, backend, esquema, migraciones, landing ni cliente móvil. Criterios: los formularios inválidos explican el error sin enviar solicitudes; código/slug se normalizan antes de validar; el éxito permanece tras refrescar el directorio; el logo SVG de referencia se convierte a PNG en navegador y utiliza el almacenamiento privado existente; las pruebas conservan los controles de acceso.

## Fallos reproducidos y correcciones

| Severidad y categoría | Evidencia e impacto | Corrección y cierre |
|---|---|---|
| Medio · validación/UX | Nueva → Crear empresa con código y razón social vacíos: cero POST y cero alertas. Un código en minúsculas también bloqueaba el envío sin explicación. | Errores por campo y alerta; trim, código en mayúsculas y slug en minúsculas antes de validar. Prueba real a 390/768/1440 px: dos campos señalados, cero POST, sin overflow. |
| Leve · confirmación | Un alta válida devuelve 201, pero el refresco seleccionaba la empresa y borraba el mensaje de éxito. | Selección durante refresco conserva la confirmación. Prueba unitaria de alta y navegador real con actualización 200 y «Cambios guardados.» visible. |
| Medio · marca | «Usar el logo vectorial mejorado» fallaba al decodificar SVG con createImageBitmap; no se enviaba el logo. | Image.decode sobre URL temporal, canvas y conversión PNG; revocación de URL. PUT real 200; imagen 1520 × 600, MIME image/png y Cache-Control no-store. |

También se valida el formulario ADMIN con mensaje visible. No se creó ni vinculó una cuenta ADMIN durante esta prueba; la empresa tiene cero administradores y su acceso operativo requiere asignarlos desde plataforma.

## Alta real y recuperación

Destino comprobado: `127.0.0.1:3306`, esquema `giova_v1`, MariaDB **10.4.32**, Flyway **V16**. Respaldo previo mediante `scripts/backup-local.ps1`, incluyendo base y volumen documental; se pausó/reanudó solamente el API local. Archivos conservados en `backups/pre-ti-registration-20261008/`:

- `giova_v1-20261009-014408.sql`, SHA-256 `7ce504229cf4eb4f1ab9786bbb03b8840529cd2015d1159bda323cb9e0f85e1b`.
- `giova_documents-20261009-014408.tar.gz`, SHA-256 `d33647cd337e229d21501b527e353af8c1f7110f3c204eb306093f044d5b9a06`.
- Manifiesto `giova_v1-20261009-014408.manifest.json`. La fecha del nombre corresponde a UTC.

Alta desde UI real con sesión SUPERADMIN y API real: **POST 201**. Empresa **2**, código `SOLUCIONES_TI`, slug `soluciones-informaticas-ti`, razón social provisional y nombre visible `Soluciones Informáticas TI`. PEN e IGV 0.18 como valores genéricos; RUC, dirección y correo vacíos. Encabezado «Datos provisionales de prueba». Colores `#E87835`, `#FFFFFF`, `#F3B247`. Logo mejorado cargado al almacenamiento privado. Verificación posterior: exactamente dos empresas, GIOVA intacta en versión 0, TI activa en versión 1 tras actualizar su formulario. Persistencia confirmada después de recargar.

Para corregir datos provisionales se usa el editor de plataforma. Para retirar la empresa se desactiva, sin borrar datos. Una restauración completa del respaldo requiere una instrucción específica y perdería las escrituras posteriores; no se ejecutó restauración.

## Comprobaciones ejecutadas

- `npm.cmd --prefix frontend run lint`: aprobado, incluye typecheck y formato.
- `npm.cmd --prefix frontend test -- --no-progress`: **47 pruebas / 12 archivos**, cero fallos; cinco regresiones nuevas.
- `npm.cmd --prefix frontend run build`: aprobado. Avisos previos: bundle inicial 510.21/500 KB y presupuestos CSS de contabilidad/cotizaciones excedidos.
- `docker compose up -d --build --no-deps web`: aprobado; web local reconstruida, API no reconstruida, volúmenes conservados.
- Playwright real sobre `http://127.0.0.1:8088`, sin respuestas simuladas: validación responsive, actualización normalizada, confirmación, logo y persistencia.
- API real: directorio sin sesión **401**; SUPERADMIN accediendo a tareas de empresa **403**; logo autenticado **200**, PNG, no-store.
- Evidencia local: `output/playwright/ti-registration-before.png`, `ti-registration-after.png` y `ti-invalid-{390,768,1440}.png`.

Limitaciones: los datos legales definitivos siguen pendientes; no se ejecutó la aceptación integral P57, ni CRUD de subcuentas con ADMIN real, ni pruebas móviles. No se modificó backend y no se repitió Maven. No hubo commits, push ni publicación. La revisión y entrega anteriores no acreditan estos cambios nuevos.

## Auditoría independiente y cierre
Antigravity run 883fb4c891ef4f8796880d0da2f75ff5 (gemini-3.8-flash-medium / medium): **APROBADO** en ocho criterios. Ejecutó diff/check, lint/typecheck, 47 pruebas y build; cuatro comandos aprobados, ninguno omitido, ninguna acción denegada y fuentes sin cambios. El navegador y las consultas reales son evidencia ejecutada por Codex, no por el auditor. Informe original: C:/Users/moral/.codex/antigravity-audit/runs/883fb4c891ef4f8796880d0da2f75ff5/report.md.

Graphify actualizado: 3.393 nodos, 9.689 aristas, 138 comunidades; extracción estructural sin LLM. Se conservaron evidencia de reproducción/cierre, respaldos y dependencias. Volumen observado antes del cierre: output/playwright 2,08 MiB; frontend/dist 0,87 MiB; caché Angular 6,87 MiB; graphify-out 46,80 MiB. No se purgaron artefactos ajenos ni cachés utilizadas por la auditoría.
