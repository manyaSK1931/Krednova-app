# KredNova Payroll: frontend

React 18 + Vite + React Router. Talks to the Spring Boot backend (`krednova-backend`) through the Vite proxy.

## Run (two terminals, Windows PowerShell)
1. Backend: `cd krednova-backend` then `mvn spring-boot:run` (see its README)
2. Frontend: `cd krednova-frontend` then `npm install` then `npm run dev`

Open http://localhost:5173. The first time, the page asks you to create the administrator account with your own name, email and password (12+ characters). After that it is a normal sign-in page. The admin adds everyone else from the Employees page.

## Connected to the real API
Every page: login and sessions, Dashboard, Employees, Attendance, Leave requests, Payroll, Payslips, Reports, Notifications and Settings. No sample data is left.
