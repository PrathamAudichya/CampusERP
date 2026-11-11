#!/usr/bin/env bash
# ============================================================================
# CAMPUS ERP — Unix/Linux/macOS Desktop Application Launcher
# University Management Platform
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 1. Verify Java 17+ Runtime
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java runtime executable was not found on your system PATH."
    echo "Campus ERP requires Java 17 or higher."
    exit 1
fi

# 2. Locate Application Executable JAR
if [ -f "${PROJECT_DIR}/target/CollegeManagementSystem.jar" ]; then
    APP_JAR="${PROJECT_DIR}/target/CollegeManagementSystem.jar"
else
    echo "[ERROR] Current build artifact not found at: ${PROJECT_DIR}/target/CollegeManagementSystem.jar"
    echo "Campus ERP will NOT fall back to outdated historical release candidate JARs."
    echo "Please package the application first using: mvn clean package -DskipTests"
    exit 1
fi

# 3. Process CLI arguments or Interactive Menu
DB_MODE="standard"

if [ "$1" = "--demo" ] || [ "$1" = "--db-mode=demo" ]; then
    DB_MODE="demo"
elif [ "$1" = "--standard" ] || [ "$1" = "--db-mode=standard" ]; then
    DB_MODE="standard"
elif [ -z "$1" ]; then
    echo "========================================"
    echo "              CAMPUS ERP"
    echo "========================================"
    echo "1. Standard Institutional Database [DEFAULT]"
    echo "2. Demo Database (Synthetic preview)"
    echo "3. Exit"
    echo ""
    read -p "Choose an option (default: 1): " USER_CHOICE
    USER_CHOICE="${USER_CHOICE:-1}"

    if [ "${USER_CHOICE}" = "1" ]; then
        DB_MODE="standard"
    elif [ "${USER_CHOICE}" = "2" ]; then
        DB_MODE="demo"
    elif [ "${USER_CHOICE}" = "3" ]; then
        echo "Exiting."
        exit 0
    else
        echo "[ERROR] Invalid option."
        exit 1
    fi
fi

echo "Launching Campus ERP in ${DB_MODE} mode..."
java -jar "${APP_JAR}" "--db-mode=${DB_MODE}" "$@"
