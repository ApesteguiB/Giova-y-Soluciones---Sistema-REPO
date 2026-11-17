# Verificación: OECE/OCDS y marca móvil

Fecha local: 2026-10-08. Estado global: **Aprobado con observaciones**. Auditorías independientes de backend y móvil aprobadas.

## Diagnóstico

- Alto, funcionalidad/red-API: ejecución `db98080c-b766-4b2c-b5e9-bc260d10b3db` FAILED después de 1 página, 11 releases y 22 upserts. Logs MariaDB 1052: columna version ambigua. El UPSERT INSERT SELECT usa perfiles con version y coincidencias con version; la suma sin calificar confunde ambas tablas. Se reproduce el SQL anterior contra MariaDB 10.4.32 y se corrige con `version=tender_matches.version+1`. No cambia endpoint ni migración.
- Alto, UX/funcionalidad: Flutter seguía con logo GIOVA, tema constante y Worker sin membresía. La corrección anterior de colores fue solamente Angular. Ahora obtiene marca/logo desde los endpoints P53 y cambia la membresía con el contrato P52. Login neutral, cambio obligatorio de contraseña, purga de documentos/imagen y remontaje por generación de sesión. Los roles sin paridad móvil se dirigen a Sin acceso móvil; no abre vistas ADMIN.

## Comprobaciones ejecutadas por Codex

- Destino: 127.0.0.1:3306, esquema giova_v1, MariaDB 10.4.32; Flyway V16 existente. Respaldo completo en `backups/pre-oece-mobile-20261008/`: SQL 107068253 bytes SHA-256 3f74d2eef71d69bb7dc3b31c811da4aebcd350ce982ca635daf5da28189575ad y archivos privados 68469 bytes/18 archivos SHA-256 047c63cb607656ba034433c6e716dd8148f7d6bb7f1ec2d92d686d7e3d88de31.
- SQL anterior: ERROR 1052. SQL corregido en transacción: conserva tenant, estado, propietario y suma version; un perfil de otra empresa no escribe. ROLLBACK ejecutado.
- `TenderSyncMariaDbTest`: servicio real + MariaDB con proveedor sintético, dos perfiles/empresas y dos ejecuciones SUCCEEDED, dos coincidencias por ejecución y actualización de version. Prueba opt-in, transacción revertida. Sin cuenta nueva ni proveedor externo.
- `backend/mvnw.cmd -B -f backend/pom.xml verify`: 156 pruebas, 0 fallos, 0 errores, 1 omitida (integración opt-in ejecutada por separado). La ejecución sin configuración local falló inicialmente por ausencia de JdbcTemplate; la ejecución final cargó el entorno local sin imprimir secretos y deshabilitó OECE durante las pruebas generales.
- `flutter analyze --no-pub`: sin incidencias. `flutter test --no-pub --timeout 30s`: 44/44. Formato 44 archivos, 0 cambios en la última comprobación. Ocho pruebas nuevas de colores/contraste, membresía, purga, fallback, contraseña y bloqueo de ruta ADMIN para SELLER.
- APK debug compilado con config/local.json. Primer build falló en el proceso Gradle al detectar Git; repetido con PATH explícito y daemon deshabilitado solo en el proceso de comprobación. No se modificó configuración global, SDK ni ACL.
- API Docker reconstruida y arrancada, conservando DB/volúmenes; cierra sesiones previas. No se modificó Angular ni scheduler.
- Android: Appium UiAutomator2 sobre emulator-5554, 1344×2992 px. API sintética loopback aislada; APK de comprobación apuntó a ella. Login neutral → selector → TI con PNG de marca autorizado y naranja → radar RUNNING/SUCCEEDED → cambio GIOVA sin logo TI. Capturas `output/oece-mobile/mobile-ti-home-synthetic.png`, `mobile-ti-drawer-synthetic.png`, `mobile-ti-radar-synthetic.png`, `mobile-giova-home-synthetic.png`. No acredita dos cuentas reales ni teléfono físico.
- Web: Playwright, pestaña propia y respuestas API sintéticas por elección del propietario. Botón → RUNNING → SUCCEEDED y resultado visible. Evidencia `output/oece-mobile/web-radar-synthetic.png`. Las pestañas del usuario y la base no se modificaron desde esta UI de comprobación.

## Auditoría y entrega

Antigravity aprobó backend (run `fef3da94077841ec98ba822d0a264ad0`, 18 pruebas focalizadas) y móvil (run `25166c6c7b07476ab270bbc3c7b131c8`, diff, analyze y 44 pruebas). Los runners no modificaron fuentes, no omitieron comandos y no registraron acciones denegadas. La integración MariaDB fue ejecutada por Codex; el auditor la revisó en solo lectura.

APK final recompilado con `config/local.json`, instalado en emulator-5554 y conectado al backend local en `10.0.2.2:8088`. El servidor sintético de QA se detuvo. ZIP de fuentes/documentación actualizado a `GIOVA-para-companeros-20261008-corregido-v4.zip`, sin credenciales, respaldos, dependencias ni builds. APK local separado en `output/oece-mobile/app-debug-local.apk`.

## Límites

El propietario eligió continuar con pruebas sintéticas después del reinicio. No se inició una nueva sincronización contra OECE con su cuenta ni se registraron cuentas de prueba. La causa SQL está reproducida y corregida; no afirmar aceptación actual del proveedor externo. P59 y P63 permanecen parciales: se corrige marca/membresía para ADMIN y el acceso neutral, pero falta paridad de vistas Contador/Vendedor (P60/P61), aceptación de dos cuentas reales y física P64. P62 continúa no aprobada.

La limpieza iOS .packages previamente rechazada no se repitió ni se cambiaron ACL. --no-pub reutiliza dependencias sin cambiarlas. Conservar respaldo, evidencia compacta y APK local para entrega; APK sintético solo para QA, no para compañeros. Sin commit/push/PR.
