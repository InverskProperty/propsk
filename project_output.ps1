# Complete Spring Boot Project File Lister
# Lists ALL files to ensure complete project visibility

$excluded = @("*backup_clean*", "*backup*", "*node_modules*", "*target*", "*build*", "*\.git*")
$webAssets = @('.css', '.js', '.png', '.jpg', '.svg', '.ico', '.woff', '.scss', '.gif', '.jpeg', '.ttf', '.eot')

Write-Host "=== ALL PROJECT FILES ===" -ForegroundColor Green

# Java files - all of them
Write-Host "--- JAVA FILES ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Filter "*.java" -EA 0 | Where-Object { 
    $_.FullName -notlike "*crecrm_backup_clean*"
} | ForEach-Object {
    $path = $_.FullName -replace [regex]::Escape((Get-Location).Path + "\"), ""
    Write-Host "  $($_.Name) [$path]"
}

# HTML files - all of them
Write-Host "--- HTML FILES ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Filter "*.html" -EA 0 | Where-Object { 
    $_.FullName -notlike "*crecrm_backup_clean*"
} | ForEach-Object {
    $path = $_.FullName -replace [regex]::Escape((Get-Location).Path + "\"), ""
    Write-Host "  $($_.Name) [$path]"
}

# SQL files - all of them
Write-Host "--- SQL FILES ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Filter "*.sql" -EA 0 | Where-Object { 
    $_.FullName -notlike "*crecrm_backup_clean*"
} | ForEach-Object {
    $path = $_.FullName -replace [regex]::Escape((Get-Location).Path + "\"), ""
    Write-Host "  $($_.Name) [$path]"
}

# Configuration files
Write-Host "--- CONFIG FILES ---" -ForegroundColor Cyan
@("*.properties", "*.yml", "*.yaml", "*.xml", "*.json", "*.gradle") | ForEach-Object {
    Get-ChildItem -Recurse -Filter $_ -EA 0 | Where-Object { 
        $_.FullName -notlike "*crecrm_backup_clean*"
    } | ForEach-Object {
        $path = $_.FullName -replace [regex]::Escape((Get-Location).Path + "\"), ""
        Write-Host "  $($_.Name) [$path]"
    }
}

# All folders to show complete structure
Write-Host "--- ALL FOLDERS ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Directory -EA 0 | Where-Object { 
    $_.FullName -notlike "*crecrm_backup_clean*"
} | ForEach-Object {
    $path = $_.FullName -replace [regex]::Escape((Get-Location).Path + "\"), ""
    Write-Host "  $($_.Name) [$path]"
}