# Smoke test all major API modules against running BE (default http://localhost:8081)
$ErrorActionPreference = 'Stop'
$base = if ($env:API_BASE) { $env:API_BASE } else { 'http://localhost:8081' }
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "smoke-$ts@test.com"
$password = 'secret123'
$results = @()

function Add-Result($module, $case, $pass, $detail) {
    $script:results += [pscustomobject]@{
        Module = $module
        Case   = $case
        Pass   = $pass
        Detail = $detail
    }
}

function Invoke-Api($method, $path, $body = $null, $token = $null, $multipart = $null) {
    $headers = @{ Accept = 'application/json' }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $uri = "$base$path"
    if ($multipart) {
        return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers -Form $multipart
    }
    if ($body) {
        return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress)
    }
    return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers
}

try {
    $health = Invoke-Api GET '/api/health'
    Add-Result 'Health' 'GET /api/health' ($health.success -eq $true) "status=$($health.data.status)"

    $reg = Invoke-Api POST '/api/auth/register' @{ email = $email; password = $password; fullName = 'Smoke User' }
    $token = $reg.data.accessToken
    Add-Result 'Auth' 'Register' ($null -ne $token) $email

    $login = Invoke-Api POST '/api/auth/login' @{ email = $email; password = $password }
    Add-Result 'Auth' 'Login' ($null -ne $login.data.accessToken) 'OK'

    $adminLogin = Invoke-Api POST '/api/auth/login' @{ email = 'admin@studyhub.local'; password = 'Admin12345' }
    $adminToken = $adminLogin.data.accessToken
    Add-Result 'Auth' 'Admin login' ($null -ne $adminToken) 'OK'

    $subject = Invoke-Api POST '/api/subjects' @{ name = "Smoke Subject $ts" } $token
    $subjectId = $subject.data.id
    Add-Result 'Subject' 'Create' ($null -ne $subjectId) "id=$subjectId"

    $subjects = Invoke-Api GET '/api/subjects' $null $token
    Add-Result 'Subject' 'List' ($subjects.data.Count -ge 1) "count=$($subjects.data.Count)"

    $filePath = Join-Path $env:TEMP "smoke-doc-$ts.txt"
    Set-Content -Path $filePath -Value 'smoke test document content for extraction' -Encoding UTF8
    $uploadJson = curl.exe -s -X POST "$base/api/documents" `
        -H "Authorization: Bearer $token" `
        -F "title=Smoke Doc" `
        -F "subjectId=$subjectId" `
        -F "file=@$filePath;type=text/plain"
    $upload = $uploadJson | ConvertFrom-Json
    $docId = $upload.data.id
    Add-Result 'Document' 'Upload' ($upload.success -eq $true) "id=$docId status=$($upload.data.extractionStatus)"

    $docs = Invoke-Api GET '/api/documents?page=0&size=10' $null $token
    Add-Result 'Document' 'List own' ($docs.data.totalElements -ge 1) "total=$($docs.data.totalElements)"

    Invoke-Api PATCH "/api/documents/$docId/visibility" @{ status = 'PUBLIC' } $token | Out-Null
    $publicDocs = Invoke-Api GET '/api/documents/public?page=0&size=10' $null $token
    Add-Result 'Document' 'Public list' ($publicDocs.data.totalElements -ge 1) "total=$($publicDocs.data.totalElements)"

    $search = Invoke-Api GET '/api/documents?keyword=smoke&page=0&size=10' $null $token
    Add-Result 'Search' 'User document search' ($search.data.totalElements -ge 0) "total=$($search.data.totalElements)"

    $chat = Invoke-Api POST '/api/chatbot/messages' @{ message = 'What is binary search?' } $token
    Add-Result 'Chatbot' 'Send message' ($null -ne $chat.data.response) "model=$($chat.data.model)"

    $history = Invoke-Api GET '/api/chatbot/history?page=0&size=10' $null $token
    Add-Result 'Chatbot' 'History' ($history.data.totalElements -ge 1) "total=$($history.data.totalElements)"

    $metrics = Invoke-Api GET '/api/admin/dashboard/metrics' $null $adminToken
    Add-Result 'Admin' 'Dashboard metrics' ($metrics.data.totalUsers -ge 1) "users=$($metrics.data.totalUsers)"

    $adminUsers = Invoke-Api GET '/api/admin/users?page=0&size=5' $null $adminToken
    Add-Result 'Admin' 'User list' ($adminUsers.data.content.Count -ge 1) "count=$($adminUsers.data.content.Count)"

    $profile = Invoke-Api GET '/api/users/me' $null $token
    Add-Result 'User' 'Profile' ($profile.data.email -eq $email) $profile.data.email
}
catch {
    Add-Result 'Script' 'Unhandled error' $false $_.Exception.Message
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Pass }).Count
Write-Host ""
Write-Host "SMOKE TEST: $($results.Count - $failed) / $($results.Count) passed"
if ($failed -gt 0) { exit 1 }
