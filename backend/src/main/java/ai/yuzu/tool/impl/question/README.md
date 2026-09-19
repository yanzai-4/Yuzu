# ai.yuzu.tool.impl.question

> v0.0.19 🍊 Asking humans.

- `AskUserTool` (`ask_user`, needs `ASK_USER`) — opens a question card with 2 to 6 options and an
  optional free-text answer, then returns `WAITING` immediately, so the other results of the batch are
  not held back. The answer later reaches the agent's mind through `QuestionAnswerHandler`: free text
  first passes the inbound safety gate, then goes through the intake (planning ∥ cognition → pool).
  Trusted output (code only).
