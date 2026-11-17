param(
    [string]$RootUser = 'root',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3306
)

$ErrorActionPreference = 'Stop'
$mysql = 'C:\xampp\mysql\bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "No se encontro $mysql" }

$envPath = Join-Path (Get-Location) '.env'
if (-not (Test-Path $envPath)) { throw "No se encontro .env en $(Get-Location)" }

$cfg = @{}
Get-Content $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') { $cfg[$matches[1].Trim()] = $matches[2].Trim() }
}
$appUser = $cfg['GIOVA_DB_USER']
$appPass = $cfg['GIOVA_DB_PASSWORD']
$migUser = $cfg['GIOVA_FLYWAY_USER']
$migPass = $cfg['GIOVA_FLYWAY_PASSWORD']
if ([string]::IsNullOrWhiteSpace($appPass) -or [string]::IsNullOrWhiteSpace($migPass)) {
    throw 'El .env no tiene las contrasenas de base de datos.'
}

function ConvertTo-SqlLiteral([string]$value) { return "'" + $value.Replace("'", "''") + "'" }

$secure = Read-Host 'Password de root de MariaDB (XAMPP)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $qApp = ConvertTo-SqlLiteral $appPass
    $qMig = ConvertTo-SqlLiteral $migPass
    $sql = @"
REPAIR TABLE mysql.db;
REPAIR TABLE mysql.tables_priv;
REPAIR TABLE mysql.columns_priv;
REPAIR TABLE mysql.procs_priv;
REPAIR TABLE mysql.global_priv;
FLUSH PRIVILEGES;
CREATE DATABASE IF NOT EXISTS giova_v1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$migUser'@'localhost' IDENTIFIED BY $qMig;
ALTER USER '$migUser'@'localhost' IDENTIFIED BY $qMig;
GRANT ALL PRIVILEGES ON giova_v1.* TO '$migUser'@'localhost';
CREATE USER IF NOT EXISTS '$migUser'@'127.0.0.1' IDENTIFIED BY $qMig;
ALTER USER '$migUser'@'127.0.0.1' IDENTIFIED BY $qMig;
GRANT ALL PRIVILEGES ON giova_v1.* TO '$migUser'@'127.0.0.1';
CREATE USER IF NOT EXISTS '$migUser'@'%' IDENTIFIED BY $qMig;
ALTER USER '$migUser'@'%' IDENTIFIED BY $qMig;
GRANT ALL PRIVILEGES ON giova_v1.* TO '$migUser'@'%';
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY $qApp;
ALTER USER '$appUser'@'localhost' IDENTIFIED BY $qApp;
GRANT SELECT, INSERT, UPDATE, DELETE ON giova_v1.* TO '$appUser'@'localhost';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY $qApp;
ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY $qApp;
GRANT SELECT, INSERT, UPDATE, DELETE ON giova_v1.* TO '$appUser'@'127.0.0.1';
CREATE USER IF NOT EXISTS '$appUser'@'%' IDENTIFIED BY $qApp;
ALTER USER '$appUser'@'%' IDENTIFIED BY $qApp;
GRANT SELECT, INSERT, UPDATE, DELETE ON giova_v1.* TO '$appUser'@'%';
FLUSH PRIVILEGES;
SELECT CONCAT('OK -> ', User, '@', Host) FROM mysql.user WHERE User IN ('$migUser', '$appUser') ORDER BY User, Host;
"@
    $sql | & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch --skip-column-names
    if ($LASTEXITCODE -ne 0) { throw "mysql termino con codigo $LASTEXITCODE" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Listo. Los usuarios quedaron habilitados para localhost, 127.0.0.1 y cualquier host.'
