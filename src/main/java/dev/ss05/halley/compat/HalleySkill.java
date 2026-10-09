package dev.ss05.halley.compat;

import cyou.rimuru.shootingstardemo.magic.Skill;
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
        return HalleyInfo.TITLE;
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

    @Override
    public int defaultKey() {
        return HalleyInfo.DEFAULT_KEY;
    }

    @Override
    public String toString() {
        return "SS-05 Halley";
    }
}
