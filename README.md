# URL Shortener Demo

A proof of concept showing we can replace Bitly with our own shortener, using Spring Boot and MySQL. It's a standalone project and doesn't touch `workroo-api-revamp`.

## What it does

- **Shorten:** `POST /api/links` with `{"longUrl": "..."}` returns a short link like `http://localhost:8090/Ab3xK9q`.
- **Redirect:** opening the short link sends a 302 redirect to the long URL and counts the click.
- **Stats:** `GET /api/links/{code}` returns the click count and last click time. `GET /api/links` lists recent links.
- **Demo page:** http://localhost:8090 lets you shorten links and watch the click counts.
- **Any link:** anyone can shorten any `http`/`https` link. Links back to the shortener itself are refused, so nobody can create redirect loops.
- **Optional allow-list:** set `SHORT_LINK_ALLOWED_HOSTS=workroo.click,...` to only allow our own sites, as production should, so nobody can use it for spam or phishing links.

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
  -d '{"longUrl":"https://progress.workroo.click?code=Xy7Pq2Lm9Rt4Vb8Nc3Kd6Fh1Jw5Gs0Az&account=acme"}'

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
| `short.link.base-url` | `https://go.workroo.click` (a DNS record on the domain we already own) |

## Design notes

- **Codes** are 7 random letters and numbers, giving 62⁷ ≈ 3.5 trillion combinations. A unique key plus a retry handles the very unlikely collision.
- **Case-sensitive codes:** the `code` column uses `ascii_bin`, because MySQL's default collation would treat `Ab3xK9q` and `ab3xk9q` as the same code.
- **Click counting** is a single atomic `UPDATE ... SET click_count = click_count + 1`, so simultaneous clicks are never lost.
- **No caching:** redirects send `Cache-Control: no-store`, so every tap reaches the server and is counted.
