package ai.yuzu.trace;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * v0.0.30 🍊 The exact JSON sent to and received from the model provider (contract type {@code LlmCallPayload}).
 *
 * <p>Credentials are never part of it: the API key only travels in an HTTP header, which is not recorded.</p>
 */
public record LlmCallPayloadView(String id, String agentId, String model, JsonNode request, JsonNode response) {
}
