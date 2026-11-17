# Frontend GIOVA

Aplicación Angular 21.2 para el catálogo público y el área privada de
operaciones. La ejecución completa recomendada está documentada en el
[`README.md`](../README.md) de la raíz.

## Requisitos

- Node.js 24.19.x y npm 11.x para desarrollo local.
- MariaDB 10.4.32 disponible si se ejecuta el API fuera de Docker.

## Instalación y desarrollo

Desde esta carpeta:

```powershell
npm ci
npm start
```

La aplicación de desarrollo queda en `http://localhost:4200/`. Para la
ejecución integrada con API, MariaDB y Nginx, usar `docker compose` desde la
raíz del proyecto.

## Comprobaciones

```powershell
npm run lint
npm test -- --watch=false
npm run build
```

`lint` ejecuta el typecheck y la comprobación de Prettier. `test` usa Vitest a
través del builder de Angular. No hay un comando `ng e2e` configurado en este
proyecto; la aceptación de navegador se ejecuta con Playwright en el entorno
local integrado.

## Estructura funcional

- Rutas públicas: `/`, `/productos`, `/contacto` y `/reclamaciones`.
- Acceso interno: `/trabajadores/login` y `/operaciones`.
- Roles fijos: `ADMIN`, `ACCOUNTANT` y `SELLER`.
- El frontend usa sesión HttpOnly y el token CSRF emitido por el backend; no
  guarda contraseñas ni tokens en `localStorage`.

Para comandos y documentación de operación, consultar el README raíz y
`docs/ENTORNO_LOCAL.md`.
