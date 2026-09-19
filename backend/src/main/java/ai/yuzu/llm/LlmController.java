package ai.yuzu.llm;

import ai.yuzu.llm.usage.TokenMeter;
import ai.yuzu.llm.usage.UsageSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v0.0.11 🍊 REST endpoints of the model layer: per-tier connectivity test and usage metrics. */
@RestController
@RequestMapping("/api")
public class LlmController {

    private final CapabilityProbe probe;
    private final TokenMeter meter;

    /** v0.0.11 🍊 Injects collaborators. */
    public LlmController(CapabilityProbe probe, TokenMeter meter) {
        this.probe = probe;
        this.meter = meter;
    }

    /** v0.0.11 🍊 Tests every tier's model and reports the working JSON strategy. */
    @PostMapping("/settings/llm/test")
    public CapabilityProbe.TestResult test() {
        return probe.test();
    }

    /** v0.0.11 🍊 Token usage and cache-hit metrics. */
    @GetMapping("/usage")
    public UsageSnapshot usage() {
        return meter.snapshot();
    }
}
