import { create } from 'zustand';

/** v0.0.4 🍊 Lifecycle of the realtime connection. */
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

/** v0.0.4 🍊 Shape of the connection store. */
export interface ConnectionState {
  status: ConnectionStatus;
  /** Last applied event id (the resume point of the stream). */
  cursor: number;
  connectionId: string | null;
  /** True once the first snapshot has been rendered. */
  bootstrapped: boolean;
  /** Consecutive failed attempts (drives the reconnect back-off label). */
  attempts: number;
}

/** v0.0.4 🍊 Live connection indicator state, written by the RoomStream. */
export const useConnectionStore = create<ConnectionState>()(() => ({
  status: 'idle',
  cursor: 0,
  connectionId: null,
  bootstrapped: false,
  attempts: 0,
}));

/** v0.0.4 🍊 Merges a partial connection update. */
export function patchConnection(patch: Partial<ConnectionState>): void {
  useConnectionStore.setState(patch);
}
