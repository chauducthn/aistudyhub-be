$ErrorActionPreference = 'Stop'
$base = if ($env:API_BASE) { $env:API_BASE } else { 'http://localhost:8081' }
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "fulltest-$ts@test.com"
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

function Invoke-Api($method, $path, $body = $null, $token = $null) {
    $headers = @{ Accept = 'application/json' }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $uri = "$base$path"
    if ($body) {
        return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress)
    }
    return Invoke-RestMethod -Method $method -Uri $uri -Headers $headers
}

try {
    $reg = Invoke-Api POST '/api/auth/register' @{ email = $email; password = $password; fullName = 'Full Test User' }
    $token = $reg.data.accessToken
    Add-Result 'Auth' 'Register' ($null -ne $token) $email

    $login = Invoke-Api POST '/api/auth/login' @{ email = $email; password = $password }
    Add-Result 'Auth' 'Login' ($null -ne $login.data.accessToken) 'OK'

    try {
        Invoke-Api POST '/api/auth/login' @{ email = 'admin@studyhub.local'; password = 'Admin12345' } | Out-Null
        Add-Result 'Auth' 'Admin login (seed)' $true 'OK'
        $adminToken = (Invoke-Api POST '/api/auth/login' @{ email = 'admin@studyhub.local'; password = 'Admin12345' }).data.accessToken
    } catch {
        Add-Result 'Auth' 'Admin login (seed)' $false '401 - password may differ on Railway DB'
        $adminToken = $null
    }

    $subject = Invoke-Api POST '/api/subjects' @{ name = "Full Test Subject $ts" } $token
    $subjectId = $subject.data.id
    Add-Result 'Subject' 'Create' ($null -ne $subjectId) "id=$subjectId"

    $subjects = Invoke-Api GET '/api/subjects' $null $token
    Add-Result 'Subject' 'List' ($subjects.data.Count -ge 1) "count=$($subjects.data.Count)"

    $filePath = Join-Path $env:TEMP "fulltest-doc-$ts.txt"
    Set-Content -Path $filePath -Value 'full test document content for search download and chatbot' -Encoding UTF8
    $uploadJson = curl.exe -s -X POST "$base/api/documents" `
        -H "Authorization: Bearer $token" `
        -F "title=Full Test Document" `
        -F "subjectId=$subjectId" `
        -F "file=@$filePath;type=text/plain"
    $upload = $uploadJson | ConvertFrom-Json
    $docId = $upload.data.id
    Add-Result 'Document' 'Upload' ($upload.success -eq $true) "id=$docId extraction=$($upload.data.extractionStatus)"

    Invoke-Api PATCH "/api/documents/$docId/visibility" @{ status = 'PUBLIC' } $token | Out-Null
    $publicDocs = Invoke-Api GET '/api/documents/public?page=0&size=10' $null $token
    Add-Result 'Document' 'Public list (auth)' ($publicDocs.data.totalElements -ge 1) "total=$($publicDocs.data.totalElements)"

    $search = Invoke-Api GET '/api/documents?keyword=full&page=0&size=10' $null $token
    Add-Result 'Search' 'User document search' ($search.data.totalElements -ge 0) "total=$($search.data.totalElements)"

    $downloadStatus = curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $token" "$base/api/documents/$docId/download"
    Add-Result 'Document' 'Download API' ($downloadStatus -eq '200') "status=$downloadStatus"

    $chat = Invoke-Api POST '/api/chatbot/messages' @{ message = 'Explain binary search briefly.' } $token
    Add-Result 'Chatbot' 'Send message' ($null -ne $chat.data.response) "model=$($chat.data.model)"

    $history = Invoke-Api GET '/api/chatbot/history?page=0&size=10' $null $token
    Add-Result 'Chatbot' 'History' ($history.data.totalElements -ge 1) "total=$($history.data.totalElements)"

    $profile = Invoke-Api GET '/api/users/me' $null $token
    Add-Result 'User' 'Profile' ($profile.data.email -eq $email) $profile.data.email

    try {
        Invoke-Api GET '/api/admin/dashboard/metrics' $null $token | Out-Null
        Add-Result 'Admin' 'RBAC (user blocked)' $false 'Expected 403'
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Add-Result 'Admin' 'RBAC (user blocked)' ($status -eq 403) "status=$status"
    }

    if ($adminToken) {
        $metrics = Invoke-Api GET '/api/admin/dashboard/metrics' $null $adminToken
        Add-Result 'Admin' 'Dashboard metrics' ($metrics.data.totalUsers -ge 1) "users=$($metrics.data.totalUsers)"

        $adminDocs = Invoke-Api GET '/api/admin/documents?page=0&size=5' $null $adminToken
        Add-Result 'Admin' 'Document list' ($adminDocs.data.content.Count -ge 0) "count=$($adminDocs.data.content.Count)"
    }

    $feStatus = curl.exe -s -o NUL -w "%{http_code}" 'http://127.0.0.1:5173/'
    Add-Result 'Frontend' 'Dev server' ($feStatus -eq '200') "status=$feStatus"
} catch {
    Add-Result 'Script' 'Unhandled error' $false $_.Exception.Message
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Pass }).Count
Write-Host ""
Write-Host "EXTENDED SMOKE: $($results.Count - $failed) / $($results.Count) passed"
if ($failed -gt 0) { exit 1 }
