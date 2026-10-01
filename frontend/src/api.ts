import type { JoinResponse, GameStateView } from './types';

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '')
  || 'http://localhost:8080';

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
  const normalized = code.trim().toUpperCase();
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(normalized)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  });
  return handle(res);
}

export async function rejoinRoom(code: string, playerId: string): Promise<JoinResponse> {
  const normalized = code.trim().toUpperCase();
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(normalized)}/rejoin`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId }),
  });
  return handle(res);
}

export async function leaveRoom(code: string, playerId: string): Promise<GameStateView> {
  const normalized = code.trim().toUpperCase();
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(normalized)}/leave`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId }),
  });
  return handle(res);
}

export async function fetchRoom(code: string, playerId?: string): Promise<GameStateView> {
  const normalized = code.trim().toUpperCase();
  const q = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  const res = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(normalized)}${q}`);
  return handle(res);
}

export async function pingHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/api/health`, { method: 'GET', cache: 'no-store' });
    return res.ok;
  } catch {
    return false;
  }
}
