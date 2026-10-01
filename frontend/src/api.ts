import type { JoinResponse, GameStateView } from './types';

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '')
  || 'http://localhost:8080';

export function getApiUrl() {
  return API_URL;
}

export function getWsUrl() {
  const env = (import.meta.env.VITE_WS_URL as string | undefined)?.replace(/\/$/, '');
  if (env) return env;
  // Derive from API URL: http -> ws, https -> wss
  if (API_URL.startsWith('https://')) return API_URL.replace(/^https/, 'wss') + '/ws';
  if (API_URL.startsWith('http://')) return API_URL.replace(/^http/, 'ws') + '/ws';
  return 'ws://localhost:8080/ws';
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const body = await res.json();
      msg = body.message || body.error || msg;
    } catch {
      try {
        msg = await res.text() || msg;
      } catch { /* ignore */ }
    }
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}

export async function createRoom(displayName: string): Promise<JoinResponse> {
  const res = await fetch(`${API_URL}/api/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  });
  return handle(res);
}

export async function joinRoom(code: string, displayName: string): Promise<JoinResponse> {
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  });
  return handle(res);
}

export async function fetchRoom(code: string, playerId?: string): Promise<GameStateView> {
  const q = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(code)}${q}`);
  return handle(res);
}
