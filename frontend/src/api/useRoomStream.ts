import { useEffect } from 'react';
import { startRoomStream } from './stream';

/** v0.0.4 🍊 Keeps the room in sync (bootstrap + SSE) while the calling component is mounted. */
export function useRoomStream(roomId: string): void {
  useEffect(() => startRoomStream(roomId), [roomId]);
}
