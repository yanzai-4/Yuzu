package ai.yuzu.bootstrap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** v0.0.5 🍊 {@code GET /api/bootstrap}: everything the UI needs to render a room plus the stream cursor. */
@RestController
@RequestMapping("/api")
public class BootstrapController {

    private final BootstrapService bootstrap;

    /** v0.0.5 🍊 Injects the bootstrap service. */
    public BootstrapController(BootstrapService bootstrap) {
        this.bootstrap = bootstrap;
    }

    /** v0.0.5 🍊 Returns the room snapshot (contract type {@code Snapshot}). */
    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap(@RequestParam(defaultValue = "room-0001") String roomId) {
        return bootstrap.snapshot(roomId);
    }
}
