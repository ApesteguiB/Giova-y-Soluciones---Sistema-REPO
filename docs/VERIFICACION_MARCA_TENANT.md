# Colores operativos por empresa

Fecha local: 2026-10-08. Estado técnico: **Aprobado con observaciones**; auditoría independiente APROBADO.

## Reproducción y contrato

Hallazgo Medio · UI/marca: en la sesión ADMIN real de TI, la API suministra `#E87835`, pero «Nueva meta» usa el gradiente `#2EC8DD` → `#14A3A8` de GIOVA. El layout ya tenía marca dinámica; los módulos internos y utilidades globales conservaban tokens antiguos y acentos escritos directamente.

Corrección autorizada: tematizar botones, enlaces, pestañas, títulos, bordes/focus y superficies decorativas dentro de la empresa seleccionada. Conservar legibilidad, estados semánticos, permisos y API. No cambiar datos, cuentas, backend, dependencias ni landing/login públicos.

Archivos de producto: `frontend/src/styles.css`; `core/tenant-branding.service.ts` y spec; `admin/admin-layout.component.html` y CSS; `admin/admin-home.component.html`; `admin/company-users-page.component.css`; CSS de cotizaciones, contabilidad, operaciones y licitaciones (rutas bajo `frontend/src/app/`).

## Implementación

- `admin-shell[data-tenant-theme]` activa alias de compatibilidad para tokens navy/blue/teal/cyan y fondos de marca. Sin marca, los alias no se aplican; no hay ciclos con las variables predeterminadas.
- Botones operativos toman color principal y contraste calculado; hover usa una variante oscura con texto blanco. Encabezados y links usan una variante que mantiene la proporción RGB de la marca, oscurecida hasta al menos 5,5:1 frente a blanco para dejar margen con fondos teñidos.
- Sidebar usa el contraste de la marca; subcuentas usa los tokens en lugar de naranja fijo. Acentos globales aqua, bordes/focus y decoraciones azules se ajustan en el área de operaciones. Gráficos de avance usan la variante de marca.
- Estados éxito/error/advertencia conservan sus colores semánticos. La presentación pública conserva GIOVA. El inicio operativo ya dice «empresa seleccionada», sin nombre fijo de otra empresa.

## Pruebas ejecutadas

Frontend: lint/typecheck/formato, **51 pruebas / 12 archivos** y build aprobados. Dos regresiones nuevas verifican contraste de colores claros/saturados/oscuros y sustitución de marca al cargar otra empresa. Un intento inicial de lint detectó formato diferente por usar Prettier desde la raíz; se ejecutó desde frontend y pasó.

Playwright con navegador y sesión ADMIN TI reales, en una pestaña QA separada: inicio, productos, clientes, cotizaciones, facturas, licitaciones y usuarios cargaron la marca TI; no se emitieron escrituras API. Metas y formulario de nueva meta se abrieron sin guardar. A 390/768/1440 px: botones `rgb(232,120,53)`, texto negro, sin overflow. Texto/acento de marca `#A05325`. Contraste medido en navegador: botón y navegación 7,17:1; título sobre superficie 5,22:1.

Prueba visual de sustitución de marca: solo la respuesta de branding de GIOVA fue **simulada**, con la sesión/datos TI conservados. Botón `#0A2342` y texto blanco; al retirar la simulación y recargar retornó el naranja real de TI. Esto no acredita cambio real de membresía ni la matriz integral de aislamiento. Landing real mantiene teal `#14A3A8` y gradiente aqua original.

Evidencia local en `output/playwright/`: `ti-colors-before.png`, `ti-colors-{390,768,1440}.png`, `ti-goal-modal-colors.png`. La identidad se oculta en las capturas. No se incluyen credenciales ni información de personas en el informe.

Docker: solo web reconstruida mediante `docker compose up -d --build --no-deps web`; API y volúmenes conservados. No se cerró ni navegó la pestaña personal del usuario para ejecutar QA. No hubo commits, push, migraciones ni pruebas móviles.

## Observaciones y reversión

El build conserva presupuestos excedidos: inicial **512,04/500 KB**, cotizaciones **11,86/8 KB**, contabilidad **8,95/8 KB**. Antes: 510,21 KB, 11,77 KB y 8,91 KB respectivamente. El incremento proviene de los estilos/derivación de colores; compilación exitosa, sin modificar presupuestos para ocultarlo.

No se ejecutó P57 integral. Para revertir esta corrección, retirar los bloques scoped y los cambios enumerados, conservando las modificaciones previas del árbol; reconstruir solo web. No restaurar DB ni borrar datos, porque esta tarea no escribió en la base.

## Auditoría y cierre
Antigravity run 081905a533b34f70b277da7b86628874, gemini-3.8-flash-medium / medium: **APROBADO** en ocho criterios. Ejecutó cuatro comandos (diff/check, lint/typecheck, 51 tests y build), todos aprobados; ninguna acción denegada, comando omitido o fuente alterada. El navegador es evidencia de Codex; la revisión no ejecutó cambio real de membresía ni escrituras API. Informe original: C:/Users/moral/.codex/antigravity-audit/runs/081905a533b34f70b277da7b86628874/report.md.

Graphify actualizado: 3.401 nodos, 9.696 aristas y 153 comunidades, sin LLM. Se conservaron original-js, respaldos, dependencias activas y evidencia necesaria; cuatro capturas de colores suman 339.621 bytes, caché Angular observada 7.207.232 bytes. No se generaron scripts temporales nuevos ni se purgaron archivos ajenos. Estado de continuidad: listo para continuar con las observaciones descritas.
