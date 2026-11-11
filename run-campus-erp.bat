@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul

REM ============================================================================
REM CAMPUS ERP — Windows Desktop Application Launcher (v2.0.9)
REM University Management Platform
REM ============================================================================

title Campus ERP — Launcher

set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

REM 1. Verify Java 17+ Runtime Availability
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo ============================================================================
    echo [ERROR] Java runtime executable was not found on your system PATH.
    echo.
    echo Campus ERP requires Java Standard Edition 17 or higher - OpenJDK or Oracle JDK.
    echo Please install Java 17+ and ensure the 'bin' directory is added to your PATH.
    echo Download: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
    echo ============================================================================
    echo.
    pause
    exit /b 1
)

REM 2. Locate Application Executable JAR
if exist "%PROJECT_DIR%\target\CollegeManagementSystem.jar" (
    set "APP_JAR=%PROJECT_DIR%\target\CollegeManagementSystem.jar"
) else (
    echo.
    echo ============================================================================
    echo [ERROR] Current build artifact not found!
    echo Expected: %PROJECT_DIR%\target\CollegeManagementSystem.jar
    echo.
    echo Campus ERP will NOT fall back to outdated release candidate JARs.
    echo Please compile and package the application first using:
    echo   mvn clean package -DskipTests
    echo ============================================================================
    echo.
    pause
    exit /b 1
)

REM 3. Display Menu
echo.
echo ========================================
echo              CAMPUS ERP
echo ========================================
echo.
echo 1. Launch Demo Database [DEFAULT]
echo 2. Launch Production Database
echo 3. Exit
echo.

set "USER_CHOICE=1"
set /p "USER_CHOICE=Choose an option (default: 1): "

REM Normalize whitespace
set "USER_CHOICE=%USER_CHOICE: =%"

if "%USER_CHOICE%"=="1" (
    REM OPTION 1: Demonstration Database in project data/ directory
    set "TARGET_DB=%PROJECT_DIR%\data\college-demo.db"

    echo.
    echo ============================================================================
    echo [PROFILE] Connecting to DEMONSTRATION database:
    echo           !TARGET_DB!
    echo [STATUS]  Live production records are completely protected and isolated.
    echo.
    echo [SQLITE INSPECTION NOTE]
    echo   To view tables and live updates in DB Browser for SQLite:
    echo   1. File -^> Open Database -^> Select: !TARGET_DB!
    echo   2. Contains all 28 tables, 8 triggers, and 66 indices.
    echo   3. Press F5 or click 'Refresh' in DB Browser to view newly committed rows!
    echo ============================================================================
    echo.
    goto :LAUNCH

) else if "%USER_CHOICE%"=="2" (
    REM OPTION 2: Production Database - Strict Safety Check
    set "PROD_DB=%PROJECT_DIR%\data\college.db"

    echo.
    echo ============================================================================
    echo [WARNING] YOU HAVE SELECTED THE LIVE INSTITUTIONAL PRODUCTION DATABASE.
    echo           Path: !PROD_DB!
    echo.
    echo All operations performed in this mode will permanently modify live
    echo institutional records.
    echo.
    echo To confirm you intend to open the live production database, type:
    echo   OPEN PRODUCTION DATABASE
    echo ============================================================================
    echo.

    set "CONFIRM_TEXT="
    set /p "CONFIRM_TEXT=Confirmation: "

    if not "!CONFIRM_TEXT!"=="OPEN PRODUCTION DATABASE" (
        echo.
        echo [ABORT] Exact confirmation was not entered.
        echo         Expected: OPEN PRODUCTION DATABASE
        echo         Received: !CONFIRM_TEXT!
        echo Live production database access cancelled safely.
        echo.
        pause
        exit /b 0
    )

    if not exist "!PROD_DB!" (
        echo.
        echo ============================================================================
        echo [ERROR] Production database does not exist at expected location:
        echo         !PROD_DB!
        echo.
        echo [SAFETY] Missing production database will NEVER be recreated by launcher.
        echo ============================================================================
        echo.
        pause
        exit /b 1
    )

    set "TARGET_DB=!PROD_DB!"
    echo.
    echo [PROFILE] Connecting to PRODUCTION database:
    echo           !TARGET_DB!
    echo.
    goto :LAUNCH

) else if "%USER_CHOICE%"=="3" (
    echo.
    echo Exiting Campus ERP Launcher.
    exit /b 0

) else (
    echo.
    echo [ERROR] Invalid selection: '%USER_CHOICE%'. Please choose 1, 2, or 3.
    pause
    exit /b 1
)

:LAUNCH
echo Launching Campus ERP...
echo Executable: %APP_JAR%
echo Database:   !TARGET_DB!
echo.

java "-Dcollege.db.path=!TARGET_DB!" -jar "%APP_JAR%"

set "APP_EXIT=%ERRORLEVEL%"
if %APP_EXIT% neq 0 (
    echo.
    echo [NOTICE] Campus ERP terminated with exit code %APP_EXIT%.
    pause
)

exit /b %APP_EXIT%
