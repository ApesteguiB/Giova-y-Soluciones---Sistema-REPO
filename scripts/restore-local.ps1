[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [Parameter(Mandatory = $true)]
    [string]$TargetDatabase,
    [Parameter(Mandatory = $true)]
    [string]$TargetDocumentVolume,
    [string]$TargetHost = '127.0.0.1',
    [int]$TargetPort = 3306,
    [string]$TargetUser = 'root'
)

$ErrorActionPreference = 'Stop'
$backupPath = (Resolve-Path -LiteralPath $BackupDirectory).Path
if ($TargetDatabase -notmatch '^giova_p30_restore_[a-z0-9_]+$') {
    throw 'TargetDatabase debe ser un esquema desechable con prefijo giova_p30_restore_.'
}
if ($TargetDocumentVolume -notmatch '^giova-p30-restore-[a-z0-9-]+$') {
    throw 'TargetDocumentVolume debe ser un volumen desechable con prefijo giova-p30-restore-.'
}

$manifestFiles = @(Get-ChildItem -LiteralPath $backupPath -Filter '*.manifest.json' -File)
if ($manifestFiles.Count -ne 1) { throw 'BackupDirectory debe contener exactamente un manifiesto .manifest.json.' }
$manifestPath = $manifestFiles[0].FullName
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.format -ne 'giova-local-backup-v1') { throw 'Formato de respaldo no reconocido.' }

$sqlPath = Join-Path $backupPath $manifest.sqlFile
$archivePath = Join-Path $backupPath $manifest.documentsArchive
if (-not (Test-Path -LiteralPath $sqlPath)) { throw "Falta el dump SQL $($manifest.sqlFile)." }
if (-not (Test-Path -LiteralPath $archivePath)) { throw "Falta el archivo de documentos $($manifest.documentsArchive)." }

$mysql = if (Test-Path -LiteralPath 'C:\xampp\mysql\bin\mysql.exe') {
    'C:\xampp\mysql\bin\mysql.exe'
} else {
    'mysql.exe'
}
$restorePassword = $env:GIOVA_RESTORE_DB_PASSWORD
$env:MYSQL_PWD = $restorePassword
$databaseExists = & $mysql --protocol=TCP --host=$TargetHost --port=$TargetPort --user=$TargetUser `
    --batch --skip-column-names --execute="SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$TargetDatabase';"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el servidor MariaDB destino.' }
if (-not [string]::IsNullOrWhiteSpace(($databaseExists -join ''))) {
    throw "El esquema destino ya existe y no se sobrescribirá: $TargetDatabase"
}

$volumeExists = cmd.exe /d /c "docker volume inspect $TargetDocumentVolume >nul 2>&1"
if ($LASTEXITCODE -eq 0) {
    throw "El volumen destino ya existe y no se sobrescribirá: $TargetDocumentVolume"
}

try {
    & $mysql --protocol=TCP --host=$TargetHost --port=$TargetPort --user=$TargetUser `
        --execute="CREATE DATABASE $TargetDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el esquema destino $TargetDatabase." }

    $quotedMysql = '"' + $mysql + '"'
    $quotedSql = '"' + $sqlPath + '"'
    $importCommand = "$quotedMysql --protocol=TCP --host=$TargetHost --port=$TargetPort --user=$TargetUser --database=$TargetDatabase --binary-mode < $quotedSql"
    & cmd.exe /d /c $importCommand
    if ($LASTEXITCODE -ne 0) { throw 'La restauración SQL terminó con error.' }

    & docker volume create $TargetDocumentVolume | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el volumen destino $TargetDocumentVolume." }
    & docker run --rm `
        --mount "type=volume,source=$TargetDocumentVolume,destination=/restore" `
        --mount "type=bind,source=$backupPath,destination=/backup,readonly" `
        alpine:3.20 sh -c "tar -xzf /backup/$($manifest.documentsArchive) -C /restore"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar el volumen de documentos.' }

    $dbRows = @(& $mysql --protocol=TCP --host=$TargetHost --port=$TargetPort --user=$TargetUser `
        --database=$TargetDatabase --batch --raw --skip-column-names `
        --execute='SELECT storage_key, size_bytes, sha256_hex FROM stored_documents ORDER BY storage_key;')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer stored_documents en el destino restaurado.' }

    $actualFiles = @(& docker run --rm `
        --mount "type=volume,source=$TargetDocumentVolume,destination=/restore,readonly" `
        alpine:3.20 sh -c 'cd /restore && find . -type f -print0 | xargs -0 sha256sum')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron calcular hashes del volumen restaurado.' }

    $actualHashes = @{}
    foreach ($line in $actualFiles) {
        if ($line -match '^(?<hash>[0-9a-fA-F]{64})\s+\./(?<path>.+)$') {
            $actualHashes[$matches.path.Replace('\', '/')] = $matches.hash.ToLowerInvariant()
        }
    }
    $expectedDocuments = @{}
    foreach ($document in @($manifest.documents)) { $expectedDocuments[$document.storageKey] = $document.sha256.ToLowerInvariant() }

    if ($dbRows.Count -ne $expectedDocuments.Count) {
        throw "La restauración reprodujo $($dbRows.Count) filas stored_documents, se esperaban $($expectedDocuments.Count)."
    }
    if ($actualHashes.Count -ne $expectedDocuments.Count) {
        throw "La restauración reprodujo $($actualHashes.Count) archivos, se esperaban $($expectedDocuments.Count)."
    }

    foreach ($row in $dbRows) {
        $parts = $row -split "`t"
        if ($parts.Count -ne 3) { throw 'Fila inesperada en stored_documents.' }
        $storageKey = $parts[0]
        if (-not $expectedDocuments.ContainsKey($storageKey)) { throw "Falta el documento esperado $storageKey en el manifiesto." }
        $expectedHash = $expectedDocuments[$storageKey]
        if ($parts[2].ToLowerInvariant() -ne $expectedHash) { throw "SHA-256 DB divergente para $storageKey." }
        if ($actualHashes[$storageKey] -ne $expectedHash) { throw "SHA-256 del archivo divergente para $storageKey." }
    }

    [ordered]@{
        status = 'verified'
        targetDatabase = $TargetDatabase
        targetDocumentVolume = $TargetDocumentVolume
        restoredDocumentRows = $dbRows.Count
        restoredDocumentFiles = $actualHashes.Count
    } | ConvertTo-Json
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
