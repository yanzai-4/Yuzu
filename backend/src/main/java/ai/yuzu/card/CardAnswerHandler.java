package ai.yuzu.card;

/** v0.0.19 🍊 Handles answers of one card purpose (QUESTION → agent's mind, TRADE_APPROVAL → execute trade, ...). */
public interface CardAnswerHandler {

    /** v0.0.19 🍊 The purpose this handler serves. */
    String purpose();

    /** v0.0.19 🍊 Called once, asynchronously, after a card was answered. */
    void onAnswer(CardAnswer answer);
}
