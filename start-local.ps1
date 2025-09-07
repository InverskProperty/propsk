Write-Host "Loading environment variables from 'Test 1 (7).env'..." -ForegroundColor Green

# Read the .env file and set environment variables
if (Test-Path "Test 1 (7).env") {
    $envContent = Get-Content "Test 1 (7).env"
    foreach ($line in $envContent) {
        if ($line -match "^([^#=]+)=(.*)$") {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim('"')
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
            Write-Host "Set $name=$value" -ForegroundColor Cyan
        }
    }
    
    Write-Host "`nEnvironment variables loaded successfully!" -ForegroundColor Green
    
    # Verify critical variables
    $googleClientId = [Environment]::GetEnvironmentVariable("GOOGLE_CLIENT_ID", "Process")
    if ($googleClientId) {
        Write-Host "✅ GOOGLE_CLIENT_ID is set: $($googleClientId.Substring(0, [Math]::Min(20, $googleClientId.Length)))..." -ForegroundColor Green
    } else {
        Write-Host "❌ GOOGLE_CLIENT_ID not found!" -ForegroundColor Red
    }
    
    Write-Host "`nStarting Spring Boot application..." -ForegroundColor Yellow
    mvn spring-boot:run
} else {
    Write-Host "❌ File 'Test 1 (7).env' not found!" -ForegroundColor Red
    exit 1
}