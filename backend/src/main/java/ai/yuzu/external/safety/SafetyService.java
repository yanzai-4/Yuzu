package ai.yuzu.external.safety;

import ai.yuzu.common.security.SecretScanner;
import ai.yuzu.agent.runtime.AgentContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.0.16 🍊 Facade of the safety reviews used by the pipelines.
 *
 * <p>Gate: a code-level secret scan blocks credentials instantly (no model call), otherwise the AI gate
 * decides. Mask: secrets are always redacted by code; content produced only by our own code (trusted) skips
 * the AI review; everything else is reviewed and only the offending passages are masked. Every block or mask
 * becomes a security incident visible in the UI.</p>
 */
@Service
public class SafetyService {

    /** v0.0.16 🍊 Result of the inbound gate. */
    public record GateResult(boolean allowed, List<String> violations, String userFacingReason) {
    }

    /** v0.0.16 🍊 Result of the outbound mask: the text the agent may read plus what was masked and why. */
    public record MaskResult(String content, List<String> maskReasons) {
    }

    private final SafetyGateModule gate;
    private final SafetyMaskModule mask;
    private final SecurityIncidentService incidents;

    /** v0.0.16 🍊 Injects collaborators. */
    public SafetyService(SafetyGateModule gate, SafetyMaskModule mask, SecurityIncidentService incidents) {
        this.gate = gate;
        this.mask = mask;
        this.incidents = incidents;
    }

    /** v0.0.16 🍊 Inbound review of content about to enter the agent's mind. */
    public GateResult gate(AgentContext ctx, String content, String source) {
        List<SecretScanner.Finding> secrets = SecretScanner.scan(content);
        if (!secrets.isEmpty()) {
            List<String> reasons = secrets.stream().map(f -> "contains a " + f.kind().toLowerCase()).distinct().toList();
            incidents.record(ctx.agentId(), SecurityIncidentService.Stage.INBOUND, "BLOCKED", reasons, content,
                    ctx.traceId());
            return new GateResult(false, reasons,
                    "The message contains what looks like a credential, so I did not process it. Never share keys or passwords in chat.");
        }
        SafetyVerdict verdict = gate.run(ctx, new SafetyReviewModule.Input(content, source));
        if (verdict.safe()) {
            return new GateResult(true, List.of(), null);
        }
        incidents.record(ctx.agentId(), SecurityIncidentService.Stage.INBOUND, "BLOCKED", verdict.violations(), content,
                ctx.traceId());
        return new GateResult(false, verdict.violations(), verdict.userFacingReason());
    }

    /** v0.0.16 🍊 Outbound review of tool results; trusted (code-only) results skip the AI review. */
    public MaskResult mask(AgentContext ctx, String content, String source, boolean trusted) {
        String redacted = SecretScanner.redact(content);
        List<String> reasons = new ArrayList<>();
        if (!redacted.equals(content)) {
            reasons.add("credential redacted by code");
        }
        if (trusted) {
            recordIfNeeded(ctx, reasons, content);
            return new MaskResult(redacted, reasons);
        }
        SafetyVerdict verdict = mask.run(ctx, new SafetyReviewModule.Input(redacted, source));
        String result = redacted;
        if (verdict.violations().contains(SafetyReviewModule.UNAVAILABLE)) {
            result = "[withheld: the safety check was unavailable]";
            reasons.add("safety check unavailable; content withheld");
        } else if (!verdict.safe()) {
            result = ContentMasker.apply(redacted, verdict.masks());
            verdict.masks().forEach(m -> reasons.add(m.reason()));
        }
        recordIfNeeded(ctx, reasons, content);
        return new MaskResult(result, reasons);
    }

    /** v0.0.16 🍊 Records an OUTBOUND incident when something was redacted or masked. */
    private void recordIfNeeded(AgentContext ctx, List<String> reasons, String original) {
        if (!reasons.isEmpty()) {
            incidents.record(ctx.agentId(), SecurityIncidentService.Stage.OUTBOUND, "MASKED", reasons, original,
                    ctx.traceId());
        }
    }
}
