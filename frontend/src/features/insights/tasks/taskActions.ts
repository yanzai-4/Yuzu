import { approveTaskList, getAgentTasks } from '../../../api/client';
import { applyLocal } from '../../../stores/applyEvent';
import { useSessionStore } from '../../../stores/session';
import { pushToast } from '../../../stores/ui';

/** v0.0.4 🍊 Approves and archives a finished task list as the current user. */
export async function approveList(listId: string, goal: string): Promise<boolean> {
  const user = useSessionStore.getState().user;
  if (!user) return false;
  try {
    const view = await approveTaskList(listId, user.id);
    applyLocal('task.list', view, view.agentId);
    pushToast({ tone: 'success', title: 'Task list approved', message: `"${goal}" was archived.` });
    return true;
  } catch {
    return false;
  }
}

/** v0.0.4 🍊 Re-fetches an agent's task lists (GET /api/agents/{id}/tasks) into the store. */
export async function refreshAgentTasks(agentId: string): Promise<void> {
  try {
    const view = await getAgentTasks(agentId);
    applyLocal('task.list', view, agentId);
  } catch {
    // Already reported (toast + error log).
  }
}
