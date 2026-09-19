import { answerCard, listMessages, postMessage } from '../../api/client';
import { applyLocal } from '../../stores/applyEvent';
import { prependOlderMessages, setLoadingOlder, useChatStore } from '../../stores/chat';
import { useRoomStore } from '../../stores/room';
import { useSessionStore } from '../../stores/session';

/** v0.0.4 🍊 Messages fetched per "load older" page. */
export const PAGE_SIZE = 50;

/** v0.0.4 🍊 Posts a chat message as the current user; resolves false when it failed (already toasted). */
export async function sendChatMessage(content: string): Promise<boolean> {
  const user = useSessionStore.getState().user;
  const text = content.trim();
  if (!user || !text) return false;
  try {
    const message = await postMessage(useRoomStore.getState().roomId, user.id, text);
    applyLocal('chat.message', message);
    return true;
  } catch {
    return false;
  }
}

/** v0.0.4 🍊 Loads the page of messages before the oldest loaded one (Virtuoso startReached). */
export async function loadOlderMessages(): Promise<void> {
  const state = useChatStore.getState();
  if (state.loadingOlder || !state.hasOlder) return;
  const firstId = state.order[0];
  const first = firstId ? state.byId[firstId] : undefined;
  if (!first) return;
  setLoadingOlder(true);
  try {
    const page = await listMessages(useRoomStore.getState().roomId, first.seq, PAGE_SIZE);
    prependOlderMessages(page, PAGE_SIZE);
  } catch {
    setLoadingOlder(false);
  }
}

/** v0.0.4 🍊 Answers a question/approval card as the current user; resolves false on failure. */
export async function answerChatCard(cardId: string, optionIds: string[], otherText?: string): Promise<boolean> {
  const user = useSessionStore.getState().user;
  if (!user) return false;
  try {
    const card = await answerCard(cardId, { userId: user.id, optionIds, ...(otherText ? { otherText } : {}) });
    applyLocal('chat.card', card, card.agentId);
    return true;
  } catch {
    return false;
  }
}
