package dev.ss05.halley.compat.tss;

import cyou.rimuru.shootingstardemo.mc1201.magic.Power;
import cyou.rimuru.shootingstardemo.mc1201.magic.Skill;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.world.HalleyInfo;

/** SS-05 as an entry on the Stellar Remote. The values themselves live in {@link HalleyInfo} and the config. */
public final class HalleySkill implements Skill {
    HalleySkill() {
    }

    @Override
    public String id() {
        return HalleyInfo.ID;
    }

    @Override
    public String tier() {
        return HalleyInfo.TIER;
    }

    @Override
    public String title() {
        return HalleyInfo.title();
    }

    @Override
    public int color() {
        return HalleyInfo.COLOR;
    }

    @Override
    public int cooldown() {
        return HalleyConfig.tuning().cooldownTicks();
    }

    @Override
    public int duration() {
        return HalleyPlan.durationFor(HalleyConfig.tuning().trenchLength());
    }

    /**
     * The power slider under it in the remote's menu (H), next to the remote's own damage and speed sliders: the whole
     * strike from 0.1x to 10x as big, like The Shooting Star's Seven Stars. The last number is what the menu's hint
     * shows the size by (its crater at 1x, in blocks across).
     */
    @Override
    public Power power() {
        return new Power(CastTuning.MIN_POWER, CastTuning.MAX_POWER, 1.0F, true, HalleyConfig.tuning().craterRadius() * 2.0F);
    }

    /**
     * Above this power the remote's menu warns that the cast is very heavy (The Shooting Star 1.4.2+; this is that
     * version's name for the method, which older versions simply don't have).
     */
    public float l() {
        return 3.0F;
    }

    @Override
    public int defaultKey() {
        return HalleyInfo.DEFAULT_KEY;
    }

    @Override
    public String toString() {
        return HalleyInfo.title();
    }
}
