package ai.yuzu.internal.intake;

import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.stereotype.Service;

/**
 * v0.0.22 🍊 Delivers code-made notices (ticket assignments, approvals, governance hints) into an agent's mind.
 *
 * <p>Asynchronous, through the normal intake (planning ∥ cognition → pool), with a first-person attribution
 * naming the source, so the notice can never pose as the agent's own thought.</p>
 */
@Service
public class NoticeService {

    private final AgentRuntimeManager runtimes;
    private final IntakePipeline intake;
    private final AsyncRunner runner;
    private final NaturalTime time;

    /** v0.0.22 🍊 Injects collaborators. */
    public NoticeService(AgentRuntimeManager runtimes, IntakePipeline intake, AsyncRunner runner, NaturalTime time) {
        this.runtimes = runtimes;
        this.intake = intake;
        this.runner = runner;
        this.time = time;
    }

    /** v0.0.22 🍊 Sends a notice to an agent (ignored when the agent has no runtime, e.g. retired). */
    public void notify(AgentId target, String source, String text, String traceId, int causalDepth) {
        AgentRuntime runtime = runtimes.find(target).orElse(null);
        if (runtime == null) {
            return;
        }
        runner.run("notice", target.value(), () -> intake.deliver(runtime.context(traceId, null, time),
                new Stimulus.NoticeStimulus(source, text), text,
                "a notice from " + source + " at " + time.compact(time.nowInstant()), causalDepth));
    }
}
