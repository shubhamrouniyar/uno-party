import { useCallback, useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import type { CardColor, ConnectionStatus, GameStateView, Session } from './types';
import { getWsUrl } from './api';

interface Options {
  session: Session | null;
  onState: (state: GameStateView) => void;
  onError?: (msg: string) => void;
}

export function useGameSocket({ session, onState, onError }: Options) {
  const clientRef = useRef<Client | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>('idle');
  const onStateRef = useRef(onState);
  const onErrorRef = useRef(onError);
  const attemptRef = useRef(0);
  const intentionalClose = useRef(false);

  useEffect(() => {
    onStateRef.current = onState;
    onErrorRef.current = onError;
  }, [onState, onError]);

  useEffect(() => {
    if (!session) {
      intentionalClose.current = true;
      clientRef.current?.deactivate();
      clientRef.current = null;
      setStatus('idle');
      attemptRef.current = 0;
      return;
    }

    intentionalClose.current = false;
    const code = session.roomCode.toUpperCase();
    setStatus('connecting');

    const client = new Client({
      brokerURL: getWsUrl(),
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 20_000,
      onConnect: () => {
        attemptRef.current = 0;
        client.reconnectDelay = 2000;
        setStatus('connected');
        client.subscribe(`/topic/room/${code}/player/${session.playerId}`, (msg: IMessage) => {
          try {
            const state = JSON.parse(msg.body) as GameStateView;
            if (state.error) onErrorRef.current?.(state.error);
            onStateRef.current(state);
          } catch {
            onErrorRef.current?.('Bad game update from server');
          }
        });
        client.publish({
          destination: `/app/room/${code}/sync`,
          body: JSON.stringify({ playerId: session.playerId, type: 'SYNC' }),
        });
      },
      onDisconnect: () => {
        if (intentionalClose.current) {
          setStatus('idle');
          return;
        }
        setStatus((s) => (s === 'idle' ? 'idle' : 'disconnected'));
      },
      onStompError: (frame) => {
        onErrorRef.current?.(frame.headers['message'] || 'WebSocket error');
      },
      onWebSocketClose: () => {
        if (intentionalClose.current) return;
        attemptRef.current += 1;
        const delay = Math.min(30_000, 1000 * 2 ** Math.min(attemptRef.current, 5));
        client.reconnectDelay = delay;
        setStatus('reconnecting');
      },
      onWebSocketError: () => {
        if (!intentionalClose.current) setStatus('reconnecting');
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      intentionalClose.current = true;
      client.deactivate();
      if (clientRef.current === client) {
        clientRef.current = null;
      }
    };
  }, [session?.playerId, session?.roomCode]);

  const sendAction = useCallback((
    type: string,
    extra?: { cardId?: string; chosenColor?: CardColor; targetPlayerId?: string },
  ) => {
    if (!session) {
      onErrorRef.current?.('Not in a room');
      return;
    }
    if (!clientRef.current?.connected) {
      onErrorRef.current?.('Not connected — reconnecting…');
      return;
    }
    const code = session.roomCode.toUpperCase();
    clientRef.current.publish({
      destination: `/app/room/${code}/action`,
      body: JSON.stringify({
        playerId: session.playerId,
        type,
        cardId: extra?.cardId,
        chosenColor: extra?.chosenColor,
        targetPlayerId: extra?.targetPlayerId,
      }),
    });
  }, [session]);

  const connected = status === 'connected';
  return { connected, status, sendAction };
}
