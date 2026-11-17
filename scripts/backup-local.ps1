[CmdletBinding()]
param(
    [string]$OutputDirectory = "$(Join-Path (Get-Location) 'backups')",
    [string]$DocumentVolumeName = 'giova-v1-documents',
    [string]$DocumentContainerName = 'giova-v1-api-1',
    [bool]$QuiesceApi = $true
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Get-Location).Path
$outputPath = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force $outputPath | Out-Null
$outputPath = (Resolve-Path -LiteralPath $outputPath).Path

$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Falta .env en la raíz del proyecto.' }
Get-Content -LiteralPath $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2])
    }
}

foreach ($name in 'GIOVA_DB_NAME','GIOVA_DB_USER','GIOVA_DB_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Falta $name en .env."
    }
}

$mysqlDump = if (Test-Path -LiteralPath 'C:\xampp\mysql\bin\mysqldump.exe') {
    'C:\xampp\mysql\bin\mysqldump.exe'
} else {
    'mysqldump.exe'
}
$dbHost = if ($env:GIOVA_DB_HOST -eq 'host.docker.internal') { '127.0.0.1' } else { $env:GIOVA_DB_HOST }
$port = if ($env:GIOVA_DB_PORT) { $env:GIOVA_DB_PORT } else { '3306' }
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
$sqlName = "giova_v1-$stamp.sql"
$archiveName = "giova_documents-$stamp.tar.gz"
$manifestName = "giova_v1-$stamp.manifest.json"
$sqlPath = Join-Path $outputPath $sqlName
$archivePath = Join-Path $outputPath $archiveName
$manifestPath = Join-Path $outputPath $manifestName

foreach ($path in $sqlPath, $archivePath, $manifestPath) {
    if (Test-Path -LiteralPath $path) { throw "Ya existe el archivo de respaldo: $path" }
}

$containerState = docker inspect $DocumentContainerName --format '{{.State.Running}}' 2>$null
if ($LASTEXITCODE -ne 0) { throw "No existe el contenedor API $DocumentContainerName." }
$volumeCheck = docker volume inspect $DocumentVolumeName 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($volumeCheck -join ''))) {
    throw "No existe el volumen privado $DocumentVolumeName."
}

$apiWasRunning = $containerState -eq 'true'
$env:MYSQL_PWD = $env:GIOVA_DB_PASSWORD
try {
    if ($QuiesceApi -and $apiWasRunning) {
        docker compose stop api | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener temporalmente el API para crear un checkpoint.' }
    }

    & $mysqlDump --protocol=TCP --host=$dbHost --port=$port --user=$env:GIOVA_DB_USER `
        $env:GIOVA_DB_NAME --single-transaction --hex-blob --result-file=$sqlPath
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $sqlPath -Force -ErrorAction SilentlyContinue
        throw "mysqldump terminó con código $LASTEXITCODE."
    }

    & docker run --rm `
        --mount "type=volume,source=$DocumentVolumeName,destination=/source,readonly" `
        --mount "type=bind,source=$outputPath,destination=/backup" `
        alpine:3.20 sh -c "tar -czf /backup/$archiveName -C /source ."
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
        throw "No se pudo comprimir el volumen $DocumentVolumeName."
    }

    $hashLines = @(& docker run --rm `
        --mount "type=volume,source=$DocumentVolumeName,destination=/source,readonly" `
        alpine:3.20 sh -c 'cd /source && find . -type f -print0 | xargs -0 sha256sum')
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron calcular hashes del volumen $DocumentVolumeName." }

    $documents = foreach ($line in $hashLines) {
        if ($line -match '^(?<hash>[0-9a-fA-F]{64})\s+\./(?<path>.+)$') {
            [ordered]@{ storageKey = $matches.path.Replace('\', '/'); sha256 = $matches.hash.ToLowerInvariant() }
        }
    }

    $manifest = [ordered]@{
        format = 'giova-local-backup-v1'
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
        database = $env:GIOVA_DB_NAME
        databaseHost = $dbHost
        databasePort = [int]$port
        sqlFile = $sqlName
        documentsArchive = $archiveName
        documentVolume = $DocumentVolumeName
        documents = @($documents)
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    [ordered]@{
        manifest = $manifestPath
        sql = [ordered]@{ path = $sqlPath; bytes = (Get-Item -LiteralPath $sqlPath).Length; sha256 = (Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant() }
        documents = [ordered]@{ path = $archivePath; bytes = (Get-Item -LiteralPath $archivePath).Length; sha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant(); files = @($documents).Count }
    } | ConvertTo-Json -Depth 6
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if ($apiWasRunning) {
        docker compose start api | Out-Null
    }
}
