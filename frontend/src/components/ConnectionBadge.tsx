import type { ConnectionStatus } from '../types';

const LABEL: Record<ConnectionStatus, string> = {
  idle: 'Offline',
  connecting: 'Connecting…',
  connected: 'Live',
  reconnecting: 'Reconnecting…',
  disconnected: 'Disconnected',
};

export function ConnectionBadge({ status }: { status: ConnectionStatus }) {
  return (
    <span className={`conn-badge ${status}`} title={LABEL[status]}>
      <span className="conn-dot" />
      {LABEL[status]}
    </span>
  );
}
