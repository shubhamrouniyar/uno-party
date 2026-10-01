import type { Session } from './types';

const KEY = 'uno-party-session-v1';

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    if (!parsed?.playerId || !parsed?.roomCode || !parsed?.displayName) return null;
    return {
      playerId: parsed.playerId,
      roomCode: String(parsed.roomCode).toUpperCase(),
      displayName: parsed.displayName,
      host: Boolean(parsed.host),
    };
  } catch {
    return null;
  }
}

export function saveSession(session: Session | null) {
  try {
    if (!session) {
      localStorage.removeItem(KEY);
      return;
    }
    localStorage.setItem(KEY, JSON.stringify({
      playerId: session.playerId,
      roomCode: session.roomCode.toUpperCase(),
      displayName: session.displayName,
      host: session.host,
    }));
  } catch {
    /* private mode / quota — ignore */
  }
}

export function clearSession() {
  saveSession(null);
}
