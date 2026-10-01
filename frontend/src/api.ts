import type { JoinResponse, GameStateView } from './types';

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '')
  || 'http://localhost:8080';

/** Railway free-tier cold starts can take 30–60s; browsers often hang without a clear error. */
const REQUEST_TIMEOUT_MS = 55_000;
const MAX_ATTEMPTS = 3;

export function getApiUrl() {
  return API_URL;
}

export function getWsUrl() {
  const env = (import.meta.env.VITE_WS_URL as string | undefined)?.replace(/\/$/, '');
  if (env) return env;
  if (API_URL.startsWith('https://')) return API_URL.replace(/^https/, 'wss') + '/ws';
  if (API_URL.startsWith('http://')) return API_URL.replace(/^http/, 'ws') + '/ws';
  return 'ws://localhost:8080/ws';
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

function isRetryableStatus(status: number) {
  return status === 408 || status === 425 || status === 429
    || status === 502 || status === 503 || status === 504;
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = res.statusText || `HTTP ${res.status}`;
    try {
      const body = await res.json();
      msg = body.message || body.error || msg;
    } catch {
      try {
        const text = await res.text();
        if (text) msg = text;
      } catch { /* ignore */ }
    }
    const err = new Error(msg) as Error & { status?: number };
    err.status = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

async function fetchJson<T>(
  path: string,
  init: RequestInit = {},
  opts?: { attempts?: number; timeoutMs?: number },
): Promise<T> {
  const attempts = opts?.attempts ?? MAX_ATTEMPTS;
  const timeoutMs = opts?.timeoutMs ?? REQUEST_TIMEOUT_MS;
  let lastError: unknown;

  for (let i = 0; i < attempts; i++) {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(`${API_URL}${path}`, {
        ...init,
        signal: controller.signal,
        cache: 'no-store',
      });
      if (!res.ok && isRetryableStatus(res.status) && i < attempts - 1) {
        await sleep(800 * 2 ** i);
        continue;
      }
      return await handle<T>(res);
    } catch (e) {
      lastError = e;
      const aborted = e instanceof DOMException && e.name === 'AbortError';
      const network = e instanceof TypeError;
      if ((aborted || network) && i < attempts - 1) {
        await sleep(800 * 2 ** i);
        continue;
      }
      if (aborted) {
        throw new Error('Server is waking up or slow — try again in a moment');
      }
      if (network) {
        throw new Error('Network error — check your connection or try again');
      }
      throw e;
    } finally {
      window.clearTimeout(timer);
    }
  }
  throw lastError instanceof Error ? lastError : new Error('Request failed');
}

/** Wake Railway / proxies before create or join. Best-effort; never throws. */
export async function wakeServer(): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/api/health/warm`, {
      method: 'GET',
      cache: 'no-store',
      signal: AbortSignal.timeout(45_000),
    });
    return res.ok;
  } catch {
    try {
      const res = await fetch(`${API_URL}/api/health`, {
        method: 'GET',
        cache: 'no-store',
        signal: AbortSignal.timeout(45_000),
      });
      return res.ok;
    } catch {
      return false;
    }
  }
}

export async function createRoom(displayName: string): Promise<JoinResponse> {
  await wakeServer();
  return fetchJson<JoinResponse>('/api/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  });
}

export async function joinRoom(code: string, displayName: string): Promise<JoinResponse> {
  const normalized = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  await wakeServer();
  return fetchJson<JoinResponse>(`/api/rooms/${encodeURIComponent(normalized)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  });
}

export async function rejoinRoom(code: string, playerId: string): Promise<JoinResponse> {
  const normalized = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  return fetchJson<JoinResponse>(`/api/rooms/${encodeURIComponent(normalized)}/rejoin`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId }),
  }, { attempts: 3, timeoutMs: REQUEST_TIMEOUT_MS });
}

export async function leaveRoom(code: string, playerId: string): Promise<GameStateView> {
  const normalized = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  return fetchJson<GameStateView>(`/api/rooms/${encodeURIComponent(normalized)}/leave`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId }),
  }, { attempts: 2, timeoutMs: 20_000 });
}

export async function fetchRoom(code: string, playerId?: string): Promise<GameStateView> {
  const normalized = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  const q = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return fetchJson<GameStateView>(
    `/api/rooms/${encodeURIComponent(normalized)}${q}`,
    { method: 'GET' },
    { attempts: 2, timeoutMs: 20_000 },
  );
}

export async function pingHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/api/health`, {
      method: 'GET',
      cache: 'no-store',
      signal: AbortSignal.timeout(15_000),
    });
    return res.ok;
  } catch {
    return false;
  }
}
