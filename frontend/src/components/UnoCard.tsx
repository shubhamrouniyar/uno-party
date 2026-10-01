import type { Card, CardColor } from '../types';

const COLOR_MAP: Record<CardColor, string> = {
  RED: '#e74c3c',
  YELLOW: '#f1c40f',
  GREEN: '#27ae60',
  BLUE: '#3498db',
  WILD: '#2c3e50',
};

function label(card: Card): string {
  switch (card.type) {
    case 'NUMBER':
      return String(card.number ?? '');
    case 'SKIP':
      return '⊘';
    case 'REVERSE':
      return '⇄';
    case 'DRAW_TWO':
      return '+2';
    case 'WILD':
      return 'W';
    case 'WILD_DRAW_FOUR':
      return '+4';
  }
}

function subLabel(card: Card): string {
  switch (card.type) {
    case 'SKIP':
      return 'SKIP';
    case 'REVERSE':
      return 'REV';
    case 'DRAW_TWO':
      return '+2';
    case 'WILD':
      return 'WILD';
    case 'WILD_DRAW_FOUR':
      return 'WILD+4';
    default:
      return '';
  }
}

interface Props {
  card: Card;
  selected?: boolean;
  playable?: boolean;
  faceDown?: boolean;
  small?: boolean;
  onClick?: () => void;
}

export function UnoCard({ card, selected, playable, faceDown, small, onClick }: Props) {
  if (faceDown) {
    return (
      <div className={`uno-card face-down ${small ? 'small' : ''}`} aria-hidden>
        <div className="card-back">UNO</div>
      </div>
    );
  }

  const bg = card.type.startsWith('WILD')
    ? 'linear-gradient(135deg, #e74c3c 0%, #f1c40f 33%, #27ae60 66%, #3498db 100%)'
    : COLOR_MAP[card.color];

  const classes = [
    'uno-card',
    small ? 'small' : '',
    selected ? 'selected' : '',
    playable ? 'playable' : '',
    onClick ? 'clickable' : '',
  ].filter(Boolean).join(' ');

  return (
    <button
      type="button"
      className={classes}
      style={{ background: bg }}
      onClick={onClick}
      disabled={!onClick}
      title={`${card.color} ${card.type}${card.number != null ? ' ' + card.number : ''}`}
    >
      <span className="card-corner top">{label(card)}</span>
      <span className="card-center">
        <span className="card-big">{label(card)}</span>
        {subLabel(card) && <span className="card-sub">{subLabel(card)}</span>}
      </span>
      <span className="card-corner bottom">{label(card)}</span>
    </button>
  );
}

export function colorHex(color: CardColor | null | undefined): string {
  if (!color) return '#95a5a6';
  return COLOR_MAP[color] || '#95a5a6';
}
