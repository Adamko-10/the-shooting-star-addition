package dev.ss05.halley.compat.tss;

import cyou.rimuru.shootingstardemo.magic.Power;
import cyou.rimuru.shootingstardemo.magic.Skill;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.world.MoonInfo;

/** SS-06 as an entry on the Stellar Remote. The values themselves live in {@link MoonInfo} and the config. */
public final class MoonSkill implements Skill {
    MoonSkill() {
    }

    @Override
    public String id() {
        return MoonInfo.ID;
    }

    @Override
    public String tier() {
        return MoonInfo.TIER;
    }

    @Override
    public String title() {
        return MoonInfo.title();
    }

    @Override
    public int color() {
        return MoonInfo.COLOR;
    }

    @Override
    public int cooldown() {
        return MoonConfig.tuning().cooldownTicks();
    }

    @Override
    public int duration() {
        return MoonPlan.DURATION;
    }

    /**
     * The power slider under it in the remote's menu (H), next to the remote's own damage and speed sliders: the whole
     * strike from 0.1x to 10x as big, like The Shooting Star's Seven Stars. The last number is what the menu's hint
     * shows the size by (the moon at 1x, in blocks across).
     */
    @Override
    public Power power() {
        return new Power(CastTuning.MIN_POWER, CastTuning.MAX_POWER, 1.0F, true, MoonConfig.tuning().moonRadius() * 2.0F);
    }

    /**
     * Above this power the remote's menu warns that the cast is very heavy (The Shooting Star 1.4.2+; this is that
     * version's name for the method, which older versions simply don't have).
     */
    public float l() {
        return 2.0F;
    }

    @Override
    public int defaultKey() {
        return MoonInfo.DEFAULT_KEY;
    }

    @Override
    public String toString() {
        return MoonInfo.title();
    }
}
