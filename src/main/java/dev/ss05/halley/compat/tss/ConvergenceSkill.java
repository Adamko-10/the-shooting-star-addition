package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1211.skill.Skill;
import dev.ss05.halley.world.Codenames;

/**
 * SA-00 · Stellar Convergence as an entry on the Stellar Remote (the user's pick: its own skill, 2026-11-04). It has no
 * sliders of its own: each weapon it fires uses its own (see {@link Convergence}).
 */
public final class ConvergenceSkill implements Skill {
    public static final String ID = "stellar_convergence";
    /** Starlight: every weapon's colour mixed toward white. */
    public static final int COLOR = 0xE6DCFF;
    /** GLFW key code 66 = B: free in vanilla and The Shooting Star; not P, Stellar Quartet's key. */
    public static final int DEFAULT_KEY = 66;
    /** A minute, like the remote's biggest skills (no cooldown skips it, as for every skill). */
    private static final int COOLDOWN = 1200;

    ConvergenceSkill() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String tier() {
        return "The Shooting Star";
    }

    @Override
    public String title() {
        return Codenames.CONVERGENCE + " · Stellar Convergence";
    }

    @Override
    public int color() {
        return COLOR;
    }

    @Override
    public int cooldown() {
        return COOLDOWN;
    }

    @Override
    public int duration() {
        return Convergence.LONGEST + 300;
    }

    @Override
    public int defaultKey() {
        return DEFAULT_KEY;
    }

    @Override
    public boolean powerSliders() {
        return false;
    }

    @Override
    public String toString() {
        return this.title();
    }
}
