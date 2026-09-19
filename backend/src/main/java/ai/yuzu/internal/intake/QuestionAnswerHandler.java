package ai.yuzu.internal.intake;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.card.CardAnswer;
import ai.yuzu.card.CardAnswerHandler;
import ai.yuzu.card.CardService;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.external.safety.SafetyService;
import org.springframework.stereotype.Component;

/**
 * v0.0.19 🍊 Delivers answers of plain question cards to the asking agent's mind.
 *
 * <p>Free-text "Other" answers are outside input, so they pass the inbound safety gate first (blocked → yellow
 * notice in the chat). The answer never goes through any chat module.</p>
 */
@Component
public class QuestionAnswerHandler implements CardAnswerHandler {

    private final AgentRuntimeManager runtimes;
    private final IntakePipeline intake;
    private final SafetyService safety;
    private final ChatService chat;
    private final NaturalTime time;

    /** v0.0.19 🍊 Injects collaborators. */
    public QuestionAnswerHandler(AgentRuntimeManager runtimes, IntakePipeline intake, SafetyService safety,
                                 ChatService chat, NaturalTime time) {
        this.runtimes = runtimes;
        this.intake = intake;
        this.safety = safety;
        this.chat = chat;
        this.time = time;
    }

    /** v0.0.19 🍊 Purpose served. */
    @Override
    public String purpose() {
        return CardService.QUESTION;
    }

    /** v0.0.19 🍊 Safety-checks free text, then delivers the answer as a new stimulus. */
    @Override
    public void onAnswer(CardAnswer answer) {
        AgentRuntime runtime = runtimes.find(AgentId.of(answer.agentId())).orElse(null);
        if (runtime == null) {
            return;
        }
        AgentContext ctx = runtime.context(answer.traceId(), null, time);
        if (answer.other() != null) {
            SafetyService.GateResult gate = safety.gate(ctx, answer.other(), "a card answer from " + answer.answeredByName());
            if (!gate.allowed()) {
                chat.post(ChatPost.warning(answer.roomId(), answer.agentId(), runtime.profile().name(),
                        "@" + answer.answeredByName() + " I could not accept that answer: " + gate.userFacingReason(),
                        ctx.traceId()));
                return;
            }
        }
        String chosen = answer.labels().isEmpty() ? "" : String.join(", ", answer.labels());
        String text = answer.answeredByName() + " answered my question \"" + answer.prompt() + "\": "
                + (chosen.isEmpty() ? "" : chosen) + (answer.other() == null ? "" : (chosen.isEmpty() ? "" : " — ")
                + "\"" + answer.other() + "\"");
        intake.deliver(ctx, new Stimulus.QuestionAnswerStimulus(answer.cardId(), answer.answeredByName(), text), text,
                answer.answeredByName() + " (human) answered my question card at " + time.compact(time.nowInstant()), 0);
    }
}
