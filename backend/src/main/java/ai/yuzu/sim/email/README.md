# ai.yuzu.sim.email

> v0.0.11 🍊 The simulated mail server (table `fake_email`, event `sim.email`).

## Main classes

- `FakeMailbox` — per-agent mailbox.
  - `addressOf(agentId)` — the agent's own address, from its citrus name (`pomelo@citrushq.test`,
    `blood.orange@citrushq.test`).
  - `inbox(agentId)` — received e-mails, newest first. The **first access** seeds five realistic
    customer e-mails from `acme.test` / `example.com` (damaged order, request for quote, product
    feedback, double charge, and one **phishing** e-mail asking for passwords and API keys, with a
    prompt-injection line, for the security demo). Seeding runs once per agent under the agent lock in
    one transaction, and publishes one `sim.email` per seeded e-mail.
  - `read(agentId, emailId)` — agent-scoped: another agent's e-mail id is NOT_FOUND.
  - `send(agentId, from, to, subject, body)` — see below.
  - `recordBlocked(agentId, from, to, subject, body)` — stores a BLOCKED attempt refused by an outer
    guard (lenient: input is sanitized and clipped, never rejected).
  - `countSentSince(agentId, instant)` — SENT e-mails only (blocked attempts do not count).
  - `emails(agentId, limit)` — both directions, newest first (UI).
- `EmailRules` (pure, no Spring) — `check(Limits, recipients, sentLastHour)` returns a `Result`
  (normalized recipients + `Violation(code, message)` list): `NO_RECIPIENTS`,
  `TOO_MANY_RECIPIENTS` (max 10), `INVALID_ADDRESS` (plain ASCII `local@domain.tld` only: no display
  names, spaces, CR/LF header injection or IDN look-alikes), `DOMAIN_NOT_ALLOWED` (exact,
  case-insensitive match against `emailAllowedDomains`: `mail.acme.test` or `acme.test.evil.com` do
  **not** match `acme.test`), `HOURLY_LIMIT` (`sentLastHour >= emailMaxPerHour`, or the limit is 0).
  `checkSender(own, from)` → `SENDER_MISMATCH` (spoofing). Helpers: `normalize`, `isValidAddress`,
  `domainOf`.
- `Email` / `EmailView` — domain record (UTC `Instant`, recipient list) and the contract shape
  (`to` joined with `, `, compact natural time).
- `EmailRepository` — agent-scoped SQL (every `SQL_*` filters by `agent_id = :agentId`).
- `InboxSeed` — the seed e-mails.

## send()

1. Validate input (BAD_REQUEST, nothing stored): subject required, one line (controls → spaces),
   ≤ 300 chars; body required, ≤ 100,000 chars; recipient line ≤ 500 chars.
2. Under the agent's `ReentrantLock`:
   - **Identity guards** — `EMAIL_SEND` permission (and not retired); the sender must be the agent's
     own address (blank `from` = own address). These run first, so a duplicate can never hide a
     forged sender.
   - **Dedupe** — SHA-256 over agent + sorted recipients + subject + body (`dedupe_hash`). If the
     identity guards pass and a SENT e-mail with the same hash exists in the last 10 minutes, it is
     returned as is (no new row, no event) — even when the hourly limit is reached meanwhile.
   - **Rules** — `EmailRules.check` with the agent's current limits and `countSentSince(now - 1h)`.
   - Allowed → OUT/SENT row. Refused → OUT/BLOCKED row.
3. Publish `sim.email` to the agent's room; a BLOCKED send then throws `PermissionDeniedException`
   ("E-mail blocked: …", details `emailId`, `reasons`, `codes`, `recipients`).
