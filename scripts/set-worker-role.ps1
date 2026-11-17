param(
    [Parameter(Mandatory = $true)] [ValidatePattern('^(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\.[a-z]{2,63})$')] [string]$Username,
    [Parameter(Mandatory = $true)] [ValidateSet('ADMIN', 'ACCOUNTANT', 'SELLER')] [string]$Role
)

$ErrorActionPreference = 'Stop'
throw 'Deshabilitado desde V16: los roles de empresa son membresías. No se conecta a la base de datos ni promueve cuentas. Use el alta local autorizada o la gestión web de cuentas.'
