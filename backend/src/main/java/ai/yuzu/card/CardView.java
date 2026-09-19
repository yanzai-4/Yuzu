package ai.yuzu.card;

import java.util.List;

/** v0.0.19 🍊 A question or approval card as shown in the chat (contract type {@code Card}). */
public record CardView(String id, String agentId, String roomId, String kind, String messageId, String prompt,
                       List<Option> options, boolean allowOther, String status, Answer answer, String answeredByName,
                       String time, String answeredTime) {

    /** v0.0.19 🍊 One selectable option. */
    public record Option(String id, String label) {
    }

    /** v0.0.19 🍊 The human's answer. */
    public record Answer(List<String> optionIds, String otherText) {
    }
}
