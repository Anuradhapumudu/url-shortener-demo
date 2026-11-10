# URL Shortener Demo

A proof of concept showing we can replace Bitly with our own shortener, using Spring Boot and MySQL. It's a standalone project

## What it does

- **Shorten:** `POST /api/links` with `{"longUrl": "..."}` returns a short link like `http://localhost:8090/Ab3xK9q`.
- **Redirect:** opening the short link sends a 302 redirect to the long URL and counts the click.
- **Stats:** `GET /api/links/{code}` returns the click count and last click time. `GET /api/links` lists recent links.
- **Demo page:** http://localhost:8090 lets you shorten links and watch the click counts.
- **Any link:** anyone can shorten any `http`/`https` link. Links back to the shortener itself are refused, so nobody can create redirect loops.
- **Optional allow-list:** set `SHORT_LINK_ALLOWED_HOSTS=` to only allow our own sites, as production should, so nobody can use it for spam or phishing links.

> **Demo security note:** the management API is intentionally unauthenticated for this proof of concept, and an empty
> `SHORT_LINK_ALLOWED_HOSTS` value permits any HTTP/HTTPS destination. Do not expose this configuration as a production service.

## Run it

1. Create a free MySQL database (e.g. an Aiven free MySQL plan).
2. Copy `.env.example` to `.env` and fill in the details:
   ```
   DB_URL=jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED
   DB_USERNAME=<username>
   DB_PASSWORD=<password>
   ```
3. Start it from this folder:
   ```
   mvn spring-boot:run
   ```
   The `short_link` table is created automatically on first start.
   For production, set `SHORT_LINK_ALLOWED_HOSTS` explicitly. Values are hostnames only; schemes, paths, ports, and
   credentials are rejected.
4. Open http://localhost:8090.

## Run it on the public domain

The app is served at **https://go.pumudu.indevs.in** through a Cloudflare Tunnel (named `url-shortener-demo`) from this Mac. No server or open ports are needed, and Cloudflare provides HTTPS.

```
./start-demo.sh
```

This builds the app, starts it, and opens the tunnel. Ctrl+C stops everything. Links only work while the script is running.

One-time setup (already done on this Mac):
```
brew install cloudflared
cloudflared tunnel login                     # pick pumudu.indevs.in
cloudflared tunnel create url-shortener-demo
cloudflared tunnel route dns url-shortener-demo go.pumudu.indevs.in
```

## Try it with curl

```bash
curl -s -X POST localhost:8090/api/links \
  -H 'Content-Type: application/json' \

curl -i localhost:8090/<code>            # 302 with Location: the long URL
curl -s localhost:8090/api/links/<code>  # clickCount: 1
```

## Tests

```
mvn test
```

The tests use an in-memory H2 database, so they don't need the `.env` file.

## How this maps to the real service

| Demo | In `workeroo-service` |
|---|---|
| `UrlShortenerService.shorten(longUrl)` | Replaces `shortenWithBitly()` in `JobServiceImpl` and the Bitly block in `JobQuoteServiceImpl` |
| `short_link` table | Goes in the `aws_master` database through a Liquibase changeset |
| `ShortLinkRepository` (JdbcTemplate) | Points at the master datasource, so links are saved in `aws_master` and not in the tenant schema of the logged-in user |
| `GET /{code}` | Becomes `GET /s/{code}`, added to `permitAll()` in `WebSecurityConfig` |
| `short.link.base-url` (a DNS record on the domain we already own) |

## Design notes

- **Codes** are 8 random characters from a 55-character alphabet (omitting 0/O, 1/l/I look-alikes), giving 55⁸ ≈ 83.7 trillion combinations. A unique key plus a retry handles collisions.
- **Case-sensitive codes:** the `code` column uses `ascii_bin`, because MySQL's default collation would treat `Ab3xK9q` and `ab3xk9q` as the same code.
- **Click counting** is a single atomic `UPDATE ... SET click_count = click_count + 1`, so simultaneous clicks are never lost.
- **Caching & Decoupling:** redirects send `Cache-Control: no-store` to the browser, while the server caches lookups in memory and processes click recording asynchronously using Java 21 Virtual Threads.
- **Click count type:** click totals use `BIGINT`, avoiding the 32-bit integer limit for popular links.

## Production readiness checklist

The demo has the redirect and persistence core, but the following work is required before production use:

- Protect all `/api/links` endpoints with authentication and tenant/owner authorization.
- Add rate limits and abuse controls for link creation, redirects, and administrative reads/deletes.
- Configure a non-empty allow-list and establish link takedown/reporting procedures.
- Decide whether links need an `expires_at` value tied to the job or quote lifecycle.
- Decide whether request counts are sufficient. Current counts include bots, link scanners, previews, and repeated requests;
  event-level analytics require a separate click-events design with an explicit privacy and retention policy.
- Add metrics and alerting for database failures, creation failures, redirect failures, 404s, and abuse.
- Run deployment tests behind the production proxy/tunnel, including HTTPS, forwarded headers, and database outages.
- Replace startup schema initialization with the production migration system (for example, Liquibase), and test MySQL
  collation behavior separately from H2.

## Production hardening (added after the production review)

- **Auth:** with `SHORT_LINK_API_KEY` set, `/api/**` requires an `X-API-Key` header. Only `GET /{code}` and `/actuator/health/**` are public.
- **`prod` profile** (`SPRING_PROFILES_ACTIVE=prod`, the Docker default) refuses to start if the allow-list is empty, the API key is under 32 characters, the code length is under 8, or the base URL is not https. It also turns the demo page off.
- **Lifecycle:** links accept `expiresInDays`, `tenantId`, `source`. Expired or deleted links return `410 Gone`. `DELETE` is a soft delete (`disabled_at`).
- **Resilience:** lookups are cached in memory (`short.link.link-cache-ttl-seconds`, default 30s, so a delete can take that long to reach other instances). A failed click write never blocks the redirect. A DB outage gives 503, not a stack trace. Known bot/preview user-agents are redirected but not counted.
- **Abuse:** per-client creation limit (`short.link.create-rate-limit-per-minute`, default 30) and a throttle on repeated unknown-code lookups (`short.link.miss-throttle-per-minute`, default 20, returns 429) to slow enumeration. Keep real limits at the edge too.
- **Guessing:** codes are 8 random characters (about 83.7 trillion combinations) with no look-alike characters. `short.link.default-expires-in-days` shrinks the pool of live links. Never rely on a short code as the only secret: tokens inside the target URL should expire or be single-use.
- **Visits vs probes:** `HEAD` requests, browser prefetches and known bot user-agents are redirected but not counted. Timestamps are stored in UTC.
- **Ops:** `/actuator/health` (liveness/readiness) is public. `/actuator/metrics` needs the API key. Metrics: `shortlink.created`, `shortlink.redirects{result}`, `shortlink.click.failures`, `shortlink.errors`.
- **Existing database:** run `db/migration/V2__link_lifecycle.sql` once before deploying. `CREATE TABLE IF NOT EXISTS` does not alter an existing table.
