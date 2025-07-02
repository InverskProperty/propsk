# PaymentMethod enum fix script
# Run this from your project root directory

Write-Host "🔧 Fixing PaymentMethod enum references..." -ForegroundColor Yellow

# List of specific files that need fixing (from the search results)
$filesToFix = @(
    "src\main\java\site\easy\to\build\crm\entity\Customer.java",
    "src\main\java\site\easy\to\build\crm\service\payprop\PayPropSyncService.java",
    "src\main\java\site\easy\to\build\crm\service\property\PropertyOwnerServiceImpl.java"
)

$totalChanges = 0
$filesChanged = @()

foreach ($filePath in $filesToFix) {
    if (Test-Path $filePath) {
        Write-Host "📝 Processing $([System.IO.Path]::GetFileName($filePath))..." -ForegroundColor Cyan
        
        # Read file content with explicit UTF-8 encoding
        $content = Get-Content $filePath -Raw -Encoding UTF8
        $originalContent = $content
        $fileChanges = 0
        
        # Count and apply each replacement
        $patterns = @{
            "PaymentMethod\.LOCAL" = "PaymentMethod.local"
            "PaymentMethod\.INTERNATIONAL" = "PaymentMethod.international"
            "PaymentMethod\.CHEQUE" = "PaymentMethod.cheque"
        }
        
        foreach ($pattern in $patterns.Keys) {
            $replacement = $patterns[$pattern]
            $matches = [regex]::Matches($content, $pattern)
            if ($matches.Count -gt 0) {
                Write-Host "    🔍 Found $($matches.Count) instances of '$pattern'" -ForegroundColor Gray
                $content = $content -replace $pattern, $replacement
                $fileChanges += $matches.Count
            }
        }
        
        # Write back if changed
        if ($content -ne $originalContent) {
            try {
                Set-Content -Path $filePath -Value $content -NoNewline -Encoding UTF8
                $filesChanged += [System.IO.Path]::GetFileName($filePath)
                $totalChanges += $fileChanges
                Write-Host "    ✅ Updated $([System.IO.Path]::GetFileName($filePath)) ($fileChanges changes)" -ForegroundColor Green
            } catch {
                Write-Host "    ❌ Failed to write $([System.IO.Path]::GetFileName($filePath)): $($_.Exception.Message)" -ForegroundColor Red
            }
        } else {
            Write-Host "    ℹ️  No changes needed in $([System.IO.Path]::GetFileName($filePath))" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ❌ File not found: $filePath" -ForegroundColor Red
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

# Verify the changes worked
Write-Host "`n🔍 Checking for any remaining uppercase PaymentMethod references..." -ForegroundColor Yellow
$remaining = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | Select-String "PaymentMethod\.(LOCAL|INTERNATIONAL|CHEQUE)"

if ($remaining) {
    Write-Host "⚠️  Found remaining uppercase references:" -ForegroundColor Red
    foreach ($item in $remaining) {
        Write-Host "  $($item.Filename):$($item.LineNumber) - $($item.Line.Trim())" -ForegroundColor Red
    }
} else {
    Write-Host "✅ All uppercase PaymentMethod references have been fixed!" -ForegroundColor Green
}

Write-Host "`n🚀 Next steps:" -ForegroundColor Yellow
Write-Host "  1. Update PaymentMethod.java with lowercase enum constants" -ForegroundColor White
Write-Host "  2. Deploy to Render" -ForegroundColor White
Write-Host "  3. Test login" -ForegroundColor White

Write-Host "`n💡 PaymentMethod.java should look like:" -ForegroundColor Yellow
Write-Host "public enum PaymentMethod {" -ForegroundColor Gray
Write-Host "    local(`"local`")," -ForegroundColor Gray  
Write-Host "    international(`"international`")," -ForegroundColor Gray
Write-Host "    cheque(`"cheque`");" -ForegroundColor Gray
Write-Host "    // ... rest of enum" -ForegroundColor Gray
Write-Host "}" -ForegroundColor Gray