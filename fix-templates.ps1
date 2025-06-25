# PowerShell script to fix Thymeleaf fragment references
# Save this as fix-templates.ps1

# Set the templates directory path
$templatesPath = "src\main\resources\templates"

# Function to fix fragment references in a file
function Fix-FragmentReferences {
    param([string]$filePath)
    
    Write-Host "Processing: $filePath"
    
    # Read the file content
    $content = Get-Content $filePath -Raw
    $originalContent = $content
    
    # Fix common fragment patterns - WITH forward slash
    $content = $content -replace 'th:insert="~\{/general/header\.html\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:insert="~\{/general/left-sidebar\.html\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{/general/footer\.html\}"', 'th:replace="~{general/footer}"'
    
    # Fix common fragment patterns - WITHOUT forward slash
    $content = $content -replace 'th:insert="~\{general/header\.html\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:insert="~\{general/left-sidebar\.html\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{general/footer\.html\}"', 'th:replace="~{general/footer}"'
    
    # Fix patterns without .html extension
    $content = $content -replace 'th:insert="~\{/general/header\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:insert="~\{/general/left-sidebar\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{/general/footer\}"', 'th:replace="~{general/footer}"'
    
    $content = $content -replace 'th:insert="~\{general/header\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:insert="~\{general/left-sidebar\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{general/footer\}"', 'th:replace="~{general/footer}"'
    
    # Alternative patterns (if you have different naming)
    $content = $content -replace 'th:insert="~\{/header\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:insert="~\{/sidebar\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{/footer\}"', 'th:replace="~{general/footer}"'
    
    # Add .html extension if missing (optional - try this if still having issues)
    # $content = $content -replace 'th:replace="~\{general/header\}"', 'th:replace="~{general/header.html}"'
    # $content = $content -replace 'th:replace="~\{general/left-sidebar\}"', 'th:replace="~{general/left-sidebar.html}"'
    # $content = $content -replace 'th:replace="~\{general/footer\}"', 'th:replace="~{general/footer.html}"'
    
    # Only write if content changed
    if ($content -ne $originalContent) {
        Set-Content $filePath $content -NoNewline
        Write-Host "✅ Fixed: $filePath" -ForegroundColor Green
        return $true
    } else {
        Write-Host "⚪ No changes needed: $filePath" -ForegroundColor Yellow
        return $false
    }
}

# Main execution
Write-Host "🔧 Starting Thymeleaf template fix..." -ForegroundColor Cyan

# Find all HTML files in templates directory
$htmlFiles = Get-ChildItem -Path $templatesPath -Recurse -Filter "*.html"

$totalFiles = $htmlFiles.Count
$fixedFiles = 0

Write-Host "Found $totalFiles HTML template files" -ForegroundColor Cyan

foreach ($file in $htmlFiles) {
    if (Fix-FragmentReferences $file.FullName) {
        $fixedFiles++
    }
}

Write-Host "`n🎉 Processing complete!" -ForegroundColor Green
Write-Host "📊 Files processed: $totalFiles" -ForegroundColor White
Write-Host "🔧 Files fixed: $fixedFiles" -ForegroundColor Green
Write-Host "⚪ Files unchanged: $($totalFiles - $fixedFiles)" -ForegroundColor Yellow

Write-Host "`n💡 Next steps:" -ForegroundColor Cyan
Write-Host "1. Review the changes: git diff" -ForegroundColor White
Write-Host "2. Test your application locally" -ForegroundColor White
Write-Host "3. Commit and push: git add . && git commit -m 'Fix all Thymeleaf fragment references' && git push origin main" -ForegroundColor White