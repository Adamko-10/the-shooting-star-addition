package dev.ss05.halley;

import java.util.Locale;
import net.minecraft.util.Mth;

/**
 * SA-04 Proxima's sizes for one cast (docs/SS-07-PROXIMA.md): a wide, shallow scorch with a glassy bowl in the middle
 * ("a large radius but small block damage depth-wise"). The power slider scales every radius, and the bowl's depth more
 * gently. They ride along in the effect packet, so every client draws the same strike.
 *
 * @param bowlRadius     the glassy bowl at ground zero, in blocks
 * @param charRadius     out to here trunks char and the ground turns to blackstone and basalt
 * @param scorchRadius   out to here leaves, grass, flowers, snow and crops burn away and the topsoil scorches
 * @param depth          the bowl's depth at its middle, in blocks
 * @param power100       the power slider, times 100
 * @param radiationTicks how long the bowl stays dangerous after the impact (0: not at all; {@link #PERMANENT}: for good)
 * @param tier           {@link #CAST} (from the remote), or a random solar flare's tier ({@link #MINOR}, {@link #MAJOR},
 *                       {@link #CATACLYSM}; see world/SolarFlares)
 */
public record ProximaParams(int bowlRadius, int charRadius, int scorchRadius, int depth, int power100, int radiationTicks, int tier) {
    /** At power 1: the bowl, the charred middle and the scorched ring, in blocks (doubled 2026-11-05, the user: "decently larger"); the bowl's depth (decided: 6). */
    public static final int BOWL = 36;
    public static final int CHAR = 100;
    public static final int SCORCH = 220;
    public static final int DEPTH = 6;
    private static final int MAGIC = 0x50524F58;
    private static final int VERSION = 3;
    public static final int PERMANENT = -1;
    /** Cast from the remote: the whole strike, film and all. */
    public static final int CAST = 0;
    /** A random flare that only lights up the sky. */
    public static final int MINOR = 1;
    /** A random flare whose light burns: exposed creatures catch fire, plants burn away; no front, no crater. */
    public static final int MAJOR = 2;
    /** A random flare with its coronal mass ejection: the whole strike, without the space intro or a film. */
    public static final int CATACLYSM = 3;

    /** The sizes at this power from the server's config ({@link ProximaConfig}). */
    public static ProximaParams of(float power) {
        ProximaConfig.Tuning t = ProximaConfig.tuning();
        float p = Mth.clamp(power, CastTuning.MIN_POWER, CastTuning.MAX_POWER);
        int radiation = t.permanentRadiation() ? PERMANENT : (int) Math.round(t.radiationMinutes() * 1200.0 * Math.max(0.5, Math.sqrt(p)));
        return new ProximaParams(Math.max(4, Math.round(t.bowlRadius() * p)), Math.max(8, Math.round(t.charRadius() * p)),
            Math.max(12, Math.round(t.scorchRadius() * p)), Math.max(1, Math.round(t.bowlDepth() * (float) Math.sqrt(p))),
            Math.round(p * 100.0F), radiation, CAST);
    }

    /** A random flare's sizes at this power, of this tier. */
    public static ProximaParams flare(float power, int tier) {
        ProximaParams p = of(power);
        return new ProximaParams(p.bowlRadius, p.charRadius, p.scorchRadius, p.depth, p.power100, p.radiationTicks, tier);
    }

    public float power() {
        return this.power100 / 100.0F;
    }

    /** A random solar flare (nobody cast it: no film, no music, it starts at the flash). */
    public boolean natural() {
        return this.tier != CAST;
    }

    /** Whether its coronal mass ejection comes: the front, ground zero, the bowl and the scorch. */
    public boolean cme() {
        return this.tier == CAST || this.tier == CATACLYSM;
    }

    /** Whether its light burns (a minor flare's only shows). */
    public boolean burns() {
        return this.tier != MINOR;
    }

    /** The plan's tick it starts at: a random flare starts at the flash (it has no space intro). */
    public int startTick() {
        return this.natural() ? ProximaPlan.FLASH : 0;
    }

    /** The flare's class from its power, logarithmic like the real ones: 0.1x about C1, 1x M5, 3x X3, 10x X25. */
    public static String flareClass(float power) {
        double flux = Math.pow(10.0, -4.3 + 1.7 * Math.log10(Math.max(0.01F, power)));
        if (flux >= 1.0E-4) {
            return String.format(Locale.ROOT, "X%.1f", flux / 1.0E-4);
        }
        if (flux >= 1.0E-5) {
            return String.format(Locale.ROOT, "M%.1f", flux / 1.0E-5);
        }
        return String.format(Locale.ROOT, "C%.1f", flux / 1.0E-6);
    }

    public int[] encode() {
        return new int[] {MAGIC, VERSION, this.bowlRadius, this.charRadius, this.scorchRadius, this.depth, this.power100,
            this.radiationTicks, this.tier};
    }

    /** Reads what {@link #encode()} wrote; anything unexpected falls back to the default sizes. */
    public static ProximaParams decode(int[] data) {
        if (data == null || data.length < 9 || data[0] != MAGIC || data[1] != VERSION) {
            return of(1.0F);
        }
        return new ProximaParams(data[2], data[3], data[4], data[5], data[6], data[7], Mth.clamp(data[8], CAST, CATACLYSM));
    }
}
