# PayProp API Inspector - Using Existing Access Token
# Run this script to inspect the actual PayProp API responses

param(
    [string]$EntityType = "beneficiaries",  # properties, tenants, beneficiaries
    [int]$Page = 1,
    [int]$Rows = 5,
    [string]$BaseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1"
)

Write-Host "🔍 PayProp API Inspector" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan

Write-Host "`n💡 To get your access token, run this in your browser console:" -ForegroundColor Yellow
Write-Host @"
// Copy and paste this in your browser console on your CRM site:
fetch('/api/payprop/oauth/token-info', {
    credentials: 'include'
})
.then(response => response.json())
.then(data => {
    console.log('Access Token:', data.access_token);
    console.log('Copy this token and paste it when prompted');
});
"@ -ForegroundColor Green

# Get access token from user
$accessToken = Read-Host "`nPaste your access token here"

if (-not $accessToken -or $accessToken.Trim() -eq "") {
    Write-Host "❌ No access token provided. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Access token received" -ForegroundColor Green

# Query the API
Write-Host "`n📥 Querying PayProp API for $EntityType..." -ForegroundColor Yellow

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
    
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode
        Write-Host "Status Code: $statusCode" -ForegroundColor Red
        
        if ($statusCode -eq 401) {
            Write-Host "🔑 Token might be expired. Get a fresh token from your browser." -ForegroundColor Yellow
        }
    }
    exit 1
}

# Analyze the Response
Write-Host "`n📊 Response Analysis" -ForegroundColor Yellow
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
        
        # Show key fields first
        Write-Host "🔑 Key Identity Fields:" -ForegroundColor Yellow
        Write-Host "   ID: $($item.id)" -ForegroundColor Cyan
        Write-Host "   Customer ID: $($item.customer_id)" -ForegroundColor Cyan
        Write-Host "   Customer Reference: $($item.customer_reference)" -ForegroundColor Cyan
        
        # Show all available fields
        Write-Host "`n📋 All Available Fields:" -ForegroundColor Yellow
        $item.PSObject.Properties | Sort-Object Name | ForEach-Object {
            $fieldName = $_.Name
            $fieldValue = $_.Value
            $valueType = if ($fieldValue -eq $null) { "NULL" } else { $fieldValue.GetType().Name }
            $displayValue = if ($fieldValue -eq $null) { "❌ NULL" } else { 
                if ($fieldValue -is [string] -and $fieldValue.Trim() -eq "") { "❌ EMPTY STRING" } else { 
                    if ($fieldValue -is [string] -and $fieldValue.Length -gt 50) {
                        $fieldValue.Substring(0, 47) + "..."
                    } else {
                        $fieldValue
                    }
                } 
            }
            
            $color = if ($fieldValue -eq $null -or ($fieldValue -is [string] -and $fieldValue.Trim() -eq "")) { 
                "Red" 
            } elseif ($fieldName -match "(name|email|id)") { 
                "Green" 
            } else { 
                "White" 
            }
            
            Write-Host "   $($fieldName.PadRight(20)) ($valueType): $displayValue" -ForegroundColor $color
        }
        
        # Specific analysis for different entity types
        Write-Host "`n🔍 Name Field Analysis:" -ForegroundColor Yellow
        switch ($EntityType) {
            "beneficiaries" {
                Write-Host "   Account Type: $($item.account_type)" -ForegroundColor Cyan
                if ($item.account_type -eq "individual") {
                    $firstName = if ($item.first_name) { $item.first_name } else { "❌ MISSING" }
                    $lastName = if ($item.last_name) { $item.last_name } else { "❌ MISSING" }
                    Write-Host "   First Name: $firstName" -ForegroundColor $(if ($item.first_name) { "Green" } else { "Red" })
                    Write-Host "   Last Name: $lastName" -ForegroundColor $(if ($item.last_name) { "Green" } else { "Red" })
                    
                    if ($item.first_name -and $item.last_name) {
                        Write-Host "   ✅ Full Name Would Be: '$($item.first_name) $($item.last_name)'" -ForegroundColor Green
                    } else {
                        Write-Host "   ❌ CANNOT CREATE FULL NAME - MISSING COMPONENTS" -ForegroundColor Red
                    }
                } else {
                    $businessName = if ($item.business_name) { $item.business_name } else { "❌ MISSING" }
                    Write-Host "   Business Name: $businessName" -ForegroundColor $(if ($item.business_name) { "Green" } else { "Red" })
                    
                    if (-not $item.business_name) {
                        Write-Host "   ❌ CANNOT CREATE BUSINESS NAME - FIELD IS MISSING" -ForegroundColor Red
                    }
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
                    
                    if ($item.first_name -and $item.last_name) {
                        Write-Host "   ✅ Full Name Would Be: '$($item.first_name) $($item.last_name)'" -ForegroundColor Green
                    } else {
                        Write-Host "   ❌ CANNOT CREATE FULL NAME - MISSING COMPONENTS" -ForegroundColor Red
                    }
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
        
        # Show exactly what would break your CRM
        Write-Host "`n💥 CRM Import Impact:" -ForegroundColor Red
        $wouldBreak = $false
        
        if ($EntityType -in @("beneficiaries", "tenants")) {
            if ($item.account_type -eq "individual") {
                if (-not $item.first_name -or -not $item.last_name) { 
                    Write-Host "   ❌ Your CRM would get: customer.setName(null + ' ' + null)" -ForegroundColor Red
                    Write-Host "   ❌ Result: 'Name is null' error" -ForegroundColor Red
                    $wouldBreak = $true
                }
            } else {
                if (-not $item.business_name) { 
                    Write-Host "   ❌ Your CRM would get: customer.setName(null)" -ForegroundColor Red
                    Write-Host "   ❌ Result: 'Name is null' error" -ForegroundColor Red
                    $wouldBreak = $true
                }
            }
        }
        
        if (-not $wouldBreak) {
            Write-Host "   ✅ This record would import successfully" -ForegroundColor Green
        }
    }
    
    # Summary Statistics
    Write-Host "`n📊 SUMMARY STATISTICS" -ForegroundColor White -BackgroundColor DarkGreen
    Write-Host "======================" -ForegroundColor White -BackgroundColor DarkGreen
    
    $totalRecords = $apiResponse.items.Count
    $problematicRecords = 0
    $missingFirstName = 0
    $missingLastName = 0
    $missingBusinessName = 0
    $missingEmail = 0
    
    foreach ($item in $apiResponse.items) {
        $hasNameIssue = $false
        
        if ($EntityType -in @("beneficiaries", "tenants")) {
            if ($item.account_type -eq "individual") {
                if (-not $item.first_name) { $missingFirstName++; $hasNameIssue = $true }
                if (-not $item.last_name) { $missingLastName++; $hasNameIssue = $true }
            } else {
                if (-not $item.business_name) { $missingBusinessName++; $hasNameIssue = $true }
            }
            if (-not $item.email_address) { $missingEmail++ }
        }
        
        if ($hasNameIssue) { $problematicRecords++ }
    }
    
    Write-Host "📊 Records analyzed: $totalRecords" -ForegroundColor White
    Write-Host "❌ Records that would cause 'Name is null': $problematicRecords" -ForegroundColor Red
    Write-Host "📈 Success rate: $(100 - [Math]::Round(($problematicRecords / $totalRecords) * 100, 1))%" -ForegroundColor $(if ($problematicRecords -eq 0) { "Green" } else { "Red" })
    
    if ($EntityType -in @("beneficiaries", "tenants")) {
        Write-Host "`nField-specific issues:" -ForegroundColor Yellow
        Write-Host "   Missing first_name: $missingFirstName" -ForegroundColor $(if ($missingFirstName -eq 0) { "Green" } else { "Red" })
        Write-Host "   Missing last_name: $missingLastName" -ForegroundColor $(if ($missingLastName -eq 0) { "Green" } else { "Red" })
        Write-Host "   Missing business_name: $missingBusinessName" -ForegroundColor $(if ($missingBusinessName -eq 0) { "Green" } else { "Red" })
        Write-Host "   Missing email_address: $missingEmail" -ForegroundColor $(if ($missingEmail -eq 0) { "Green" } else { "Red" })
    }
    
    if ($problematicRecords -gt 0) {
        Write-Host "`n💡 SOLUTION:" -ForegroundColor Yellow -BackgroundColor DarkRed
        Write-Host "Add null checking to your PayPropSyncOrchestrator methods:" -ForegroundColor Yellow
        Write-Host "- updateCustomerFromPayPropBeneficiaryData()" -ForegroundColor White
        Write-Host "- updateCustomerFromPayPropTenantData()" -ForegroundColor White
        Write-Host "Replace null names with defaults like 'Unknown Beneficiary'" -ForegroundColor White
    } else {
        Write-Host "`n🎉 All records in this sample look good!" -ForegroundColor Green
        Write-Host "Try checking more pages to see if issues exist elsewhere." -ForegroundColor Yellow
    }
    
} else {
    Write-Host "❌ No items found in the response" -ForegroundColor Red
    Write-Host "Full response:" -ForegroundColor Gray
    $apiResponse | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor Gray
}

Write-Host "`n🎯 USAGE EXAMPLES:" -ForegroundColor Cyan
Write-Host "==================" -ForegroundColor Cyan
Write-Host "Check more beneficiaries: .\inspect_payprop.ps1 -EntityType beneficiaries -Rows 10" -ForegroundColor Gray
Write-Host "Check tenants: .\inspect_payprop.ps1 -EntityType tenants" -ForegroundColor Gray  
Write-Host "Check properties: .\inspect_payprop.ps1 -EntityType properties" -ForegroundColor Gray
Write-Host "Different page: .\inspect_payprop.ps1 -EntityType beneficiaries -Page 5" -ForegroundColor Gray