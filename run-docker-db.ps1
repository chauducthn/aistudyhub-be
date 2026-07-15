# Start the local MySQL database used by the Spring Boot backend.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error 'Docker CLI was not found. Install/start Docker Desktop and try again.'
}

$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Error 'Missing .env. Copy .env.docker.example to .env and configure local secrets first.'
}

docker compose --env-file .env up -d mysql
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Docker Compose failed to start MySQL.'
}

Write-Host 'Waiting for MySQL healthcheck...'
for ($i = 1; $i -le 30; $i++) {
    $status = docker inspect --format '{{.State.Health.Status}}' aistudyhub-mysql 2>$null
    if ($status -eq 'healthy') {
        Write-Host 'MySQL is ready on 127.0.0.1:3306.'
        exit 0
    }
    Start-Sleep -Seconds 2
}

Write-Error "MySQL did not become healthy. Run 'docker logs aistudyhub-mysql' for details."
