import type { CardColor } from '../types';
import { colorHex } from './UnoCard';

const COLORS: CardColor[] = ['RED', 'YELLOW', 'GREEN', 'BLUE'];

interface Props {
  onPick: (c: CardColor) => void;
  onCancel: () => void;
}

export function ColorPicker({ onPick, onCancel }: Props) {
  return (
    <div className="modal-backdrop">
      <div className="modal color-picker">
        <h3>Pick a color</h3>
        <div className="color-grid">
          {COLORS.map((c) => (
            <button
              key={c}
              type="button"
              className="color-swatch"
              style={{ background: colorHex(c) }}
              onClick={() => onPick(c)}
              aria-label={c}
            >
              {c}
            </button>
          ))}
        </div>
        <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
