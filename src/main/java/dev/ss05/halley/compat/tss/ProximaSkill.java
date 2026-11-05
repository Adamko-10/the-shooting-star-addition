package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1211.skill.Power;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.ProximaConfig;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.world.ProximaInfo;

/** SA-04 Proxima as an entry on the Stellar Remote. The values themselves live in {@link ProximaInfo} and its plan. */
public final class ProximaSkill implements Skill {
    ProximaSkill() {
    }

    @Override
    public String id() {
        return ProximaInfo.ID;
    }

    @Override
    public String tier() {
        return ProximaInfo.TIER;
    }

    @Override
    public String title() {
        return ProximaInfo.title();
    }

    @Override
    public int color() {
        return ProximaInfo.COLOR;
    }

    @Override
    public int cooldown() {
        return ProximaConfig.tuning().cooldownTicks();
    }

    @Override
    public int duration() {
        return ProximaPlan.DURATION;
    }

    /** The power slider: the whole flare from 0.1x to 10x as wide. The menu's hint shows the scorch across, in blocks. */
    @Override
    public Power power() {
        return new Power(CastTuning.MIN_POWER, CastTuning.MAX_POWER, 1.0F, true, ProximaConfig.tuning().scorchRadius() * 2.0F);
    }

    /** Above this power the remote's menu warns that the cast is very heavy (its scorch is already wide at 1x). */
    @Override
    public float warnAbove() {
        return 3.0F;
    }

    @Override
    public int defaultKey() {
        return ProximaInfo.DEFAULT_KEY;
    }

    @Override
    public String toString() {
        return ProximaInfo.title();
    }
}
