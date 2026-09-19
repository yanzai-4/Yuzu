package ai.yuzu.monitor;

/** v0.0.12 🍊 One running span as the status board sees it: module, declared desk state, latest text and recency tick. */
record ActiveSpan(String spanId, ModuleKind module, DeskState desk, String text, long tick) {
}
