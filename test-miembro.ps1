# test-miembro.ps1 — ejecutar con: .\test-miembro.ps1

# 1. Login
$loginBody = '{"email":"admin@iglesiapaibog.com","password":"Admin2024!"}'
$response = Invoke-RestMethod -Method POST `
    -Uri "http://localhost:8080/api/auth/login" `
    -ContentType "application/json" `
    -Body $loginBody

$TOKEN = $response.accessToken
Write-Host "Token: $($TOKEN.Substring(0,30))..."

# 2. Crear miembro — capturando el body del error si falla
$body = '{"nombres":"Maria","apellidos":"Garcia Lopez","email":"maria.garcia@test.com","telefono":"3001234567","direccion":"Cra 7 45-20","estadoCivil":"SOLTERO"}'

try {
    $miembro = Invoke-RestMethod -Method POST `
        -Uri "http://localhost:8080/api/v1/miembros" `
        -Headers @{ 
            "Authorization" = "Bearer $TOKEN"
            "Content-Type"  = "application/json"
        } `
        -Body $body
    Write-Host "EXITO:"
    $miembro | ConvertTo-Json -Depth 5
} catch {
    $webEx = $_.Exception -as [System.Net.WebException]
    if ($webEx -and $webEx.Response) {
        $stream = $webEx.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errorBody = $reader.ReadToEnd()
        Write-Host "ERROR BODY:"
        Write-Host $errorBody
    } else {
        Write-Host "ERROR: $($_.Exception.Message)"
    }
}
