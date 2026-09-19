package ai.yuzu.card;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.19 🍊 {@code POST /api/cards/{cardId}/answer}: a human answers a question or approval card. */
@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cards;

    /** v0.0.19 🍊 Injects the card service. */
    public CardController(CardService cards) {
        this.cards = cards;
    }

    /** v0.0.19 🍊 Answers a card (first valid answer wins). */
    @PostMapping("/{cardId}/answer")
    public CardView answer(@PathVariable String cardId, @Valid @RequestBody AnswerRequest request) {
        return cards.answer(cardId, request.userId(), request.optionIds(), request.otherText());
    }

    /** v0.0.19 🍊 Body of an answer. */
    public record AnswerRequest(@NotBlank String userId, List<String> optionIds, String otherText) {
    }
}
