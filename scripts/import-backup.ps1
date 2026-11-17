param(
    [Parameter(Mandatory = $true)] [string]$SqlPath,
    [string]$RootUser = 'root',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3306,
    [string]$Database = 'giova_v1'
)

# Reemplaza por completo la base `giova_v1` con el contenido de un respaldo
# .sql (el que genera phpMyAdmin o mysqldump). Antes de borrar nada, guarda un
# respaldo de lo que haya ahora.

$ErrorActionPreference = 'Stop'
$mysql = 'C:\xampp\mysql\bin\mysql.exe'
$mysqldump = 'C:\xampp\mysql\bin\mysqldump.exe'
if (-not (Test-Path $mysql)) { throw "No se encontró $mysql" }
if (-not (Test-Path $mysqldump)) { throw "No se encontró $mysqldump" }

$SqlPath = (Resolve-Path -LiteralPath $SqlPath).Path
if (-not (Test-Path -LiteralPath $SqlPath -PathType Leaf)) { throw "No existe el archivo $SqlPath" }
if ([string]::IsNullOrWhiteSpace($env:GIOVA_ROOT_PASSWORD)) {
    throw 'Define GIOVA_ROOT_PASSWORD solo en la sesión actual.'
}

Write-Host ''
Write-Host "Se va a BORRAR la base '$Database' de $HostName y reemplazarla por:"
Write-Host "  $SqlPath"
Write-Host ''
$confirmation = Read-Host "Escriba el nombre de la base para confirmar ($Database)"
if ($confirmation -cne $Database) { throw 'Importación cancelada.' }

$env:MYSQL_PWD = $env:GIOVA_ROOT_PASSWORD
try {
    $existe = '' + (& $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch --skip-column-names `
        --execute="SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$Database';")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar MariaDB (código $LASTEXITCODE). ¿Está encendido MySQL en XAMPP?" }

    if ($existe.Trim() -ne '0') {
        $respaldo = Join-Path (Get-Location) ("respaldo-$Database-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
        Write-Host "Guardando el estado actual en $respaldo ..."
        cmd /c "`"$mysqldump`" --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --databases $Database > `"$respaldo`""
        if ($LASTEXITCODE -ne 0) { throw "El respaldo previo falló (código $LASTEXITCODE). No se borró nada." }

        Write-Host "Borrando $Database ..."
        & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch `
            --execute="DROP DATABASE ``$Database``;"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo borrar $Database (código $LASTEXITCODE)." }
    }

    # El archivo del compañero ya trae CREATE DATABASE y USE. Si el respaldo que
    # importas no los tiene, créala antes para que las tablas caigan en su sitio.
    & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch `
        --execute="CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear $Database (código $LASTEXITCODE)." }

    Write-Host 'Importando (puede tardar un minuto)...'
    cmd /c "`"$mysql`" --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --database=$Database < `"$SqlPath`""
    if ($LASTEXITCODE -ne 0) { throw "La importación falló (código $LASTEXITCODE)." }

    $resumen = & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch --skip-column-names `
        --execute="SELECT CONCAT(COUNT(*),' tablas') FROM information_schema.tables WHERE table_schema='$Database'; SELECT CONCAT('ultima migracion aplicada: V',MAX(CAST(version AS UNSIGNED))) FROM ``$Database``.flyway_schema_history;"
    Write-Host ''
    Write-Host "$Database importada."
    $resumen | ForEach-Object { Write-Host "  $_" }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Falta volver a crear los usuarios de la base y sus permisos:'
Write-Host '  .\scripts\bootstrap-xampp.ps1'
Write-Host '(el respaldo no trae usuarios de MariaDB, solo datos).'
