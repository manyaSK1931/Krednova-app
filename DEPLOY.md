# Putting KredNova Payroll online (with HTTPS)

You need: a small Linux server (2 GB RAM is enough), a domain name, and Docker with Docker Compose installed on the server.

1. **Point your domain at the server.** In your domain's DNS settings, create an `A` record for e.g. `payroll.yourcompany.com` with the server's IP address. Open ports 80 and 443 in the server firewall; keep everything else closed (do not open 3306 or 8080).
2. **Copy this folder** (the one with `docker-compose.yml`) to the server.
3. **Create your settings:** `cp .env.example .env`, then edit `.env`: your domain and three long random secrets (use a password manager to generate them).
4. **Start it:** `docker compose up -d --build`. The first start takes a few minutes. Caddy gets the HTTPS certificate automatically once the DNS record works.
5. **Create the admin:** open `https://your-domain`. The page asks for your name, email, a password (12+ characters) and the `SETUP_KEY` from `.env`. After that, add everyone else from **Employees**, and add a **second admin** so payroll needs two people.
6. **Add your holidays and working hours** under **Settings**.

## Everyday
- Logs: `docker compose logs -f backend`. Update after changing code: `docker compose up -d --build`.
- **Backups (important):** `docker compose exec db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" krednova' > backup-$(date +%F).sql`. Run it daily (cron), copy the file off the server, and practise restoring one.
- Keep the server updated (`apt update && apt upgrade`) and rebuild now and then to get new base images.
- To reset the admin password when nobody can sign in: ask another admin to use **Employees → Set temporary password**.

This kit was written without being run on a real server, so test it on a staging domain first and read `SECURITY.md` before storing real salary data.
