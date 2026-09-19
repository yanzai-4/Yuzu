import { useRoomStream } from '../../api/useRoomStream';
import { PaneBoundary } from '../../components/PaneBoundary';
import { Spotlight } from '../../components/Spotlight';
import { Toaster } from '../../components/Toaster';
import { useMediaQuery } from '../../lib/useMediaQuery';
import { useConnectionStore } from '../../stores/connection';
import { DEFAULT_ROOM_ID } from '../../stores/room';
import { useUiStore } from '../../stores/ui';
import { AgentInspector } from '../agents/AgentInspector';
import { AgentsDialog } from '../agents/AgentsDialog';
import { ChatPane } from '../chat/ChatPane';
import { ConsoleDialog } from '../console/ConsoleDialog';
import { DemoBar } from '../demo/DemoBar';
import { useDemoKeys } from '../demo/useDemoKeys';
import { InsightsPane } from '../insights/InsightsPane';
import { TraceWaterfall } from '../insights/trace/TraceWaterfall';
import { OfficePane } from '../office/OfficePane';
import { BootScreen } from './BootScreen';
import { MobileTabBar } from './MobileTabBar';
import { TopBar } from './TopBar';
import { useSessionRefresh } from './useSessionRefresh';

/** v0.0.4 🍊 The signed-in app: top bar + three panes (tabs below 1100 px) + dialogs, drawer and toasts. */
export function Workspace() {
  useRoomStream(DEFAULT_ROOM_ID);
  useSessionRefresh();
  useDemoKeys();
  const wide = useMediaQuery('(min-width: 1100px)');
  const compact = useMediaQuery('(max-width: 640px)');
  const pane = useUiStore((s) => s.mobilePane);
  const bootstrapped = useConnectionStore((s) => s.bootstrapped);

  return (
    <div className="flex h-dvh flex-col bg-bg">
      <TopBar compact={compact} />
      <DemoBar />
      {!bootstrapped ? (
        <BootScreen />
      ) : wide ? (
        <main className="grid min-h-0 flex-1 grid-cols-[minmax(300px,1fr)_minmax(420px,1.4fr)_minmax(330px,1.05fr)] gap-3 p-3">
          <PaneBoundary name="Chat">
            <Spotlight id="chat" className="flex min-h-0 flex-1 flex-col">
              <ChatPane />
            </Spotlight>
          </PaneBoundary>
          <PaneBoundary name="Office">
            <Spotlight id="office" className="flex min-h-0 flex-1 flex-col">
              <OfficePane />
            </Spotlight>
          </PaneBoundary>
          <PaneBoundary name="Insights">
            <InsightsPane />
          </PaneBoundary>
        </main>
      ) : (
        <main className="flex min-h-0 flex-1 flex-col p-2 pb-[4.25rem]">
          <PaneBoundary key={pane} name={pane === 'chat' ? 'Chat' : pane === 'office' ? 'Office' : 'Insights'}>
            {pane === 'chat' ? <ChatPane /> : pane === 'office' ? <OfficePane /> : <InsightsPane />}
          </PaneBoundary>
        </main>
      )}
      {!wide ? <MobileTabBar /> : null}
      <PaneBoundary name="Coworkers dialog" quiet>
        <AgentsDialog />
      </PaneBoundary>
      <PaneBoundary name="Console" quiet>
        <ConsoleDialog />
      </PaneBoundary>
      <PaneBoundary name="Agent Inspector" quiet>
        <AgentInspector />
      </PaneBoundary>
      <PaneBoundary name="Trace waterfall" quiet>
        <TraceWaterfall />
      </PaneBoundary>
      <Toaster />
    </div>
  );
}
