# Improved Spring Boot Project Structure Viewer
# Balances compactness with readability using tree-like structure

$basePath = (Get-Location).Path
$crmBase = "src\main\java\site\easy\to\build\crm"

function Get-RelativePath($fullPath) {
    return $fullPath -replace [regex]::Escape($basePath + "\"), ""
}

function Show-TreeStructure($groupedFiles, $title, $rootLabel = "📁 root") {
    Write-Host "`n=== $title ===" -ForegroundColor Green
    
    # Check if we have any files
    if ($groupedFiles.Keys.Count -eq 0) {
        Write-Host "  └── (no files found)" -ForegroundColor Gray
        return
    }
    
    # Sort directories by depth first, then alphabetically
    $sortedDirs = $groupedFiles.Keys | Sort-Object @{Expression={($_ -split '\\').Count}}, @{Expression={$_}}
    
    foreach ($dir in $sortedDirs) {
        $depth = ($dir -split '\\').Count - 1
        $indent = "  " * $depth
        
        if ($dir -eq "root") {
            Write-Host "$indent$rootLabel" -ForegroundColor Yellow
        } else {
            $dirName = Split-Path $dir -Leaf
            Write-Host "$indent├── 📁 $dirName/" -ForegroundColor Yellow
        }
        
        # Show files in this directory
        $files = $groupedFiles[$dir]
        if ($files -and $files.Count -gt 0) {
            $sortedFiles = $files | Sort-Object
            foreach ($file in $sortedFiles) {
                Write-Host "$indent│   ├── 📄 $file" -ForegroundColor White
            }
        }
    }
}

Write-Host "🏗️  SPRING BOOT PROJECT STRUCTURE" -ForegroundColor Magenta
Write-Host "📍 Base: $basePath" -ForegroundColor Gray

# JAVA FILES - Hierarchical structure
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" -EA 0 | Where-Object { 
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*backup*"
}

$javaGrouped = @{}
foreach ($file in $javaFiles) {
    $relativePath = Get-RelativePath $file.FullName
    $shortPath = $relativePath -replace [regex]::Escape($crmBase + "\"), ""
    $directory = Split-Path $shortPath -Parent
    
    if ($directory -eq "." -or $directory -eq "") { $directory = "root" }
    if (-not $javaGrouped.ContainsKey($directory)) {
        $javaGrouped[$directory] = @()
    }
    $javaGrouped[$directory] += $file.BaseName + ".java"
}

Show-TreeStructure $javaGrouped "JAVA SOURCE FILES" "📁 src/main/java/.../crm"

# HTML TEMPLATES - Hierarchical structure  
$htmlFiles = Get-ChildItem -Path "src\main\resources\templates" -Filter "*.html" -Recurse -EA 0

$htmlGrouped = @{}
foreach ($file in $htmlFiles) {
    $relativePath = $file.FullName -replace [regex]::Escape($basePath + "\src\main\resources\templates\"), ""
    $directory = Split-Path $relativePath -Parent
    
    if ($directory -eq "." -or $directory -eq "") { $directory = "root" }
    if (-not $htmlGrouped.ContainsKey($directory)) {
        $htmlGrouped[$directory] = @()
    }
    $htmlGrouped[$directory] += $file.BaseName + ".html"
}

Show-TreeStructure $htmlGrouped "HTML TEMPLATES" "📁 templates"

# CONFIGURATION FILES
Write-Host "`n=== CONFIGURATION FILES ===" -ForegroundColor Green
$configFiles = @(
    "pom.xml",
    "src/main/resources/application.properties"
)

foreach ($config in $configFiles) {
    if (Test-Path $config) {
        Write-Host "  ├── 📄 $config" -ForegroundColor White
    }
}

# SQL FILES
Write-Host "`n=== SQL MIGRATION FILES ===" -ForegroundColor Green
$sqlFiles = Get-ChildItem -Recurse -Filter "*.sql" -EA 0 | Where-Object { 
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*backup*"
}

if ($sqlFiles.Count -gt 0) {
    foreach ($sql in ($sqlFiles | Sort-Object Name)) {
        $relativePath = Get-RelativePath $sql.FullName
        Write-Host "  ├── 📄 $relativePath" -ForegroundColor White
    }
} else {
    Write-Host "  └── (no SQL files found)" -ForegroundColor Gray
}

# SUMMARY STATISTICS
Write-Host "`n=== PROJECT SUMMARY ===" -ForegroundColor Magenta
Write-Host "📊 Java files: $($javaFiles.Count)" -ForegroundColor Cyan
Write-Host "📊 HTML templates: $($htmlFiles.Count)" -ForegroundColor Cyan
Write-Host "📊 SQL files: $($sqlFiles.Count)" -ForegroundColor Cyan

# QUICK REFERENCE - Key directories
Write-Host "`n=== KEY DIRECTORIES ===" -ForegroundColor Green
$keyDirs = @(
    "controller", "service", "entity", "repository", 
    "config", "util", "google"
)

foreach ($keyDir in $keyDirs) {
    $count = ($javaGrouped.Keys | Where-Object { $_ -like "*$keyDir*" }).Count
    if ($count -gt 0) {
        Write-Host "  📁 ${keyDir}: $count locations" -ForegroundColor Yellow
    }
}

Write-Host "`n✅ Structure analysis complete!" -ForegroundColor Green