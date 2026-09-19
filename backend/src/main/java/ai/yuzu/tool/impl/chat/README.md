# ai.yuzu.tool.impl.chat

> v0.0.18 🍊 Chat tools.

- `ChatPostTool` (`chat_post`) — posts as the agent; code requires an @mention (and `CHAT_MENTION_ALL` for
  @all), respects the room budget, sets causal depth + 1. Trusted output ("Posted at ...").
