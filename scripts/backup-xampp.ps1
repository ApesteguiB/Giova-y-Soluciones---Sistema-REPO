param(
    [string]$OutputDirectory = "$(Join-Path (Get-Location) 'backups')"
)

$ErrorActionPreference = 'Stop'
$envPath = Join-Path (Get-Location) '.env'
if (-not (Test-Path $envPath)) { throw 'Falta .env en la raíz del proyecto.' }

Get-Content $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2])
    }
}

foreach ($name in 'GIOVA_DB_NAME','GIOVA_DB_USER','GIOVA_DB_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Falta $name en .env."
    }
}

$mysqlBin = if (Test-Path 'C:\xampp\mysql\bin\mysqldump.exe') {
    'C:\xampp\mysql\bin\mysqldump.exe'
} else {
    'mysqldump.exe'
}
$dbHost = if ($env:GIOVA_DB_HOST -eq 'host.docker.internal') { '127.0.0.1' } else { $env:GIOVA_DB_HOST }
$port = if ($env:GIOVA_DB_PORT) { $env:GIOVA_DB_PORT } else { '3306' }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$destination = Join-Path $OutputDirectory "giova_v1-$stamp.sql"

$env:MYSQL_PWD = $env:GIOVA_DB_PASSWORD
try {
    # La V1 usa únicamente tablas InnoDB; el usuario runtime no necesita EVENT ni privilegios globales.
    & $mysqlBin --protocol=TCP --host=$dbHost --port=$port --user=$env:GIOVA_DB_USER --databases $env:GIOVA_DB_NAME --single-transaction --hex-blob --result-file=$destination
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
        throw "mysqldump terminó con código $LASTEXITCODE."
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Get-Item $destination | Select-Object FullName, Length
