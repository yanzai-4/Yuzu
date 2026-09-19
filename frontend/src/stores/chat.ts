import { create } from 'zustand';
import type { Card, ChatMessage } from '../api/types';
import { CowList, CowRecord } from './cow';

/** v0.0.4 🍊 Virtuoso index of the first loaded message; decreases as older pages are prepended. */
export const CHAT_FIRST_INDEX = 1_000_000;
/** v0.0.4 🍊 A typing indicator without a follow-up expires after this long. */
export const TYPING_TTL_MS = 20_000;

/** v0.0.4 🍊 Shape of the chat store. */
export interface ChatState {
  byId: Record<string, ChatMessage>;
  /** Message ids sorted by `seq` (ascending). */
  order: string[];
  cards: Record<string, Card>;
  /** agentId → epoch ms when it started typing. */
  typing: Record<string, number>;
  firstItemIndex: number;
  hasOlder: boolean;
  loadingOlder: boolean;
}

/** v0.0.4 🍊 An empty chat state (initial and after a room switch). */
export function emptyChatState(): ChatState {
  return {
    byId: {},
    order: [],
    cards: {},
    typing: {},
    firstItemIndex: CHAT_FIRST_INDEX,
    hasOlder: false,
    loadingOlder: false,
  };
}

/** v0.0.4 🍊 Group chat messages, question/approval cards and typing indicators. */
export const useChatStore = create<ChatState>()(() => emptyChatState());

/** v0.0.4 🍊 Batched, copy-on-write writer of the chat store used by the event reducer. */
export class ChatDraft {
  private readonly byId: CowRecord<ChatMessage>;
  private readonly cards: CowRecord<Card>;
  private readonly typing: CowRecord<number>;
  private readonly order: CowList<string>;
  private unsorted = false;

  /** v0.0.4 🍊 Starts a draft from the current store state. */
  constructor(state: ChatState = useChatStore.getState()) {
    this.byId = new CowRecord(state.byId);
    this.cards = new CowRecord(state.cards);
    this.typing = new CowRecord(state.typing);
    this.order = new CowList(state.order);
  }

  /** v0.0.4 🍊 Inserts or replaces a message (keeps text already streamed through deltas). */
  upsertMessage(message: ChatMessage): void {
    const prev = this.byId.get(message.id);
    let next = message;
    if (
      prev &&
      message.streamState === 'STREAMING' &&
      prev.content.length > message.content.length &&
      prev.content.startsWith(message.content)
    ) {
      next = { ...message, content: prev.content };
    }
    this.byId.set(message.id, next);
    if (!prev) {
      const items = this.order.items;
      const lastId = items[items.length - 1];
      const last = lastId ? this.byId.get(lastId) : undefined;
      this.order.mutable().push(message.id);
      if (last && last.seq > message.seq) this.unsorted = true;
    }
    if (message.authorKind === 'AGENT' && message.streamState !== 'STREAMING') this.typing.delete(message.authorId);
  }

  /** v0.0.4 🍊 Appends streamed text to a message and updates its stream state. */
  appendDelta(messageId: string, delta: string, streamState: ChatMessage['streamState']): void {
    const prev = this.byId.get(messageId);
    if (!prev) return;
    this.byId.set(messageId, { ...prev, content: prev.content + delta, streamState });
    if (streamState !== 'STREAMING' && prev.authorKind === 'AGENT') this.typing.delete(prev.authorId);
  }

  /** v0.0.4 🍊 Inserts or replaces a card. */
  upsertCard(card: Card): void {
    this.cards.set(card.id, card);
  }

  /** v0.0.4 🍊 Starts or stops an agent's typing indicator. */
  setTyping(agentId: string, typing: boolean): void {
    if (typing) this.typing.set(agentId, Date.now());
    else this.typing.delete(agentId);
  }

  /** v0.0.4 🍊 Writes every changed collection to the store in one update. */
  commit(): void {
    const patch: Partial<ChatState> = {};
    if (this.byId.changed) patch.byId = this.byId.value;
    if (this.cards.changed) patch.cards = this.cards.value;
    if (this.typing.changed) patch.typing = this.typing.value;
    if (this.order.changed) {
      const order = this.order.value;
      if (this.unsorted) {
        const byId = this.byId.value;
        order.sort((a, b) => (byId[a]?.seq ?? 0) - (byId[b]?.seq ?? 0));
      }
      patch.order = order;
    }
    if (Object.keys(patch).length > 0) useChatStore.setState(patch);
  }
}

/** v0.0.4 🍊 Prepends a page of older messages (from `GET /messages?beforeSeq=`). */
export function prependOlderMessages(messages: ChatMessage[], pageSize: number): void {
  const state = useChatStore.getState();
  const fresh = messages.filter((m) => !state.byId[m.id]).sort((a, b) => a.seq - b.seq);
  const byId = { ...state.byId };
  for (const message of fresh) byId[message.id] = message;
  useChatStore.setState({
    byId,
    order: [...fresh.map((m) => m.id), ...state.order],
    firstItemIndex: state.firstItemIndex - fresh.length,
    hasOlder: messages.length >= pageSize && fresh.length > 0,
    loadingOlder: false,
  });
}

/** v0.0.4 🍊 Marks the "load older messages" request as running or finished. */
export function setLoadingOlder(loadingOlder: boolean): void {
  useChatStore.setState({ loadingOlder });
}

/** v0.0.4 🍊 Drops typing indicators that never received a "stopped typing" event. */
export function pruneTyping(now: number): void {
  const { typing } = useChatStore.getState();
  const stale = Object.entries(typing).filter(([, since]) => now - since > TYPING_TTL_MS);
  if (stale.length === 0) return;
  const next = { ...typing };
  for (const [agentId] of stale) delete next[agentId];
  useChatStore.setState({ typing: next });
}
