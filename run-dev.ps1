# Chay backend tu thu muc aistudyhub-be (doc .env + Railway MySQL)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Error "Missing .env file. Copy .env.example to .env first."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $eq = $line.IndexOf('=')
        if ($eq -gt 0) {
            $name = $line.Substring(0, $eq).Trim()
            $value = $line.Substring($eq + 1).Trim()
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Write-Host "Profile: $env:SPRING_PROFILES_ACTIVE"
Write-Host "DB: $($env:DB_URL)"
$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8081' }
Write-Host "Starting Spring Boot on http://localhost:$port ..."
Write-Host "Swagger: http://localhost:$port/swagger-ui.html"

# Duong dan co [ ] lam mvnw.cmd loi -> dung short path (8.3)
$fso = New-Object -ComObject Scripting.FileSystemObject
$shortRoot = $fso.GetFolder($PSScriptRoot).ShortPath
if (-not $shortRoot) {
    Write-Error "Could not resolve short path for: $PSScriptRoot"
}

Push-Location $shortRoot
try {
    & (Join-Path $shortRoot 'mvnw.cmd') '-DskipTests' 'spring-boot:run'
} finally {
    Pop-Location
}
