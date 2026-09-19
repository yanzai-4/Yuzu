import { useEffect } from 'react';
import { joinSession } from '../../api/client';
import { setSessionUser, useSessionStore } from '../../stores/session';

/**
 * v0.0.4 🍊 Re-joins with the persisted username once per session so the stored user stays valid
 * (re-joining with the same name returns the same user; a reset backend hands out a fresh id).
 */
export function useSessionRefresh(): void {
  const username = useSessionStore((s) => s.user?.username);
  useEffect(() => {
    if (!username) return;
    let cancelled = false;
    joinSession(username)
      .then((user) => {
        if (!cancelled) setSessionUser(user);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [username]);
}
