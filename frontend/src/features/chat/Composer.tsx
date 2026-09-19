import { useId, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent } from 'react';
import { IconButton } from '../../components/Button';
import { findMentionQuery, rankMentionTargets, type MentionTarget } from '../../lib/mentions';
import { useMentionTargets } from '../../stores/selectors';
import { sendChatMessage } from './chatActions';
import { MentionMenu } from './MentionMenu';

const MAX_HEIGHT = 168;

/** v0.0.4 🍊 Message composer: autosizing textarea, @-mention autocomplete, Enter to send. */
export function Composer() {
  const { targets } = useMentionTargets();
  const [text, setText] = useState('');
  const [caret, setCaret] = useState(0);
  const [menuIndex, setMenuIndex] = useState(0);
  const [dismissedAt, setDismissedAt] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const menuId = useId();

  const query = findMentionQuery(text, caret);
  const candidates = query && dismissedAt !== query.start ? rankMentionTargets(query.query, targets) : [];
  const menuOpen = candidates.length > 0;
  const activeIndex = Math.min(menuIndex, candidates.length - 1);

  const autosize = (el: HTMLTextAreaElement) => {
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`;
  };

  const onChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setText(event.target.value);
    setCaret(event.target.selectionStart);
    setMenuIndex(0);
    autosize(event.target);
  };

  const pick = (target: MentionTarget) => {
    if (!query) return;
    const insertion = `@${target.name} `;
    const next = text.slice(0, query.start) + insertion + text.slice(caret);
    const position = query.start + insertion.length;
    setText(next);
    setCaret(position);
    setMenuIndex(0);
    requestAnimationFrame(() => {
      const el = inputRef.current;
      if (!el) return;
      el.focus();
      el.setSelectionRange(position, position);
      autosize(el);
    });
  };

  const send = async () => {
    if (!text.trim() || sending) return;
    setSending(true);
    const ok = await sendChatMessage(text);
    setSending(false);
    if (ok) {
      setText('');
      setCaret(0);
      requestAnimationFrame(() => {
        const el = inputRef.current;
        if (el) {
          autosize(el);
          el.focus();
        }
      });
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (menuOpen) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const delta = event.key === 'ArrowDown' ? 1 : -1;
        setMenuIndex((activeIndex + delta + candidates.length) % candidates.length);
        return;
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        const target = candidates[activeIndex];
        if (target) {
          event.preventDefault();
          pick(target);
        }
        return;
      }
      if (event.key === 'Escape' && query) {
        event.preventDefault();
        setDismissedAt(query.start);
        return;
      }
    }
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void send();
    }
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void send();
  };

  return (
    <form onSubmit={onSubmit} className="relative border-t border-line bg-surface-2 p-2.5">
      {menuOpen ? (
        <MentionMenu id={menuId} candidates={candidates} activeIndex={activeIndex} onHover={setMenuIndex} onPick={pick} />
      ) : null}
      <div className="flex items-end gap-2 rounded-xl border border-line-strong bg-surface p-1.5 shadow-sm focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/25">
        <textarea
          ref={inputRef}
          rows={1}
          value={text}
          onChange={onChange}
          onKeyDown={onKeyDown}
          onSelect={(e) => setCaret(e.currentTarget.selectionStart)}
          placeholder="Message the team — type @ to mention a coworker"
          aria-label="Message"
          role="combobox"
          aria-expanded={menuOpen}
          aria-controls={menuOpen ? menuId : undefined}
          aria-activedescendant={menuOpen ? `${menuId}-${activeIndex}` : undefined}
          aria-autocomplete="list"
          maxLength={4000}
          className="max-h-[168px] min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-sm text-ink outline-none placeholder:text-ink-3"
        />
        <IconButton type="submit" icon="send" label="Send message" variant="primary" size="md" loading={sending} disabled={!text.trim()} />
      </div>
      <p className="mt-1 hidden px-1 text-[10px] text-ink-3 sm:block">
        <kbd className="font-sans font-semibold">Enter</kbd> to send · <kbd className="font-sans font-semibold">Shift+Enter</kbd> for a new
        line · <span className="font-semibold">@all</span> notifies everyone
      </p>
    </form>
  );
}
