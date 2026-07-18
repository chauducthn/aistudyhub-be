# Start the local MySQL database used by the Spring Boot backend.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error "Docker CLI was not found. Install Docker Desktop, reopen PowerShell, then run this script again."
}

$composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
if (-not (Test-Path -LiteralPath $composeFile)) {
    Write-Error "Missing docker-compose.yml in $PSScriptRoot"
}

Write-Host "Starting local MySQL for StudyHubAI..."
docker compose --env-file .env.docker.example up -d mysql
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose failed to start MySQL. Check whether MYSQL_PORT in .env.docker.example is already in use."
}

Write-Host "Waiting for MySQL healthcheck..."
for ($i = 1; $i -le 30; $i++) {
    $status = docker inspect --format '{{.State.Health.Status}}' aistudyhub-mysql 2>$null
    if ($status -eq 'healthy') {
        Write-Host "MySQL is ready on 127.0.0.1:3307"
        Write-Host "Use DB_URL=jdbc:mysql://127.0.0.1:3307/aistudyhub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
        exit 0
    }
    Start-Sleep -Seconds 2
}

Write-Error "MySQL did not become healthy in time. Run 'docker logs aistudyhub-mysql' for details."
