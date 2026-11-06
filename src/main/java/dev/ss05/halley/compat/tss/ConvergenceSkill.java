package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1211.skill.Power;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import dev.ss05.halley.world.Codenames;

/**
 * SA-00 · Stellar Convergence as an entry on the Stellar Remote (the user's pick: its own skill, 2026-11-04). Each weapon
 * it fires uses its own sliders, scaled by this one's (see {@link Convergence}).
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

    /**
     * The master power slider (the user, 2026-11-05): every ticked weapon fires at its own power times this, so the whole
     * barrage scales at once. Its speed slider multiplies each weapon's speed, its damage slider caps each one's damage.
     */
    @Override
    public Power power() {
        return new Power(0.1F, 10.0F, 1.0F, true, 0.0F);
    }

    /** Above this the remote's menu warns that the cast is very heavy (every weapon at twice its own power). */
    @Override
    public float warnAbove() {
        return 2.0F;
    }

    @Override
    public String toString() {
        return this.title();
    }
}
