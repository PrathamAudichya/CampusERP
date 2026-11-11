@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul

REM ============================================================================
REM CAMPUS ERP — Standard Institutional Launcher (Windows)
REM Target Profile: STANDARD DATABASE
REM Database: %LOCALAPPDATA%\CampusERP\database\campuserp.db
REM ============================================================================

title Campus ERP — Standard Institutional Platform

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java 17+ runtime not found on PATH.
    pause
    exit /b 1
)

if exist "%PROJECT_DIR%\target\CollegeManagementSystem.jar" (
    set "APP_JAR=%PROJECT_DIR%\target\CollegeManagementSystem.jar"
) else (
    echo [ERROR] Build artifact target\CollegeManagementSystem.jar not found!
    echo Please compile first: mvn clean package -DskipTests
    pause
    exit /b 1
)

echo ============================================================================
echo CAMPUS ERP — STANDARD INSTITUTIONAL PRODUCTION PLATFORM
echo Profile:  STANDARD DATABASE
echo Location: %%LOCALAPPDATA%%\CampusERP\database\campuserp.db
echo ============================================================================
echo.

java -jar "%APP_JAR%" --db-mode=standard %*
exit /b %ERRORLEVEL%
