package ai.yuzu.room;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** v0.0.5 🍊 {@code POST /api/session/join}: create or resume a human identity by username. */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final HumanUserService users;

    /** v0.0.5 🍊 Injects the user service. */
    public SessionController(HumanUserService users) {
        this.users = users;
    }

    /** v0.0.5 🍊 Joins the room with the given username. */
    @PostMapping("/join")
    public UserView join(@RequestBody JoinRequest request,
                         @RequestParam(defaultValue = "room-0001") String roomId) {
        return users.join(roomId, request.username());
    }

    /** v0.0.5 🍊 Join request body. */
    public record JoinRequest(@NotBlank String username) {
    }
}
