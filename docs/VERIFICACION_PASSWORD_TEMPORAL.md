# Contraseña temporal invisible al crear ADMIN

Fecha local: 2026-10-08. Estado técnico: **Aprobado con observaciones**; Antigravity aprobó la corrección.

## Fallo y alcance

Hallazgo Medio, categoría UX/credenciales: el alta de ADMIN recibía una contraseña temporal y mostraba el éxito, pero `refreshTenants` invocaba `selectTenant`, que borraba la contraseña recién recibida. La prueba de regresión antes de modificar la implementación falló por ausencia del panel: una prueba fallida de 49. El resultado no implica que el backend dejara de generar la contraseña.

Contrato acotado: cambiar únicamente `platform-page.component.ts` y su spec. Conservar la contraseña en memoria durante el refresco de la misma empresa; borrarla al seleccionar manualmente, cambiar de empresa, iniciar nueva empresa o pulsar «Ocultar y borrar». No persistir en localStorage, documentación ni logs. Al vincular una cuenta existente no se genera una clave nueva: el mensaje debe indicar que conserva su contraseña y ofrecer la acción de restablecimiento existente. Sin cambios de backend, API ni DB.

Corrección: la selección automática de la misma empresa conserva la contraseña; las demás selecciones la borran. Dos regresiones nuevas cubren persistencia tras refresco/borrado explícito y selección manual. El mensaje de vinculación explica el uso de la contraseña actual.

## Verificación ejecutada

- Frontend lint/typecheck, **49 pruebas en 12 archivos** y build: aprobados. Permanecen avisos previos de bundle inicial 510.21/500 KB y CSS de contabilidad/cotizaciones.
- Docker: `docker compose up -d --build --no-deps web`, web reconstruida y saludable; API y volúmenes conservados.
- Playwright con navegador real a 390/768/1440 px: panel visible después del refresco y ocultación explícita confirmados. Cuenta existente: mensaje explicativo y sin panel de contraseña nueva.
- El POST de cuentas y las respuestas de lista ADMIN fueron **simulados**, con datos de prueba no persistentes. Directorio de empresas consultado contra API real. No se creó ni restableció ninguna cuenta real durante esta corrección.
- Consulta posterior real del directorio: TI tiene **un ADMIN**. El checkpoint anterior describía cero administradores en el momento del alta inicial; la cuenta fue añadida después de aquella prueba. No se registraron su identidad ni credenciales en este informe.
- Graphify: 3.394 nodos, 9.690 aristas, 137 comunidades; actualización estructural sin LLM. Conservado `original-js/`.

## Recuperación para el usuario

Recargar la aplicación para recibir la corrección. Seleccionar la empresa y pulsar «Restablecer contraseña» en el ADMIN ya creado; confirmar. La nueva clave aparece en «Contraseña temporal», arriba de la página, y debe cambiarse en el primer acceso. No es posible recuperar en claro la contraseña anterior una vez oculta o borrada. No recrear la cuenta.

Sin commits, push, migraciones, restablecimientos reales ni nuevas fases. La aceptación integral P57 sigue pendiente. Se conservan dependencias activas y evidencia de pruebas; no se borran respaldos ni datos.

## Auditoría independiente
Antigravity run c07670f21907451d9261a370139fb618, gemini-3.8-flash-medium/medium: **APROBADO**, nueve criterios. Cuatro comandos ejecutados y aprobados; fuentes intactas, ninguna acción denegada ni comando omitido. Informe original: C:/Users/moral/.codex/antigravity-audit/runs/c07670f21907451d9261a370139fb618/report.md. Las pruebas de navegador descritas fueron ejecutadas por Codex, con respuesta de creación simulada; no acreditan escritura real de cuentas. Listo para continuar, con observaciones y pendientes de fases previas conservados.
