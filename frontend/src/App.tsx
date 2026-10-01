import { useCallback, useState } from 'react';
import type { CardColor, GameStateView, Session } from './types';
import { createRoom, joinRoom } from './api';
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

  const onState = useCallback((s: GameStateView) => {
    setState(s);
    if (s.error) setError(s.error);
  }, []);

  const { connected, sendAction } = useGameSocket({
    session,
    onState,
    onError: setError,
  });

  const handleCreate = async (name: string) => {
    setBusy(true);
    setError(null);
    try {
      const res = await createRoom(name);
      setSession({
        playerId: res.playerId,
        roomCode: res.roomCode,
        displayName: res.displayName,
        host: res.host,
      });
      setState(res.gameState);
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
      setSession({
        playerId: res.playerId,
        roomCode: res.roomCode,
        displayName: res.displayName,
        host: res.host,
      });
      setState(res.gameState);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to join room');
    } finally {
      setBusy(false);
    }
  };

  const leave = () => {
    if (session) sendAction('LEAVE');
    setSession(null);
    setState(null);
    setError(null);
  };

  if (!session || !state) {
    return <Home busy={busy} error={error} onCreate={handleCreate} onJoin={handleJoin} />;
  }

  if (state.status === 'LOBBY') {
    return (
      <div className="app-shell">
        {error && <div className="banner-error" onClick={() => setError(null)}>{error}</div>}
        <Lobby
          session={session}
          state={state}
          connected={connected}
          onStart={() => sendAction('START')}
          onLeave={leave}
        />
      </div>
    );
  }

  return (
    <div className="app-shell">
      {error && <div className="banner-error" onClick={() => setError(null)}>{error}</div>}
      <GameBoard
        session={session}
        state={state}
        connected={connected}
        onPlay={(cardId, color?: CardColor) => sendAction('PLAY', { cardId, chosenColor: color })}
        onDraw={() => sendAction('DRAW')}
        onPass={() => sendAction('PASS')}
        onCallUno={() => sendAction('CALL_UNO')}
        onChallengeUno={(targetPlayerId) => sendAction('CHALLENGE_UNO', { targetPlayerId })}
        onRematch={() => sendAction('REMATCH')}
        onLeave={leave}
      />
    </div>
  );
}
