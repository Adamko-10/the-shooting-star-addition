package dev.ss05.halley;

import net.minecraft.util.Mth;

/**
 * SA-04 Proxima's sizes for one cast (docs/SS-07-PROXIMA.md): a wide, shallow scorch with a glassy bowl in the middle
 * ("a large radius but small block damage depth-wise"). The power slider scales every radius, and the bowl's depth more
 * gently. They ride along in the effect packet, so every client draws the same strike.
 *
 * @param bowlRadius   the glassy bowl at ground zero, in blocks
 * @param charRadius   out to here trunks char and the ground turns to blackstone and basalt
 * @param scorchRadius out to here leaves, grass, flowers, snow and crops burn away and the topsoil scorches
 * @param depth        the bowl's depth at its middle, in blocks
 * @param power100     the power slider, times 100
 */
public record ProximaParams(int bowlRadius, int charRadius, int scorchRadius, int depth, int power100) {
    /** At power 1: the bowl, the charred middle and the scorched ring, in blocks (doubled 2026-11-05, the user: "decently larger"); the bowl's depth (decided: 6). */
    public static final int BOWL = 36;
    public static final int CHAR = 100;
    public static final int SCORCH = 220;
    public static final int DEPTH = 6;
    private static final int MAGIC = 0x50524F58;
    private static final int VERSION = 1;

    public static ProximaParams of(float power) {
        float p = Mth.clamp(power, CastTuning.MIN_POWER, CastTuning.MAX_POWER);
        return new ProximaParams(Math.max(4, Math.round(BOWL * p)), Math.max(8, Math.round(CHAR * p)),
            Math.max(12, Math.round(SCORCH * p)), Math.max(2, Math.round(DEPTH * (float) Math.sqrt(p))), Math.round(p * 100.0F));
    }

    public float power() {
        return this.power100 / 100.0F;
    }

    public int[] encode() {
        return new int[] {MAGIC, VERSION, this.bowlRadius, this.charRadius, this.scorchRadius, this.depth, this.power100};
    }

    /** Reads what {@link #encode()} wrote; anything unexpected falls back to the default sizes. */
    public static ProximaParams decode(int[] data) {
        if (data == null || data.length < 7 || data[0] != MAGIC || data[1] != VERSION) {
            return of(1.0F);
        }
        return new ProximaParams(data[2], data[3], data[4], data[5], data[6]);
    }
}
