@echo off
echo Testing PayProp OAuth Token Exchange - Direct cURL Test
echo =====================================================
echo.

REM Your exact credentials from environment
set CLIENT_ID=Propsk
set CLIENT_SECRET=rphobpAUw1jgXI52
set TOKEN_URL=https://uk.payprop.com/api/oauth/access_token
set REDIRECT_URI=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback

echo Client ID: %CLIENT_ID%
echo Client Secret: %CLIENT_SECRET:~0,4%...
echo Token URL: %TOKEN_URL%
echo Redirect URI: %REDIRECT_URI%
echo.

echo STEP 1: Get Authorization Code
echo Go to this URL in your browser:
echo https://uk.payprop.com/api/oauth/authorize?response_type=code^&client_id=%CLIENT_ID%^&redirect_uri=https%%3A%%2F%%2Fspoutproperty-hub.onrender.com%%2Fapi%%2Fpayprop%%2Foauth%%2Fcallback^&state=manual-test
echo.
echo After authorization, copy the 'code' parameter from the callback URL
echo.

REM Replace this with actual authorization code
set /p AUTH_CODE=Enter the authorization code: 

if "%AUTH_CODE%"=="" (
    echo ERROR: No authorization code provided
    pause
    exit /b 1
)

echo.
echo STEP 2: Testing token exchange...
echo.

curl -X POST "%TOKEN_URL%" ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=authorization_code" ^
  -d "code=%AUTH_CODE%" ^
  -d "client_id=%CLIENT_ID%" ^
  -d "client_secret=%CLIENT_SECRET%" ^
  -d "redirect_uri=%REDIRECT_URI%" ^
  -v

echo.
echo Test completed.
pause