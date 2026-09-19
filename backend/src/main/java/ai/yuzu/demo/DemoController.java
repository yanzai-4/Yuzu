package ai.yuzu.demo;

import ai.yuzu.agent.AgentView;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.29 🍊 One-click demo setup: {@code POST /api/demo/seed?roomId=} returns the four demo coworkers. */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoSeeder seeder;
    private final NaturalTime time;

    /** v0.0.29 🍊 Injects collaborators. */
    public DemoController(DemoSeeder seeder, NaturalTime time) {
        this.seeder = seeder;
        this.time = time;
    }

    /** v0.0.29 🍊 Seeds the room (idempotent) and returns Yuzu, Lime, Kumquat and Pomelo in that order. */
    @PostMapping("/seed")
    public List<AgentView> seed(@RequestParam(required = false) String roomId) {
        return seeder.seed(roomId).stream().map(agent -> agent.toView(time)).toList();
    }
}
