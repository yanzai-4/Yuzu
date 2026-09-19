package ai.yuzu.chat;

import ai.yuzu.common.time.NaturalTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.5 🍊 REST endpoints of the group chat (history and posting). */
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class ChatController {

    private final ChatService chat;
    private final NaturalTime time;

    /** v0.0.5 🍊 Injects the chat service. */
    public ChatController(ChatService chat, NaturalTime time) {
        this.chat = chat;
        this.time = time;
    }

    /** v0.0.5 🍊 Latest messages, or older ones when {@code beforeSeq} is given (ascending). */
    @GetMapping
    public List<ChatMessageView> history(@PathVariable String roomId,
                                         @RequestParam(required = false) Long beforeSeq,
                                         @RequestParam(defaultValue = "50") int limit) {
        return chat.history(roomId, beforeSeq, limit);
    }

    /** v0.0.5 🍊 Posts a message from a human; mentions are parsed server-side. */
    @PostMapping
    public ChatMessageView post(@PathVariable String roomId, @Valid @RequestBody PostMessageRequest request) {
        return chat.postHuman(roomId, request.userId(), request.content()).toView(time);
    }

    /** v0.0.5 🍊 Body of a human post. */
    public record PostMessageRequest(@NotBlank String userId, @NotBlank String content) {
    }
}
