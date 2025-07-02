# Complete PowerShell script to fix ALL SyncStatus enum references
# Run this from your project root directory (where pom.xml is located)

Write-Host "🔍 Fixing ALL SyncStatus references in Java files..." -ForegroundColor Yellow

# Define ALL the search patterns and replacements
$replacements = @{
    "SyncStatus\.PENDING" = "SyncStatus.pending"
    "SyncStatus\.SYNCED" = "SyncStatus.synced"
    "SyncStatus\.ERROR" = "SyncStatus.error"
    "SyncStatus\.FAILED" = "SyncStatus.error"
    "SyncStatus\.SYNCING" = "SyncStatus.pending"
    "SyncStatus\.CONFLICT" = "SyncStatus.error"
}

# Get all Java files recursively
$javaFiles = Get-ChildItem -Path "src" -Filter "*.java" -Recurse

$totalChanges = 0
$filesChanged = @()

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    $fileChanges = 0
    
    # Apply each replacement using regex
    foreach ($pattern in $replacements.Keys) {
        $replacement = $replacements[$pattern]
        
        # Count matches before replacement
        $matches = [regex]::Matches($content, $pattern)
        if ($matches.Count -gt 0) {
            Write-Host "  📝 Found $($matches.Count) instances of '$pattern' in $($file.Name)" -ForegroundColor Cyan
            $content = $content -replace $pattern, $replacement
            $fileChanges += $matches.Count
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
}

# Verify no uppercase references remain
Write-Host "`n🔍 Checking for any remaining uppercase references..." -ForegroundColor Yellow
$remaining = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | Select-String "SyncStatus\.(PENDING|SYNCED|ERROR|FAILED|SYNCING|CONFLICT)"

if ($remaining) {
    Write-Host "⚠️  Found remaining uppercase references:" -ForegroundColor Red
    foreach ($item in $remaining) {
        Write-Host "  $($item.Filename):$($item.LineNumber) - $($item.Line.Trim())" -ForegroundColor Red
    }
} else {
    Write-Host "✅ No remaining uppercase SyncStatus references found!" -ForegroundColor Green
}

Write-Host "`n🚀 Next steps:" -ForegroundColor Yellow
Write-Host "  1. Update your SyncStatus.java with the new enum (lowercase constants)" -ForegroundColor White
Write-Host "  2. Deploy to Render" -ForegroundColor White
Write-Host "  3. Test login" -ForegroundColor White