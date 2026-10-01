import { useMemo, useState } from 'react';
import type { Card, CardColor, ConnectionStatus, GameStateView, Session } from '../types';
import { UnoCard, colorHex } from './UnoCard';
import { ColorPicker } from './ColorPicker';
import { ConnectionBadge } from './ConnectionBadge';

interface Props {
  session: Session;
  state: GameStateView;
  connected: boolean;
  connectionStatus: ConnectionStatus;
  onPlay: (cardId: string, color?: CardColor) => void;
  onDraw: () => void;
  onPass: () => void;
  onCallUno: () => void;
  onChallengeUno: (targetId: string) => void;
  onRematch: () => void;
  onLeave: () => void;
}

function canPlayCard(card: Card, state: GameStateView): boolean {
  if (!state.yourTurn || state.status !== 'PLAYING') return false;
  if (state.mustDrawOrPlay) {
    return card.id === state.lastDrawnCardId;
  }
  const top = state.topCard;
  if (!top) return false;
  if (card.type === 'WILD' || card.type === 'WILD_DRAW_FOUR') return true;
  const active = state.activeColor || top.color;
  if (card.color === active) return true;
  if (card.type === 'NUMBER' && top.type === 'NUMBER' && card.number === top.number) return true;
  if (card.type !== 'NUMBER' && card.type === top.type) return true;
  return false;
}

export function GameBoard({
  session, state, connectionStatus, onPlay, onDraw, onPass, onCallUno, onChallengeUno, onRematch, onLeave,
}: Props) {
  const [pendingWild, setPendingWild] = useState<Card | null>(null);
  const [localToast, setLocalToast] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const you = state.players.find((p) => p.id === session.playerId);
  const isHost = you?.host ?? session.host;

  const playableIds = useMemo(() => {
    const set = new Set<string>();
    for (const c of state.yourHand || []) {
      if (canPlayCard(c, state)) set.add(c.id);
    }
    return set;
  }, [state]);

  const handleCardClick = (card: Card) => {
    if (!playableIds.has(card.id)) {
      setLocalToast('That card cannot be played');
      setTimeout(() => setLocalToast(null), 1500);
      return;
    }
    if (card.type === 'WILD' || card.type === 'WILD_DRAW_FOUR') {
      setPendingWild(card);
      return;
    }
    onPlay(card.id);
  };

  const challengable = state.players.filter(
    (p) => p.id !== session.playerId && p.handSize === 1 && !p.calledUno,
  );

  const finished = state.status === 'FINISHED';
  const paused = state.status === 'PAUSED';

  const copyCode = async () => {
    try {
      await navigator.clipboard?.writeText(state.roomCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch { /* ignore */ }
  };

  return (
    <div className="game-board">
      <header className="game-top">
        <div className="game-top-left">
          <button type="button" className="code-chip" onClick={() => { void copyCode(); }} title="Copy room code">
            {state.roomCode} {copied ? '✓' : '⧉'}
          </button>
          <ConnectionBadge status={connectionStatus} />
        </div>
        <div className="meta">
          <span
            className="color-chip"
            style={{ background: colorHex(state.activeColor) }}
            title="Active color"
          >
            {state.activeColor || '—'}
          </span>
          <span className="dir" title="Direction">{state.direction === 1 ? '↻' : '↺'}</span>
          <button type="button" className="btn ghost tiny" onClick={onLeave}>Leave</button>
        </div>
      </header>

      {paused && (
        <div className="pause-banner" role="status">
          {state.message || 'Game paused — waiting for players to reconnect'}
        </div>
      )}

      <section className="opponents" aria-label="Players">
        {state.players.map((p) => (
          <div
            key={p.id}
            className={[
              'opp',
              p.current ? 'current' : '',
              p.id === session.playerId ? 'self' : '',
              !p.connected ? 'offline' : '',
            ].filter(Boolean).join(' ')}
          >
            <div className="opp-name">
              {p.name}{p.host ? ' 👑' : ''}{p.calledUno ? ' UNO!' : ''}
              {!p.connected ? ' · offline' : ''}
            </div>
            <div className="opp-cards">
              {Array.from({ length: Math.min(p.handSize, 8) }).map((_, i) => (
                <UnoCard
                  key={i}
                  card={{ id: String(i), color: 'WILD', type: 'WILD', number: null }}
                  faceDown
                  small
                />
              ))}
              {p.handSize > 8 && <span className="more">+{p.handSize - 8}</span>}
            </div>
            <div className="opp-count">{p.handSize} card{p.handSize === 1 ? '' : 's'}</div>
          </div>
        ))}
      </section>

      <section className="table">
        <button
          type="button"
          className="pile draw-pile"
          disabled={!state.yourTurn || state.mustDrawOrPlay || state.status !== 'PLAYING'}
          onClick={onDraw}
        >
          <UnoCard
            card={{ id: 'deck', color: 'WILD', type: 'WILD', number: null }}
            faceDown
          />
          <span className="pile-label">Draw ({state.drawPileCount})</span>
        </button>
        <div className="pile discard-pile">
          {state.topCard ? <UnoCard card={state.topCard} /> : <div className="empty-pile">?</div>}
          <span className="pile-label">Discard</span>
        </div>
      </section>

      {state.yourTurn && state.status === 'PLAYING' && (
        <div className="turn-banner pulse">
          Your turn!{state.mustDrawOrPlay ? ' Play the drawn card or pass.' : ''}
        </div>
      )}
      {!state.yourTurn && state.status === 'PLAYING' && (
        <div className="turn-banner waiting-turn">
          Waiting for {state.players.find((p) => p.current)?.name || 'someone'}…
        </div>
      )}

      <section className="hand-area">
        <div className="hand">
          {(state.yourHand || []).map((c) => (
            <UnoCard
              key={c.id}
              card={c}
              playable={playableIds.has(c.id)}
              onClick={state.yourTurn && state.status === 'PLAYING' ? () => handleCardClick(c) : undefined}
            />
          ))}
        </div>
        <div className="hand-actions">
          {state.yourTurn && !state.mustDrawOrPlay && state.status === 'PLAYING' && (
            <button type="button" className="btn secondary" onClick={onDraw}>Draw</button>
          )}
          {state.mustDrawOrPlay && (
            <button type="button" className="btn secondary" onClick={onPass}>Pass</button>
          )}
          {(state.yourHand?.length === 1 || state.yourHand?.length === 2) && state.status === 'PLAYING' && (
            <button type="button" className="btn uno" onClick={onCallUno}>UNO!</button>
          )}
          {challengable.map((p) => (
            <button
              key={p.id}
              type="button"
              className="btn danger"
              onClick={() => onChallengeUno(p.id)}
            >
              Catch {p.name}
            </button>
          ))}
        </div>
      </section>

      <aside className="event-log" aria-live="polite">
        {(state.eventLog || []).slice(-8).map((e, i) => (
          <div key={`${i}-${e}`} className="event">{e}</div>
        ))}
      </aside>

      {localToast && <div className="toast">{localToast}</div>}

      {pendingWild && (
        <ColorPicker
          onPick={(color) => {
            onPlay(pendingWild.id, color);
            setPendingWild(null);
          }}
          onCancel={() => setPendingWild(null)}
        />
      )}

      {finished && (
        <div className="modal-backdrop">
          <div className="modal winner-modal">
            <h2>🎉 {state.winnerName || 'Someone'} wins!</h2>
            <p>Great game — rematch?</p>
            <div className="lobby-actions">
              {isHost && (
                <button type="button" className="btn primary" onClick={onRematch}>Rematch</button>
              )}
              {!isHost && <p className="waiting">Waiting for host to rematch…</p>}
              <button type="button" className="btn ghost" onClick={onLeave}>Leave</button>
            </div>
          </div>
        </div>
      )}

      {paused && state.players.length < 2 && (
        <div className="modal-backdrop">
          <div className="modal">
            <h2>Paused</h2>
            <p>{state.message || 'Need at least 2 players. Ask a friend to reconnect, or leave and start a new room.'}</p>
            <div className="lobby-actions">
              {isHost && state.players.length >= 2 && (
                <button type="button" className="btn primary" onClick={onRematch}>Restart game</button>
              )}
              <button type="button" className="btn ghost" onClick={onLeave}>Leave room</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
