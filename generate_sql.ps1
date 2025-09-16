# Generate SQL import from CSV
$csvPath = "C:\Users\sajid\crecrm\full_transactions.csv"
$sqlPath = "C:\Users\sajid\crecrm\complete_bulk_import.sql"

# Read CSV
$csv = Import-Csv $csvPath

# Start SQL content
$sqlContent = @()
$sqlContent += "USE crm_db;"
$sqlContent += "DELETE FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';"
$sqlContent += ""

# Process each row
foreach ($row in $csv) {
    # Map CSV transaction types to database constraint values
    $transactionType = switch ($row.transaction_type) {
        "deposit" { "deposit" }
        "fee" { "commission_payment" }
        "payment" { "payment_to_beneficiary" }
        "expense" { "payment_to_contractor" }
        default { "adjustment" }
    }
    $description = $row.description -replace "'", "''"
    $propertyName = $row.property_reference -replace "'", "''"
    $tenantName = $row.customer_reference -replace "'", "''"
    $reference = $row.bank_reference -replace "'", "''"

    $sql = "INSERT INTO financial_transactions (transaction_date, amount, description, transaction_type, property_name, tenant_name, category_name, reference, data_source, is_actual_transaction, created_at, updated_at) VALUES ('$($row.transaction_date)', $($row.amount), '$description', '$transactionType', '$propertyName', '$tenantName', '$($row.category)', '$reference', 'HISTORICAL_IMPORT', 1, NOW(), NOW());"
    $sqlContent += $sql
}

$sqlContent += ""
$sqlContent += "-- Verify import"
$sqlContent += "SELECT COUNT(*) as total_imported FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';"
$sqlContent += "SELECT transaction_date, COUNT(*) as daily_count FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT' GROUP BY transaction_date ORDER BY transaction_date LIMIT 10;"

# Write to file
$sqlContent | Out-File -FilePath $sqlPath -Encoding UTF8
Write-Host "Generated SQL file with $($csv.Count) transactions: $sqlPath"