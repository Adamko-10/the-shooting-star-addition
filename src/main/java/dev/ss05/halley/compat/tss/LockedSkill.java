package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1201.skill.Power;
import cyou.rimuru.rimulib.mc1201.skill.Skill;
import dev.ss05.halley.OtherAddons;
import java.util.ArrayList;
import java.util.List;

/**
 * A skill of TON 618 Addition (Adam's other addon) listed on the Stellar Remote but locked while that addon isn't
 * installed, saying what to install (Adam, 2026-11-19: "Display ton618 addition skills when shooting star addition is
 * installed but lock it and tell what u need installed"; his pick: both ways, TON 618 Addition lists ours the same way).
 * With TON 618 Addition loaded these aren't listed: its own real skills join the remote instead. Picking one shows its
 * warning page (so RimuLib gives it no key), casting it only says what's missing; Stellar Convergence and /ssa strike
 * leave it out. Their ids are our own ({@code ton618_...}), never TON 618 Addition's, so its icons, keys and texts
 * never clash with ours if both end up installed.
 */
public final class LockedSkill implements Skill {
    /** A mod it needs: its id and the name players know it by. */
    public record Need(String modId, String name) {
        public boolean installed() {
            return OtherAddons.loaded(this.modId);
        }
    }

    public static final Need TON618_ADDITION = new Need("ton618_addition", "TON 618 Addition");
    public static final Need TON618 = new Need("ton618", "TON 618");
    public static final Need SS21_QUASAR = new Need("shooting_star_ss21", "SS-21 Quasar");

    /** TA-01 · Last Light (TON 618 Addition's own colour and title). */
    public static final LockedSkill LAST_LIGHT = new LockedSkill("ton618_last_light", "TA-01 · Last Light", 0xBFE0FF,
        List.of(TON618_ADDITION, TON618));
    /** TA-02 · Quasar Jets (it also needs SS-21 Quasar). */
    public static final LockedSkill QUASAR_JETS = new LockedSkill("ton618_quasar_jets", "TA-02 · Quasar Jets", 0xA8C8FF,
        List.of(TON618_ADDITION, TON618, SS21_QUASAR));

    private final String id;
    private final String title;
    private final int color;
    private final List<Need> needs;

    private LockedSkill(String id, String title, int color, List<Need> needs) {
        this.id = id;
        this.title = title;
        this.color = color;
        this.needs = needs;
    }

    /** The ones to list on the remote: none once TON 618 Addition is loaded (its real skills are there then). */
    static List<LockedSkill> toList() {
        return TON618_ADDITION.installed() ? List.of() : List.of(LAST_LIGHT, QUASAR_JETS);
    }

    public List<Need> needs() {
        return this.needs;
    }

    /** What is missing, as players read it: "TON 618 Addition and TON 618", "TON 618 Addition, TON 618 and SS-21 Quasar". */
    public String missing() {
        List<String> names = new ArrayList<>();
        for (Need need : this.needs) {
            if (!need.installed()) {
                names.add(need.name());
            }
        }
        if (names.isEmpty()) {
            return TON618_ADDITION.name();
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** What a cast of it says. */
    public String refusal() {
        return this.title + " is locked: install " + this.missing() + " to use it";
    }

    @Override
    public String id() {
        return this.id;
    }

    /** The line over the title in the menu. */
    @Override
    public String tier() {
        return "TON 618 Addition · locked";
    }

    @Override
    public String title() {
        return this.title;
    }

    @Override
    public int color() {
        return this.color;
    }

    @Override
    public int cooldown() {
        return 0;
    }

    @Override
    public int duration() {
        return 1;
    }

    /** None (GLFW's "unknown"): a skill with a select warning gets no key from RimuLib anyway. */
    @Override
    public int defaultKey() {
        return -1;
    }

    @Override
    public Power power() {
        return null;
    }

    @Override
    public boolean powerSliders() {
        return false;
    }

    @Override
    public boolean speedSlider() {
        return false;
    }

    /** The menu's page when it is picked: what to install. */
    @Override
    public String selectWarning() {
        return "Locked. " + this.title + " comes with TON 618 Addition, another addon by the SA Series' author. Install "
            + this.missing() + " to use it.";
    }

    @Override
    public String toString() {
        return this.title;
    }
}
