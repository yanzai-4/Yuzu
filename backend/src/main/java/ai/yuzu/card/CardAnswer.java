package ai.yuzu.card;

import java.util.List;

/**
 * v0.0.19 🍊 A validated answer to a card, handed to the handler registered for the card's purpose.
 *
 * @param labels   the chosen option labels (in order)
 * @param other    free text typed in "Other" (null when not used)
 * @param payload  JSON payload stored with the card (for example a pending trade id)
 */
public record CardAnswer(String cardId, String agentId, String roomId, String purpose, String prompt,
                         List<String> labels, String other, String answeredById, String answeredByName,
                         String payload, String traceId) {
}
