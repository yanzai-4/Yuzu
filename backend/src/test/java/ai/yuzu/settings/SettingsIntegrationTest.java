package ai.yuzu.settings;

import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.7 🍊 Settings persistence, key encryption at rest, masking and validation. */
@IntegrationTest
@AutoConfigureMockMvc
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SecretVault vault;

    @Autowired
    private SettingsService settings;

    /** v0.0.7 🍊 The key is stored encrypted, returned masked, and decrypts back to the original. */
    @Test
    void keyIsEncryptedAndMasked() throws Exception {
        JsonNode view = json(mvc.perform(put("/api/settings/llm/key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"sk-test-1234567890abcd\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(view.get("hasKey").asBoolean()).isTrue();
        assertThat(view.get("apiKeyMasked").asText()).isEqualTo("sk-…abcd");
        assertThat(view.toString()).doesNotContain("1234567890");
        String stored = jdbc.sql("SELECT v FROM app_setting WHERE k = 'llm.key.OPENAI'").query(String.class).single();
        assertThat(stored).doesNotContain("sk-test");
        assertThat(settings.requireApiKey()).isEqualTo("sk-test-1234567890abcd");
    }

    /** v0.0.7 🍊 Tier models and provider are saved; invalid URLs are rejected. */
    @Test
    void updateTiersAndValidate() throws Exception {
        JsonNode view = json(mvc.perform(put("/api/settings/llm").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"provider":"EDGEONE","baseUrl":"","tiers":{
                          "IMPORTANT":{"model":"@makers/kimi-k2.6","reasoningEffort":"","maxOutputTokens":3000},
                          "DEFAULT":{"model":"@makers/deepseek-v4-flash","maxOutputTokens":1500},
                          "LIGHT":{"model":"@makers/minimax-m2.7","maxOutputTokens":400}}}
                        """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(view.get("baseUrl").asText()).isEqualTo("https://ai-gateway.edgeone.link/v1");
        assertThat(view.get("tiers").get("DEFAULT").get("model").asText()).isEqualTo("@makers/deepseek-v4-flash");
        assertThat(view.get("hasKey").asBoolean()).isFalse();

        mvc.perform(put("/api/settings/llm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"CUSTOM\",\"baseUrl\":\"ftp://evil\"}")).andExpect(status().isBadRequest());
        String bootstrap = mvc.perform(get("/api/bootstrap")).andReturn().getResponse().getContentAsString();
        assertThat(json(bootstrap).get("settings").get("provider").asText()).isEqualTo("EDGEONE");

        mvc.perform(put("/api/settings/llm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"OPENAI\"}")).andExpect(status().isOk());
    }

    /** v0.0.7 🍊 A ciphertext cannot be decrypted for another purpose (provider binding). */
    @Test
    void ciphertextIsBoundToPurpose() {
        String cipher = vault.encrypt("secret-value", "yuzu:llm-key:OPENAI");
        assertThat(vault.decrypt(cipher, "yuzu:llm-key:OPENAI")).isEqualTo("secret-value");
        assertThatThrownBy(() -> vault.decrypt(cipher, "yuzu:llm-key:EDGEONE")).isInstanceOf(IllegalStateException.class);
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
