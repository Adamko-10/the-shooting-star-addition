package dev.ss05.halley;

/**
 * How one cast of Halley or Luna was set on the Stellar Remote: the three sliders under each skill in the remote's
 * menu (H), the same as under The Shooting Star's own skills. The remote sends them with every cast; the server
 * clamps them to the skill's range.
 *
 * @param power  how big the whole strike is, as a factor of the configured sizes (0.1 to 10)
 * @param damage what each hit does to a creature; {@link Float#POSITIVE_INFINITY} (the default) erases it outright
 * @param speed  how fast the whole skill plays, film and all (0.3 to 3)
 */
public record CastTuning(float power, float damage, float speed) {
    /** The smallest and largest power the remote's slider offers (like The Shooting Star's Seven Stars). */
    public static final float MIN_POWER = 0.1F;
    public static final float MAX_POWER = 10.0F;
    /** The remote's speed slider's range (its own, for every skill). */
    public static final float MIN_SPEED = 0.3F;
    public static final float MAX_SPEED = 3.0F;
    /** Everything at its default: the configured sizes, erasing hits, normal speed. */
    public static final CastTuning DEFAULT = new CastTuning(1.0F, Float.POSITIVE_INFINITY, 1.0F);

    public CastTuning {
        power = Float.isNaN(power) ? 1.0F : Math.max(MIN_POWER, Math.min(MAX_POWER, power));
        damage = Float.isNaN(damage) || damage <= 0.0F ? Float.POSITIVE_INFINITY : damage;
        speed = Float.isNaN(speed) ? 1.0F : Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Whether a hit erases outright (the damage slider at its end, ∞), the way the other skills' strike zones do. */
    public boolean erases() {
        return this.damage == Float.POSITIVE_INFINITY;
    }

    /** A blast hit of {@code damage}, held to the damage slider (unless that is at ∞, which leaves it as it is). */
    public float cap(float damage) {
        return this.erases() ? damage : Math.min(damage, this.damage);
    }
}
