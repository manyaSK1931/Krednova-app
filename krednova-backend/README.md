# KredNova Payroll: backend (Spring Boot 3.3, Java 21)

REST API under `/api/v1`. Session login with an HttpOnly cookie, CSRF protection, role checks on every endpoint.

## Run locally (Windows PowerShell)
Needs JDK 21 and Maven (`java -version`, `mvn -v`).
```
cd krednova-backend
mvn spring-boot:run
```
This uses the `local` profile: a database file saved in `./data`, so your accounts and data survive restarts. There are no demo accounts.

**First sign-in:** open the website (http://localhost:5173). While no users exist it shows "Create your admin account". Enter your name, email and a password of 12+ characters. That account is the administrator; it adds everyone else on the Employees page (each person gets a temporary password).
To start fresh, stop the server and delete the `data` folder.

Optional: set `KREDNOVA_SETUP_KEY` before starting and the setup form will also ask for that key (useful on any server others can reach).

## Passwords and security
Passwords an admin sets (new employee, or **Employees → click a name → Set temporary password**) are temporary: the person must choose their own at first sign-in. Anyone can use **Change password** in the top bar; this signs them out everywhere. Rules: 12+ characters, not containing your email name or the word "password". See `../SECURITY.md` for everything that is protected and the go-live checklist.

## Demo data (optional)
`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"` starts an in-memory database with demo accounts (password `KredNova@Dev2026`, e.g. sneha@krednova.test). Data resets on every restart.

## Use MySQL instead
Create the database and a user, then set these environment variables and run with the `prod` profile:
```
CREATE DATABASE krednova CHARACTER SET utf8mb4;
$env:DB_URL="jdbc:mysql://localhost:3306/krednova"
$env:DB_USER="krednova"
$env:DB_PASSWORD="your-password"
$env:KREDNOVA_SETUP_KEY="a-long-random-key"
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```
Flyway creates the tables from `db/migration/V1__schema.sql`. Open the website and create the first admin on the setup page. `prod` needs HTTPS because the session cookie is HTTPS-only (for a local test only: `$env:SERVER_SERVLET_SESSION_COOKIE_SECURE="false"`).

## Endpoints
Auth: GET /auth/csrf, GET /auth/setup-status, POST /auth/setup (first admin only, works once), POST /auth/login, POST /auth/change-password, GET /auth/me, POST /auth/logout
Employees: GET /employees, GET /employees/{id}, POST /employees (admin), PATCH /employees/{id}/status (admin), PATCH /employees/{id}/password (admin, sets a temporary password)
Attendance: POST /attendance/check-in, POST /attendance/check-out, GET /attendance/me, GET /attendance/today (manager, admin)
Leave: POST /leave-requests, GET /leave-requests, GET /leave-requests/balances, PATCH /leave-requests/{id}/decision (manager, admin)
Payroll (admin): POST /payroll/runs, GET /payroll/runs, GET /payroll/runs/{id}/records, POST /payroll/runs/{id}/approve, POST /payroll/runs/{id}/finalize
Payslips: GET /payroll/me (your own finalized payslips)

## Before real use
Payroll rules are illustrative (PF 12% of basic, unpaid leave = gross / 26 per day). Have them reviewed by a payroll professional. Follow the checklist in `../SECURITY.md` (HTTPS, MySQL, backups, security review).
