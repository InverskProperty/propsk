@echo off
echo ========================================
echo PayProp Historical Data Import Process
echo ========================================
echo.

echo Step 1: Clearing existing historical data...
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p"iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW" railway < "C:\Users\sajid\crecrm\clear_historical_data.sql"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to clear existing data
    pause
    exit /b 1
)

echo Step 1 completed successfully.
echo.

echo Step 2: Importing new CSV data...
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p"iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW" railway --local-infile=1 < "C:\Users\sajid\crecrm\import_full_data.sql"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import CSV data
    pause
    exit /b 1
)

echo Step 2 completed successfully.
echo.

echo Step 3: Verifying import results...
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p"iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW" railway -e "SELECT 'FINAL COUNT:' as status, COUNT(*) as records FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';"

echo.
echo ========================================
echo Import process completed!
echo ========================================
echo.
echo Next steps:
echo 1. Run property linkage update (if needed)
echo 2. Verify data in PayProp dashboard
echo 3. Test financial reports
echo.
pause