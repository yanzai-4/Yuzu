package ai.yuzu.llm.structured;

import ai.yuzu.llm.provider.LlmResult;

/**
 * v0.0.10 🍊 A validated structured answer.
 *
 * @param value    the parsed and validated record
 * @param attempts format attempts used (1 = first answer was valid)
 * @param strategy JSON strategy that produced it
 * @param last     raw result of the successful attempt
 */
public record StructuredResult<T>(T value, int attempts, OutputStrategy strategy, LlmResult last) {
}
