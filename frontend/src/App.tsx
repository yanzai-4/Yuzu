import { useSessionStore } from './stores/session';

/** v0.0.4 🍊 Root component: the join screen until a user exists, then the workspace. */
export function App() {
  const user = useSessionStore((s) => s.user);
  return <div className="p-6">{user ? `Hello ${user.username}` : 'Join'}</div>;
}
