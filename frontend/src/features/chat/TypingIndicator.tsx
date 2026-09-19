import { useEffect, useMemo } from 'react';
import { pruneTyping, useChatStore } from '../../stores/chat';
import { useRoomStore } from '../../stores/room';

/** v0.0.4 🍊 "Lime is typing…" line fed by `chat.typing` events (stale entries expire). */
export function TypingIndicator() {
  const typing = useChatStore((s) => s.typing);
  const agents = useRoomStore((s) => s.agents);

  useEffect(() => {
    const timer = setInterval(() => pruneTyping(Date.now()), 5000);
    return () => clearInterval(timer);
  }, []);

  const names = useMemo(
    () => Object.keys(typing).map((id) => agents[id]?.name).filter((n): n is string => Boolean(n)),
    [typing, agents],
  );
  const label =
    names.length === 0
      ? ''
      : names.length === 1
        ? `${names[0]} is typing`
        : names.length === 2
          ? `${names[0]} and ${names[1]} are typing`
          : `${names.length} coworkers are typing`;

  return (
    <div className="flex h-6 shrink-0 items-center gap-1.5 px-4 text-[11px] text-ink-3" aria-live="polite">
      {label ? (
        <>
          <span className="flex gap-0.5" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <span key={i} className="animate-typing-dot size-1 rounded-full bg-accent" style={{ animationDelay: `${i * 0.15}s` }} />
            ))}
          </span>
          <span>{label}…</span>
        </>
      ) : null}
    </div>
  );
}
