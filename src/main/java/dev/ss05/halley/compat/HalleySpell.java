package dev.ss05.halley.compat;

import dev.aek.shootingstardemo.mc1211.spell.ActiveSpell;
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

    public HalleySpell(ServerPlayer caster, Vec3 target, long seed, HalleyParams params) {
        super(StarBridge.SKILL, caster, caster.position(), target, seed, false);
        HalleyPlan plan = new HalleyPlan(this.origin, this.target, caster.getYRot(), seed, params);
        this.strike = new HalleyStrike(this.level, plan, caster);
    }

    @Override
    protected void onTick(int t) {
        this.strike.tick(t, this.caster);
    }

    @Override
    protected boolean finished() {
        return this.age > this.strike.plan().duration;
    }

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
