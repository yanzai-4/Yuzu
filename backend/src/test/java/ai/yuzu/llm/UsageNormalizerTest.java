package ai.yuzu.llm;

import ai.yuzu.llm.usage.Usage;
import ai.yuzu.llm.usage.UsageNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.8 🍊 Usage normalization across OpenAI, DeepSeek, Kimi and Responses-style payloads. */
class UsageNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** v0.0.8 🍊 OpenAI Chat Completions. */
    @Test
    void openAi() throws Exception {
        Usage u = UsageNormalizer.from(mapper.readTree("""
                {"prompt_tokens":2000,"completion_tokens":300,"prompt_tokens_details":{"cached_tokens":1536},
                 "completion_tokens_details":{"reasoning_tokens":128}}"""));
        assertThat(u).isEqualTo(new Usage(2000, 1536, 0, 300, 128, false, true));
    }

    /** v0.0.8 🍊 DeepSeek hit/miss fields. */
    @Test
    void deepSeek() throws Exception {
        Usage u = UsageNormalizer.from(mapper.readTree("""
                {"prompt_cache_hit_tokens":900,"prompt_cache_miss_tokens":100,"completion_tokens":50}"""));
        assertThat(u.promptTokens()).isEqualTo(1000);
        assertThat(u.cachedTokens()).isEqualTo(900);
        assertThat(u.cacheReported()).isTrue();
    }

    /** v0.0.8 🍊 Kimi top-level cached_tokens and a provider without cache fields. */
    @Test
    void kimiAndNoCache() throws Exception {
        assertThat(UsageNormalizer.from(mapper.readTree("{\"prompt_tokens\":500,\"completion_tokens\":5,\"cached_tokens\":256}")).cachedTokens())
                .isEqualTo(256);
        Usage none = UsageNormalizer.from(mapper.readTree("{\"prompt_tokens\":500,\"completion_tokens\":5}"));
        assertThat(none.cacheReported()).isFalse();
        assertThat(none.cachedTokens()).isZero();
    }

    /** v0.0.8 🍊 Responses-style input/output fields. */
    @Test
    void responsesStyle() throws Exception {
        Usage u = UsageNormalizer.from(mapper.readTree("""
                {"input_tokens":800,"output_tokens":40,"input_tokens_details":{"cached_tokens":512},
                 "output_tokens_details":{"reasoning_tokens":10}}"""));
        assertThat(u).isEqualTo(new Usage(800, 512, 0, 40, 10, false, true));
    }
}
