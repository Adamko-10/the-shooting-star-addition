package dev.ss05.halley.compat.tss;

import cyou.rimuru.shootingstardemo.mc1201.spell.ActiveSpell;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.world.MoonStrike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * SS-06 running inside The Shooting Star's spell engine (which ticks it, keeps one per caster, and sends the effect
 * packet to nearby players). All the behaviour is in {@link MoonStrike}; this only adapts it.
 */
public final class MoonSpell extends ActiveSpell {
    private final MoonStrike strike;

    /** {@code tuning} is the remote's sliders: the spell engine runs this spell (and every client its film) at its speed. */
    public MoonSpell(ServerPlayer caster, Vec3 target, long seed, MoonParams params, CastTuning tuning) {
        super(TssLink.MOON, caster, caster.position(), target, seed, false, TssLink.tuning(tuning));
        MoonPlan plan = new MoonPlan(this.origin, this.target, seed, params);
        this.strike = new MoonStrike(this.level, plan, caster, tuning);
    }

    @Override
    protected void onTick(int t) {
        this.strike.tick(t, this.caster);
    }

    /**
     * The film ends at the timeline's end. A big strike's work on the land may still be going on then: the spell runs on
     * until it is done (at most {@link #MAX_EXTRA} ticks more), rather than doing the rest in one go.
     */
    @Override
    protected boolean finished() {
        int extra = this.age - MoonPlan.DURATION;
        return extra > 0 && (this.strike.worldDone() || extra > MAX_EXTRA);
    }

    /** How long the spell may run on past its timeline to finish its work on the land: five minutes of it. */
    private static final int MAX_EXTRA = 6000;

    /** The moonfall's sizes ride along in the effect packet's int array, so clients film the same moon. */
    @Override
    protected int[] fxTargets() {
        return this.strike.plan().params.encode();
    }

    @Override
    protected void onEnd() {
        this.strike.end();
    }
}
