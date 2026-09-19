package ai.yuzu.llm.usage;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * v0.0.8 🍊 Maps the different usage shapes of OpenAI-compatible providers to one {@link Usage}.
 *
 * <ul>
 *   <li>OpenAI Chat Completions: {@code prompt_tokens_details.cached_tokens},
 *       {@code completion_tokens_details.reasoning_tokens};</li>
 *   <li>Responses-style: {@code input_tokens}, {@code input_tokens_details.cached_tokens}, {@code output_tokens};</li>
 *   <li>DeepSeek-style: {@code prompt_cache_hit_tokens} / {@code prompt_cache_miss_tokens};</li>
 *   <li>Moonshot/Kimi-style: top-level {@code cached_tokens}.</li>
 * </ul>
 */
public final class UsageNormalizer {

    private UsageNormalizer() {
    }

    /** v0.0.8 🍊 Normalizes a {@code usage} node; a missing node yields {@link Usage#NONE}. */
    public static Usage from(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return Usage.NONE;
        }
        Integer hit = intOrNull(usage, "prompt_cache_hit_tokens");
        Integer miss = intOrNull(usage, "prompt_cache_miss_tokens");
        int prompt = firstNonNull(intOrNull(usage, "prompt_tokens"), intOrNull(usage, "input_tokens"),
                hit != null && miss != null ? hit + miss : null, 0);
        Integer cached = firstNonNullOrNull(
                intOrNull(usage.path("prompt_tokens_details"), "cached_tokens"),
                intOrNull(usage.path("input_tokens_details"), "cached_tokens"),
                hit,
                intOrNull(usage, "cached_tokens"));
        int cacheWrite = firstNonNull(
                intOrNull(usage.path("prompt_tokens_details"), "cache_write_tokens"),
                intOrNull(usage, "cache_creation_input_tokens"), null, 0);
        int completion = firstNonNull(intOrNull(usage, "completion_tokens"), intOrNull(usage, "output_tokens"),
                null, 0);
        int reasoning = firstNonNull(
                intOrNull(usage.path("completion_tokens_details"), "reasoning_tokens"),
                intOrNull(usage.path("output_tokens_details"), "reasoning_tokens"), null, 0);
        return new Usage(prompt, cached == null ? 0 : cached, cacheWrite, completion, reasoning, false,
                cached != null);
    }

    /** v0.0.8 🍊 Integer field or null when absent. */
    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asInt();
    }

    /** v0.0.8 🍊 First non-null of three candidates, else the fallback. */
    private static int firstNonNull(Integer a, Integer b, Integer c, int fallback) {
        return a != null ? a : b != null ? b : c != null ? c : fallback;
    }

    /** v0.0.8 🍊 First non-null candidate, or null when all are null. */
    private static Integer firstNonNullOrNull(Integer... candidates) {
        for (Integer candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
