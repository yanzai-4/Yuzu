package ai.yuzu.sim.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** v0.0.11 🍊 Deterministic prices: price = f(instrument, minute), a stateless multi-scale random walk. */
final class PriceModel {

    /** v0.0.11 🍊 Number of noise layers; layer k changes every 2^k minutes (1 minute to about 17 hours). */
    static final int OCTAVES = 11;

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    /** v0.0.11 🍊 Static helpers only. */
    private PriceModel() {
    }

    /** v0.0.11 🍊 The price at an epoch minute, rounded to cents. */
    static BigDecimal priceAt(Instrument instrument, long minute) {
        double logMove = 0;
        for (int octave = 0; octave < OCTAVES; octave++) {
            long span = 1L << octave;
            logMove += amplitude(instrument, octave) * smoothNoise(instrument.seed(), octave, (double) minute / span);
        }
        double price = instrument.basePrice().doubleValue() * Math.exp(logMove);
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).max(MIN_PRICE);
    }

    /** v0.0.11 🍊 Largest possible |log(price / base)|, a hard bound on how far a price can drift. */
    static double maxLogMove(Instrument instrument) {
        double total = 0;
        for (int octave = 0; octave < OCTAVES; octave++) {
            total += amplitude(instrument, octave);
        }
        return total;
    }

    /** v0.0.11 🍊 Random-walk scaling: a layer spanning n minutes moves about volatility × √n. */
    private static double amplitude(Instrument instrument, int octave) {
        return instrument.volatility() * Math.sqrt(1L << octave);
    }

    /** v0.0.11 🍊 Value noise in [-1, 1] smoothly interpolated between integer knots. */
    private static double smoothNoise(long seed, int octave, double t) {
        double floor = Math.floor(t);
        long knot = (long) floor;
        double f = t - floor;
        double eased = f * f * (3 - 2 * f);
        double a = noise(seed, octave, knot);
        double b = noise(seed, octave, knot + 1);
        return a + (b - a) * eased;
    }

    /** v0.0.11 🍊 Hash of (seed, octave, knot) mapped to [-1, 1). */
    private static double noise(long seed, int octave, long knot) {
        long z = mix(seed ^ (octave * 0xD1B54A32D192ED03L) ^ (knot * 0x9E3779B97F4A7C15L));
        return ((z >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /** v0.0.11 🍊 SplitMix64 finalizer: spreads every input bit over the output. */
    private static long mix(long value) {
        long z = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
