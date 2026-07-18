# Run Spring Boot against the local Docker MySQL database.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) {
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
}

$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/aistudyhub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '123456'
$env:JPA_DDL_AUTO = 'update'

Write-Host "Profile: $env:SPRING_PROFILES_ACTIVE"
Write-Host "DB: $($env:DB_URL)"
$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8081' }
Write-Host "Starting Spring Boot on http://localhost:$port ..."
Write-Host "Swagger: http://localhost:$port/swagger-ui.html"

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
