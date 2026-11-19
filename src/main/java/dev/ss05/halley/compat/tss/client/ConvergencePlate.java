package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.mc1211.client.magic.ClientSkills;
import cyou.rimuru.rimulib.mc1211.client.magic.SkillTuning;
import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.rimulib.mc1211.skill.Tuning;
import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.compat.tss.Convergence;
import dev.ss05.halley.compat.tss.TssLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * SA-00 · Stellar Convergence's card in the remote's menu (H), "look B" of 1.9.0: while SA-00 is the selected skill,
 * under the addon's general plate (client/AdditionPanel draws both): every remote skill with a tick box (1-9 ticks
 * one), where they land (R: on the crosshair, or in a ring round it), the screen shake (X), which addons it can fire
 * and which are missing (the user, 2026-11-11), the credit to The Shooting Star - Stellar Quartet (ikun520), whose idea
 * this follows, and the no-limits warning. The picks, with each ticked weapon's own sliders (which this same menu
 * edits), go to the server whenever they change, and as the game joins it.
 */
public final class ConvergencePlate {
    /** GLFW key codes: 1-9, R and X. */
    private static final int KEY_1 = 49;
    private static final int KEY_RING = 82;
    private static final int KEY_SHAKE = 88;
    private static final int ROW = 13;

    /** The addons Convergence knows (docs/ADDONS.md): a name, and the remote skill ids that mean it is installed. */
    private record Addon(String name, String... skills) {
    }

    private static final Addon[] ADDONS = {
        new Addon("SS-21 Quasar", "quasar"),
        new Addon("SX-01 Solar Flare", "solar_flare"),
        new Addon("TON 618 Addition", "quasar_jets", "last_light")
    };

    private static Convergence.Picks sent;

    private ConvergencePlate() {
    }

    /** The weapons it can fire, in the menu's order (the remote's skills, other addons' on it too), at most nine. */
    static List<Skill> weapons() {
        List<Skill> weapons = new ArrayList<>();
        SkillSet set = SkillSet.of(TssLink.SKILL);
        if (set == null) {
            return weapons;
        }
        for (Skill skill : set.skills) {
            if (skill != TssLink.CONVERGENCE && weapons.size() < 9) {
                weapons.add(skill);
            }
        }
        return weapons;
    }

    // ---- Input. ---------------------------------------------------------------------------------------------------

    /** A key pressed while the menu is open: true if it was one of the card's (only while SA-00 is selected). */
    public static boolean key(int key) {
        if (!selected() || HalleyClientConfig.platesFolded()) {
            return false;
        }
        if (key == KEY_RING) {
            HalleyClientConfig.setConvergenceRing(!HalleyClientConfig.convergenceRing());
            AdditionPanel.click(true);
            sync(false);
            return true;
        }
        if (key == KEY_SHAKE) {
            HalleyClientConfig.setConvergenceShake(HalleyClientConfig.convergenceShake().next());
            AdditionPanel.click(true);
            return true;
        }
        int index = key - KEY_1;
        List<Skill> weapons = weapons();
        if (index < 0 || index >= 9) {
            return false;
        }
        if (index >= weapons.size()) {
            AdditionPanel.click(false);
            return true;
        }
        List<String> ticked = new ArrayList<>(HalleyClientConfig.convergence());
        String id = weapons.get(index).id();
        if (!ticked.remove(id)) {
            ticked.add(id);
        }
        HalleyClientConfig.setConvergence(ticked);
        AdditionPanel.click(true);
        sync(false);
        return true;
    }

    /**
     * Tells the server the picks if they changed since it was last told ({@code always}: tell it anyway, as the game
     * joins). Each weapon goes with its sliders as the remote's menu has them now.
     */
    public static void sync(boolean always) {
        List<Convergence.Pick> picks = new ArrayList<>();
        List<String> ticked = HalleyClientConfig.convergence();
        for (Skill skill : weapons()) {
            if (ticked.contains(skill.id())) {
                Tuning tuning = SkillTuning.of(skill);
                picks.add(new Convergence.Pick(skill.id(), tuning.power(), tuning.damage(), tuning.speed(), tuning.width()));
            }
        }
        Convergence.Picks now = new Convergence.Picks(HalleyClientConfig.convergenceRing(), List.copyOf(picks));
        if ((always || !now.equals(sent)) && Minecraft.getInstance().getConnection() != null
            && NetClientApi.canSend(Convergence.Picks.PACKET)) {
            NetClientApi.send(now);
            sent = now;
        }
    }

    // ---- Drawing. -------------------------------------------------------------------------------------------------

    /** Whether SA-00 is the remote's selected skill: the plate only shows then (the user, 2026-11-05). */
    private static boolean selected() {
        return ClientSkills.selected(SkillSet.of(TssLink.CONVERGENCE)) == TssLink.CONVERGENCE;
    }

    /** Whether SA-00 is selected (for StarClientBridge, which picks the card to draw). */
    static boolean isSelected() {
        return selected();
    }

    /** SA-00's card, at {@code top} in the plate's space, {@code w} wide (from AdditionPanel); returns its height. */
    public static int drawCard(GuiGraphics g, Font font, int top, int w) {
        List<Skill> weapons = weapons();
        List<String> ticked = HalleyClientConfig.convergence();
        boolean ring = HalleyClientConfig.convergenceRing();
        // without Stellar Quartet the card says how to unlock it, in place of its hint
        boolean locked = dev.ss05.halley.StellarQuartet.REQUIRED && !dev.ss05.halley.StellarQuartet.installedHere();
        Component hint = Component.translatable(locked ? "gui.shooting_star_addition.convergence.locked" : "gui.shooting_star_addition.convergence.hint");
        Component credit = Component.translatable("gui.shooting_star_addition.convergence.credit");
        Component warning = Component.translatable("gui.shooting_star_addition.convergence.warning");
        java.util.Set<String> onRemote = new java.util.HashSet<>();
        for (Skill skill : weapons) {
            onRemote.add(skill.id());
        }
        int listTop = top + 48;
        int whereTop = listTop + weapons.size() * ROW + 6;
        // 1.9.0: each setting with its explanation under it (as on the other cards)
        int line = ROW + 9;
        int addonsTop = whereTop + 2 * line + 6;
        int h = addonsTop - top + 12 + (ADDONS.length + 1) * 12 + 4 + 24;

        AdditionPanel.plate(g, 2, top + 3, w + 2, top + h + 3, 0x26000000);
        AdditionPanel.plate(g, 0, top, w, top + h, 0xF4000000 | AdditionPanel.PLATE);
        g.fill(0, top + 2, 3, top + h - 2, 0xFF000000 | 0xB9A4FF);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.convergence").withStyle(ChatFormatting.BOLD), 11, top + 7,
            0xFF000000 | AdditionPanel.INK, false);
        g.drawString(font, hint, 11, top + 18, 0xFF000000 | (locked ? AdditionPanel.WARN : AdditionPanel.GREY), false);
        g.fill(11, top + 30, w - 10, top + 31, 0xFF000000 | AdditionPanel.RULE);

        g.drawString(font, Component.translatable("gui.shooting_star_addition.group.weapons").withStyle(ChatFormatting.BOLD), 11, top + 36,
            0xFF000000 | AdditionPanel.GREY, false);
        for (int i = 0; i < weapons.size(); i++) {
            Skill skill = weapons.get(i);
            boolean on = ticked.contains(skill.id());
            int y = listTop + i * ROW;
            AdditionPanel.keyCap(g, font, 11, y, String.valueOf(i + 1), true);
            // the tick box, in the weapon's colour
            int color = 0xFF000000 | skill.color() & 0xFFFFFF;
            AdditionPanel.plate(g, 28, y, 38, y + 10, on ? color : 0xFF000000 | AdditionPanel.GREY);
            if (!on) {
                AdditionPanel.plate(g, 29, y + 1, 37, y + 9, 0xFF000000 | AdditionPanel.PLATE);
            }
            g.drawString(font, skill.title(), 43, y + 1, 0xFF000000 | (on ? AdditionPanel.INK : AdditionPanel.GREY), false);
            if (on) {
                // what will fire: its own sliders with SA-00's applied
                Tuning own = SkillTuning.of(skill);
                Tuning tuning = Convergence.scaled(new Convergence.Pick(skill.id(), own.power(), own.damage(), own.speed(), own.width()),
                    SkillTuning.of(TssLink.CONVERGENCE)).clampFor(skill);
                String sliders = (Convergence.finale(skill) ? "last · " : "") + String.format(Locale.ROOT, "%.1f× · %s · %.1f×", tuning.power(),
                    Float.isInfinite(tuning.damage()) ? "∞" : String.format(Locale.ROOT, "%.0f", tuning.damage()), tuning.speed());
                g.drawString(font, sliders, w - 12 - font.width(sliders), y + 1, 0xFF000000 | AdditionPanel.GREY, false);
            }
        }
        g.fill(11, whereTop - 4, w - 10, whereTop - 3, 0xFF000000 | AdditionPanel.RULE);

        g.drawString(font, Component.translatable("gui.shooting_star_addition.convergence.where"), 11, whereTop + 1, 0xFF000000 | AdditionPanel.INK, false);
        AdditionPanel.keyCap(g, font, w - 22, whereTop, "R", true);
        Component where = Component.translatable(ring ? "gui.shooting_star_addition.convergence.ring" : "gui.shooting_star_addition.convergence.point");
        g.drawString(font, where, w - 26 - font.width(where), whereTop + 1, 0xFF000000 | AdditionPanel.GOLD, false);
        AdditionPanel.describe(g, font, whereTop, "convergence.where");
        g.drawString(font, Component.translatable("gui.shooting_star_addition.convergence.shake"), 11, whereTop + line + 1,
            0xFF000000 | AdditionPanel.INK, false);
        AdditionPanel.keyCap(g, font, w - 22, whereTop + line, "X", true);
        Component shake = Component.translatable("gui.shooting_star_addition.convergence.shake."
            + HalleyClientConfig.convergenceShake().name().toLowerCase(Locale.ROOT));
        g.drawString(font, shake, w - 26 - font.width(shake), whereTop + line + 1, 0xFF000000 | AdditionPanel.GOLD, false);
        AdditionPanel.describe(g, font, whereTop + line, "convergence.shake");
        g.fill(11, addonsTop - 4, w - 10, addonsTop - 3, 0xFF000000 | AdditionPanel.RULE);

        // which addons it can fire, installed or missing (Stellar Quartet unlocks it rather than firing)
        g.drawString(font, Component.translatable("gui.shooting_star_addition.group.addons").withStyle(ChatFormatting.BOLD), 11, addonsTop,
            0xFF000000 | AdditionPanel.GREY, false);
        int y = addonsTop + 12;
        for (Addon addon : ADDONS) {
            boolean installed = false;
            for (String id : addon.skills()) {
                installed |= onRemote.contains(id);
            }
            addonRow(g, font, y, w, Component.literal(addon.name()), installed, "gui.shooting_star_addition.addon.fires");
            y += 12;
        }
        addonRow(g, font, y, w, Component.literal("Stellar Quartet"), dev.ss05.halley.StellarQuartet.installedHere(),
            "gui.shooting_star_addition.addon.unlocks");
        y += 16;
        g.drawString(font, credit, 11, y, 0xFF000000 | AdditionPanel.GREY, false);
        g.drawString(font, warning, 11, y + 11, 0xFF000000 | AdditionPanel.WARN, false);
        return h;
    }

    private static void addonRow(GuiGraphics g, Font font, int y, int w, Component name, boolean installed, String doesKey) {
        AdditionPanel.plate(g, 12, y + 2, 18, y + 8, 0xFF000000 | (installed ? 0x3FAE62 : AdditionPanel.GREY));
        g.drawString(font, name, 23, y + 1, 0xFF000000 | (installed ? AdditionPanel.INK : AdditionPanel.GREY), false);
        Component state = installed ? Component.translatable(doesKey) : Component.translatable("gui.shooting_star_addition.addon.missing");
        g.drawString(font, state, w - 12 - font.width(state), y + 1, 0xFF000000 | (installed ? 0x2E8A4C : AdditionPanel.GOLD), false);
    }
}
