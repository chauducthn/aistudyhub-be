$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$fso = New-Object -ComObject Scripting.FileSystemObject
$shortRoot = $fso.GetFolder($PSScriptRoot).ShortPath
if (-not $shortRoot) {
    Write-Error "Could not resolve short path for: $PSScriptRoot"
}
Push-Location $shortRoot
try {
    & (Join-Path $shortRoot 'mvnw.cmd') '-Dtest=ChatbotControllerIntegrationTest' 'test'
} finally {
    Pop-Location
}
