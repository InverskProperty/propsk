# PowerShell script to fix SyncStatus enum references
# Run this from your project root directory (where pom.xml is located)

Write-Host "🔍 Searching for SyncStatus references in Java files..." -ForegroundColor Yellow

# Define the search patterns and replacements
$replacements = @{
    "SyncStatus.PENDING" = "SyncStatus.pending"
    "SyncStatus.SYNCED" = "SyncStatus.synced"
    "SyncStatus.ERROR" = "SyncStatus.error"
    "SyncStatus.FAILED" = "SyncStatus.error"
    "SyncStatus.SYNCING" = "SyncStatus.pending"
    "SyncStatus.CONFLICT" = "SyncStatus.error"
}

# Get all Java files recursively
$javaFiles = Get-ChildItem -Path "src" -Filter "*.java" -Recurse

$totalChanges = 0
$filesChanged = @()

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    $fileChanges = 0
    
    # Apply each replacement
    foreach ($old in $replacements.Keys) {
        $new = $replacements[$old]
        if ($content -match [regex]::Escape($old)) {
            Write-Host "  📝 Found '$old' in $($file.Name)" -ForegroundColor Cyan
            $content = $content -replace [regex]::Escape($old), $new
            $fileChanges++
        }
    }
    
    # If changes were made, write back to file
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        $filesChanged += $file.Name
        $totalChanges += $fileChanges
        Write-Host "  ✅ Updated $($file.Name) ($fileChanges changes)" -ForegroundColor Green
    }
}

Write-Host "`n📊 Summary:" -ForegroundColor Yellow
Write-Host "  Total files changed: $($filesChanged.Count)" -ForegroundColor White
Write-Host "  Total replacements made: $totalChanges" -ForegroundColor White

if ($filesChanged.Count -gt 0) {
    Write-Host "`n📁 Files that were modified:" -ForegroundColor Yellow
    foreach ($file in $filesChanged) {
        Write-Host "  - $file" -ForegroundColor White
    }
    
    Write-Host "`n🚀 Next steps:" -ForegroundColor Yellow
    Write-Host "  1. Update your SyncStatus.java with the new enum (lowercase constants)" -ForegroundColor White
    Write-Host "  2. Commit your changes to git" -ForegroundColor White
    Write-Host "  3. Deploy to Render" -ForegroundColor White
} else {
    Write-Host "`n✨ No SyncStatus references found that needed updating!" -ForegroundColor Green
}

Write-Host "`n🔍 You can also manually search for any remaining references:" -ForegroundColor Yellow
Write-Host "  grep -r 'SyncStatus\.' src/ --include='*.java'" -ForegroundColor Gray