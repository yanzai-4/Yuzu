package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import org.springframework.stereotype.Component;

import java.util.List;

/** v0.0.12 🍊 Default code-only summarizer: latest START/STATE text of the most relevant active module, clipped to ~80 chars. */
@Component
public class CodeBubbleSummarizer implements BubbleSummarizer {

    /** v0.0.12 🍊 The board's default (already the focus module's latest text), else the latest START/STATE text, else idle. */
    @Override
    public String summarize(AgentId agentId, List<ModuleEvent> recentEvents, String currentDefault) {
        if (currentDefault != null && !currentDefault.isBlank()) {
            return BubbleText.summary(currentDefault, BubbleText.IDLE_SUMMARY);
        }
        if (recentEvents != null) {
            for (int i = recentEvents.size() - 1; i >= 0; i--) {
                ModuleEvent event = recentEvents.get(i);
                if ((event.phase() == EventPhase.START || event.phase() == EventPhase.STATE)
                        && !event.text().isBlank()) {
                    return BubbleText.summary(event.text(), BubbleText.IDLE_SUMMARY);
                }
            }
        }
        return BubbleText.IDLE_SUMMARY;
    }
}
