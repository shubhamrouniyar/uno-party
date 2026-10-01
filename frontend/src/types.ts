export type CardColor = 'RED' | 'YELLOW' | 'GREEN' | 'BLUE' | 'WILD';
export type CardType = 'NUMBER' | 'SKIP' | 'REVERSE' | 'DRAW_TWO' | 'WILD' | 'WILD_DRAW_FOUR';
export type GameStatus = 'LOBBY' | 'PLAYING' | 'PAUSED' | 'FINISHED';
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected';

export interface Card {
  id: string;
  color: CardColor;
  type: CardType;
  number: number | null;
}

export interface PlayerView {
  id: string;
  name: string;
  handSize: number;
  connected: boolean;
  calledUno: boolean;
  host: boolean;
  current: boolean;
}

export interface GameStateView {
  roomCode: string;
  status: GameStatus;
  players: PlayerView[];
  topCard: Card | null;
  activeColor: CardColor | null;
  direction: number;
  drawPileCount: number;
  yourHand: Card[];
  yourPlayerId: string | null;
  yourTurn: boolean;
  mustDrawOrPlay: boolean;
  lastDrawnCardId: string | null;
  winnerId: string | null;
  winnerName: string | null;
  eventLog: string[];
  error?: string | null;
  message?: string | null;
}

export interface JoinResponse {
  playerId: string;
  roomCode: string;
  displayName: string;
  host: boolean;
  gameState: GameStateView;
}

export interface Session {
  playerId: string;
  roomCode: string;
  displayName: string;
  host: boolean;
}
