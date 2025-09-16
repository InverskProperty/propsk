@echo off
echo Testing financial data export...

REM Try different MySQL paths
if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    echo Found MySQL 8.0
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u crm_user -pcrm_password -D crm_db -e "SELECT 'Test connection successful'"
) else if exist "C:\xampp\mysql\bin\mysql.exe" (
    echo Found XAMPP MySQL
    "C:\xampp\mysql\bin\mysql.exe" -u crm_user -pcrm_password -D crm_db -e "SELECT 'Test connection successful'"
) else (
    echo MySQL not found in standard locations
    echo Please check MySQL installation
)

echo Export test completed.
pause