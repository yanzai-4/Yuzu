package ai.yuzu.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** v0.0.6 🍊 The citrus fruits agents are named after, with their avatar key and color. */
public final class CitrusCatalog {

    /** v0.0.6 🍊 One citrus identity. */
    public record Citrus(String name, String avatarKey, String color) {
    }

    private static final List<Citrus> ALL = List.of(
            citrus("Yuzu", "#F4C430"), citrus("Lime", "#7BC043"), citrus("Kumquat", "#FF9F1C"),
            citrus("Pomelo", "#E6D96A"), citrus("Lemon", "#FFD91A"), citrus("Mandarin", "#FF8C00"),
            citrus("Grapefruit", "#FF6F61"), citrus("Clementine", "#FFA62B"), citrus("Tangerine", "#F28500"),
            citrus("Bergamot", "#B5CC4A"), citrus("Calamansi", "#8DC63F"), citrus("Sudachi", "#4CAF50"),
            citrus("Citron", "#E4D96F"), citrus("Blood Orange", "#D1462F"), citrus("Satsuma", "#FFA500"),
            citrus("Kabosu", "#6B8E23"));

    private CitrusCatalog() {
    }

    /** v0.0.6 🍊 Every citrus in catalog order. */
    public static List<Citrus> all() {
        return ALL;
    }

    /** v0.0.6 🍊 Finds a citrus by name (case-insensitive). */
    public static Optional<Citrus> byName(String name) {
        return ALL.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    /** v0.0.6 🍊 Picks a random citrus whose name is not taken (case-insensitive); empty when all are used. */
    public static Optional<Citrus> pickAvailable(Collection<String> takenNames) {
        Set<String> taken = takenNames.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Citrus> free = new ArrayList<>(ALL.stream()
                .filter(c -> !taken.contains(c.name().toLowerCase(Locale.ROOT))).toList());
        if (free.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(free.get(ThreadLocalRandom.current().nextInt(free.size())));
    }

    /** v0.0.6 🍊 Builds an entry whose avatar key is the lowercase dashed name. */
    private static Citrus citrus(String name, String color) {
        return new Citrus(name, name.toLowerCase(Locale.ROOT).replace(' ', '-'), color);
    }
}
