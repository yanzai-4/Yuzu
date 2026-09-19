# S13 Monitor Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover and verify the inherited S13 monitor and trace backend so modules emit lifecycle events, agent desks remain live, and trace history is persisted and queryable.

**Architecture:** `MonitorService` is the monitor-bus facade. It sends each immutable `ModuleEvent` independently to asynchronous persistence, `AgentStatusBoard`, and SSE. The board keeps one locked `AgentLiveState` per agent, with coalesced status and summary triggers; trace repositories read the same `module_event` history after a flush.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, JUnit 5, AssertJ, MySQL/Flyway integration tests.

**Spec:** `/Users/ruixianghuang/Documents/Claude_Project/Hackathon/.superpowers/sdd/ai-agent-agent-agent-ai-immutable-mccarthy/restore-s13-monitor-brief.md`

## Global Constraints

- Work only in `/Users/ruixianghuang/Documents/Claude_Project/Hackathon/.claude/worktrees/agent-a52e907265affea87` on branch `worktree-agent-a52e907265affea87`.
- Preserve Java 21, do not use preview APIs, and do not introduce `synchronized` in production code.
- Preserve inherited uncommitted production code; tests precede any behavior change and record their observed result.
- Keep English code/comments/README and use the current `v0.0.12 🍊` tag convention.
- Do not rebase, merge, push, edit another worktree, or dispatch subagents.

---

### Task 1: Baseline and synthetic transition coverage

**Files:**
- Modify: `backend/src/test/java/ai/yuzu/monitor/AgentStatusBoardTest.java` only if a consumer-visible synthetic event transition lacks proof.
- Verify: `backend/src/test/java/ai/yuzu/monitor/*Test.java`

**Interfaces:**
- Consumes: `AgentStatusBoard.apply(ModuleEvent, DeskState)` and `AgentStatusBoard.status(AgentId)`.
- Produces: regression evidence that synthetic START/STATE/END/ERROR events produce the required live state and bubble transitions.

- [x] **Step 1: Establish the inherited baseline**

Run: `mvn -q -Dtest='ai.yuzu.monitor.*Test,ai.yuzu.trace.*Test' test`

Expected: unit monitor tests run; integration tests may be blocked only if the local test MySQL socket is unavailable.

- [x] **Step 2: Write a focused failing synthetic-event test if current coverage misses a required observable transition**

```java
fixture.event(agent, ModuleKind.TOOL, EventPhase.START, "Searching");
assertStatus(fixture.board.status(agent), "WORKING", "Tool", "Searching");
fixture.event(agent, ModuleKind.TOOL, EventPhase.ERROR, "Timed out");
assertStatus(fixture.board.status(agent), "ERROR", "Tool", "Timed out");
```

- [x] **Step 3: Run the focused test and record RED or the inherited pass characterization**

Run: `mvn -q -Dtest=AgentStatusBoardTest test`

Expected: the test fails before any production correction when a real transition is missing; otherwise record that inherited tests already prove every required transition and make no redundant test-only change.

- [x] **Step 4: Make the smallest production correction only if the new test exposes a behavior gap**

```java
afterChange(state, state.apply(event, desk, System.nanoTime()));
```

- [x] **Step 5: Re-run focused monitor and trace tests**

Run: `mvn -q -Dtest='ai.yuzu.monitor.*Test,ai.yuzu.trace.*Test' test`

Expected: all unit monitor/trace tests pass; any integration limitation is reported with its exact external cause.

### Task 2: Recover and deliver the inherited S13 implementation

**Files:**
- Add: inherited `backend/src/main/java/ai/yuzu/monitor/**` and `backend/src/main/java/ai/yuzu/trace/**` files.
- Modify: inherited agent integration and monitor/trace tests.
- Create: recovery report at the required SDD path.

**Interfaces:**
- Consumes: the existing module-event table, `SseHub`, agent lifecycle callbacks, and `NaturalTime`.
- Produces: committed S13 monitor/trace backend and a factual recovery report.

- [x] **Step 1: Verify static constraints**

Run: `rg -n '\\bsynchronized\\b' backend/src/main/java/ai/yuzu/monitor backend/src/main/java/ai/yuzu/trace`

Expected: no matches.

- [x] **Step 2: Run the broadest feasible backend verification**

Run: `mvn -q test`

Expected: every non-MySQL test passes; record exact integration result if the environment blocks the local database connection.

- [x] **Step 3: Write the recovery report**

Include recovered files, architecture notes, exact test commands/results including the red/green characterization, commit hash, and environment concerns.

- [x] **Step 4: Commit all recovered S13 work on the existing branch**

Run: `git add backend docs/superpowers/plans && git commit -m 'feat: recover monitor and trace backend'`

Expected: one commit containing all inherited S13 source, tests, documentation, and recovery plan. The report outside the worktree is intentionally excluded.
