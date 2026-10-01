# UNO Party

Fun **online multiplayer UNO-like** card game — no login. Create or join a room with a short code and a display name, then play in real time.

**Stack:** Java Spring Boot 3 (WebSocket/STOMP + REST) · React + Vite + TypeScript

## How to play

1. One player **creates a room** and shares the 6-character code.
2. Friends **join** with the code + a display name (2–6 players).
3. Host hits **Start** when ready.
4. Match the discard pile by **color** or **number/symbol**. Wilds let you pick a color.
5. **Skip**, **Reverse**, **Draw Two**, **Wild**, and **Wild Draw Four** work like classic UNO.
6. When you have 1 card left, hit **UNO!** — if you forget, others can **Catch** you for +2 cards.
7. First to empty their hand wins. Host can start a **rematch**.

## Resilience

- Session persisted in `localStorage` (room code + playerId + name); refresh auto-rejoins the same seat.
- STOMP reconnect with exponential backoff + 10s heartbeats (server + client).
- On disconnect: seat kept ~60s (marked offline); then removed and the game continues (or **pauses** if &lt;2 players).
- Mid-game leave: turn skipped cleanly, cards returned to the deck, host transferred if needed.
- Join errors are explicit (full / started / bad code / duplicate name). Late join is **lobby-only**.
- HTTP `/api/health` ping from the SPA keeps idle proxies happier.

## Structure

```
.
├── backend/          Spring Boot 3.x (JDK 21), Maven, STOMP WebSocket
│   ├── Dockerfile    Railway-ready (honors PORT)
│   └── src/...
├── frontend/         React + Vite + TypeScript
│   ├── netlify.toml  SPA redirects
│   └── src/...
└── README.md
```

## Architecture

### REST

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health / keep-alive |
| POST | `/api/rooms` | Create room `{ displayName }` → playerId + roomCode |
| POST | `/api/rooms/{code}/join` | Join `{ displayName }` (lobby only) |
| POST | `/api/rooms/{code}/rejoin` | Rejoin seat `{ playerId }` |
| POST | `/api/rooms/{code}/leave` | Leave `{ playerId }` (reliable vs WS-only) |
| GET | `/api/rooms/{code}?playerId=` | Snapshot of room / hand |

### WebSocket (STOMP)

- Endpoint: `/ws` (native WebSocket; SockJS also available)
- Subscribe: `/topic/room/{code}/player/{playerId}` — personalized state (includes your hand)
- Send actions: `/app/room/{code}/action` with body:
  - `{ playerId, type: "START"|"PLAY"|"DRAW"|"PASS"|"CALL_UNO"|"CHALLENGE_UNO"|"REMATCH"|"LEAVE", cardId?, chosenColor?, targetPlayerId? }`
- Sync on connect: `/app/room/{code}/sync`

### Room lifecycle

- In-memory game state (no database).
- Disconnect grace ~60s, then seat removed.
- Idle rooms purged after ~45 minutes of inactivity.
- CORS allows `localhost` / `127.0.0.1` and `*.netlify.app`.

## Prerequisites

- JDK 21 + Maven 3.9+
- Node.js 20+ and npm

## Run locally

### Backend

```bash
cd backend
mvn spring-boot:run
```

API / WS: http://localhost:8080 · ws://localhost:8080/ws

### Frontend

```bash
cd frontend
npm install
cp .env.example .env   # optional
npm run dev
```

Env vars:

- `VITE_API_URL` — REST base (default `http://localhost:8080`)
- `VITE_WS_URL` — STOMP broker URL (default derived as `ws(s)://…/ws`)

## Deploy notes (do not deploy until approved)

### Backend → Railway

1. Create a Railway service from `backend/` (Dockerfile).
2. Railway sets `PORT` automatically; the app and Dockerfile honor it.
3. Public HTTPS URL becomes your API / WS host (`wss://…/ws`).

### Frontend → Netlify

1. Base directory: `frontend`
2. Build uses `netlify.toml` (`npm run build` → `dist`, SPA redirect).
3. Set `VITE_API_URL` to the Railway HTTPS URL and `VITE_WS_URL` to `wss://<railway-host>/ws`.

> Deploy is **intentionally not done** until explicit approval.

## License

Sample / party project — use freely.
