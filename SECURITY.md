# KredNova Payroll: security notes

## What is protected
- **Sign-in:** passwords are stored as BCrypt hashes (never in plain text). The session is an HttpOnly cookie (JavaScript cannot read it), `SameSite=Strict`, HTTPS-only in `prod`, 30-minute timeout.
- **Brute force:** 5 wrong passwords lock the account for 15 minutes, and each IP address is limited to 10 sign-in attempts per minute. A locked account and a wrong password give the same answer, so nobody can discover which emails exist.
- **Forged requests (CSRF):** every change request needs a token that only the real website can send.
- **Permissions:** every API endpoint checks the role on the server (Admin / Manager / Employee). Managers only see their own team and never see salaries. Employees only see their own data. Hiding a button in React is never the only protection.
- **Database:** all SQL uses bound parameters (no SQL injection). Error messages never leak internals.
- **Sessions are live-checked:** deactivating someone, changing their role, or changing/resetting their password ends their open sessions immediately.
- **Passwords:** at least 12 characters, no email name or "password" inside, no trivial repeats. Passwords an admin sets are temporary: the person must choose their own at first sign-in. Anyone can change their password from the top bar.
- **Payroll:** if there is more than one admin, a payroll run must be approved by a different admin than the one who calculated it.
- **Audit log:** sign-ins, lockouts, password changes/resets, employee changes and payroll steps are recorded (never passwords or salaries).
- **First admin:** the setup page works once, only while no users exist. Set `KREDNOVA_SETUP_KEY` to also require a key.
- **Response headers:** HSTS, no-sniff, no-referrer, frame blocking, restrictive permissions, no caching of API responses.

## Before you put it on the internet
1. **HTTPS only.** `DEPLOY.md` and `docker-compose.yml` set this up with automatic certificates (or use `krednova-frontend/nginx.conf.example`). Never expose port 8080 or MySQL directly.
2. Run with the **`prod` profile and MySQL**, with a database user that only has rights on the `krednova` database. The local file database (`./data`) is not encrypted: fine for trying things out, not for real salary data.
3. Set `KREDNOVA_SETUP_KEY` for the first start, then create the admin straight away.
4. Create at least **two admins** so the payroll two-person rule applies, and keep their emails real and personal (no shared accounts).
5. Turn on **backups** for the database and test restoring one. Restrict who can read the server and its environment variables.
6. Keep dependencies current: `mvn versions:display-dependency-updates` and `npm audit` now and then, and update Spring Boot patch releases.
7. Have the payroll rules (PF, leave deduction) reviewed by a payroll professional, and have a security professional review the deployment.

## Real data only
The Dashboard, Reports, Payslips, Notifications and Settings pages all read live data from the database. There is no sample data left in the app. Notifications are worked out from real leave requests, holidays, payroll runs and attendance. Settings (work hours, leave days, holidays) are saved in the database and changes are recorded in the audit log.

## Known limits
- No two-factor authentication yet (the next most valuable addition).
- Rate limiting is in memory, which is fine for one server.
- Leave days are counted as calendar days (weekends and holidays are not skipped), and payroll deducts only approved unpaid leave, not unmarked absences. Confirm these rules with your HR policy.
