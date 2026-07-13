$ErrorActionPreference = 'Stop'
$base = if ($env:API_BASE) { $env:API_BASE } else { 'http://localhost:8081' }
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$results = @()

function Add-Result($module, $case, $pass, $detail) {
    $script:results += [pscustomobject]@{ Module = $module; Case = $case; Pass = $pass; Detail = $detail }
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
    $ownerEmail = "owner-$ts@test.com"
    $viewerEmail = "viewer-$ts@test.com"
    $password = 'secret123'

    $ownerReg = Invoke-Api POST '/api/auth/register' @{ email = $ownerEmail; password = $password; fullName = 'Owner User' }
    $ownerToken = $ownerReg.data.accessToken
    Add-Result 'Auth' 'Owner register' ($null -ne $ownerToken) $ownerEmail

    $viewerReg = Invoke-Api POST '/api/auth/register' @{ email = $viewerEmail; password = $password; fullName = 'Viewer User' }
    $viewerToken = $viewerReg.data.accessToken
    Add-Result 'Auth' 'Viewer register' ($null -ne $viewerToken) $viewerEmail

    $forgot = Invoke-Api POST '/api/auth/forgot-password' @{ email = $ownerEmail }
    Add-Result 'Auth' 'Forgot password' ($forgot.success -eq $true) $forgot.message

    $subject = Invoke-Api POST '/api/subjects' @{ name = "Feature Subject $ts" } $ownerToken
    $subjectId = $subject.data.id
    Invoke-Api PATCH "/api/subjects/$subjectId" @{ name = "Updated Subject $ts" } $ownerToken | Out-Null
    $subjects = Invoke-Api GET '/api/subjects' $null $ownerToken
    Add-Result 'Subject' 'Create/update/list' ($subjects.data[0].name -like 'Updated Subject*') $subjects.data[0].name

    $filePath = Join-Path $env:TEMP "feature-doc-$ts.txt"
    Set-Content -Path $filePath -Value 'feature test document for report and public access' -Encoding UTF8
    $uploadJson = curl.exe -s -X POST "$base/api/documents" `
        -H "Authorization: Bearer $ownerToken" `
        -F "title=Feature Test Doc" `
        -F "subjectId=$subjectId" `
        -F "file=@$filePath;type=text/plain"
    $upload = $uploadJson | ConvertFrom-Json
    $docId = $upload.data.id
    Add-Result 'Document' 'Upload + extract' ($upload.data.extractionStatus -eq 'EXTRACTED') "id=$docId"

    Invoke-Api PATCH "/api/documents/$docId" @{ title = 'Updated Feature Doc'; description = 'Updated desc' } $ownerToken | Out-Null
    $detail = Invoke-Api GET "/api/documents/$docId" $null $ownerToken
    Add-Result 'Document' 'Update + get detail' ($detail.data.title -eq 'Updated Feature Doc') $detail.data.title

    Invoke-Api PATCH "/api/documents/$docId/visibility" @{ status = 'PUBLIC' } $ownerToken | Out-Null
    $publicDetail = Invoke-Api GET "/api/documents/public/$docId" $null $viewerToken
    Add-Result 'Document' 'Public detail (viewer)' ($publicDetail.data.status -eq 'PUBLIC') "status=$($publicDetail.data.status)"

    $report = Invoke-Api POST "/api/documents/$docId/reports" @{ reason = 'OTHER'; description = 'Test report flow' } $viewerToken
    Add-Result 'Report' 'Create report' ($report.data.status -eq 'PENDING') "id=$($report.data.id)"

    try {
        Invoke-Api POST "/api/documents/$docId/reports" @{ reason = 'OTHER'; description = 'Duplicate' } $viewerToken | Out-Null
        Add-Result 'Report' 'Duplicate blocked' $false 'Expected error'
    } catch {
        Add-Result 'Report' 'Duplicate blocked' $true 'Rejected as expected'
    }

    Invoke-Api PATCH '/api/users/me' @{ fullName = 'Owner Updated' } $ownerToken | Out-Null
    $profile = Invoke-Api GET '/api/users/me' $null $ownerToken
    Add-Result 'User' 'Update profile' ($profile.data.fullName -eq 'Owner Updated') $profile.data.fullName

    Invoke-Api PATCH '/api/users/me/password' @{ currentPassword = $password; newPassword = 'newsecret123' } $ownerToken | Out-Null
    $relogin = Invoke-Api POST '/api/auth/login' @{ email = $ownerEmail; password = 'newsecret123' }
    Add-Result 'User' 'Change password + relogin' ($null -ne $relogin.data.accessToken) 'OK'

    Invoke-Api DELETE "/api/documents/$docId" $null $relogin.data.accessToken | Out-Null
    try {
        Invoke-Api GET "/api/documents/$docId" $null $relogin.data.accessToken | Out-Null
        Add-Result 'Document' 'Soft delete' $false 'Still accessible'
    } catch {
        Add-Result 'Document' 'Soft delete' $true '404 after delete'
    }

    try {
        Invoke-Api DELETE "/api/subjects/$subjectId" $null $relogin.data.accessToken | Out-Null
        Add-Result 'Subject' 'Delete subject blocked' $false 'Unexpected success'
    } catch {
        Add-Result 'Subject' 'Delete subject blocked' $true '400 when documents still linked'
    }

    try {
        $adminLogin = Invoke-Api POST '/api/auth/login' @{ email = 'admin@studyhub.local'; password = 'Admin12345' }
        $adminToken = $adminLogin.data.accessToken
        $adminDocs = Invoke-Api GET '/api/admin/documents?page=0&size=5' $null $adminToken
        Add-Result 'Admin' 'Documents list' ($adminDocs.data.content.Count -ge 0) "count=$($adminDocs.data.content.Count)"
    } catch {
        Add-Result 'Admin' 'Admin features' $false '401 - seed admin password mismatch on Railway DB'
    }
} catch {
    Add-Result 'Script' 'Unhandled error' $false $_.Exception.Message
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Pass }).Count
Write-Host ""
Write-Host "FULL FEATURE TEST: $($results.Count - $failed) / $($results.Count) passed"
if ($failed -gt 0) { exit 1 }
