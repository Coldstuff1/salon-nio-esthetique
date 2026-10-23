# Operations

How the production stack is put together, how to deploy it on one server, and the routine tasks after that.
Local development is in the [README](../README.md).

## The stack

```
Internet ──443──▶ Caddy ──▶ frontend (nginx: SPA, /api proxy, CSP, rate limit) ──▶ backend (Spring Boot)
                    │                                                                  │
                    └──────▶ Keycloak (auth.<domain>) ◀──── signing keys ─────────────┤
                                  │                                                    │
                                  └────────────▶ Postgres ◀────────────────────────────┘
Browser ──presigned URLs──▶ managed S3 bucket (photos, videos) ◀── backend (head/delete)
```

- `compose.prod.yaml` runs everything; only Caddy publishes ports (80, 443). Postgres sits on an internal
  network with no route out. Keycloak's admin port is published on the server's loopback only.
- Secrets live in `.env.prod` on the server (template: `.env.prod.example`); each service receives only the
  variables it uses.
- The backend runs with `SPRING_PROFILES_ACTIVE=prod` (`application-prod.yaml`): it refuses to start when a
  setting is missing, and the API docs are switched off.
- Keycloak is built from `backend/docker/keycloak/Dockerfile` with the `nio` theme and the production realm
  (`realm-nio.json`) baked in. The realm is imported **only on the first start**; later changes are made in the
  admin console (and, if they should survive a rebuild from scratch, mirrored in `realm-nio.json`).

What the realm enforces: email verification before booking, a password policy (12+ characters, not the
username or email), lockout after 5 failed sign-ins (growing to 15 minutes), reCAPTCHA on registration, and a
one-time code (TOTP) on every sign-in for employees and admins (the `otp-required` role, part of both).

## First deployment

1. **Server**: a small Linux VPS in a Canadian region, with Docker Engine and the Compose plugin. Allow only
   22, 80 and 443 in the firewall. Docker bypasses `ufw` for published ports, which is why nothing else is
   published. SSH with keys only, no root login; enable unattended security upgrades. 4 GB of RAM is enough:
   each service has a `mem_limit` in `compose.prod.yaml` (about 2.5 GB in all) and the JVMs size their heaps
   from it. Add a 2 GB swap file for the image builds.
2. **DNS**: `A`/`AAAA` records for `<domain>` and `auth.<domain>` pointing at the server.
3. **Email**: an account with a transactional email provider; add its SPF and DKIM records and a DMARC
   policy for the domain. With Amazon SES (region `ca-central-1`): verify the domain with Easy DKIM, set a
   custom MAIL FROM subdomain, create SMTP credentials in the SES console and request production access (it
   can take a day). The SMTP host is `email-smtp.ca-central-1.amazonaws.com`, port 587.
4. **reCAPTCHA**: register the site (v2, "I'm not a robot" checkbox) for `auth.<domain>`.
5. **Media bucket** (example for AWS S3, region `ca-central-1`):
   - create a bucket with *Block all public access* on and default encryption;
   - CORS configuration, so browsers can upload and show photos directly:
     ```json
     [{ "AllowedOrigins": ["https://<domain>"], "AllowedMethods": ["GET", "PUT", "HEAD"],
        "AllowedHeaders": ["content-type"], "MaxAgeSeconds": 3600 }]
     ```
   - an IAM user for the app, with an access key and only this policy (`ListBucket` makes S3 answer 404
     rather than 403 for an upload that never arrived):
     ```json
     { "Version": "2012-10-17", "Statement": [
       { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::<bucket>/media/*" },
       { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::<bucket>" }] }
     ```
   - turn on versioning, so a deleted photo can be recovered, and a lifecycle rule that permanently deletes
     noncurrent versions after 30 days and removes expired delete markers (otherwise deleted files are kept,
     and billed, forever).
6. **Backup bucket**: a second bucket, with versioning and the same lifecycle rule (the key on the server then
   can't destroy existing backups: it has no `s3:DeleteObjectVersion`), and a second IAM user with this
   policy. Don't reuse the app's key.
   ```json
   { "Version": "2012-10-17", "Statement": [
     { "Effect": "Allow", "Action": ["s3:ListBucket", "s3:GetBucketLocation"],
       "Resource": "arn:aws:s3:::<backup-bucket>" },
     { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
       "Resource": "arn:aws:s3:::<backup-bucket>/*" }] }
   ```
7. **Configure**: clone the repository on the server, `cp .env.prod.example .env.prod`, fill in every value,
   `chmod 600 .env.prod`. Keep a copy of the whole file in a password manager: without `RESTIC_PASSWORD` the
   backups can't be read, and the dump restores the database roles with their passwords, so a rebuilt server
   needs the same values.
8. **Start**: build the images one at a time, so a small server doesn't run three builds at once, then start:
   ```sh
   source deploy/lib.sh   # from the repository root
   stack build keycloak && stack build backend && stack build frontend
   stack up -d
   ```
   then `stack ps` until everything is healthy. Caddy fetches the certificates on the first request.
9. **Replace the bootstrap admin** (see *Keycloak admin console* below): sign in with `KC_BOOTSTRAP_ADMIN_*`,
   create your own user in the `master` realm, give it the `admin` role, sign in as it and set up a one-time
   code (*Signing in* in the account menu), then delete the bootstrap user. The bootstrap variables are only
   used again if the master realm has no admin left.
10. **Staff accounts**: see *Employees* below.
11. **Backups**: schedule `deploy/backup.sh` (below), run it once by hand, and do a restore drill.

## Keycloak admin console

It is not reachable from the internet: Caddy answers 404 for `/admin` and `/realms/master` on
`auth.<domain>`. Use an SSH tunnel:

```sh
ssh -L 8180:localhost:8180 <user>@<server>
```

then open <http://localhost:8180/admin/> on your machine.

## Employees

**Onboarding**: the person registers on the site like a client (or an admin creates them in the `nio` realm
under *Users*). In the admin console, give the user the `employee` realm role (for an admin, `admin`). At their
next sign-in Keycloak makes them set up an authenticator app. Their staff profile appears on first use; an admin
can also pre-register them under *Admin → Employees*.

**Offboarding**: in the site's admin pages, deactivate the employee (they disappear from the public pages and
lose access to staff pages at once), and in Keycloak disable the user or remove the `employee` role.

## Backups and restore

`deploy/backup.sh` dumps both databases (app and Keycloak, roles included), encrypts the dump with restic and
uploads it to the backup bucket, keeping 30 daily and 12 monthly snapshots. Schedule it, e.g. with systemd:

```ini
# /etc/systemd/system/nio-backup.service
[Service]
Type=oneshot
User=<deploy user>
ExecStart=/opt/nio/deploy/backup.sh

# /etc/systemd/system/nio-backup.timer
[Timer]
OnCalendar=*-*-* 03:30:00
Persistent=true

[Install]
WantedBy=timers.target
```

`systemctl enable --now nio-backup.timer`; check runs with `journalctl -u nio-backup`.

To restore, list the snapshots and pick one:

```sh
source deploy/lib.sh && restic snapshots   # from the repository root
deploy/restore.sh <snapshot-id>      # or no argument for the latest
```

It asks for confirmation, stops the backend and Keycloak, replaces every database, and starts them again.
Photos aren't in the dump: they stay in the media bucket (versioned).

Do a restore drill before launch and then every few months, ideally into a scratch server.

## Updating

```sh
git pull
docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
```

On a 4 GB server, build one service at a time as in *First deployment*, and run `docker builder prune` now and
then to free disk space. Flyway migrates the database on backend start. Dependabot proposes dependency and
image updates weekly; CI (`.github/workflows/ci.yml`) runs the tests, `npm audit` and a Trivy scan of each
image. Accepted scan findings are listed in `.trivyignore.yaml`, each with an expiry date.

## Rotating secrets

- **Database passwords**: change them in Postgres first, then in `.env.prod`, then recreate the service
  (the init script only ran once, on the empty volume):
  ```sh
  docker compose -f compose.prod.yaml --env-file .env.prod exec postgres \
      psql -U postgres -c "ALTER ROLE nio_app PASSWORD '<new>'"
  docker compose -f compose.prod.yaml --env-file .env.prod up -d backend
  ```
  Same for `keycloak` (then recreate `keycloak`) and `postgres` itself.
- **SMTP and reCAPTCHA**: the values in `.env.prod` were copied into the realm on first start; change them in the
  admin console (*Realm settings → Email*; *Authentication → registration-nio → reCAPTCHA*).
- **S3 keys**: create the new key, update `.env.prod`, `up -d backend`, then delete the old key.
- **Keycloak signing keys**: *Realm settings → Keys → Providers*: add a new `rsa-generated` provider with a higher
  priority, wait a day (sessions roll over), then remove the old one.

## Monitoring

- An external uptime check (any free service) on `https://<domain>/` and
  `https://auth.<domain>/realms/nio/.well-known/openid-configuration`.
- Logs: `docker compose -f compose.prod.yaml --env-file .env.prod logs --since 24h <service>`. Every change made
  through the API is logged by the backend as `POST /api/... -> 201 by <user id> (<username>)`.
- Memory: `docker stats --no-stream` shows each service against its limit. A service that hits its limit is
  restarted (`docker inspect <container> --format '{{.State.OOMKilled}}'`); raise its `mem_limit` if that
  happens.
- Keycloak keeps sign-in and admin events for 90 days: *Events* in the admin console. Repeated `LOGIN_ERROR`
  for one account means someone is guessing its password (the lockout is already slowing them down).

## Rehearsing locally

The production stack can run on a workstation, with Mailpit and SeaweedFS standing in for the email provider
and the bucket, and Caddy using its own local certificate authority for `nio.localhost`:

```sh
docker compose -f compose.prod.yaml -f deploy/compose.staging.yaml --env-file deploy/staging.env up -d --build
```

Then open <https://nio.localhost> (accept the certificate warning); emails arrive at <http://localhost:8025>,
the admin console is at <http://localhost:8180/admin/> (`bootstrap-admin` / `staging-admin`). Remove it all with
`... down -v` (same flags).

## Privacy (Québec Law 25)

The salon collects names, emails, phone numbers and booking notes, which can hold health details (allergies,
skin conditions). Not legal advice: confirm against the guidance of the Commission d'accès à l'information.

- [ ] Publish a privacy policy (French and English), linked from the site footer and the registration page:
      what is collected and why, who can see it (the employee you book with, admins), how long it is kept, the
      service providers involved (hosting, email, reCAPTCHA by Google, storage), and how to make a request.
- [ ] Name the person responsible for the protection of personal information (by default the business owner)
      and publish their contact details on the site.
- [ ] Keep a register of confidentiality incidents and know the reporting steps for a serious one
      (notify the Commission and the people affected).
- [ ] Keep hosting, storage and backups in Canada, or assess the transfer first.
- [ ] Set a retention period for past bookings and apply it.
- [ ] Handle deletion requests: delete the user in Keycloak, then anonymize their app profile (bookings keep
      pointing at the row):
      ```sql
      UPDATE customer SET display_name = 'Deleted client', email = NULL, phone = NULL WHERE id = '<user id>';
      UPDATE booking SET notes = NULL WHERE customer_id = '<user id>';
      ```
