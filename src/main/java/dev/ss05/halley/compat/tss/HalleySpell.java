package dev.ss05.halley.compat.tss;

import cyou.rimuru.shootingstardemo.mc1211.spell.ActiveSpell;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.world.HalleyStrike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * SS-05 running inside The Shooting Star's spell engine (which ticks it, keeps one per caster, and sends the effect
 * packet to nearby players). All the behaviour is in {@link HalleyStrike}; this only adapts it.
 */
public final class HalleySpell extends ActiveSpell {
    private final HalleyStrike strike;

    /** {@code tuning} is the remote's sliders: the spell engine runs this spell (and every client its film) at its speed. */
    public HalleySpell(ServerPlayer caster, Vec3 target, long seed, HalleyParams params, CastTuning tuning) {
        super(TssLink.SKILL, caster, caster.position(), target, seed, false, TssLink.tuning(tuning));
        HalleyPlan plan = new HalleyPlan(this.origin, this.target, caster.getYRot(), seed, params);
        this.strike = new HalleyStrike(this.level, plan, caster, tuning);
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
        int extra = this.age - this.strike.plan().duration;
        return extra > 0 && (this.strike.worldDone() || extra > MAX_EXTRA);
    }

    /** How long the spell may run on past its timeline to finish its work on the land: five minutes of it. */
    private static final int MAX_EXTRA = 6000;

    /** The strike's sizes ride along in the effect packet's int array, so clients film the same crater. */
    @Override
    protected int[] fxTargets() {
        return this.strike.plan().params.encode();
    }

    @Override
    protected void onEnd() {
        this.strike.end();
    }
}
