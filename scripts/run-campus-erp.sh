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

# 3. Interactive Menu
echo "========================================"
echo "              CAMPUS ERP"
echo "========================================"
echo "1. Launch Demo Database [DEFAULT]"
echo "2. Launch Production Database"
echo "3. Exit"
echo ""
read -p "Choose an option (default: 1): " USER_CHOICE
USER_CHOICE="${USER_CHOICE:-1}"

if [ "${USER_CHOICE}" = "1" ]; then
    TARGET_DB="${PROJECT_DIR}/data/college-demo.db"
    echo "[PROFILE] Connecting to DEMONSTRATION database: ${TARGET_DB}"
    echo "[STATUS] Live production records are completely protected and isolated."
    echo "[SQLITE NOTE] Open ${TARGET_DB} in DB Browser and press F5 to refresh live updates."
elif [ "${USER_CHOICE}" = "2" ]; then
    PROD_DB="${PROJECT_DIR}/data/college.db"
    echo "[WARNING] Live institutional database selected: ${PROD_DB}"
    read -p "Type 'OPEN PRODUCTION DATABASE' to confirm: " CONFIRM_TEXT
    if [ "${CONFIRM_TEXT}" != "OPEN PRODUCTION DATABASE" ]; then
        echo "[ABORT] Confirmation mismatch. Exiting."
        exit 0
    fi
    TARGET_DB="${PROD_DB}"
elif [ "${USER_CHOICE}" = "3" ]; then
    echo "Exiting."
    exit 0
else
    echo "[ERROR] Invalid option."
    exit 1
fi

echo "Launching Campus ERP..."
java "-Dcollege.db.path=${TARGET_DB}" -jar "${APP_JAR}"

