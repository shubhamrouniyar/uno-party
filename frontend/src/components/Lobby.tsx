import type { GameStateView, Session } from '../types';

interface Props {
  session: Session;
  state: GameStateView;
  connected: boolean;
  onStart: () => void;
  onLeave: () => void;
}

export function Lobby({ session, state, connected, onStart, onLeave }: Props) {
  const canStart = session.host && state.players.length >= 2;

  return (
    <div className="lobby-panel">
      <div className="room-badge">
        <span className="label">Room code</span>
        <span className="code">{state.roomCode}</span>
        <button
          type="button"
          className="btn ghost tiny"
          onClick={() => navigator.clipboard?.writeText(state.roomCode)}
        >
          Copy
        </button>
      </div>

      <p className="hint">
        Share the code with friends. Host can start when 2–6 players have joined.
        {connected ? ' ● Live' : ' ○ Connecting…'}
      </p>

      <ul className="player-list">
        {state.players.map((p) => (
          <li key={p.id} className={p.id === session.playerId ? 'you' : ''}>
            <span className="avatar">{p.name.charAt(0).toUpperCase()}</span>
            <span className="pname">
              {p.name}
              {p.host ? ' 👑' : ''}
              {p.id === session.playerId ? ' (you)' : ''}
            </span>
            <span className={`dot ${p.connected ? 'on' : 'off'}`} />
          </li>
        ))}
      </ul>

      <div className="lobby-actions">
        {session.host ? (
          <button type="button" className="btn primary" disabled={!canStart} onClick={onStart}>
            {state.players.length < 2 ? 'Waiting for players…' : 'Start game'}
          </button>
        ) : (
          <p className="waiting">Waiting for host to start…</p>
        )}
        <button type="button" className="btn ghost" onClick={onLeave}>Leave</button>
      </div>
    </div>
  );
}
