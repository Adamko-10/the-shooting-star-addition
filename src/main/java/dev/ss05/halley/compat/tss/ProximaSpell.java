package dev.ss05.halley.compat.tss;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.world.ProximaStrike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** SA-04 running as one of the addon's spells (see {@link Spell}). All the behaviour is in {@link ProximaStrike}. */
final class ProximaSpell extends Spell {
    /** How long the spell may run on past its timeline to finish its work on loaded land: five minutes of it. */
    private static final int MAX_EXTRA = 6000;

    private final ProximaStrike strike;

    ProximaSpell(ServerPlayer caster, Vec3 target, long seed, ProximaParams params, CastTuning tuning) {
        super(TssLink.PROXIMA, caster, target, seed, tuning);
        this.strike = new ProximaStrike(this.level, new ProximaPlan(this.target, seed, params), caster, tuning);
    }

    @Override
    protected void onTick(int t) {
        this.strike.tick(t, this.caster);
    }

    @Override
    protected boolean finished() {
        int extra = this.age - ProximaPlan.DURATION;
        return extra > 0 && (this.strike.worldDone() || extra > MAX_EXTRA);
    }

    /** The flare's sizes ride along in the effect packet's int array, so clients draw the same strike. */
    @Override
    protected int[] fxTargets() {
        return this.strike.plan().params.encode();
    }

    @Override
    protected boolean worldDone() {
        return this.strike.worldDone();
    }

    @Override
    protected void onEnd() {
        this.strike.end();
    }
}
