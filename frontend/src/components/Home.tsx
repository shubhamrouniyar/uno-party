import { useState } from 'react';

interface Props {
  busy: boolean;
  busyHint: string | null;
  error: string | null;
  serverReady: boolean | null;
  onCreate: (name: string) => void;
  onJoin: (code: string, name: string) => void;
}

export function Home({ busy, busyHint, error, serverReady, onCreate, onJoin }: Props) {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [mode, setMode] = useState<'menu' | 'create' | 'join'>('menu');

  const trimmed = name.trim();
  const codeOk = /^[A-Z0-9]{6}$/.test(code.trim().toUpperCase());

  return (
    <div className="home">
      <div className="hero-card">
        <div className="logo-row" aria-hidden>
          <span className="logo-badge r">U</span>
          <span className="logo-badge y">N</span>
          <span className="logo-badge g">O</span>
          <span className="logo-badge b">!</span>
        </div>
        <h1>UNO Party</h1>
        <p className="tagline">Online multiplayer · no login · just a room code</p>

        {serverReady === false && (
          <p className="server-hint warn" role="status">
            Server may be waking up — create/join can take up to a minute the first time.
          </p>
        )}
        {serverReady === true && mode === 'menu' && (
          <p className="server-hint ok" role="status">Server ready</p>
        )}

        {mode === 'menu' && (
          <div className="menu-actions">
            <button type="button" className="btn primary big" onClick={() => setMode('create')}>
              Create room
            </button>
            <button type="button" className="btn secondary big" onClick={() => setMode('join')}>
              Join with code
            </button>
          </div>
        )}

        {mode === 'create' && (
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              if (trimmed && !busy) onCreate(trimmed);
            }}
          >
            <label>
              Display name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={20}
                placeholder="Your name"
                autoFocus
                autoComplete="nickname"
                required
                disabled={busy}
              />
            </label>
            <div className="form-row">
              <button type="button" className="btn ghost" onClick={() => setMode('menu')} disabled={busy}>Back</button>
              <button type="submit" className="btn primary" disabled={busy || !trimmed}>
                {busy ? (busyHint || 'Creating…') : 'Create'}
              </button>
            </div>
          </form>
        )}

        {mode === 'join' && (
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              if (trimmed && codeOk && !busy) onJoin(code.trim().toUpperCase(), trimmed);
            }}
          >
            <label>
              Display name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={20}
                placeholder="Your name"
                autoFocus
                autoComplete="nickname"
                required
                disabled={busy}
              />
            </label>
            <label>
              Room code
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6))}
                maxLength={6}
                placeholder="ABC123"
                inputMode="text"
                autoCapitalize="characters"
                spellCheck={false}
                required
                disabled={busy}
              />
            </label>
            <div className="form-row">
              <button type="button" className="btn ghost" onClick={() => setMode('menu')} disabled={busy}>Back</button>
              <button type="submit" className="btn primary" disabled={busy || !trimmed || !codeOk}>
                {busy ? (busyHint || 'Joining…') : 'Join'}
              </button>
            </div>
          </form>
        )}

        {busy && busyHint && <p className="busy-hint" role="status">{busyHint}</p>}
        {error && <p className="error-msg" role="alert">{error}</p>}

        <ul className="rules-mini">
          <li>2–6 players · seats survive refresh / blips for ~3 min</li>
          <li>Match color or number · Wilds change color</li>
          <li>Call UNO at 1 card — or get caught +2!</li>
        </ul>
      </div>
    </div>
  );
}
