[CmdletBinding()]
param(
    [string]$OutputPath,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $projectRoot ("GIOVA-V1-entrega-P31-{0}.zip" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
} elseif (-not [System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $projectRoot $OutputPath
}

$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$hashPath = "$OutputPath.sha256"
if (((Test-Path -LiteralPath $OutputPath -PathType Leaf) -or (Test-Path -LiteralPath $hashPath -PathType Leaf)) -and -not $Force) {
    throw "Ya existe el paquete o su hash. Usa otro nombre o -Force: $OutputPath"
}

$files = @(
    '.dockerignore',
    '.env.example',
    '.gitignore',
    'README.md',
    'compose.yaml',
    'backend/Dockerfile',
    'backend/.mvn/wrapper/maven-wrapper.properties',
    'backend/mvnw',
    'backend/mvnw.cmd',
    'backend/pom.xml',
    'frontend/Dockerfile',
    'frontend/angular.json',
    'frontend/package.json',
    'frontend/package-lock.json',
    'frontend/README.md',
    'frontend/tsconfig.app.json',
    'frontend/tsconfig.json',
    'frontend/tsconfig.spec.json',
    'infra/nginx/nginx.conf',
    'docs/DATOS_Y_API.md',
    'docs/ENTORNO_LOCAL.md',
    'docs/FUENTES_TECNICAS.md',
    'docs/IMPORTACION.md',
    'docs/TOOLCHAIN.md',
    'docs/referencia/catalogo-original.json',
    'docs/referencia/ocds-p27-fuentes.md',
    'scripts/backup-local.ps1',
    'scripts/backup-xampp.ps1',
    'scripts/bootstrap-xampp.ps1',
    'scripts/check-environment.ps1',
    'scripts/create-worker.ps1',
    'scripts/generate-seed.mjs',
    'scripts/package-delivery.ps1',
    'scripts/restore-local.ps1',
    'scripts/set-worker-role.ps1',
    'scripts/verify-private-documents.ps1'
)

$directories = @(
    'backend/src',
    'frontend/src',
    'frontend/public'
)

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("giova-v1-package-{0}-{1}" -f $PID, (Get-Date -Format 'yyyyMMddHHmmss'))
$packageRoot = Join-Path $tempRoot 'GIOVA-V1'

try {
    New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null

    foreach ($relative in $files) {
        $source = Join-Path $projectRoot $relative
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Falta el archivo requerido para la entrega: $relative"
        }
        $destination = Join-Path $packageRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
    }

    foreach ($relative in $directories) {
        $source = Join-Path $projectRoot $relative
        if (-not (Test-Path -LiteralPath $source -PathType Container)) {
            throw "Falta el directorio requerido para la entrega: $relative"
        }
        $destination = Join-Path $packageRoot $relative
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        Copy-Item -Path (Join-Path $source '*') -Destination $destination -Recurse -Force
    }

    $outputDirectory = Split-Path -Parent $OutputPath
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $OutputPath) { Remove-Item -LiteralPath $OutputPath -Force }
    Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $OutputPath -CompressionLevel Optimal

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $forbidden = @(
        '(^|/)(\.env|node_modules|target|dist|backups|graphify-out)(/|$)',
        '(^|/)AGENTS\.md$',
        '(^|/)docs/(PLAN_PROYECTO|ESTADO_TRABAJO)\.md$',
        '(^|/)docs/(evidencia|referencia/verificacion-plan\.json|referencia/origen-manifest\.json)(/|$)',
        '(^|/)(GIOVA-V1\.zip|GIOVA-V1-entrega-.*\.zip)$'
    )
    $archive = [System.IO.Compression.ZipFile]::OpenRead($OutputPath)
    try {
        $entries = @($archive.Entries | ForEach-Object { $_.FullName })
        $normalizedEntries = @($entries | ForEach-Object { $_ -replace '\\', '/' })
        $violations = @($normalizedEntries | Where-Object {
            $entry = $_
            $forbidden | Where-Object { $entry -match $_ }
        })
        if ($violations.Count -gt 0) {
            throw "El paquete contiene rutas prohibidas: $($violations -join ', ')"
        }
    } finally {
        $archive.Dispose()
    }

    $hash = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    "{0} *{1}" -f $hash, (Split-Path -Leaf $OutputPath) | Set-Content -LiteralPath $hashPath -Encoding ASCII
    [ordered]@{
        package = $OutputPath
        sha256 = $hash
        hashFile = $hashPath
        entries = $entries.Count
    } | ConvertTo-Json
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
