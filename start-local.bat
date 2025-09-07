@echo off
echo Loading environment variables from Test 1 (7).env...

REM Read the .env file and set environment variables
for /f "usebackq tokens=1,2 delims==" %%a in ("Test 1 (7).env") do (
    if not "%%a"=="" if not "%%b"=="" (
        set "%%a=%%b"
        echo Set %%a=%%b
    )
)

echo.
echo Starting Spring Boot application...
mvn spring-boot:run