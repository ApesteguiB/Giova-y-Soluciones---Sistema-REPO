param(
    [Parameter(Mandatory = $true)] [ValidatePattern('^(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\.[a-z]{2,63})$')] [string]$Username,
    [Parameter(Mandatory = $true)] [ValidateLength(2, 100)] [string]$DisplayName,
    [Parameter(Mandatory = $true)] [ValidateSet('ADMIN', 'ACCOUNTANT', 'SELLER', 'SUPERADMIN')] [string]$Role,
    [ValidatePattern('^[A-Z0-9][A-Z0-9_-]{2,29}$')] [string]$TenantCode
)

$ErrorActionPreference = 'Stop'
if ($Role -eq 'SUPERADMIN') {
    if (-not [string]::IsNullOrWhiteSpace($TenantCode)) {
        throw 'SUPERADMIN no puede tener una empresa asignada.'
    }
    $confirmation = Read-Host 'Para crear un SUPERADMIN local, escriba CREATE SUPERADMIN'
    if ($confirmation -cne 'CREATE SUPERADMIN') { throw 'Creación de SUPERADMIN cancelada.' }
} elseif ([string]::IsNullOrWhiteSpace($TenantCode)) {
    throw 'TenantCode es obligatorio para ADMIN, ACCOUNTANT y SELLER.'
}

$previousDbHost = $env:GIOVA_DB_HOST
$secure = Read-Host 'Contraseña (mínimo 12 caracteres)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    if ($plain.Length -lt 12) { throw 'La contraseña debe tener al menos 12 caracteres.' }
    $envPath = Join-Path (Get-Location) '.env'
    if (Test-Path $envPath) {
        Get-Content $envPath | ForEach-Object {
            if ($_ -match '^\s*([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2]) }
        }
    }
    # Este script corre desde Windows; host.docker.internal solo corresponde
    # al proceso API dentro de Docker. Restaurar el valor al salir evita
    # contaminar comandos posteriores en esta sesión.
    $env:GIOVA_DB_HOST = '127.0.0.1'
    $env:GIOVA_CATALOG_ENABLED = 'true'
    $env:GIOVA_WORKER_USERNAME = $Username
    $env:GIOVA_WORKER_DISPLAY_NAME = $DisplayName
    $env:GIOVA_WORKER_PASSWORD = $plain
    $env:GIOVA_WORKER_ROLE = $Role
    if ($Role -eq 'SUPERADMIN') {
        $env:GIOVA_WORKER_CONFIRM_SUPERADMIN = 'CREATE_SUPERADMIN'
        Remove-Item Env:GIOVA_WORKER_TENANT_CODE -ErrorAction SilentlyContinue
    } else {
        $env:GIOVA_WORKER_TENANT_CODE = $TenantCode.Trim().ToUpperInvariant()
        Remove-Item Env:GIOVA_WORKER_CONFIRM_SUPERADMIN -ErrorAction SilentlyContinue
    }
    Push-Location (Join-Path (Get-Location) 'backend')
    try {
         & .\mvnw.cmd 'spring-boot:run' '-Dspring-boot.run.main-class=pe.giova.auth.WorkerProvisioningApplication'
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    if ($null -eq $previousDbHost) {
        Remove-Item Env:GIOVA_DB_HOST -ErrorAction SilentlyContinue
    } else {
        $env:GIOVA_DB_HOST = $previousDbHost
    }
    Remove-Item Env:GIOVA_WORKER_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:GIOVA_WORKER_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:GIOVA_WORKER_DISPLAY_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:GIOVA_WORKER_ROLE -ErrorAction SilentlyContinue
    Remove-Item Env:GIOVA_WORKER_TENANT_CODE -ErrorAction SilentlyContinue
    Remove-Item Env:GIOVA_WORKER_CONFIRM_SUPERADMIN -ErrorAction SilentlyContinue
}
