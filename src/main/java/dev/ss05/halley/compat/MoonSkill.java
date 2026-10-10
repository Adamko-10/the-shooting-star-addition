package dev.ss05.halley.compat;

import cyou.rimuru.shootingstardemo.mc1201.magic.Skill;
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
        return MoonInfo.TITLE;
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

    @Override
    public int defaultKey() {
        return MoonInfo.DEFAULT_KEY;
    }

    @Override
    public String toString() {
        return "SS-06 Luna";
    }
}
