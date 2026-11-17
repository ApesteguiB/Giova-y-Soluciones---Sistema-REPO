$ErrorActionPreference = 'Stop'

Write-Host "Node: $(node --version)"
$javaVersion = cmd.exe /d /c "java -version 2>&1"
if ($LASTEXITCODE -ne 0 -or $javaVersion.Count -eq 0) { throw 'Java no está disponible.' }
Write-Host "Java: $($javaVersion[0])"
Write-Host "Docker Compose: $(docker compose version)"

$maria = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if ($maria) {
    Write-Host "MariaDB TCP 3306: LISTENING"
} else {
    Write-Warning "MariaDB TCP 3306: no está escuchando"
}

docker compose config --quiet
Write-Host "compose.yaml: válido"
