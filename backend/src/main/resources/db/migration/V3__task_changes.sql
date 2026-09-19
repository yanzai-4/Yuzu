-- v0.0.10 🍊 Task system additions: goal history, ticket assigner, and at most one open task list per agent.
--
--   * task_list.goal_history — JSON array of {previousGoal, reason, changedAt}; an edited goal is never lost.
--   * task_list.open_slot    — 1 while a list is ACTIVE or AWAITING_APPROVAL, NULL once ARCHIVED. The unique key
--                              (agent_id, open_slot) turns "every agent has ONE current task list" into a database
--                              invariant (NULLs never collide, so archived history is unlimited).
--   * ticket.assigned_by_*   — who assigned the ticket; that actor is the default publisher of the assignee's list.
ALTER TABLE task_list
    ADD COLUMN goal_history JSON NULL AFTER goal,
    ADD COLUMN open_slot TINYINT GENERATED ALWAYS AS (IF(status = 'ARCHIVED', NULL, 1)) VIRTUAL,
    ADD UNIQUE KEY uk_open_list (agent_id, open_slot);

ALTER TABLE ticket
    ADD COLUMN assigned_by_kind ENUM ('HUMAN','AGENT') NULL AFTER assignee_id,
    ADD COLUMN assigned_by_id   VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER assigned_by_kind,
    ADD COLUMN assigned_by_name VARCHAR(64) NULL AFTER assigned_by_id;
