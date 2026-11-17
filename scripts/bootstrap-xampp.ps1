param(
    [string]$RootUser = 'root',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3306
)

$ErrorActionPreference = 'Stop'
$mysql = 'C:\xampp\mysql\bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "No se encontró $mysql" }
if ([string]::IsNullOrWhiteSpace($env:GIOVA_ROOT_PASSWORD)) { throw 'Define GIOVA_ROOT_PASSWORD solo en la sesión actual.' }
if ([string]::IsNullOrWhiteSpace($env:GIOVA_MIGRATOR_PASSWORD)) { throw 'Define GIOVA_MIGRATOR_PASSWORD solo en la sesión actual.' }
if ([string]::IsNullOrWhiteSpace($env:GIOVA_APP_PASSWORD)) { throw 'Define GIOVA_APP_PASSWORD solo en la sesión actual.' }

function ConvertTo-SqlLiteral([string]$value) {
    return "'" + $value.Replace("'", "''") + "'"
}

$env:MYSQL_PWD = $env:GIOVA_ROOT_PASSWORD
$sql = @"
CREATE DATABASE IF NOT EXISTS giova_v1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'giova_migrator'@'%' IDENTIFIED BY $(ConvertTo-SqlLiteral $env:GIOVA_MIGRATOR_PASSWORD);
ALTER USER 'giova_migrator'@'%' IDENTIFIED BY $(ConvertTo-SqlLiteral $env:GIOVA_MIGRATOR_PASSWORD);
GRANT ALL PRIVILEGES ON giova_v1.* TO 'giova_migrator'@'%';
CREATE USER IF NOT EXISTS 'giova_app'@'%' IDENTIFIED BY $(ConvertTo-SqlLiteral $env:GIOVA_APP_PASSWORD);
ALTER USER 'giova_app'@'%' IDENTIFIED BY $(ConvertTo-SqlLiteral $env:GIOVA_APP_PASSWORD);
GRANT SELECT, INSERT, UPDATE, DELETE ON giova_v1.* TO 'giova_app'@'%';
FLUSH PRIVILEGES;
"@
try {
    $sql | & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$RootUser --batch --skip-column-names
    if ($LASTEXITCODE -ne 0) { throw "mysql terminó con código $LASTEXITCODE" }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host 'giova_v1 preparado con usuarios giova_migrator y giova_app.'
