@echo off
cd /d "%~dp0app"
echo.
echo   Starting Dream Archive...
echo   The browser will open automatically.
echo   Close this window to stop.
echo.
node app.js
pause
