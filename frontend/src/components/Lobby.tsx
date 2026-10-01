import { useState } from 'react';
import type { ConnectionStatus, GameStateView, Session } from '../types';
import { ConnectionBadge } from './ConnectionBadge';

interface Props {
  session: Session;
  state: GameStateView;
  connected: boolean;
  connectionStatus: ConnectionStatus;
  onStart: () => void;
  onLeave: () => void;
}

export function Lobby({ session, state, connectionStatus, onStart, onLeave }: Props) {
  const [copied, setCopied] = useState(false);
  const you = state.players.find((p) => p.id === session.playerId);
  const isHost = you?.host ?? session.host;
  const canStart = isHost && state.players.length >= 2 && state.players.length <= 6;

  const copyCode = async () => {
    try {
      await navigator.clipboard?.writeText(state.roomCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* ignore */
    }
  };

  return (
    <div className="lobby-panel">
      <div className="lobby-header">
        <ConnectionBadge status={connectionStatus} />
      </div>

      <div className="room-badge">
        <span className="label">Room code</span>
        <span className="code" aria-label={`Room code ${state.roomCode}`}>{state.roomCode}</span>
        <button type="button" className="btn ghost tiny" onClick={() => { void copyCode(); }}>
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>

      <p className="hint">
        Share the code with friends. Host starts when 2–6 players are ready.
      </p>

      <ul className="player-list">
        {state.players.map((p) => (
          <li key={p.id} className={p.id === session.playerId ? 'you' : ''}>
            <span className="avatar" style={{ background: avatarColor(p.name) }}>
              {p.name.charAt(0).toUpperCase()}
            </span>
            <span className="pname">
              {p.name}
              {p.host ? ' 👑' : ''}
              {p.id === session.playerId ? ' (you)' : ''}
            </span>
            <span className={`dot ${p.connected ? 'on' : 'off'}`} title={p.connected ? 'Online' : 'Offline'} />
          </li>
        ))}
      </ul>

      <div className="lobby-actions">
        {isHost ? (
          <button type="button" className="btn primary" disabled={!canStart} onClick={onStart}>
            {state.players.length < 2 ? 'Waiting for players…' : 'Start game'}
          </button>
        ) : (
          <p className="waiting">Waiting for host to start…</p>
        )}
        <button type="button" className="btn ghost" onClick={onLeave}>Leave room</button>
      </div>
    </div>
  );
}

function avatarColor(name: string): string {
  const colors = ['#e74c3c', '#3498db', '#27ae60', '#f1c40f', '#9b59b6', '#e67e22'];
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h + name.charCodeAt(i) * 17) % colors.length;
  return colors[h];
}
