package ai.yuzu.chat;

import ai.yuzu.room.RoomMember;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.0.5 🍊 Extracts @mentions from message text against the room's member names.
 *
 * <p>Names may contain spaces ("Blood Orange"), so the longest matching name wins. A mention must start
 * the text or follow a non-word character (so e-mail addresses are ignored) and must end at a non-word
 * character. {@code @all} mentions everyone.</p>
 */
public final class MentionParser {

    private MentionParser() {
    }

    /** v0.0.5 🍊 Parsed mentions: member ids in order of first appearance plus the @all flag. */
    public record Mentions(List<String> memberIds, boolean all) {
    }

    /** v0.0.5 🍊 Parses the text against the given members. */
    public static Mentions parse(String text, List<RoomMember> members) {
        if (text == null || text.indexOf('@') < 0) {
            return new Mentions(List.of(), false);
        }
        List<RoomMember> byLength = new ArrayList<>(members);
        byLength.sort(Comparator.comparingInt((RoomMember m) -> m.name().length()).reversed());
        Set<String> ids = new LinkedHashSet<>();
        boolean all = false;
        int i = 0;
        while (i < text.length()) {
            int at = text.indexOf('@', i);
            if (at < 0) {
                break;
            }
            if (at > 0 && isWordChar(text.charAt(at - 1))) {
                i = at + 1;
                continue;
            }
            int start = at + 1;
            if (matchesAt(text, start, "all")) {
                all = true;
                i = start + 3;
                continue;
            }
            boolean matched = false;
            for (RoomMember member : byLength) {
                if (matchesAt(text, start, member.name())) {
                    ids.add(member.id());
                    i = start + member.name().length();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                i = start;
            }
        }
        return new Mentions(List.copyOf(ids), all);
    }

    /** v0.0.5 🍊 Renders "@Name" for a member (used by agents replying to someone). */
    public static String tag(String name) {
        return "@" + name;
    }

    /** v0.0.5 🍊 Case-insensitive match of a name at a position, followed by a word boundary. */
    private static boolean matchesAt(String text, int start, String name) {
        int end = start + name.length();
        if (name.isEmpty() || end > text.length() || !text.regionMatches(true, start, name, 0, name.length())) {
            return false;
        }
        return end == text.length() || !isWordChar(text.charAt(end));
    }

    /** v0.0.5 🍊 Letters, digits and underscore count as word characters. */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
