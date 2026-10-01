import { useCallback, useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import type { CardColor, GameStateView, Session } from './types';
import { getWsUrl } from './api';

interface Options {
  session: Session | null;
  onState: (state: GameStateView) => void;
  onError?: (msg: string) => void;
}

export function useGameSocket({ session, onState, onError }: Options) {
  const clientRef = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);
  const onStateRef = useRef(onState);
  const onErrorRef = useRef(onError);
  onStateRef.current = onState;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!session) {
      clientRef.current?.deactivate();
      clientRef.current = null;
      setConnected(false);
      return;
    }

    const code = session.roomCode.toUpperCase();
    const client = new Client({
      brokerURL: getWsUrl(),
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/room/${code}/player/${session.playerId}`, (msg: IMessage) => {
          const state = JSON.parse(msg.body) as GameStateView;
          if (state.error) onErrorRef.current?.(state.error);
          onStateRef.current(state);
        });
        client.publish({
          destination: `/app/room/${code}/sync`,
          body: JSON.stringify({ playerId: session.playerId, type: 'SYNC' }),
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        onErrorRef.current?.(frame.headers['message'] || 'WebSocket error');
      },
      onWebSocketError: () => {
        setConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [session?.playerId, session?.roomCode]);

  const sendAction = useCallback((
    type: string,
    extra?: { cardId?: string; chosenColor?: CardColor; targetPlayerId?: string },
  ) => {
    if (!session || !clientRef.current?.connected) {
      onErrorRef.current?.('Not connected to server');
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

  return { connected, sendAction };
}
