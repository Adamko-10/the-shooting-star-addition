package dev.ss05.halley.client;

import javax.annotation.Nullable;

/**
 * What a frame shows while Stellar Convergence's split screen runs (1.9.0; Adam, after its first test: "make everything
 * else invisible in that screen", "cuts sfx"): each part's frame shows only its own weapon, so our renderers skip the
 * other strikes' effects ({@link #shows}; The Shooting Star's own are taken out of its effect list by the film), and
 * while the camera visits a part that isn't the live one the sound listener stays where it was ({@link #holdListener};
 * SoundListenerMixin), so the sounds don't jump about with the camera.
 */
public final class FilmView {
    public static final int HALLEY = 1;
    public static final int LUNA = 2;
    public static final int PROXIMA = 4;
    /** Stellar Convergence's own effect (its sigil and shock ring): never in a part. */
    public static final int CONVERGENCE = 8;

    /** -1: everything shows (no split screen); else which of our effects this frame shows. */
    public static volatile int only = -1;
    /** True while this frame's camera is on a part that isn't the live one. */
    public static volatile boolean holdListener;
    /** Run at the start of every frame while the split screen plays (set by the film). */
    @Nullable
    public static volatile Runnable frameStart;

    private FilmView() {
    }

    public static boolean shows(int what) {
        int o = only;
        return o < 0 || (o & what) != 0;
    }

    /** From the start of every frame (HalleyClient), before anything is drawn or painted. */
    public static void runFrameStart() {
        Runnable hook = frameStart;
        if (hook != null) {
            hook.run();
        }
    }

    /** Back to showing everything (the film is over, or something failed). */
    public static void reset() {
        only = -1;
        holdListener = false;
    }
}
