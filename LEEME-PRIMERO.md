# Léeme primero

Sistema de gestión comercial multiempresa (GIOVA y Soluciones TI sobre la misma
plataforma). Esta carpeta trae el código, los scripts y la base de datos.

## Qué hay aquí

- `TUTORIAL-PC-NUEVA.md` — camino directo para levantarlo en una máquina limpia.
- `INSTALACION.md` — guía completa, con solución de problemas.
- `db/giova_v1.sql` — base de datos con los datos de prueba. Impórtala ANTES de
  levantar el sistema; al arrancar, la aplicación le aplica sola la migración
  V17 (sitios públicos por empresa).
- `docs/SITIOS_POR_EMPRESA.md` — cómo cada empresa tiene su propio sitio público.
- `.env.example` — cópialo como `.env`. `bootstrap-xampp.ps1` lo genera por ti.

No incluye `.env`, `node_modules`, `target` ni la carpeta `.git`: cada quien los
genera en su máquina.

## Para verlo funcionando

- Sitio de GIOVA: `http://localhost`
- Sitio de Soluciones TI: `http://localhost/?empresa=SOLUCIONES_TI`
  (el `?empresa=` solo funciona desde localhost, para demostrar las dos empresas
  sin registrar dominios reales; en producción cada una entra por su dominio)
- Acceso interno: `http://localhost/trabajadores/login`

## Contraseñas: cómo se crean realmente

**Nadie escribe un hash a mano.** El sistema guarda solo el hash (BCrypt), pero
lo calcula él. Hay tres caminos, y ninguno pasa por editar la base de datos:

1. **Primer usuario de una empresa** (cuando aún no puedes entrar):
   `.\scripts\create-worker.ps1 -Username admin.giova -DisplayName "Admin GIOVA" -Role ADMIN -TenantCode GIOVA`
   El script pide la contraseña por teclado (mínimo 12 caracteres), nunca la
   muestra en pantalla y la guarda ya cifrada. Si el usuario ya existe, el
   script falla a propósito: no sirve para cambiar contraseñas.

2. **Los demás usuarios** los crea el administrador desde el sistema, en
   **Operaciones → Administración → Usuarios**. Ahí el sistema genera una
   contraseña temporal, la muestra UNA sola vez y el usuario la cambia al
   entrar. Desde la misma pantalla se restablece la contraseña de cualquiera.

3. **Cada quien cambia la suya** en `/trabajadores/cambiar-contrasena`.

Editar la tabla `workers` a mano, además de innecesario, deja la cuenta rota:
desde la migración V16 el rol no vive en `workers` sino en `worker_memberships`,
así que un INSERT manual crea un usuario que entra pero no ve nada.
