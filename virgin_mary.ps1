# Ultra-Compact Spring Boot Project File Lister
# Extreme character reduction by eliminating redundancy

$basePath = (Get-Location).Path
$crmBase = "src\main\java\site\easy\to\build\crm"

function Get-RelativePath($fullPath) {
    return $fullPath -replace [regex]::Escape($basePath + "\"), ""
}

Write-Host "=== COMPACT PROJECT VIEW ===" -ForegroundColor Green

# Java files - remove common prefix and exclude target
Write-Host "`n--- JAVA FILES ---" -ForegroundColor Cyan
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" -EA 0 | Where-Object { 
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*backup*"
}

$javaGrouped = @{}
foreach ($file in $javaFiles) {
    $relativePath = Get-RelativePath $file.FullName
    $shortPath = $relativePath -replace [regex]::Escape($crmBase + "\"), ""
    $directory = Split-Path $shortPath -Parent
    
    if ($directory -eq ".") { $directory = "root" }
    if (-not $javaGrouped.ContainsKey($directory)) {
        $javaGrouped[$directory] = @()
    }
    $javaGrouped[$directory] += $file.BaseName
}

foreach ($dir in ($javaGrouped.Keys | Sort-Object)) {
    if ($dir -eq "root") {
        Write-Host "  [] CrmApplication"
    } else {
        Write-Host "  [$dir] " -NoNewline -ForegroundColor Yellow
        ($javaGrouped[$dir] | Sort-Object) -join ", " | Write-Host
    }
}

# HTML files - source only, compressed paths
Write-Host "`n--- HTML TEMPLATES ---" -ForegroundColor Cyan
$htmlFiles = Get-ChildItem -Path "src\main\resources\templates" -Filter "*.html" -Recurse -EA 0

$htmlGrouped = @{}
foreach ($file in $htmlFiles) {
    $relativePath = $file.FullName -replace [regex]::Escape($basePath + "\src\main\resources\templates\"), ""
    $directory = Split-Path $relativePath -Parent
    
    if ($directory -eq ".") { $directory = "root" }
    if (-not $htmlGrouped.ContainsKey($directory)) {
        $htmlGrouped[$directory] = @()
    }
    $htmlGrouped[$directory] += $file.BaseName
}

foreach ($dir in ($htmlGrouped.Keys | Sort-Object)) {
    if ($dir -eq "root") {
        Write-Host "  [/] " -NoNewline
        $htmlGrouped[$dir] -join ", " | Write-Host
    } else {
        Write-Host "  [$dir] " -NoNewline -ForegroundColor Yellow
        $htmlGrouped[$dir] -join ", " | Write-Host
    }
}

# Config files - essential only
Write-Host "`n--- CONFIG ---" -ForegroundColor Cyan
Write-Host "  pom.xml, application.properties"

# SQL files - source only
Write-Host "`n--- SQL ---" -ForegroundColor Cyan
$sqlFiles = Get-ChildItem -Recurse -Filter "*.sql" -EA 0 | Where-Object { 
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*backup*"
}
$sqlFiles | ForEach-Object { Write-Host "  $($_.Name)" }