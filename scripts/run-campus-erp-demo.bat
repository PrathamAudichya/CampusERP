@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul

REM ============================================================================
REM CAMPUS ERP — Demonstration Launcher (Windows)
REM Target Profile: DEMO DATABASE
REM Database: %LOCALAPPDATA%\CampusERP\demo\campuserp-demo.db
REM ============================================================================

title Campus ERP — Demonstration Mode

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
echo CAMPUS ERP — DEMONSTRATION & TESTING ENVIRONMENT
echo Profile:  DEMO DATABASE
echo Location: %%LOCALAPPDATA%%\CampusERP\demo\campuserp-demo.db
echo.
echo [NOTICE] This launcher connects to an isolated synthetic demonstration database.
echo Live production records are protected and will NOT be accessed or modified.
echo ============================================================================
echo.

set "PROCEED=Y"
set /p "PROCEED=Launch Campus ERP in DEMO mode? (Y/n): "
if /i not "!PROCEED!"=="Y" if /i not "!PROCEED!"=="" (
    echo Launch cancelled.
    exit /b 0
)

echo Launching Campus ERP Demo...
java -jar "%APP_JAR%" --db-mode=demo %*
exit /b %ERRORLEVEL%
