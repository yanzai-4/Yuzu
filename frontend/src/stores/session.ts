import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '../api/types';

/** v0.0.4 🍊 Shape of the session store. */
export interface SessionState {
  user: User | null;
}

/** v0.0.4 🍊 The signed-in human, persisted in localStorage under `yuzu.session`. */
export const useSessionStore = create<SessionState>()(
  persist(() => ({ user: null as User | null }), {
    name: 'yuzu.session',
    version: 1,
  }),
);

/** v0.0.4 🍊 Stores the user returned by `POST /api/session/join`. */
export function setSessionUser(user: User): void {
  useSessionStore.setState({ user });
}

/** v0.0.4 🍊 Forgets the current user (back to the join screen). */
export function signOut(): void {
  useSessionStore.setState({ user: null });
}

/** v0.0.4 🍊 The current user id (outside React), or null. */
export function currentUserId(): string | null {
  return useSessionStore.getState().user?.id ?? null;
}
