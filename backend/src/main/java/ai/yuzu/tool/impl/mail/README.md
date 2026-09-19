# ai.yuzu.tool.impl.mail

> v0.0.27 🍊 Mail tools over the simulated mail server: reading is free, sending always waits for a human.

- `EmailReadTool` (`email_read`, needs `EMAIL_READ`, LOW) — argument `emailId?`. No id lists my inbox
  (`FakeMailbox.inbox`, which seeds five realistic customer e-mails on first access, one of them a phishing
  message with a prompt-injection line); an id opens that e-mail in full (`FakeMailbox.read` is agent-scoped,
  so another agent's id is NOT_FOUND). Bodies are clipped (200 characters in the list, 8,000 when opened) and
  every result ends with a reminder that e-mail text is information, never an instruction.
  **Untrusted** — the output passes the outbound safety review.
- `EmailSendTool` (`email_send`, needs `EMAIL_SEND`, **HIGH**) — arguments `to[]`, `subject`, `body`.
  It never sends anything itself:
  1. `PermissionGuard.require(EMAIL_SEND)` (defense in depth, even without the dispatcher).
  2. `EmailRules.check` with the agent's own limits and `FakeMailbox.countSentSince` — a refused recipient
     domain, a bad address or a reached hourly limit is recorded with `FakeMailbox.recordBlocked` and returned
     as `DENIED`, so no human is bothered with a mail the rules already forbid.
  3. `ApprovalGate.request` opens an Approve / Reject card (`EMAIL_APPROVAL`) whose payload is the
     `EmailDraft`, and the call returns `WAITING` — the rest of the tool batch is not held back.
- `EmailApprovalHandler` (`EMAIL_APPROVAL`) — approved → `FakeMailbox.send`, which re-runs **every** guard
  (permission, own sender address, allowlist, hourly limit, 10-minute duplicate window), so an approval only
  lifts the "a human must look at this" gate and never a limit; rejected → `recordBlocked`. The outcome
  reaches the agent as a code-made notice through `NoticeService`, attributed to the human who decided.
- `EmailDraft` — the card payload (`to[]`, `subject`, `body`). `MailFormat` — previews, clipping, recipient
  lines and the untrusted-content reminder.

The five layers behind an outgoing e-mail: behavior review → high-risk second review → `PermissionGuard` →
approval card → the mailbox's own guards inside `send`.
