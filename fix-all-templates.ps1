# Fix All Thymeleaf Template Paths Script
# Run this from your project root directory

Write-Host "Starting template path fixes..." -ForegroundColor Green

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
    
    # Fix all the template includes - th:insert patterns
    $content = $content -replace 'th:insert="~\{/general/head\.html\}"', 'th:insert="~{general/head}"'
    $content = $content -replace 'th:insert="~\{/general/header\.html\}"', 'th:insert="~{general/header}"'
    $content = $content -replace 'th:insert="~\{/general/footer\.html\}"', 'th:insert="~{general/footer}"'
    $content = $content -replace 'th:insert="~\{/general/left-sidebar\.html\}"', 'th:insert="~{general/left-sidebar}"'
    $content = $content -replace 'th:insert="~\{/general/right-sidebar\.html\}"', 'th:insert="~{general/right-sidebar}"'
    $content = $content -replace 'th:insert="~\{/general/page-titles\.html\}"', 'th:insert="~{general/page-titles}"'
    
    # Fix all the template includes - th:replace patterns
    $content = $content -replace 'th:replace="~\{/general/head\.html\}"', 'th:replace="~{general/head}"'
    $content = $content -replace 'th:replace="~\{/general/header\.html\}"', 'th:replace="~{general/header}"'
    $content = $content -replace 'th:replace="~\{/general/footer\.html\}"', 'th:replace="~{general/footer}"'
    $content = $content -replace 'th:replace="~\{/general/left-sidebar\.html\}"', 'th:replace="~{general/left-sidebar}"'
    $content = $content -replace 'th:replace="~\{/general/right-sidebar\.html\}"', 'th:replace="~{general/right-sidebar}"'
    $content = $content -replace 'th:replace="~\{/general/page-titles\.html\}"', 'th:replace="~{general/page-titles}"'
    
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
Write-Host "Template fixing completed!" -ForegroundColor Green
Write-Host "Files processed: $($templateFiles.Count)" -ForegroundColor Yellow
Write-Host "Files fixed: $fixedCount" -ForegroundColor Green

if ($fixedCount -gt 0) {
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Review the changes with: git diff" -ForegroundColor White
    Write-Host "2. Commit the changes: git add ." -ForegroundColor White
    Write-Host "3. Push to deploy: git commit -m 'Fix all template paths' && git push" -ForegroundColor White
} else {
    Write-Host "No template files needed fixing." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")