# KredNova Payroll: frontend

React 18 + Vite + React Router. Talks to the Spring Boot backend (`krednova-backend`) through the Vite proxy.

## Run (Windows PowerShell)
From `krednova-frontend`, run `npm install` once, then `npm run dev`. The local frontend proxies API calls to the hosted Render backend by default.

Open http://localhost:5173. The first time, the page asks you to create the administrator account with your own name, email and password (12+ characters). After that it is a normal sign-in page. The admin adds everyone else from the Employees page.

## Use a local backend instead
The development proxy defaults to `https://krednova-backend.onrender.com`. To run the frontend against a local backend on port 8080, set this in PowerShell before starting Vite:
```
$env:VITE_API_PROXY_TARGET="http://localhost:8080"
npm run dev
```
Requests go through Vite's same-origin `/api` proxy, so no browser CORS configuration is needed.

## Connected to the real API
Every page: login and sessions, Dashboard, Employees, Attendance, Leave requests, Payroll, Payslips, Reports, Notifications and Settings. No sample data is left.
