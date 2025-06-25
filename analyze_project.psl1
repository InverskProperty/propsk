# Project structure export script for PowerShell
# Run this from your project root directory

Write-Host "=== PROJECT STRUCTURE ===" -ForegroundColor Green
Write-Host "Current directory: $(Get-Location)" -ForegroundColor Yellow
Write-Host ""

# Show overall structure
Write-Host "=== DIRECTORY TREE ===" -ForegroundColor Green
Get-ChildItem -Recurse -Directory | Where-Object { $_.FullName -notmatch '(target|node_modules|\.git|\.idea)' } | Select-Object -First 30 | ForEach-Object { 
    $indent = "  " * (($_.FullName -replace [regex]::Escape((Get-Location).Path), "").Split([IO.Path]::DirectorySeparatorChar).Length - 2)
    Write-Host "$indent$($_.Name)" 
}

Write-Host ""
Write-Host "=== KEY FILES ===" -ForegroundColor Green

# Build files
Write-Host "--- Build Files ---" -ForegroundColor Cyan
Get-ChildItem -Name "pom.xml", "build.gradle", "settings.gradle" -ErrorAction SilentlyContinue

# Configuration files
Write-Host "--- Configuration Files ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Name "application*.properties", "application*.yml" -ErrorAction SilentlyContinue

# Java source structure
Write-Host "--- Java Source Structure ---" -ForegroundColor Cyan
if (Test-Path "src/main/java") {
    Get-ChildItem -Path "src/main/java" -Recurse -Name "*.java" | Select-Object -First 20
} else {
    Write-Host "No src/main/java directory found"
}

# Resources
Write-Host "--- Resource Files ---" -ForegroundColor Cyan
if (Test-Path "src/main/resources") {
    Get-ChildItem -Path "src/main/resources" -Recurse -Name | Select-Object -First 15
} else {
    Write-Host "No src/main/resources directory found"
}

# Templates
Write-Host "--- Template Files ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Name "*.html", "*.jsp", "*.thymeleaf" -ErrorAction SilentlyContinue | Select-Object -First 10

# Static files
Write-Host "--- Static Files ---" -ForegroundColor Cyan
Get-ChildItem -Recurse -Path "*static*" -ErrorAction SilentlyContinue | Select-Object -First 10 -ExpandProperty Name

Write-Host ""
Write-Host "=== PACKAGE STRUCTURE ===" -ForegroundColor Green
if (Test-Path "src/main/java") {
    Get-ChildItem -Path "src/main/java" -Recurse -Directory | ForEach-Object { 
        $_.FullName -replace [regex]::Escape((Resolve-Path "src/main/java").Path + "\"), "" -replace "\\", "." 
    } | Sort-Object
}

Write-Host ""
Write-Host "=== FILE COUNTS ===" -ForegroundColor Green
$javaCount = (Get-ChildItem -Recurse -Name "*.java" -ErrorAction SilentlyContinue).Count
$htmlCount = (Get-ChildItem -Recurse -Name "*.html" -ErrorAction SilentlyContinue).Count
$cssCount = (Get-ChildItem -Recurse -Name "*.css" -ErrorAction SilentlyContinue).Count
$jsCount = (Get-ChildItem -Recurse -Name "*.js" -ErrorAction SilentlyContinue).Count

Write-Host "Java files: $javaCount"
Write-Host "HTML files: $htmlCount"
Write-Host "CSS files: $cssCount"
Write-Host "JS files: $jsCount"