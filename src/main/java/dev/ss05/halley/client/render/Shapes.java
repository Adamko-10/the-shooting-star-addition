package dev.ss05.halley.client.render;

/** Shape numbers understood by the halley_glow shader (keep in step with halley_glow.fsh). */
public final class Shapes {
    public static final int GLOW = 0;
    public static final int RING = 1;
    public static final int GLINT = 2;
    /** Variant 0 = ion tail, 1 = dust tail, 2 = plasma wake. */
    public static final int STREAK = 3;
    public static final int BOW = 4;
    public static final int CHEVRON = 5;
    public static final int RETICLE = 6;
    public static final int DISC = 7;
    public static final int DASH = 8;
    public static final int SHOCK = 9;
    /** A lens flare: a long, thin line across the screen (draw it with {@link GlowBatch#rect}). */
    public static final int FLARE = 10;
    /** A wall of snow and light standing on the ground: x along it, y up it (draw it with {@link GlowBatch#wall}). */
    public static final int WALL = 11;

    /** GLOW variant: only the haze, without the white-hot core. */
    public static final int HAZE = 1;

    public static final int ION = 0;
    public static final int DUST = 1;
    public static final int PLASMA = 2;

    private Shapes() {
    }
}
