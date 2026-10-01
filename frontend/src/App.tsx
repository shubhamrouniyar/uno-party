import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import type { CardColor, GameStateView, Session } from './types';
import { createRoom, joinRoom, leaveRoom, pingHealth, rejoinRoom } from './api';
import { clearSession, loadSession, saveSession } from './session';
import { useGameSocket } from './useGameSocket';
import { Home } from './components/Home';
import { Lobby } from './components/Lobby';
import { GameBoard } from './components/GameBoard';
import './App.css';

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [state, setState] = useState<GameStateView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | null>(null);

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

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const saved = loadSession();
      if (!saved) {
        if (!cancelled) setRestoring(false);
        return;
      }
      try {
        const res = await rejoinRoom(saved.roomCode, saved.playerId);
        if (cancelled) return;
        applySession({
          playerId: res.playerId,
          roomCode: res.roomCode,
          displayName: res.displayName,
          host: res.host,
        }, res.gameState);
        showToast('Rejoined your seat');
      } catch (e) {
        clearSession();
        if (!cancelled) {
          setSession(null);
          setState(null);
          setError(e instanceof Error ? e.message : 'Could not restore session');
        }
      } finally {
        if (!cancelled) setRestoring(false);
      }
    })();
    return () => { cancelled = true; };
  }, [applySession, showToast]);

  useEffect(() => {
    if (!session) return;
    const id = window.setInterval(() => { void pingHealth(); }, 25_000);
    void pingHealth();
    return () => window.clearInterval(id);
  }, [session?.playerId, session?.roomCode]);

  const onState = useCallback((s: GameStateView) => {
    setState(s);
    if (s.error) {
      setError(s.error);
      showToast(s.error);
    }
    setSession((prev) => {
      if (!prev) return prev;
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

  const handleCreate = async (name: string) => {
    setBusy(true);
    setError(null);
    try {
      const res = await createRoom(name);
      applySession({
        playerId: res.playerId,
        roomCode: res.roomCode,
        displayName: res.displayName,
        host: res.host,
      }, res.gameState);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create room');
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = async (code: string, name: string) => {
    setBusy(true);
    setError(null);
    try {
      const res = await joinRoom(code, name);
      applySession({
        playerId: res.playerId,
        roomCode: res.roomCode,
        displayName: res.displayName,
        host: res.host,
      }, res.gameState);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to join room');
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    const current = session;
    if (current) {
      // Prefer reliable REST leave; WS may already be dropping
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
    return <Home busy={busy} error={error} onCreate={handleCreate} onJoin={handleJoin} />;
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
      onPlay={(cardId, color?: CardColor) => sendAction('PLAY', { cardId, chosenColor: color })}
      onDraw={() => sendAction('DRAW')}
      onPass={() => sendAction('PASS')}
      onCallUno={() => sendAction('CALL_UNO')}
      onChallengeUno={(targetPlayerId) => sendAction('CHALLENGE_UNO', { targetPlayerId })}
      onRematch={() => sendAction('REMATCH')}
      onLeave={() => { void leave(); }}
    />,
  );
}
