# CAMPUS ERP — DEVELOPER SETUP & BUILD MANUAL
**Target System:** Campus ERP  
**Runtime Requirement:** Java Standard Edition (SE) 17 LTS or higher  
**Build Tool:** Apache Maven 3.8+  
**Operating Systems Supported:** Windows 10/11, macOS, Linux  

---

## 1. System Requirements & Prerequisites

To develop, compile, or run Campus ERP locally, ensure the following tools are installed:

| Tool | Minimum Version | Recommended Provider | Verification Command |
|:---|:---:|:---|:---|
| **Java Development Kit (JDK)** | 17 LTS | Eclipse Temurin (Adoptium), Oracle OpenJDK | `java -version` and `javac -version` |
| **Apache Maven** | 3.8.0+ | Official Apache Maven binary release | `mvn -version` |
| **Git** (Optional) | 2.30+ | Standard Git distribution | `git --version` |

> [!IMPORTANT]
> The application targets Java 17 bytecode (Major Version 61). Ensure `JAVA_HOME` points to a valid JDK 17+ installation.

---

## 2. Project Dependencies

All project dependencies are standard and hosted on Maven Central. They are declared in `pom.xml`:

| Dependency Group & Artifact | Version | Purpose |
|:---|:---:|:---|
| `org.xerial:sqlite-jdbc` | `3.54.1.0` | Embedded SQLite JDBC driver for local relational data storage. |
| `org.mindrot:jbcrypt` | `0.4` | Salted password hashing (BCrypt algorithm) for secure user credential storage. |
| `com.formdev:flatlaf` | `3.6` | Modern, high-DPI desktop Swing look-and-feel theme. |
| `org.junit.jupiter:junit-jupiter-api` | `5.10.2` | JUnit 5 Jupiter API for automated unit and integration tests (test scope). |
| `org.junit.jupiter:junit-jupiter-engine` | `5.10.2` | JUnit 5 test execution engine (test scope). |

---

## 3. Build & Compilation Instructions

### 3.1 Standard Compilation
To compile all Java classes without running tests:
```bash
mvn compile
```

### 3.2 Running the Full Automated Test Suite
To compile classes and execute all 52 automated test suites (282 tests):
```bash
mvn test
```
All tests execute against disposable, isolated in-memory or temporary test databases created under `target/test-dbs/`. The production database `data/college.db` is never touched.

### 3.3 Packaging the Fat / Executable JAR
To package an executable standalone fat JAR with all shaded dependencies:
```bash
mvn package -DskipTests
```
The resulting executable JAR will be generated at:
`target/CollegeManagementSystem.jar`

---

## 4. Application Execution Instructions

### 4.1 Launching via the Provided Launcher Scripts (Recommended)

#### On Windows:
Execute the Windows launcher batch script from the project root or `scripts/`:
```cmd
scripts\run-campus-erp.bat
```
The launcher presents an interactive menu:
- **Option 1 (Default):** Launches against a safe, isolated demonstration database (`college-demo.db` in `%LOCALAPPDATA%`).
- **Option 2:** Launches against the live institutional database (`data/college.db`) after requiring explicit confirmation (`OPEN PRODUCTION DATABASE`).

#### On Linux / macOS:
```bash
chmod +x scripts/run-campus-erp.sh
./scripts/run-campus-erp.sh
```

### 4.2 Launching Directly via Java CLI
To launch the application directly from the packaged JAR or released candidate:
```bash
# Launch with isolated demo database (recommended for developers)
java -Dcollege.db.path=data/college-demo.db -jar release/CampusERP-v2.0.9-candidate.jar

# Or launch freshly built JAR
java -Dcollege.db.path=data/college-demo.db -jar target/CollegeManagementSystem.jar
```

### 4.3 Secure Account Provisioning for Developers & QA
Campus ERP enforces a zero-default-password credential policy. All test accounts must be provisioned via secure workflows:

1. **Initial Administrator Setup:**
   On a clean database, launch the application without arguments or execute:
   ```bash
   java -jar target/CollegeManagementSystem.jar --setup-admin admin
   ```
   Follow the non-echoing console prompts to define your initial secret password.

2. **Faculty and Student Test Accounts:**
   Once logged in as Administrator:
   - Navigate to **Administration** → **User Management**.
   - Select any seeded personnel/student account or click **Add User**.
   - Click **Reset Password** to generate a cryptographically random one-time temporary password.
   - The user will be required to change this temporary password upon first login (`must_change_password=1`).

> [!NOTE]
> Accounts seeded with `must_change_password = 1` remain uninitialized until an administrator generates an active temporary credential or provisions the user profile.

