import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import type { CardColor, GameStateView, Session } from './types';
import { createRoom, joinRoom, leaveRoom, pingHealth, rejoinRoom, wakeServer } from './api';
import { clearSession, loadSession, saveSession } from './session';
import { useGameSocket } from './useGameSocket';
import { Home } from './components/Home';
import { Lobby } from './components/Lobby';
import { GameBoard } from './components/GameBoard';
import './App.css';

function statusOf(e: unknown): number | undefined {
  return typeof e === 'object' && e !== null && 'status' in e
    ? Number((e as { status?: number }).status)
    : undefined;
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [state, setState] = useState<GameStateView | null>(null);
  const [busy, setBusy] = useState(false);
  const [busyHint, setBusyHint] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const [serverReady, setServerReady] = useState<boolean | null>(null);
  const [pendingPlayId, setPendingPlayId] = useState<string | null>(null);
  const toastTimer = useRef<number | null>(null);
  const leavingRef = useRef(false);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2800);
  }, []);

  const applySession = useCallback((next: Session, gameState: GameStateView) => {
    const normalized: Session = {
      ...next,
      roomCode: next.roomCode.toUpperCase(),
      host: gameState.players?.find((p) => p.id === next.playerId)?.host ?? next.host,
    };
    setSession(normalized);
    setState(gameState);
    saveSession(normalized);
  }, []);

  // Restore seat; only clear session on definitive 404 (seat gone), not on cold-start blips.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const saved = loadSession();
      if (!saved) {
        if (!cancelled) setRestoring(false);
        return;
      }
      let lastMsg = 'Could not restore session';
      for (let attempt = 0; attempt < 4; attempt++) {
        try {
          await wakeServer();
          const res = await rejoinRoom(saved.roomCode, saved.playerId);
          if (cancelled) return;
          applySession({
            playerId: res.playerId,
            roomCode: res.roomCode,
            displayName: res.displayName,
            host: res.host,
          }, res.gameState);
          showToast('Rejoined your seat');
          if (!cancelled) setRestoring(false);
          return;
        } catch (e) {
          const st = statusOf(e);
          lastMsg = e instanceof Error ? e.message : lastMsg;
          if (st === 404 || st === 400) {
            clearSession();
            if (!cancelled) {
              setSession(null);
              setState(null);
              setError(lastMsg);
            }
            break;
          }
          // transient — retry
        }
      }
      if (!cancelled) setRestoring(false);
    })();
    return () => { cancelled = true; };
  }, [applySession, showToast]);

  // Wake Railway on landing (cold start) + keep-alive while seated
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const ok = await wakeServer();
      if (!cancelled) setServerReady(ok);
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!session) return;
    const id = window.setInterval(() => { void pingHealth(); }, 20_000);
    void pingHealth();
    return () => window.clearInterval(id);
  }, [session?.playerId, session?.roomCode]);

  // If we were removed from the room (timeout), clear local seat — not on intentional Leave.
  useEffect(() => {
    if (!session || !state?.players || leavingRef.current) return;
    const stillSeated = state.players.some((p) => p.id === session.playerId);
    if (!stillSeated || state.message === 'Room closed' || state.message === 'Left room') {
      clearSession();
      setSession(null);
      setState(null);
      setError('Your seat expired or the room closed — create or join again');
    }
  }, [session, state]);

  const onState = useCallback((s: GameStateView) => {
    setPendingPlayId(null);
    setState(s);
    if (s.error) {
      setError(s.error);
      showToast(s.error);
    }
    setSession((prev) => {
      if (!prev) return prev;
      const stillSeated = s.players?.some((p) => p.id === prev.playerId);
      if (!stillSeated) return prev; // effect above clears
      const host = s.players?.find((p) => p.id === prev.playerId)?.host ?? prev.host;
      const next = { ...prev, host, roomCode: s.roomCode || prev.roomCode };
      saveSession(next);
      return next;
    });
  }, [showToast]);

  const onSocketError = useCallback((msg: string) => {
    setError(msg);
    showToast(msg);
  }, [showToast]);

  const { status, sendAction } = useGameSocket({
    session,
    onState,
    onError: onSocketError,
  });

  const withBusy = async (hint: string, fn: () => Promise<void>) => {
    setBusy(true);
    setBusyHint(hint);
    setError(null);
    const slow = window.setTimeout(() => {
      setBusyHint('Waking server — first load can take up to a minute…');
    }, 2500);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Request failed');
    } finally {
      window.clearTimeout(slow);
      setBusy(false);
      setBusyHint(null);
    }
  };

  const handleCreate = (name: string) => withBusy('Creating room…', async () => {
    const res = await createRoom(name);
    applySession({
      playerId: res.playerId,
      roomCode: res.roomCode,
      displayName: res.displayName,
      host: res.host,
    }, res.gameState);
    setServerReady(true);
  });

  const handleJoin = (code: string, name: string) => withBusy('Joining room…', async () => {
    const res = await joinRoom(code, name);
    applySession({
      playerId: res.playerId,
      roomCode: res.roomCode,
      displayName: res.displayName,
      host: res.host,
    }, res.gameState);
    setServerReady(true);
  });

  const leave = async () => {
    const current = session;
    leavingRef.current = true;
    if (current) {
      // Prefer reliable REST leave while session/WS still alive; keep Leave button intentional.
      try {
        await leaveRoom(current.roomCode, current.playerId);
      } catch {
        try { sendAction('LEAVE'); } catch { /* ignore */ }
      }
    }
    clearSession();
    setSession(null);
    setState(null);
    setError(null);
    leavingRef.current = false;
  };

  if (restoring) {
    return (
      <div className="home">
        <div className="hero-card">
          <div className="logo-row">
            <span className="logo-badge r">U</span>
            <span className="logo-badge y">N</span>
            <span className="logo-badge g">O</span>
            <span className="logo-badge b">!</span>
          </div>
          <h1>UNO Party</h1>
          <p className="tagline">Restoring your seat…</p>
        </div>
      </div>
    );
  }

  if (!session || !state) {
    return (
      <Home
        busy={busy}
        busyHint={busyHint}
        error={error}
        serverReady={serverReady}
        onCreate={handleCreate}
        onJoin={handleJoin}
      />
    );
  }

  const shell = (body: ReactNode) => (
    <div className="app-shell">
      {error && (
        <div className="banner-error" role="alert" onClick={() => setError(null)}>
          {error} <span className="dismiss">✕</span>
        </div>
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
      {body}
    </div>
  );

  if (state.status === 'LOBBY') {
    return shell(
      <Lobby
        session={session}
        state={state}
        connected={status === 'connected'}
        connectionStatus={status}
        onStart={() => sendAction('START')}
        onLeave={() => { void leave(); }}
      />,
    );
  }

  return shell(
    <GameBoard
      session={session}
      state={state}
      connected={status === 'connected'}
      connectionStatus={status}
      pendingPlayId={pendingPlayId}
      onPlay={(cardId, color?: CardColor) => {
        const yours = state.status === 'PLAYING' && (
          state.currentPlayerId
            ? state.currentPlayerId === session.playerId
            : !!state.yourTurn
        );
        // Optimistic only hides the card. Turn stays on the server currentPlayerId.
        const sent = sendAction('PLAY', { cardId, chosenColor: color });
        if (sent && yours) setPendingPlayId(cardId);
      }}
      onDraw={() => sendAction('DRAW')}
      onPass={() => sendAction('PASS')}
      onCallUno={() => sendAction('CALL_UNO')}
      onChallengeUno={(targetPlayerId) => sendAction('CHALLENGE_UNO', { targetPlayerId })}
      onRematch={() => sendAction('REMATCH')}
      onLeave={() => { void leave(); }}
    />,
  );
}
