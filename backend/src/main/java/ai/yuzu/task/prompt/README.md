# ai.yuzu.task.prompt

> v0.0.20 🍊 English, byte-stable prompt text of task lists (cache-friendly for LLM prompt prefixes).

## Main class

`TaskPromptRenderer` (static, pure):

- `renderCurrent(TaskListView)` — the current list, or `No current task list.`:

  ```
  Goal: Launch the landing page
  Earlier goal: Launch a page (replaced Sat Sep 19, 11:40:02 AM; reason: Alice wants pricing too)
  Publisher: Alice (human)
  Status: in progress
  Ticket: ticket-0000-00000000aa
  Started: Sat Sep 19, 11:32:05 AM
  Progress: 2 of 4 finished (1 done, 1 struck, 1 in progress, 1 to do)
  [x] 1. Draft the copy (note: approved by Alice)
  [~] 2. Add a pricing table (struck: pricing is not public yet)
  [>] 3. Build the page (in progress)
  [ ] 4. Deploy
  ```

- `renderHistory(List<TaskList>)` — the last archived lists (most recent first) with archive time, outcome and
  items; `No archived task lists yet.` when empty. Feed it `TaskListService.recentArchived(agentId, 3)`.
- `renderList(TaskList)` — one list in the current-list format.

## Rules

- Same input, same bytes: items are always ordered by `ord`, lines are joined with `\n`, no trailing whitespace.
- Times are the absolute natural-language strings of the DTOs (never "5 minutes ago").
- Every free text is flattened to one line, so stored text can never forge extra item lines.
- Item numbers are the stable `ord` values; `TaskList.itemAt(ord)` maps a number back to its item id.
