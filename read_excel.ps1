$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$workbook = $excel.Workbooks.Open('C:\Users\sajid\crecrm\statement_optionc_customer_79_2024-12-22_2025-11-21 (63).xlsx')

foreach($sheet in $workbook.Sheets) {
    Write-Host "=== SHEET: $($sheet.Name) ==="
    $usedRange = $sheet.UsedRange
    $rowCount = [Math]::Min($usedRange.Rows.Count, 150)
    $colCount = $usedRange.Columns.Count

    for($row = 1; $row -le $rowCount; $row++) {
        $line = ""
        for($col = 1; $col -le $colCount; $col++) {
            $cell = $usedRange.Cells.Item($row, $col)
            $val = $cell.Text
            if ($val -eq $null) { $val = "" }
            $line += "$val`t"
        }
        Write-Host $line
    }
    Write-Host ""
}

$workbook.Close($false)
$excel.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
