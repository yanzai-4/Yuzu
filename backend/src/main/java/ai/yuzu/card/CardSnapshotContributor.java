package ai.yuzu.card;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.19 🍊 Adds the room's recent cards to the bootstrap snapshot. */
@Component
public class CardSnapshotContributor implements SnapshotContributor {

    private final CardService cards;

    /** v0.0.19 🍊 Injects the card service. */
    public CardSnapshotContributor(CardService cards) {
        this.cards = cards;
    }

    /** v0.0.19 🍊 Contributes cards. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.addAll("cards", cards.recent(roomId, 50));
    }
}
