package dev.ss05.halley.compat;

import cyou.rimuru.shootingstardemo.mc1201.spell.ActiveSpell;
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

    public MoonSpell(ServerPlayer caster, Vec3 target, long seed, MoonParams params) {
        super(StarBridge.MOON, caster, caster.position(), target, seed, false);
        MoonPlan plan = new MoonPlan(this.origin, this.target, seed, params);
        this.strike = new MoonStrike(this.level, plan, caster);
    }

    @Override
    protected void onTick(int t) {
        this.strike.tick(t, this.caster);
    }

    @Override
    protected boolean finished() {
        return this.age > MoonPlan.DURATION;
    }

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
