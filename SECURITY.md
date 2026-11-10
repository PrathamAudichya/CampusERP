# Security Policy — Campus ERP

Campus ERP is designed for institutional deployment and adheres to rigorous application security and data protection standards.

---

## 1. Supported Versions

Security updates and patches are actively maintained for the following versions:

| Version | Supported | Security Patch Status |
|:---|:---:|:---|
| 2.x (Current Milestone Baseline) | :white_check_mark: | Supported (Active Development) |
| 1.0.x | :x: | End of Life |

---

## 2. Reporting a Vulnerability

We take the security of Campus ERP seriously. If you identify a potential security vulnerability, please disclose it responsibly:

### Private Disclosure Process:
1. **GitHub Security Advisory:** Submit a private report via the repository's **Security** tab under **Advisories → Report a vulnerability**.
2. **Do Not File Public Issues:** Please do NOT report security vulnerabilities, potential exploits, or sensitive credential discoveries in public GitHub Issues or discussions.
3. **Information to Include:**
   - Detailed description of the vulnerability and its potential impact.
   - Specific component or file affected (e.g. `AuthService.java`, `FeePaymentDAO.java`).
   - Step-by-step reproduction guide or proof-of-concept (POC) code.
   - Any suggested remediations or mitigations.

### Response Timeline:
- The maintainers will acknowledge receipt of your report within 48 hours.
- A technical evaluation and verification will be provided within 5 business days.
- Once verified, a patch will be authored, regression-tested, and released with proper credit to the reporter.

---

## 3. Security Requirements & Coding Guidelines

Contributors must strictly uphold the following core security principles:

1. **Zero Hardcoded Secrets Policy:**
   - Under no circumstances should passwords, API tokens, private keys, or personal identifiers be checked into Git.
   - Pre-commit and CI secret scanning are enforced on all branches.
2. **Database Isolation & Local Privacy:**
   - Runtime SQLite databases (`data/college.db`, `*.db`, `*.sqlite`, `*.db-wal`) contain operational data and must never be committed to Git.
   - Automated tests must run exclusively against ephemeral, disposable in-memory or temporary databases.
3. **Password Security & Cryptography:**
   - User authentication strictly utilizes OpenBSD BCrypt (cost factor 12) with unique per-user salts.
   - Passwords in memory must be cleared promptly; plain passwords must never appear in logs or error traces.
   - Accounts provisioned with temporary passwords must enforce mandatory change upon initial login.
4. **Injection Prevention:**
   - All database queries must use JDBC `PreparedStatement` with parameterized placeholders (`?`). Dynamic SQL string concatenation is strictly prohibited.
5. **Data Protection:**
   - Demonstration records must exclusively use RFC 2606 synthetic domain names (`@example.test`) and synthetic contact numbers.
