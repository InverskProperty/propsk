# Fix Thymeleaf Template Paths - Correct Version
# This keeps the .html extension but removes the leading slash
# Run this from your project root directory

Write-Host "Starting template path corrections..." -ForegroundColor Green

# Check if templates directory exists
$templatesPath = "src/main/resources/templates"
if (-not (Test-Path $templatesPath)) {
    Write-Host "Templates directory not found: $templatesPath" -ForegroundColor Red
    Write-Host "Make sure you're running this from the project root directory" -ForegroundColor Red
    exit 1
}

# Get all HTML files in templates directory recursively
$templateFiles = Get-ChildItem -Path $templatesPath -Recurse -Filter "*.html"

Write-Host "Found $($templateFiles.Count) template files" -ForegroundColor Yellow

$fixedCount = 0

foreach ($file in $templateFiles) {
    Write-Host "Processing: $($file.Name)" -ForegroundColor Cyan
    
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Fix template includes - Remove leading slash but KEEP .html extension
    # th:replace patterns
    $content = $content -replace 'th:replace="~\{/general/([^}]+)\.html\}"', 'th:replace="~{general/$1.html}"'
    $content = $content -replace 'th:replace="~\{general/([^}]+)\}"', 'th:replace="~{general/$1.html}"'
    
    # th:insert patterns  
    $content = $content -replace 'th:insert="~\{/general/([^}]+)\.html\}"', 'th:insert="~{general/$1.html}"'
    $content = $content -replace 'th:insert="~\{general/([^}]+)\}"', 'th:insert="~{general/$1.html}"'
    
    # Check if content was changed
    if ($content -ne $originalContent) {
        Set-Content $file.FullName $content -Encoding UTF8
        Write-Host "  ✓ Fixed template paths in: $($file.Name)" -ForegroundColor Green
        $fixedCount++
    } else {
        Write-Host "  - No changes needed in: $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Template correction completed!" -ForegroundColor Green
Write-Host "Files processed: $($templateFiles.Count)" -ForegroundColor Yellow
Write-Host "Files fixed: $fixedCount" -ForegroundColor Green

Write-Host ""
Write-Host "Expected result format:" -ForegroundColor Cyan
Write-Host '  th:replace="~{general/header.html}"' -ForegroundColor White
Write-Host '  th:replace="~{general/left-sidebar.html}"' -ForegroundColor White

if ($fixedCount -gt 0) {
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Review the changes with: git diff" -ForegroundColor White
    Write-Host "2. Commit the changes: git add ." -ForegroundColor White
    Write-Host "3. Push to deploy: git commit -m 'Correct template paths with .html extension' && git push" -ForegroundColor White
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")