# College Management System — User Guide

**Version:** 1.0.0  
**Application Architecture:** Role-Based Swing Desktop Application

---

## 1. Authentication & Common Navigation

### 1.1 Logging In
Launch the application and enter your assigned username and password into the `LoginFrame`.

### 1.2 Mandatory Password Change (SEC-08)
If your account has the `must_change_password` flag enabled (default for initial `admin` account), you will be prompted immediately to set a new password:
- Passwords must not be blank.
- You must confirm the new password identically.
- Upon successful update, access to the full dashboard is unlocked.

### 1.3 Role-Based Interface
The system dynamically tailors its menu and navigation sidebar according to your role:
- **`ADMIN`:** Full institutional management, financial operations, audit logs, master records.
- **`FACULTY`:** Course management, attendance marking, grading for assigned courses, and faculty dashboard.
- **`STUDENT`:** Isolated profile, personal course attendance, examination marks, fee ledger, and student dashboard.

---

## 2. Administrator Instructions

### 2.1 Department Management
- Create departments with unique codes (e.g. `CS`, `EC`, `ME`, `CE`) and names.
- Toggle department active status. Inactive departments cannot receive new student/course enrollments.

### 2.2 Student & Faculty Management
- **Student Registration:** Registers both a user login account and a student profile atomically. If user creation or student profiling fails, the entire transaction rolls back.
- **Faculty Registration:** Atomically creates user credentials and faculty profile records with designations and join dates.

### 2.3 Course Catalog & Course Assignment
- Define courses with course codes, credit hours, semesters, and maximum student capacities.
- Assign faculty to courses for the active academic year (default: `2026-2027`).

### 2.4 Enrollment Management
- Enroll students in courses. The service layer strictly enforces course capacity guards: enrollment attempts beyond `max_capacity` are rejected.

### 2.5 Fee Structure & Financial Ledger
- **Fee Structures:** Defined per department and semester with exact integer paise amounts (e.g., ₹50,000 = `5000000` paise).
- **Payment Collection:** Collect payments with support for modes (`CASH`, `ONLINE`, `CHEQUE`, `DEMAND_DRAFT`).
- **Refunds:** Issue partial or full refunds against completed transactions. Refunds are recorded as child transaction records and cannot exceed the original payment amount.
- **Immutability:** Financial payment records are strictly protected by database triggers. Direct row deletions or arbitrary column mutations are prevented.

### 2.6 Institutional Reporting & Audit Logs
- Generate aggregate institutional reports: department enrollment statistics, fee collection summaries, attendance rates.
- Inspect the append-only `audit_log` capturing administrative events and security actions with timestamps.

---

## 3. Faculty Instructions

### 3.1 Faculty Dashboard
Displays current assigned courses, enrolled student counts, and schedule summaries for the active academic year.

### 3.2 Marking Daily Attendance
1. Select your assigned course and academic year.
2. The enrolled student list is loaded.
3. Select the attendance date (strict `YYYY-MM-DD` ISO format).
4. Mark statuses (`PRESENT`, `ABSENT`, `LATE`, `EXCUSED`).
5. Submit the batch. The service layer verifies your faculty assignment and commits the records atomically.

### 3.3 Recording Examination Marks
1. Select an examination for your assigned course.
2. Enter marks for enrolled students.
3. System verifies scores are within `[0, total_marks]`, validates decimal precision (maximum 2 decimal places), and automatically computes grades.

---

## 4. Student Instructions

### 4.1 Student Dashboard & Profile
View personal academic details, enrollment status, department, and contact information.

### 4.2 Viewing Attendance & Academic Performance
- View personal attendance percentages per course.
- View examination results, grades, and cumulative marks.
- Cross-student access is prohibited by query isolation.

### 4.3 Reviewing Fee Status
- View assigned fee structures, total paid amounts, pending balances, and refund receipts in integer currency precision.

---

## 5. Logging Out
Click the **Logout** button on the top toolbar or window menu to securely invalidate the current user session and return to the login screen.

