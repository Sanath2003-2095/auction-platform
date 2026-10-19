# Gavel - Real-Time Auction Platform

End-to-end auction system: **Spring Boot** REST API + **WebSocket (STOMP)** live updates + **React** UI.

- Auction creation, bidding, user management, JWT authentication
- **Concurrency-safe bidding** with optimistic locking (`@Version`) and automatic retry
- Layered architecture (controller -> service -> repository), global exception handling
- Query optimisation: composite indexes, entity graphs (no N+1), in-memory Caffeine cache for stats
- Live UI: prices flash as bids arrive, countdowns, price chart, outbid/won notifications, anti-sniping

## Run it locally (no Docker, no MySQL needed)

Requirements: Java 21, Maven, Node 20+.

```bash
# terminal 1 - API on :8080 (in-memory H2 in MySQL mode, demo data seeded)
cd backend
mvn spring-boot:run

# terminal 2 - UI on :5174 (proxies /api and /ws to :8080)
cd frontend
npm install
npm run dev
```

Open http://localhost:5174 and click **Continue as demo user**.

| Account | Password | Notes |
|---|---|---|
| `demo` | `demo1234` | normal user |
| `admin` | `admin12345` (or `ADMIN_PASSWORD`) | user management page |
| `alice`, `bob`, `carol`, `dave` | `password123` | demo bots that keep bidding |

Bots and seed data are for demos. Turn them off in production with
`APP_DEMO_BOTS_ENABLED=false` and `APP_SEED_ENABLED=false`.

## Run with Docker (MySQL + API + nginx UI)

```bash
docker compose up --build
```

UI: http://localhost:3000, API: http://localhost:8080.

## Tests

```bash
cd backend && mvn test
```

- `BidProcessorTest` - bid rules (increment, own auction, ended, self-outbid, anti-sniping)
- `ApiFlowTest` - register/login, 401/403, full auction lifecycle through HTTP
- `BidConcurrencyTest` - 40 threads bid on one auction simultaneously; asserts no lost update,
  bid count equals history, price equals highest bid, and prices strictly increase by the increment

## How concurrent bidding works

1. `Auction` has a `@Version` column. Every bid reads the auction, validates, updates price/leader/count and flushes.
2. If two transactions race, the second `UPDATE ... WHERE version = ?` matches 0 rows and Hibernate throws an optimistic-lock failure.
3. `BidService` catches it and retries in a **new transaction** (small random back-off, up to 12 attempts). The loser re-validates against the fresh price, so it is either accepted or rejected with "Bid must be at least X". After the attempts run out the API returns `409`.
4. Only after commit is the event pushed to `/topic/auctions/{id}` and `/topic/auctions`.

## API summary

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/auth/me` | user |
| GET | `/api/auctions?status=&q=&category=&sort=&page=&size=` | public |
| GET | `/api/auctions/{id}`, `/api/auctions/{id}/bids`, `/api/auctions/categories`, `/api/stats` | public |
| POST | `/api/auctions` | user |
| DELETE | `/api/auctions/{id}` (cancel, seller/admin) | user |
| POST | `/api/auctions/{id}/bids` | user |
| GET | `/api/users/me/auctions`, `/api/users/me/bids` | user |
| GET/PATCH | `/api/admin/users`, `/api/admin/users/{id}/enabled?value=` | admin |
| WS | `/ws` (STOMP) topics `/topic/auctions`, `/topic/auctions/{id}` | public |

## Deploying

You need: a MySQL database, the API, and the static frontend.

### Option A - Render (API + static site) + free hosted MySQL

1. Create a MySQL database (e.g. Aiven, Railway, or TiDB Cloud free tier). Note host, port, db name, user, password.
2. Push this repo to GitHub.
3. In Render: **New > Blueprint**, select the repo (it reads `render.yaml`). Fill in:
   - `DB_URL` = `jdbc:mysql://HOST:PORT/DBNAME?useSSL=true&serverTimezone=UTC`, `DB_USERNAME`, `DB_PASSWORD`
   - `ADMIN_PASSWORD` = a strong password
   - `CORS_ALLOWED_ORIGINS` = the frontend URL (set after the static site exists)
   - `VITE_API_URL` (on the static site) = the API URL, e.g. `https://auction-api.onrender.com`
4. Redeploy both once the URLs are known. Free Render services sleep when idle, so the first request is slow.

### Option B - Vercel frontend + any Docker host for the API

- Frontend: import `frontend/` into Vercel, set `VITE_API_URL` to the API URL (`vercel.json` handles SPA routing).
- API: build `backend/Dockerfile` on Fly.io, Railway, Google Cloud Run, AWS App Runner, etc. Cloud Run needs
  min-instances >= 1 for WebSockets to stay warm.

### Environment variables (API)

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` = MySQL | (H2 if unset) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | - |
| `JWT_SECRET` | HMAC key, **>= 32 chars, keep secret** | dev value |
| `JWT_EXPIRATION_MINUTES` | token lifetime | 1440 |
| `CORS_ALLOWED_ORIGINS` | comma-separated frontend origins (also used for WebSocket) | localhost |
| `ADMIN_PASSWORD` | password of the seeded `admin` user | `admin12345` |
| `APP_SEED_ENABLED`, `APP_DEMO_BOTS_ENABLED` | demo data / bots | true |

> Before going public: set a real `JWT_SECRET` and `ADMIN_PASSWORD`, and decide whether to keep the demo bots and seeded accounts.

## Project layout

```
backend/   Spring Boot 3 (Java 21): config, security, domain, repository, service, web, demo
frontend/  React 18 + Vite: pages, live.js (STOMP), api.js, styles.css
docker-compose.yml, render.yaml, .github/workflows/ci.yml
```

## Measuring the "25% faster retrieval" claim

Indexes (`idx_auction_status_end`, `idx_bid_auction_amount`), entity graphs and the stats cache are in place.
To quote a number honestly, benchmark before/after on a seeded MySQL (e.g. 100k auctions) with
`k6` or JMeter against `GET /api/auctions?status=ACTIVE&sort=ending`, once with and once without the indexes.
