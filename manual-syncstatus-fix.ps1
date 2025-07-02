# Manual fix for SyncStatus references
# This script will process each file individually

Write-Host "🔧 Manually fixing SyncStatus references..." -ForegroundColor Yellow

# List of files that need fixing (from the output above)
$filesToFix = @(
    "src\main\java\site\easy\to\build\crm\controller\PortfolioController.java",
    "src\main\java\site\easy\to\build\crm\entity\Block.java", 
    "src\main\java\site\easy\to\build\crm\entity\Customer.java",
    "src\main\java\site\easy\to\build\crm\service\portfolio\PortfolioServiceImpl.java"
)

foreach ($filePath in $filesToFix) {
    if (Test-Path $filePath) {
        Write-Host "📝 Processing $filePath..." -ForegroundColor Cyan
        
        # Read file content
        $content = Get-Content $filePath -Raw -Encoding UTF8
        $originalContent = $content
        
        # Apply replacements
        $content = $content -replace "SyncStatus\.PENDING", "SyncStatus.pending"
        $content = $content -replace "SyncStatus\.SYNCED", "SyncStatus.synced" 
        $content = $content -replace "SyncStatus\.ERROR", "SyncStatus.error"
        $content = $content -replace "SyncStatus\.FAILED", "SyncStatus.error"
        $content = $content -replace "SyncStatus\.SYNCING", "SyncStatus.pending"
        $content = $content -replace "SyncStatus\.CONFLICT", "SyncStatus.error"
        
        # Write back if changed
        if ($content -ne $originalContent) {
            Set-Content -Path $filePath -Value $content -NoNewline -Encoding UTF8
            Write-Host "  ✅ Updated $([System.IO.Path]::GetFileName($filePath))" -ForegroundColor Green
        } else {
            Write-Host "  ℹ️  No changes needed in $([System.IO.Path]::GetFileName($filePath))" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ❌ File not found: $filePath" -ForegroundColor Red
    }
}

Write-Host "`n🔍 Final check for remaining uppercase references..." -ForegroundColor Yellow
$remaining = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | Select-String "SyncStatus\.(PENDING|SYNCED|ERROR|FAILED|SYNCING|CONFLICT)"

if ($remaining) {
    Write-Host "⚠️  Still found some references:" -ForegroundColor Yellow
    foreach ($item in $remaining) {
        Write-Host "  $($item.Filename):$($item.LineNumber) - $($item.Line.Trim())" -ForegroundColor Yellow
    }
} else {
    Write-Host "✅ All uppercase SyncStatus references have been fixed!" -ForegroundColor Green
}

Write-Host "`n🚀 Don't forget to:" -ForegroundColor Yellow
Write-Host "  1. Update SyncStatus.java with lowercase enum constants" -ForegroundColor White
Write-Host "  2. Deploy to Render" -ForegroundColor White