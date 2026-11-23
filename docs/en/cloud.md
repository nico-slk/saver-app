# Preparing Saver for the cloud without choosing a provider

Saver already has building blocks that make provider comparisons possible without redesigning the domain: the backend is packaged as a Java container, configuration comes from environment variables, Flyway versions the schema, and object storage is abstracted behind `StorageService`. The API uses PostgreSQL. The frontend is an SPA that can be served as static files.

## Portable target architecture

| Responsibility | Saver requirement | Comparison criteria |
|---|---|---|
| Frontend | Static files, SPA, route fallback | Static hosting, HTTPS, domain, and `/api` rewrite or secure CORS |
| API | Java 21 container with no persistent local state | Container runtime, scale-to-zero or low minimum, logs, and health checks |
| Database | Persistent PostgreSQL | Managed PostgreSQL, backups, restore, and TLS connectivity from the API |
| Receipts | Private object storage | S3-compatible API or `StorageService` adapter, signed URLs, and lifecycle policy |
| Secrets | DB, JWT, storage, and email credentials | Secret manager or secret variables, never embedded in the frontend |
| Scheduled jobs | Inflation sync and future notifications | External scheduler or distributed locking when multiple instances run |

## Cost criteria

Do not compare only the advertised server price. For a small app, review:

1. Whether API and frontend can scale to zero or have a mandatory monthly minimum.
2. PostgreSQL base cost, connection limits, backups, and retention. The database is often the largest persistent cost.
3. Free quotas and overage prices for storage, data egress, logs, builds, and requests.
4. Region proximity to users and the database to limit latency and cross-region transfer.
5. Whether PostgreSQL and objects can be exported without proprietary formats.
6. Available budget alerts, spending limits, and automatic backups.

The lowest-cost option depends on actual usage, region, traffic, and backup policy. Compare a minimal architecture using consistent assumptions and check current pricing in official calculators before deciding.

## Technical readiness and open decisions

- Keep one backend artifact (Dockerfile) and configure ports, JDBC, email, and storage only through environment/secrets.
- Keep the API stateless: receipts must not depend on a container's ephemeral disk.
- Use standard PostgreSQL and additive Flyway migrations; validate export and restore with the chosen provider.
- Prefer frontend hosting with a CDN and `/api` rewrite where available. If frontend and API use separate domains, configure CORS and cookie `SameSite`/`Secure` policy.
- Add push and email delivery adapters before enabling notifications. Web push requires a service worker, VAPID keys, per-device subscriptions, expiration, and unsubscribe; email requires a provider, quotas, bounce handling, and templates.
- Run scheduled work through a managed scheduler or distributed lock. Do not assume `@Scheduled` runs only once when scaling horizontally.
- Define log retention, spending limits, alerts, secret rotation, and tested restore procedures before real use.

## Reproducible evaluation

For each option, record region, frontend, container runtime, PostgreSQL, storage, scheduler, backups, minimum monthly cost, cost at 1,000 and 10,000 monthly active users, egress cost, and export/exit path. Shared usage assumptions prevent comparing incompatible plans.

[`deployment.md`](../en/deployment.md) documents the existing GCP path; it is a reference option, not a final decision.
