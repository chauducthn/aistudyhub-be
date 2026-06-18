# SCRUM-45 live QA against http://localhost:8081
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$ownerEmail = "qa-owner-$ts@test.com"
$viewerEmail = "qa-viewer-$ts@test.com"
$password = 'secret123'
$results = @()

function Add-Result($case, $expected, $actual, $pass) {
    $script:results += [pscustomobject]@{ Case = $case; Expected = $expected; Actual = $actual; Pass = $pass }
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
    $ownerReg = Invoke-Api POST '/api/auth/register' @{ email = $ownerEmail; password = $password; fullName = 'QA Owner' }
    $ownerToken = $ownerReg.data.accessToken
    Add-Result 'Register owner' '201 + token' 'OK' $true

    $viewerReg = Invoke-Api POST '/api/auth/register' @{ email = $viewerEmail; password = $password; fullName = 'QA Viewer' }
    $viewerToken = $viewerReg.data.accessToken
    Add-Result 'Register viewer' '201 + token' 'OK' $true

    $chat1 = Invoke-Api POST '/api/chatbot/messages' @{ message = 'Explain binary search in 3 bullets' } $ownerToken
    $pass = ($chat1.data.model -eq 'LOCAL_STUDY_ASSISTANT' -or $chat1.data.model) -and $chat1.data.response
    Add-Result 'A.2 Chat without document' '200 + model + response' "model=$($chat1.data.model)" $pass

    $filePath = Join-Path $env:TEMP 'qa-chat-notes.txt'
    Set-Content -Path $filePath -Value 'polymorphism notes for QA chatbot test' -Encoding UTF8
    $uploadJson = curl.exe -s -X POST "$base/api/documents" `
        -H "Authorization: Bearer $ownerToken" `
        -F "title=QA Chat Notes" `
        -F "file=@$filePath;type=text/plain"
    $upload = $uploadJson | ConvertFrom-Json
    if (-not $upload.success) { throw "Upload failed: $uploadJson" }
    $docId = $upload.data.id
    $extractStatus = $upload.data.extractionStatus
    Add-Result 'Upload document' '200 + id' "id=$docId status=$extractStatus" ($null -ne $docId)

    $chat2 = Invoke-Api POST '/api/chatbot/messages' @{ documentId = $docId; message = 'Summarize this document' } $ownerToken
    $pass2 = ($chat2.data.documentId -eq $docId) -and ($chat2.data.response -match 'polymorphism|QA|notes')
    Add-Result 'A.3 Chat own document' '200 + documentId + context' "docId=$($chat2.data.documentId)" $pass2

    try {
        Invoke-Api POST '/api/chatbot/messages' @{ documentId = $docId; message = 'Read private document' } $viewerToken | Out-Null
        Add-Result 'A.5 Private doc other user' '403' '200 (unexpected)' $false
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Add-Result 'A.5 Private doc other user' '403' $status ($status -eq 403)
    }

    Invoke-Api PATCH "/api/documents/$docId/visibility" @{ status = 'PUBLIC' } $ownerToken | Out-Null
    $chat4 = Invoke-Api POST '/api/chatbot/messages' @{ documentId = $docId; message = 'Summarize public notes' } $viewerToken
    $pass4 = ($chat4.data.documentId -eq $docId)
    Add-Result 'A.4 Public doc other user' '200' "docId=$($chat4.data.documentId)" $pass4

    $histOwner = Invoke-Api GET '/api/chatbot/history?page=0&size=20' $null $ownerToken
    $histViewer = Invoke-Api GET '/api/chatbot/history?page=0&size=20' $null $viewerToken
    Add-Result 'A.6 History owner count' '2' $histOwner.data.totalElements ($histOwner.data.totalElements -eq 2)
    Add-Result 'A.6 History viewer count' '1' $histViewer.data.totalElements ($histViewer.data.totalElements -eq 1)

    Invoke-Api DELETE '/api/chatbot/history' $null $ownerToken | Out-Null
    $histAfter = Invoke-Api GET '/api/chatbot/history?page=0&size=20' $null $ownerToken
    Add-Result 'A.6 Clear history' '0' $histAfter.data.totalElements ($histAfter.data.totalElements -eq 0)
}
catch {
    Add-Result 'Script error' 'no error' $_.Exception.Message $false
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Pass }).Count
Write-Host "PASSED: $($results.Count - $failed) / $($results.Count)"
if ($failed -gt 0) { exit 1 }
