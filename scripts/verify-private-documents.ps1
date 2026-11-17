[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'compose.yaml no es válido.' }

$volume = docker volume inspect giova-v1-documents 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($volume -join ''))) {
    throw 'No existe el volumen privado giova-v1-documents.'
}

$mounts = docker inspect giova-v1-api-1 --format '{{json .Mounts}}'
if ($LASTEXITCODE -ne 0 -or $mounts -notmatch 'giova-v1-documents' -or $mounts -notmatch '/var/lib/giova/documents') {
    throw 'El contenedor API no tiene montado el volumen privado esperado.'
}

$containerEnvironment = docker inspect giova-v1-api-1 --format '{{range .Config.Env}}{{println .}}{{end}}'
if ($LASTEXITCODE -ne 0 -or -not (($containerEnvironment -join "`n") -match '(?m)^GIOVA_DOCUMENTS_ROOT=/var/lib/giova/documents$')) {
    throw 'El API no apunta la raíz de documentos al volumen privado Docker.'
}

$publicProbe = $null
try {
    $publicProbe = Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8088/storage/documents/' -ErrorAction Stop
} catch {
    if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -notin @(404, 403)) {
        throw "Nginx respondió $([int]$_.Exception.Response.StatusCode) para la carpeta privada."
    }
}
if ($null -ne $publicProbe) {
    throw 'Nginx expuso inesperadamente la carpeta privada.'
}

Write-Host 'Almacenamiento privado: OK (volumen montado; Nginx no sirve la carpeta).'
