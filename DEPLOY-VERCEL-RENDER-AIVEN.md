# Hosting KredNova on Vercel (website) + Render (backend) + Aiven (MySQL)

The website (Vercel) forwards every `/api/...` call to the backend through `krednova-frontend/vercel.json`.
**Open `vercel.json` and make sure the Render address in it is exactly your Render service URL.**

## 1. Aiven MySQL
Create the service, then allow connections from anywhere (`0.0.0.0/0`) in "Allowed IP addresses" (Render free has no fixed IP).
Build the Java URL (do not paste Aiven's `mysql://` link):
`jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Kolkata`

## 2. Render (backend)
New > Web Service, Root Directory `krednova-backend`, Runtime **Docker**, Health Check Path `/api/v1/auth/csrf`, region Singapore.
Environment variables:

| Name | Value |
|---|---|
| `DB_URL` | the jdbc URL above |
| `DB_USER` | `avnadmin` (or your Aiven user) |
| `DB_PASSWORD` | your Aiven password |
| `KREDNOVA_SETUP_KEY` | a long random secret. **Set it**, otherwise whoever opens the site first can create the admin |
| `KREDNOVA_LOGIN_RATE_LIMIT` | `30` (everyone shares one IP behind Vercel) |

`SPRING_PROFILES_ACTIVE=prod` is now set inside the Dockerfile. Without it the app silently used a throw-away H2 file database,
which is why accounts disappeared and sign-in said "incorrect".

## 3. Vercel (website)
Root Directory `krednova-frontend`, framework Vite. No environment variables needed.

## 4. First start
Open the Vercel site. The first load can take up to a minute while Render wakes up (a yellow banner says so).
Then create the admin with your setup key, and sign in.

## Being locked out
Five wrong passwords lock the account for 15 minutes (the message is the same as "wrong password" on purpose).
Wait 15 minutes, or ask another admin: Employees > Set temporary password.
To avoid the sleeping server, use Render's paid plan or a free UptimeRobot monitor on `https://<service>.onrender.com/api/v1/auth/csrf` every 5 minutes.
