package ai.yuzu.internal.consciousness;

import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.0.12 🍊 Renders pool messages for prompts as a JSON array of {"from", "text"} objects.
 *
 * <p>The {@code from} label is chosen by CODE from the message origin, never taken from text: SELF and
 * SUBCONSCIOUS both read "me (my own thought)" (the main consciousness cannot tell them apart), EXTERNAL uses
 * the attribution built by code, REVIEW reads "my behavior check warned me". All text is JSON-escaped, so
 * external content cannot close a string and forge a "me (my own thought)" entry.</p>
 */
@Component
public class PoolRenderer {

    /** v0.0.12 🍊 Label shown for the agent's own (and subconscious) thoughts. */
    public static final String OWN_THOUGHT = "me (my own thought)";

    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.12 🍊 Injects JSON and time helpers. */
    public PoolRenderer(Jsons jsons, NaturalTime time) {
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.12 🍊 Renders messages in order as a byte-stable JSON array. */
    public String render(List<PoolMessage> messages) {
        List<Map<String, String>> items = new ArrayList<>(messages.size());
        for (PoolMessage m : messages) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("from", label(m));
            item.put("text", m.text());
            items.add(item);
        }
        return jsons.write(items);
    }

    /** v0.0.12 🍊 Code-chosen source label of a message. */
    public String label(PoolMessage m) {
        return switch (m.origin()) {
            case SELF, SUBCONSCIOUS -> OWN_THOUGHT;
            case REVIEW -> "my behavior check warned me at " + time.compact(m.createdAt());
            case EXTERNAL -> m.attribution();
        };
    }
}
