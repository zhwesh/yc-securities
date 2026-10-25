@echo off
setlocal
cd /d "%~dp0"

echo [yc] 1/2 building frontend ...
call npm run build
if errorlevel 1 (
    echo.
    echo [yc] build FAILED, deploy folder untouched.
    pause
    exit /b 1
)

echo.
echo [yc] 2/2 copying dist -^> deploy\html ...
if exist "deploy\html" rmdir /s /q "deploy\html"
xcopy "dist" "deploy\html" /E /I /Y >nul
if errorlevel 1 (
    echo [yc] copy failed
    pause
    exit /b 1
)

echo.
echo [yc] done. nginx serves static files directly, no restart needed.
echo      Open http://localhost and refresh.
echo.
pause
