import { JoinScreen } from './features/join/JoinScreen';
import { Workspace } from './features/shell/Workspace';
import { Toaster } from './components/Toaster';
import { useSessionStore } from './stores/session';

/** v0.0.4 🍊 Root component: the join screen until a user exists, then the workspace. */
export function App() {
  const hasUser = useSessionStore((s) => s.user !== null);
  if (hasUser) return <Workspace />;
  return (
    <>
      <JoinScreen />
      <Toaster />
    </>
  );
}
