import { colorFromName } from '../../lib/colors';
import type { YuzuApi } from '../client';
import type { ChatMessage, Snapshot, User } from '../types';
import type { MockContext } from './context';
import { clone, hex, nowText, recordId } from './util';

/** Messages included in the bootstrap snapshot (older ones are paged with beforeSeq). */
const SNAPSHOT_MESSAGES = 40;

type ChatApi = Pick<
  YuzuApi,
  'joinSession' | 'getBootstrap' | 'listMessages' | 'postMessage' | 'answerCard' | 'listTickets' | 'approveTaskList'
>;

/** v0.0.4 🍊 Mock session, bootstrap, chat, card and approval endpoints. */
export function createChatApi(ctx: MockContext): ChatApi {
  const { world, reactions } = ctx;
  const { server } = world;
  const { state } = server;

  return {
    async joinSession(username) {
      await ctx.latency();
      const name = username.trim();
      if (!name) throw ctx.fail('POST', '/api/session/join', 'BAD_REQUEST', 'Username must not be empty.');
      if (name.length > 40) throw ctx.fail('POST', '/api/session/join', 'BAD_REQUEST', 'Username is too long (max 40).');
      const existing = [...state.users.values()].find((u) => u.username.toLowerCase() === name.toLowerCase());
      if (existing) return clone(existing);
      const user: User = { id: `user-${hex(4)}`, username: name, color: colorFromName(name), roomId: state.roomId };
      state.users.set(user.id, user);
      server.publish('user.joined', user);
      world.postSystem(`${name} joined the room.`);
      void reactions.greet(user);
      return clone(user);
    },

    async getBootstrap(roomId) {
      await ctx.latency();
      if (roomId !== state.roomId) throw ctx.fail('GET', '/api/bootstrap', 'NOT_FOUND', `Room ${roomId} does not exist.`);
      const snapshot: Snapshot = {
        roomId: state.roomId,
        roomName: state.roomName,
        users: [...state.users.values()],
        agents: [...state.agents.values()],
        statuses: [...state.statuses.values()].filter((s) => state.agents.has(s.agentId)),
        messages: state.messages.slice(-SNAPSHOT_MESSAGES),
        cards: [...state.cards.values()],
        tickets: [...state.tickets.values()],
        taskLists: [...state.taskLists.values()],
        usage: state.meter.snapshot(),
        emails: [...state.emails].reverse(),
        trades: [...state.trades].reverse(),
        portfolios: [...state.portfolios.values()],
        incidents: state.incidents,
        settings: state.settings,
        eventCursor: server.eventCursor,
        time: nowText(),
      };
      return clone(snapshot);
    },

    async listMessages(roomId, beforeSeq, limit = 50) {
      await ctx.latency();
      if (roomId !== state.roomId) throw ctx.fail('GET', `/api/rooms/${roomId}/messages`, 'NOT_FOUND', 'Unknown room.');
      const older = beforeSeq === undefined ? state.messages : state.messages.filter((m) => m.seq < beforeSeq);
      return clone(older.slice(-limit));
    },

    async postMessage(roomId, userId, content) {
      const path = `/api/rooms/${roomId}/messages`;
      await ctx.latency();
      const user = ctx.user('POST', path, userId);
      const text = content.trim();
      if (!text) throw ctx.fail('POST', path, 'BAD_REQUEST', 'Message must not be empty.');
      if (text.length > 4000) throw ctx.fail('POST', path, 'BAD_REQUEST', 'Message is too long (max 4,000 characters).');
      const message: ChatMessage = {
        id: recordId('msg'),
        roomId,
        seq: server.nextSeq(),
        authorKind: 'HUMAN',
        authorId: user.id,
        authorName: user.username,
        kind: 'TEXT',
        content: text,
        mentions: world.mentionsIn(text),
        mentionAll: /(^|\s)@all\b/i.test(text),
        closure: false,
        causalDepth: 0,
        streamState: 'NONE',
        time: nowText(),
      };
      state.messages.push(message);
      server.publish('chat.message', message);
      void reactions.onHumanMessage(message, user);
      return clone(message);
    },

    async answerCard(cardId, body) {
      const path = `/api/cards/${cardId}/answer`;
      await ctx.latency();
      const card = state.cards.get(cardId);
      if (!card) throw ctx.fail('POST', path, 'NOT_FOUND', 'This card does not exist.');
      const user = ctx.user('POST', path, body.userId);
      if (card.status !== 'OPEN') {
        throw ctx.fail('POST', path, 'CONFLICT', `This card is already ${card.status.toLowerCase()}${card.answeredByName ? ` (by ${card.answeredByName})` : ''}.`);
      }
      const other = body.otherText?.trim() || null;
      const unknown = body.optionIds.filter((id) => !card.options.some((o) => o.id === id));
      if (unknown.length > 0) throw ctx.fail('POST', path, 'BAD_REQUEST', 'Unknown option.', { optionIds: unknown });
      if (body.optionIds.length === 0 && !other) throw ctx.fail('POST', path, 'BAD_REQUEST', 'Pick an option or write an answer.');
      if (other && !card.allowOther) throw ctx.fail('POST', path, 'BAD_REQUEST', 'This card does not accept free-text answers.');
      card.status = 'ANSWERED';
      card.answer = { optionIds: body.optionIds, otherText: other };
      card.answeredByName = user.username;
      card.answeredTime = nowText();
      server.publish('chat.card', card, card.agentId);
      reactions.onCardAnswered(card, user);
      return clone(card);
    },

    async listTickets() {
      await ctx.latency();
      return clone([...state.tickets.values()]);
    },

    async approveTaskList(listId, userId) {
      const path = `/api/task-lists/${listId}/approve`;
      await ctx.latency();
      const user = ctx.user('POST', path, userId);
      const view = [...state.taskLists.values()].find((v) => v.current?.id === listId);
      const list = view?.current;
      if (!view || !list) throw ctx.fail('POST', path, 'NOT_FOUND', 'This task list is not current anymore.');
      if (list.status !== 'AWAITING_APPROVAL') {
        throw ctx.fail('POST', path, 'CONFLICT', 'Only finished task lists (awaiting approval) can be approved.');
      }
      list.status = 'ARCHIVED';
      list.archivedTime = nowText();
      view.recentArchived = [list, ...view.recentArchived].slice(0, 5);
      view.current = null;
      world.publishTaskList(view);
      const ticket = list.ticketId ? state.tickets.get(list.ticketId) : undefined;
      if (ticket) world.publishTicket({ ...ticket, status: 'APPROVED' });
      const agent = state.agents.get(list.agentId);
      world.postSystem(`${user.username} approved ${agent?.name ?? 'an agent'}'s task list "${list.goal}".`);
      return clone(view);
    },
  };
}
