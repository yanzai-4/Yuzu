import { AnimatePresence, motion } from 'motion/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import { useShallow } from 'zustand/react/shallow';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Spinner } from '../../components/Spinner';
import { useChatStore } from '../../stores/chat';
import { loadOlderMessages } from './chatActions';
import { MessageRow } from './MessageRow';

interface ListContext {
  loadingOlder: boolean;
  hasOlder: boolean;
}

/** v0.0.4 🍊 Top of the list: spinner while paging, or the start-of-room marker. */
function ListHeader({ context }: { context?: ListContext }) {
  if (context?.loadingOlder) {
    return (
      <div className="flex justify-center py-3 text-ink-3">
        <Spinner size={14} label="Loading older messages" />
      </div>
    );
  }
  if (context && !context.hasOlder) {
    return <p className="py-3 text-center text-[11px] text-ink-3">This is the beginning of the room.</p>;
  }
  return <div className="h-3" />;
}

/** v0.0.4 🍊 Bottom spacer of the list. */
function ListFooter() {
  return <div className="h-2" />;
}

const COMPONENTS = { Header: ListHeader, Footer: ListFooter };
const loadOlder = () => void loadOlderMessages();
/** Open the room scrolled to the newest message, aligned to the bottom edge. */
const INITIAL_LOCATION = { index: 'LAST', align: 'end' } as const;

/** v0.0.4 🍊 Virtualized message list: sticks to the bottom, pages older messages in at the top. */
export function MessageList() {
  const { order, firstItemIndex, loadingOlder, hasOlder } = useChatStore(
    useShallow((s) => ({ order: s.order, firstItemIndex: s.firstItemIndex, loadingOlder: s.loadingOlder, hasOlder: s.hasOlder })),
  );
  const listRef = useRef<VirtuosoHandle>(null);
  const [atBottom, setAtBottom] = useState(true);
  // True once the list has reached the newest message after mounting; paging only starts then, so the
  // initial render (which briefly sits at the top) does not trigger a spurious "load older".
  const [settled, setSettled] = useState(false);
  const context = useMemo(() => ({ loadingOlder, hasOlder }), [loadingOlder, hasOlder]);
  const hasItems = order.length > 0;

  const onAtBottomChange = useCallback((bottom: boolean) => {
    setAtBottom(bottom);
    if (bottom) setSettled(true);
  }, []);

  const scrollerRef = useRef<HTMLElement | null>(null);
  const setScroller = useCallback((el: HTMLElement | Window | null) => {
    scrollerRef.current = el instanceof HTMLElement ? el : null;
  }, []);

  useEffect(() => {
    if (settled || !hasItems) return;
    // Virtuoso positions the initial item a few animation frames after mount, which stalls in
    // background tabs (no rAF). Nudge the scroller directly until the list reports "at bottom".
    const timers = [80, 300, 700, 1500, 3000].map((ms) =>
      setTimeout(() => {
        listRef.current?.scrollToIndex({ index: 'LAST', align: 'end' });
        const el = scrollerRef.current;
        if (el) el.scrollTop = el.scrollHeight;
      }, ms),
    );
    return () => timers.forEach(clearTimeout);
  }, [settled, hasItems]);

  const itemContent = useCallback(
    (index: number, id: string) => <MessageRow id={id} prevId={order[index - firstItemIndex - 1] ?? null} />,
    [order, firstItemIndex],
  );

  const jumpToLatest = () => listRef.current?.scrollToIndex({ index: 'LAST', behavior: 'smooth', align: 'end' });

  if (order.length === 0) {
    return (
      <div className="grid h-full place-items-center p-4">
        <EmptyState icon="message" title="No messages yet">
          Say hello and @mention a coworker to get started.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="relative h-full">
      <Virtuoso<string, ListContext>
        ref={listRef}
        scrollerRef={setScroller}
        className="h-full"
        data={order}
        context={context}
        firstItemIndex={firstItemIndex}
        initialTopMostItemIndex={INITIAL_LOCATION}
        alignToBottom
        computeItemKey={(_, id) => id}
        itemContent={itemContent}
        followOutput={(bottom) => (bottom ? 'smooth' : false)}
        atBottomStateChange={onAtBottomChange}
        atBottomThreshold={80}
        startReached={settled ? loadOlder : undefined}
        increaseViewportBy={{ top: 600, bottom: 300 }}
        components={COMPONENTS}
      />
      <AnimatePresence>
        {!atBottom ? (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            className="absolute right-3 bottom-3"
          >
            <Button size="sm" icon="arrowDown" onClick={jumpToLatest} className="shadow-md">
              Latest
            </Button>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
