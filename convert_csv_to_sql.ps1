# PowerShell script to convert full_data.csv to SQL INSERT statements

$csvPath = "C:\Users\sajid\crecrm\full_data.csv"
$sqlPath = "C:\Users\sajid\crecrm\full_import_generated.sql"

# Read CSV file
$csvData = Import-Csv -Path $csvPath

# Initialize SQL content
$sqlContent = @"
-- Generated SQL Import Script from full_data.csv
-- Total records to import: $($csvData.Count)

"@

# Process CSV records in batches of 100 for better performance
$batchSize = 100
$totalRecords = $csvData.Count
$currentBatch = 0

for ($i = 0; $i -lt $totalRecords; $i += $batchSize) {
    $currentBatch++
    $endIndex = [Math]::Min($i + $batchSize - 1, $totalRecords - 1)

    $sqlContent += "`n-- Batch $currentBatch (Records $($i+1) to $($endIndex+1))`n"
    $sqlContent += "INSERT INTO financial_transactions (amount, transaction_date, transaction_type, description, reference, property_name, tenant_name, category_name, data_source, created_at, updated_at) VALUES`n"

    $batchRecords = @()
    for ($j = $i; $j -le $endIndex; $j++) {
        $row = $csvData[$j]

        # Map transaction types
        $transactionType = switch ($row.transaction_type) {
            'deposit' { 'invoice' }
            'fee' { 'commission_payment' }
            'payment' { 'payment_to_beneficiary' }
            'expense' { 'payment_to_contractor' }
            default { $row.transaction_type }
        }

        # Escape single quotes in strings
        $description = $row.description -replace "'", "''"
        $propertyRef = $row.property_reference -replace "'", "''"
        $customerRef = $row.customer_reference -replace "'", "''"
        $reference = $row.bank_reference -replace "'", "''"
        $category = $row.category -replace "'", "''"

        $valueString = "($($row.amount), '$($row.transaction_date)', '$transactionType', '$description', '$reference', '$propertyRef', '$customerRef', '$category', 'HISTORICAL_IMPORT', NOW(), NOW())"
        $batchRecords += $valueString
    }

    $sqlContent += ($batchRecords -join ",`n") + ";`n"
    $sqlContent += "`n-- Verify batch $currentBatch`n"
    $sqlContent += "SELECT 'BATCH $currentBatch IMPORTED' as status, COUNT(*) as total_records FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';`n"
}

# Add final verification
$sqlContent += @"

-- Final verification
SELECT 'IMPORT COMPLETED' as status, COUNT(*) as total_imported
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT';

-- Breakdown by type
SELECT transaction_type, category_name, COUNT(*) as records, SUM(amount) as total_amount
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT'
GROUP BY transaction_type, category_name
ORDER BY COUNT(*) DESC;
"@

# Write SQL file
$sqlContent | Out-File -FilePath $sqlPath -Encoding UTF8

Write-Host "Generated SQL import script: $sqlPath"
Write-Host "Total records to import: $totalRecords"
Write-Host "Number of batches: $currentBatch"