# PayProp API Inspector - See exactly what data is being returned
# Run this script to inspect the actual PayProp API responses

param(
    [string]$EntityType = "beneficiaries",  # properties, tenants, beneficiaries
    [int]$Page = 1,
    [int]$Rows = 5,
    [string]$BaseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1"
)

# PayProp OAuth2 credentials from your env vars
$ClientId = "Propsk"
$ClientSecret = "L7GJfqHWduV9IdU7"
$TokenUrl = "https://ukapi.staging.payprop.com/api/oauth/access_token"

Write-Host "🔍 PayProp API Inspector" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan

# Step 1: Get OAuth2 Access Token
Write-Host "`n🔐 Step 1: Getting OAuth2 Access Token..." -ForegroundColor Yellow

$tokenBody = @{
    grant_type = "client_credentials"
    client_id = $ClientId
    client_secret = $ClientSecret
    scope = "read:export:beneficiaries read:export:tenants read:export:properties"
}

try {
    $tokenResponse = Invoke-RestMethod -Uri $TokenUrl -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
    $accessToken = $tokenResponse.access_token
    Write-Host "✅ Access token obtained successfully" -ForegroundColor Green
    Write-Host "   Token Type: $($tokenResponse.token_type)" -ForegroundColor Gray
    Write-Host "   Expires In: $($tokenResponse.expires_in) seconds" -ForegroundColor Gray
    Write-Host "   Scopes: $($tokenResponse.scope)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Failed to get access token: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Response: $($_.Exception.Response | ConvertTo-Json)" -ForegroundColor Red
    exit 1
}

# Step 2: Query the API
Write-Host "`n📥 Step 2: Querying PayProp API for $EntityType..." -ForegroundColor Yellow

$apiUrl = "$BaseUrl/export/$EntityType"
$queryParams = "?page=$Page&rows=$Rows"
$fullUrl = $apiUrl + $queryParams

Write-Host "   URL: $fullUrl" -ForegroundColor Gray

$headers = @{
    "Authorization" = "Bearer $accessToken"
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

try {
    $apiResponse = Invoke-RestMethod -Uri $fullUrl -Method Get -Headers $headers
    Write-Host "✅ API call successful" -ForegroundColor Green
} catch {
    Write-Host "❌ API call failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Response: $($_.Exception.Response | ConvertTo-Json)" -ForegroundColor Red
    exit 1
}

# Step 3: Analyze the Response
Write-Host "`n📊 Step 3: Response Analysis" -ForegroundColor Yellow
Write-Host "============================" -ForegroundColor Yellow

if ($apiResponse.items) {
    $itemCount = $apiResponse.items.Count
    Write-Host "📋 Found $itemCount $EntityType" -ForegroundColor Green
    
    if ($apiResponse.pagination) {
        $pagination = $apiResponse.pagination
        Write-Host "📄 Pagination Info:" -ForegroundColor Cyan
        Write-Host "   Current Page: $($pagination.page)" -ForegroundColor Gray
        Write-Host "   Rows Per Page: $($pagination.rows)" -ForegroundColor Gray
        Write-Host "   Total Rows: $($pagination.total_rows)" -ForegroundColor Gray
        Write-Host "   Total Pages: $($pagination.total_pages)" -ForegroundColor Gray
    }
    
    Write-Host "`n🔍 Detailed Item Analysis:" -ForegroundColor Cyan
    Write-Host "=============================" -ForegroundColor Cyan
    
    for ($i = 0; $i -lt [Math]::Min($itemCount, 3); $i++) {
        $item = $apiResponse.items[$i]
        Write-Host "`n--- $EntityType Item $($i + 1) ---" -ForegroundColor White -BackgroundColor DarkBlue
        
        # Show all available fields
        Write-Host "📋 Available Fields:" -ForegroundColor Yellow
        $item.PSObject.Properties | ForEach-Object {
            $fieldName = $_.Name
            $fieldValue = $_.Value
            $valueType = if ($fieldValue -eq $null) { "NULL" } else { $fieldValue.GetType().Name }
            $displayValue = if ($fieldValue -eq $null) { "❌ NULL" } else { 
                if ($fieldValue -is [string] -and $fieldValue.Trim() -eq "") { "❌ EMPTY STRING" } else { $fieldValue } 
            }
            
            $color = if ($fieldValue -eq $null -or ($fieldValue -is [string] -and $fieldValue.Trim() -eq "")) { 
                "Red" 
            } elseif ($fieldName -match "(name|email|id)") { 
                "Green" 
            } else { 
                "White" 
            }
            
            Write-Host "   $fieldName ($valueType): $displayValue" -ForegroundColor $color
        }
        
        # Specific analysis for different entity types
        Write-Host "`n🔍 Field Analysis:" -ForegroundColor Yellow
        switch ($EntityType) {
            "beneficiaries" {
                Write-Host "   Account Type: $($item.account_type)" -ForegroundColor Cyan
                if ($item.account_type -eq "individual") {
                    $firstName = if ($item.first_name) { $item.first_name } else { "❌ MISSING" }
                    $lastName = if ($item.last_name) { $item.last_name } else { "❌ MISSING" }
                    Write-Host "   First Name: $firstName" -ForegroundColor $(if ($item.first_name) { "Green" } else { "Red" })
                    Write-Host "   Last Name: $lastName" -ForegroundColor $(if ($item.last_name) { "Green" } else { "Red" })
                    Write-Host "   Full Name Would Be: '$firstName $lastName'" -ForegroundColor $(if ($item.first_name -and $item.last_name) { "Green" } else { "Red" })
                } else {
                    $businessName = if ($item.business_name) { $item.business_name } else { "❌ MISSING" }
                    Write-Host "   Business Name: $businessName" -ForegroundColor $(if ($item.business_name) { "Green" } else { "Red" })
                }
                $email = if ($item.email_address) { $item.email_address } else { "❌ MISSING" }
                Write-Host "   Email: $email" -ForegroundColor $(if ($item.email_address) { "Green" } else { "Red" })
                Write-Host "   Payment Method: $($item.payment_method)" -ForegroundColor Cyan
            }
            "tenants" {
                Write-Host "   Account Type: $($item.account_type)" -ForegroundColor Cyan
                if ($item.account_type -eq "individual") {
                    $firstName = if ($item.first_name) { $item.first_name } else { "❌ MISSING" }
                    $lastName = if ($item.last_name) { $item.last_name } else { "❌ MISSING" }
                    Write-Host "   First Name: $firstName" -ForegroundColor $(if ($item.first_name) { "Green" } else { "Red" })
                    Write-Host "   Last Name: $lastName" -ForegroundColor $(if ($item.last_name) { "Green" } else { "Red" })
                    Write-Host "   Full Name Would Be: '$firstName $lastName'" -ForegroundColor $(if ($item.first_name -and $item.last_name) { "Green" } else { "Red" })
                } else {
                    $businessName = if ($item.business_name) { $item.business_name } else { "❌ MISSING" }
                    Write-Host "   Business Name: $businessName" -ForegroundColor $(if ($item.business_name) { "Green" } else { "Red" })
                }
                $email = if ($item.email_address) { $item.email_address } else { "❌ MISSING" }
                Write-Host "   Email: $email" -ForegroundColor $(if ($item.email_address) { "Green" } else { "Red" })
            }
            "properties" {
                $propName = if ($item.name) { $item.name } else { "❌ MISSING" }
                Write-Host "   Property Name: $propName" -ForegroundColor $(if ($item.name) { "Green" } else { "Red" })
                Write-Host "   Customer Reference: $($item.customer_reference)" -ForegroundColor Cyan
                if ($item.address) {
                    Write-Host "   Address: $($item.address.address_line_1), $($item.address.city)" -ForegroundColor Cyan
                }
            }
        }
        
        # Show problematic fields that would cause "Name is null" errors
        Write-Host "`n⚠️  Potential Issues:" -ForegroundColor Yellow
        $issues = @()
        
        if ($EntityType -in @("beneficiaries", "tenants")) {
            if ($item.account_type -eq "individual") {
                if (-not $item.first_name) { $issues += "Missing first_name for individual account" }
                if (-not $item.last_name) { $issues += "Missing last_name for individual account" }
                if (-not $item.first_name -or -not $item.last_name) { 
                    $issues += "Would cause 'Name is null' error in your CRM" 
                }
            } else {
                if (-not $item.business_name) { 
                    $issues += "Missing business_name for business account"
                    $issues += "Would cause 'Name is null' error in your CRM"
                }
            }
            if (-not $item.email_address) { $issues += "Missing email_address" }
        }
        
        if ($issues.Count -eq 0) {
            Write-Host "   ✅ No issues found with this record" -ForegroundColor Green
        } else {
            foreach ($issue in $issues) {
                Write-Host "   ❌ $issue" -ForegroundColor Red
            }
        }
    }
    
    # Summary
    Write-Host "`n📊 SUMMARY" -ForegroundColor White -BackgroundColor DarkGreen
    Write-Host "============" -ForegroundColor White -BackgroundColor DarkGreen
    
    $totalNull = 0
    $totalEmpty = 0
    $problematicRecords = 0
    
    foreach ($item in $apiResponse.items) {
        $hasNameIssue = $false
        
        if ($EntityType -in @("beneficiaries", "tenants")) {
            if ($item.account_type -eq "individual") {
                if (-not $item.first_name -or -not $item.last_name) { $hasNameIssue = $true }
            } else {
                if (-not $item.business_name) { $hasNameIssue = $true }
            }
        }
        
        if ($hasNameIssue) { $problematicRecords++ }
    }
    
    Write-Host "Total $EntityType retrieved: $itemCount" -ForegroundColor White
    Write-Host "Records that would cause 'Name is null' errors: $problematicRecords" -ForegroundColor $(if ($problematicRecords -gt 0) { "Red" } else { "Green" })
    Write-Host "Success rate: $(100 - [Math]::Round(($problematicRecords / $itemCount) * 100, 1))%" -ForegroundColor $(if ($problematicRecords -eq 0) { "Green" } else { "Yellow" })
    
    if ($problematicRecords -gt 0) {
        Write-Host "`n💡 RECOMMENDATION:" -ForegroundColor Yellow
        Write-Host "Add null checking to your Customer creation methods as suggested earlier!" -ForegroundColor Yellow
        Write-Host "This will handle the incomplete PayProp staging data gracefully." -ForegroundColor Yellow
    }
    
} else {
    Write-Host "❌ No items found in the response" -ForegroundColor Red
    Write-Host "Full response:" -ForegroundColor Gray
    $apiResponse | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor Gray
}

Write-Host "`n🎯 USAGE EXAMPLES:" -ForegroundColor Cyan
Write-Host "==================" -ForegroundColor Cyan
Write-Host "To check beneficiaries: .\inspect_payprop.ps1 -EntityType beneficiaries" -ForegroundColor Gray
Write-Host "To check tenants: .\inspect_payprop.ps1 -EntityType tenants" -ForegroundColor Gray  
Write-Host "To check properties: .\inspect_payprop.ps1 -EntityType properties" -ForegroundColor Gray
Write-Host "To get more records: .\inspect_payprop.ps1 -EntityType beneficiaries -Rows 10" -ForegroundColor Gray
Write-Host "To check different page: .\inspect_payprop.ps1 -EntityType beneficiaries -Page 2" -ForegroundColor Gray