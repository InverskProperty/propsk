# Fix Double HTML Extensions Script
# This script fixes templates that have .html.html (caused by previous scripts)
# Run this from your project root directory

Write-Host "Starting double HTML extension fix..." -ForegroundColor Green

# Check if templates directory exists
$templatesPath = "src/main/resources/templates"
if (-not (Test-Path $templatesPath)) {
    Write-Host "Templates directory not found: $templatesPath" -ForegroundColor Red
    Write-Host "Make sure you're running this from the project root directory" -ForegroundColor Red
    exit 1
}

# Get all HTML files in templates directory recursively
$templateFiles = Get-ChildItem -Path $templatesPath -Recurse -Filter "*.html"

Write-Host "Found $($templateFiles.Count) template files to check" -ForegroundColor Yellow

$fixedCount = 0

foreach ($file in $templateFiles) {
    Write-Host "Processing: $($file.Name)" -ForegroundColor Cyan
    
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Fix double .html.html extensions in template includes
    # th:replace patterns
    $content = $content -replace 'th:replace="~\{([^}]+)\.html\.html\}"', 'th:replace="~{$1.html}"'
    
    # th:insert patterns
    $content = $content -replace 'th:insert="~\{([^}]+)\.html\.html\}"', 'th:insert="~{$1.html}"'
    
    # th:include patterns (just in case)
    $content = $content -replace 'th:include="~\{([^}]+)\.html\.html\}"', 'th:include="~{$1.html}"'
    
    # Also fix any patterns that might have been created with absolute paths
    $content = $content -replace 'th:replace="~\{/([^}]+)\.html\.html\}"', 'th:replace="~{$1.html}"'
    $content = $content -replace 'th:insert="~\{/([^}]+)\.html\.html\}"', 'th:insert="~{$1.html}"'
    
    # Fix specific patterns we know about
    $content = $content -replace 'general/header\.html\.html', 'general/header.html'
    $content = $content -replace 'general/footer\.html\.html', 'general/footer.html'
    $content = $content -replace 'general/left-sidebar\.html\.html', 'general/left-sidebar.html'
    $content = $content -replace 'general/right-sidebar\.html\.html', 'general/right-sidebar.html'
    $content = $content -replace 'general/head\.html\.html', 'general/head.html'
    $content = $content -replace 'general/page-titles\.html\.html', 'general/page-titles.html'
    
    # Check if content was changed
    if ($content -ne $originalContent) {
        # Show what was fixed
        $changes = @()
        if ($originalContent -match '\.html\.html') {
            $changes += "Fixed .html.html extensions"
        }
        
        Set-Content $file.FullName $content -Encoding UTF8
        Write-Host "  ✓ Fixed: $($file.Name) - $($changes -join ', ')" -ForegroundColor Green
        $fixedCount++
        
        # Show specific fixes made
        $originalLines = $originalContent -split "`n"
        $newLines = $content -split "`n"
        
        for ($i = 0; $i -lt $originalLines.Length; $i++) {
            if ($i -lt $newLines.Length -and $originalLines[$i] -ne $newLines[$i]) {
                if ($originalLines[$i] -match '\.html\.html') {
                    Write-Host "    Line $($i+1): Fixed double extension" -ForegroundColor Yellow
                    Write-Host "      Before: $($originalLines[$i].Trim())" -ForegroundColor Red
                    Write-Host "      After:  $($newLines[$i].Trim())" -ForegroundColor Green
                }
            }
        }
    } else {
        Write-Host "  - No double extensions found in: $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Double HTML extension fix completed!" -ForegroundColor Green
Write-Host "Files processed: $($templateFiles.Count)" -ForegroundColor Yellow
Write-Host "Files fixed: $fixedCount" -ForegroundColor Green

if ($fixedCount -gt 0) {
    Write-Host ""
    Write-Host "Summary of fixes:" -ForegroundColor Cyan
    Write-Host "- Fixed .html.html → .html in template includes" -ForegroundColor White
    Write-Host "- Fixed th:replace, th:insert, and th:include patterns" -ForegroundColor White
    Write-Host "- Fixed both relative and absolute path patterns" -ForegroundColor White
    
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Review the changes with: git diff" -ForegroundColor White
    Write-Host "2. Commit the changes: git add ." -ForegroundColor White
    Write-Host "3. Push to deploy: git commit -m 'Fix double HTML extensions in templates' && git push" -ForegroundColor White
} else {
    Write-Host "No double HTML extensions found in any template files." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Expected result format:" -ForegroundColor Cyan
Write-Host '  th:replace="~{general/header.html}"' -ForegroundColor White
Write-Host '  NOT: th:replace="~{general/header.html.html}"' -ForegroundColor Red

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")