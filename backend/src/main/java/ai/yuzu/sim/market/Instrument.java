package ai.yuzu.sim.market;

import ai.yuzu.common.error.NotFoundException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** v0.0.11 🍊 The simulated listed companies (citrus-themed), each with a base price and a per-minute volatility. */
public enum Instrument {
    CITR("Citrus Holdings", "42.00", 0.0012),
    LIME("Lime Logistics", "18.50", 0.0018),
    YUZU("Yuzu Labs", "128.00", 0.0022),
    PEEL("Peel Packaging", "7.25", 0.0026),
    ZEST("Zest Foods", "63.40", 0.0010);

    private final String company;
    private final BigDecimal basePrice;
    private final double volatility;

    /** v0.0.11 🍊 Declares an instrument. */
    Instrument(String company, String basePrice, double volatility) {
        this.company = company;
        this.basePrice = new BigDecimal(basePrice);
        this.volatility = volatility;
    }

    /** v0.0.11 🍊 Ticker symbol (the constant name, for example "YUZU"). */
    public String symbol() {
        return name();
    }

    /** v0.0.11 🍊 Company name shown next to the symbol. */
    public String company() {
        return company;
    }

    /** v0.0.11 🍊 Price around which the random walk moves. */
    public BigDecimal basePrice() {
        return basePrice;
    }

    /** v0.0.11 🍊 Typical log-price move per minute. */
    public double volatility() {
        return volatility;
    }

    /** v0.0.11 🍊 Stable seed of the instrument's price path (String.hashCode is specified, so it never changes). */
    long seed() {
        return name().hashCode() * 0x9E3779B97F4A7C15L;
    }

    /** v0.0.11 🍊 Finds an instrument by symbol, ignoring case and surrounding spaces. */
    public static Optional<Instrument> find(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        String wanted = symbol.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(instrument -> instrument.name().equals(wanted)).findFirst();
    }

    /** v0.0.11 🍊 The instrument for a symbol, or NOT_FOUND listing the available symbols. */
    public static Instrument require(String symbol) {
        return find(symbol).orElseThrow(() -> {
            String shown = String.valueOf(symbol).replaceAll("\\p{Cntrl}", "?").strip();
            shown = shown.length() > 20 ? shown.substring(0, 20) + "…" : shown;
            return (NotFoundException) new NotFoundException("Unknown symbol \"" + shown + "\". Available symbols: "
                    + String.join(", ", symbols()) + ".").with("symbol", shown).with("symbols", symbols());
        });
    }

    /** v0.0.11 🍊 Every symbol in declaration order. */
    public static List<String> symbols() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
