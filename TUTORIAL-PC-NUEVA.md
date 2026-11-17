# Levantar el sistema en una computadora nueva

Guía corta para una máquina donde no hay nada instalado, por ejemplo una de la
universidad. Para el detalle completo está `INSTALACION.md`; esto es el camino
directo.

Todo lo que necesitas está en esta misma carpeta: el código y, en `db\`, la
base de datos con los datos de prueba.

---

## 0 · Antes de nada: ¿tienes permisos de administrador?

En una computadora de laboratorio esto decide todo.

**Docker Desktop necesita instalación con administrador y virtualización
activada en la BIOS.** Si no puedes instalarlo, no te quedes atascado: al final
de esta guía está el **camino B**, que levanta lo mismo sin Docker.

Prueba rápido en PowerShell:

```powershell
docker --version
```

Si responde una versión, vas por el camino A. Si dice que no reconoce el
comando y no puedes instalarlo, salta al camino B.

---

## Camino A · Con Docker (lo normal)

### 1. Instalar

| Programa | Para qué |
|---|---|
| **XAMPP** | Trae MariaDB, la base de datos |
| **Docker Desktop** | Levanta el backend y el frontend |

No hace falta Java ni Node: Docker los trae dentro.

Abre el panel de XAMPP y dale **Start** a MySQL (debe quedar verde). Abre
Docker Desktop y espera a que diga *Engine running*.

### 2. Copiar la carpeta

Descomprime el proyecto en una ruta **corta y sin OneDrive**, por ejemplo:

```
C:\giova
```

Rutas largas o sincronizadas con OneDrive dan errores de permisos al compilar.

### 3. Preparar la base de datos

En PowerShell, parado en la carpeta del proyecto:

```powershell
cd C:\giova
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force

# La contraseña de root de XAMPP. Si no le pusiste ninguna, ábrela primero
# desde phpMyAdmin o pon una con mysqladmin.
$env:GIOVA_ROOT_PASSWORD = "tu-contraseña-de-root"

.\scripts\import-backup.ps1 -SqlPath .\db\giova_v1.sql
```

Pide escribir `giova_v1` para confirmar. Al terminar debe decir **37 tablas**.

Ahora crea los usuarios de la base y sus permisos. Elige contraseñas tuyas de
al menos 12 caracteres:

```powershell
$env:GIOVA_MIGRATOR_PASSWORD = "cambia-esta-clave-1"
$env:GIOVA_APP_PASSWORD      = "cambia-esta-clave-2"
.\scripts\bootstrap-xampp.ps1
```

### 4. Configurar

```powershell
Copy-Item .env.example .env
notepad .env
```

Completa estas dos líneas con las mismas de arriba, guarda y cierra:

```
GIOVA_DB_PASSWORD=cambia-esta-clave-2
GIOVA_FLYWAY_PASSWORD=cambia-esta-clave-1
```

### 5. Levantar

```powershell
docker compose up -d --build
```

La primera vez tarda varios minutos: compila Java y Angular. Cuando termine:

```powershell
docker compose logs api --tail 30
```

Tiene que aparecer `Started GiovaBackendApplication`. Antes verás a Flyway
aplicando la migración **V17**, que crea los sitios de las dos empresas.

### 6. Crear tu cuenta

El respaldo trae cuentas, pero sus contraseñas no viajan en claro. Créate las
tuyas:

```powershell
.\scripts\create-worker.ps1 -Username tu.admin -DisplayName 'Tu Nombre' -Role ADMIN -TenantCode GIOVA
.\scripts\create-worker.ps1 -Username tu.ti    -DisplayName 'Tu Nombre' -Role ADMIN -TenantCode SOLUCIONES_TI
```

Mínimo 12 caracteres, **sin tildes ni ñ** para evitar problemas de codificación.
Anótalas: no hay forma de recuperarlas.

### 7. Entrar

| Dirección | Qué se ve |
|---|---|
| http://localhost:8088 | Sitio público de GIOVA |
| http://soluciones.localhost:8088 | Sitio público de Soluciones Informática TI |
| http://localhost:8088/trabajadores/login | Acceso interno |

Si el navegador no resuelve `soluciones.localhost`, usa
`http://127.0.0.1:8088/?empresa=soluciones-ti`.

---

## Camino B · Sin Docker

Sirve cuando no puedes instalar Docker Desktop. Necesitas:

- **XAMPP** (igual que arriba)
- **JDK 17 o superior** — vale la versión portable de Temurin, que se
  descomprime sin instalar
- **Node.js 20 o superior** — también hay versión portable (el `.zip`, no el
  instalador)

Comprueba que los tiene:

```powershell
java -version
node -v
```

Haz los pasos **3 y 4** del camino A igual (importar la base, crear usuarios de
MariaDB, `.env`). Con una diferencia: como el backend ya no corre dentro de un
contenedor, la base se alcanza por `127.0.0.1`, no por `host.docker.internal`.

```powershell
cd C:\giova
$env:GIOVA_CATALOG_ENABLED = "true"
$env:GIOVA_DB_HOST         = "127.0.0.1"
$env:GIOVA_DB_PASSWORD     = "cambia-esta-clave-2"
$env:GIOVA_FLYWAY_PASSWORD = "cambia-esta-clave-1"

cd backend
.\mvnw.cmd spring-boot:run
```

Deja esa ventana abierta. En **otra** ventana de PowerShell:

```powershell
cd C:\giova\frontend
npm install
npm run dev
```

Se abre en `http://localhost:4200`. Ojo: por este camino el frontend corre en
el 4200, no en el 8088, y los dominios por empresa se prueban con
`http://localhost:4200/?empresa=soluciones-ti`.

---

## Si algo sale mal

| Síntoma | Qué pasó |
|---|---|
| `502 Bad Gateway` | El backend no arrancó. `docker compose logs api` |
| `Migrations have failed validation` | La base no está limpia. Vuelve a correr `import-backup.ps1` |
| `Connection refused to 127.0.0.1` en el log de Docker | Quedó la variable del paso 6 en esa ventana. Ciérrala y levanta desde una nueva |
| `El usuario ya existe` | Esa cuenta ya fue creada. Usa otro nombre |
| `no está firmado digitalmente` | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force` |
| Docker: *Virtualization support not detected* | Falta VT-x o SVM en la BIOS. Usa el camino B |
| El puerto 8088 está ocupado | Cambia el puerto en `compose.yaml`, en el servicio `web` |

---

## Dos avisos

**La carpeta `db\` tiene datos reales** de las dos empresas y los hashes de las
contraseñas de los trabajadores. Está excluida de git a propósito. No la subas
a GitHub ni la dejes en una carpeta compartida del laboratorio.

**Al terminar en una computadora prestada**, borra la base y la carpeta:

```powershell
& "C:\xampp\mysql\bin\mysql.exe" -u root -p -e "DROP DATABASE giova_v1;"
```

y elimina `C:\giova`. Si no, dejas los datos de los clientes en una máquina
que usa cualquiera.
