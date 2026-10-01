import { useMemo, useState } from 'react';
import type { Card, CardColor, GameStateView, Session } from '../types';
import { UnoCard, colorHex } from './UnoCard';
import { ColorPicker } from './ColorPicker';

interface Props {
  session: Session;
  state: GameStateView;
  connected: boolean;
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
  session, state, connected, onPlay, onDraw, onPass, onCallUno, onChallengeUno, onRematch, onLeave,
}: Props) {
  const [pendingWild, setPendingWild] = useState<Card | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const playableIds = useMemo(() => {
    const set = new Set<string>();
    for (const c of state.yourHand || []) {
      if (canPlayCard(c, state)) set.add(c.id);
    }
    return set;
  }, [state]);

  const handleCardClick = (card: Card) => {
    if (!playableIds.has(card.id)) {
      setToast('That card cannot be played');
      setTimeout(() => setToast(null), 1500);
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

  return (
    <div className="game-board">
      <header className="game-top">
        <div>
          <span className="code-chip">{state.roomCode}</span>
          <span className={`live ${connected ? 'on' : ''}`}>{connected ? 'Live' : '…'}</span>
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

      <section className="opponents">
        {state.players.map((p) => (
          <div
            key={p.id}
            className={`opp ${p.current ? 'current' : ''} ${p.id === session.playerId ? 'self' : ''}`}
          >
            <div className="opp-name">
              {p.name}{p.host ? ' 👑' : ''}{p.calledUno ? ' UNO!' : ''}
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
        <div className="pile draw-pile" onClick={state.yourTurn && !state.mustDrawOrPlay ? onDraw : undefined}>
          <UnoCard
            card={{ id: 'deck', color: 'WILD', type: 'WILD', number: null }}
            faceDown
          />
          <span className="pile-label">Draw ({state.drawPileCount})</span>
        </div>
        <div className="pile discard-pile">
          {state.topCard ? <UnoCard card={state.topCard} /> : <div className="empty-pile">?</div>}
          <span className="pile-label">Discard</span>
        </div>
      </section>

      {state.yourTurn && (
        <div className="turn-banner">Your turn!{state.mustDrawOrPlay ? ' Play the drawn card or pass.' : ''}</div>
      )}

      <section className="hand-area">
        <div className="hand">
          {(state.yourHand || []).map((c) => (
            <UnoCard
              key={c.id}
              card={c}
              playable={playableIds.has(c.id)}
              onClick={state.yourTurn ? () => handleCardClick(c) : undefined}
            />
          ))}
        </div>
        <div className="hand-actions">
          {state.yourTurn && !state.mustDrawOrPlay && (
            <button type="button" className="btn secondary" onClick={onDraw}>Draw</button>
          )}
          {state.mustDrawOrPlay && (
            <button type="button" className="btn secondary" onClick={onPass}>Pass</button>
          )}
          {(state.yourHand?.length === 1 || state.yourHand?.length === 2) && (
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

      <aside className="event-log">
        {(state.eventLog || []).slice(-8).map((e, i) => (
          <div key={i} className="event">{e}</div>
        ))}
      </aside>

      {toast && <div className="toast">{toast}</div>}

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
              {session.host && (
                <button type="button" className="btn primary" onClick={onRematch}>Rematch</button>
              )}
              {!session.host && <p className="waiting">Waiting for host to rematch…</p>}
              <button type="button" className="btn ghost" onClick={onLeave}>Leave</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
