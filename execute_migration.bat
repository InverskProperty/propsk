@echo off
echo Executing block tag migration...

rem Database connection details from application-local.properties
set DB_HOST=ballast.proxy.rlwy.net
set DB_PORT=45419
set DB_NAME=railway
set DB_USER=root
set DB_PASS=bpKegDvimyMvbRFbYDcsAxsDFpzmeilH

rem Try to execute the migration script using mysql command
echo Connecting to %DB_HOST%:%DB_PORT%/%DB_NAME%...

rem If mysql is available, execute the script
mysql -h %DB_HOST% -P %DB_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% < migrate_blocks_to_simple_owner_tags.sql

rem If mysql not available, show manual instructions
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo MySQL CLI not available. Please execute the following SQL manually:
    echo.
    type migrate_blocks_to_simple_owner_tags.sql
    echo.
    echo Connection details:
    echo Host: %DB_HOST%
    echo Port: %DB_PORT%
    echo Database: %DB_NAME%
    echo Username: %DB_USER%
    echo Password: %DB_PASS%
)

pause