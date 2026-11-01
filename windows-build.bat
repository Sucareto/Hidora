@echo off
docker compose -f "%~dp0compose.yaml" run --rm --build apk %*
set status=%ERRORLEVEL%
pause
exit /b %status%
