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
import net.minecraft.util.Mth;

/**
 * SA-00 · Stellar Convergence's plate in the remote's menu (H), in its top right corner beside the skill list (the
 * addon's own plate has the top left): every remote skill with a tick box (1-9 ticks one), where they land (R: on the
 * crosshair, or in a ring round it), the credit to The Shooting Star - Stellar Quartet (ikun520), whose idea this
 * follows, and the no-limits warning. The picks, with each ticked weapon's own sliders (which this same menu edits),
 * go to the server whenever they change, and as the game joins it.
 */
public final class ConvergencePlate {
    /** GLFW key codes: 1-9, R, X, V and T. */
    private static final int KEY_1 = 49;
    private static final int KEY_STYLE = 84;
    private static final int KEY_RING = 82;
    private static final int KEY_SHAKE = 88;
    private static final int KEY_HIDE = 86;
    private static final int WIDTH = 200;
    private static final int ROW = 12;
    /** Smaller than the addon's own plate (the user, 2026-11-05: "overwhelming"). */
    private static final float SIZE = 0.8F;
    /** The old look (T switches, the user, 2026-11-05): always shown, the addon plate's size, roomier rows. */
    private static final int OLD_WIDTH = 236;
    private static final int OLD_ROW = 13;

    private static Convergence.Picks sent;

    private ConvergencePlate() {
    }

    /** The weapons it can fire, in the menu's order (any mod's remote skills), at most nine. */
    static List<Skill> weapons() {
        List<Skill> weapons = new ArrayList<>();
        for (SkillSet set : SkillSet.all()) {
            if (set.hidden) {
                continue;
            }
            for (Skill skill : set.skills) {
                if (skill != TssLink.CONVERGENCE && weapons.size() < 9) {
                    weapons.add(skill);
                }
            }
        }
        return weapons;
    }

    // ---- Input. ---------------------------------------------------------------------------------------------------

    /** A key pressed while the menu is open (from WhiteSpaceMixin): true if it was one of the plate's. */
    public static boolean key(int key) {
        boolean old = HalleyClientConfig.convergencePlateOld();
        if (!old && !selected()) {
            return false;
        }
        if (key == KEY_STYLE) {
            HalleyClientConfig.setConvergencePlateOld(!old);
            AdditionPanel.click(true);
            return true;
        }
        if (key == KEY_HIDE && !old) {
            HalleyClientConfig.setConvergencePlate(!HalleyClientConfig.convergencePlate());
            AdditionPanel.click(true);
            return true;
        }
        if (!old && !HalleyClientConfig.convergencePlate()) {
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

    public static void draw(GuiGraphics g) {
        // the picks go to the server whatever is shown: the sliders under the other skills change while they're selected
        sync(false);
        boolean old = HalleyClientConfig.convergencePlateOld();
        if (!old && !selected()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("gui.shooting_star_addition.convergence").withStyle(ChatFormatting.BOLD);
        // smaller, but never below one screen pixel per font pixel (smaller text turns to mush)
        float scale = Math.max(Mth.clamp(g.guiWidth() / 860.0F, AdditionPanel.MIN_SCALE, AdditionPanel.MAX_SCALE) * (old ? 1.0F : SIZE),
            1.0F / (float) Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushPose();
        if (!old && !HalleyClientConfig.convergencePlate()) {
            // hidden: just its name and the key that brings it back
            int w = font.width(title) + 40;
            g.pose().translate(g.guiWidth() - 8.0F - w * scale, 8.0F, 400.0F);
            g.pose().scale(scale, scale, 1.0F);
            AdditionPanel.plate(g, 0, 0, w, 20, 0xF4000000 | AdditionPanel.PLATE);
            g.fill(w - 3, 2, w, 18, 0xFF000000 | 0xB9A4FF);
            g.drawString(font, title, 8, 6, 0xFF000000 | AdditionPanel.INK, false);
            AdditionPanel.keyCap(g, font, w - 20, 5, "V", true);
            g.pose().popPose();
            return;
        }
        List<Skill> weapons = weapons();
        List<String> ticked = HalleyClientConfig.convergence();
        boolean ring = HalleyClientConfig.convergenceRing();
        Component credit = Component.translatable("gui.shooting_star_addition.convergence.credit");
        Component warning = Component.translatable("gui.shooting_star_addition.convergence.warning");
        // without Stellar Quartet the plate says how to unlock it, in place of its hint
        boolean locked = dev.ss05.halley.StellarQuartet.REQUIRED && !dev.ss05.halley.StellarQuartet.installedHere();
        Component hint = Component.translatable(locked ? "gui.shooting_star_addition.convergence.locked"
            : old ? "gui.shooting_star_addition.convergence.hint.old" : "gui.shooting_star_addition.convergence.hint");
        Component hint2 = old ? Component.translatable("gui.shooting_star_addition.convergence.hint2.old") : Component.empty();
        int row = old ? OLD_ROW : ROW;
        int w = Math.max(old ? OLD_WIDTH : WIDTH, Math.max(Math.max(font.width(credit), font.width(warning)), Math.max(font.width(hint), font.width(hint2))) + 22);
        int listTop = old ? 49 : 38;
        int ringTop = listTop + weapons.size() * row + 6;
        int h = ringTop + (old ? 66 : 64);

        // over the menu, in its top right corner
        g.pose().translate(g.guiWidth() - 8.0F - w * scale, 8.0F, 400.0F);
        g.pose().scale(scale, scale, 1.0F);
        AdditionPanel.plate(g, 2, 3, w + 2, h + 3, 0x26000000);
        AdditionPanel.plate(g, 0, 0, w, h, 0xF4000000 | AdditionPanel.PLATE);
        g.fill(w - 3, 2, w, h - 2, 0xFF000000 | 0xB9A4FF);
        g.drawString(font, title, 11, 7, 0xFF000000 | AdditionPanel.INK, false);
        AdditionPanel.keyCap(g, font, w - 24, 6, old ? "T" : "V", true);
        g.fill(11, 19, w - 10, 20, 0xFF000000 | AdditionPanel.RULE);
        g.drawString(font, hint, 11, 24, 0xFF000000 | (locked ? AdditionPanel.WARN : AdditionPanel.GREY), false);
        g.drawString(font, hint2, 11, 35, 0xFF000000 | AdditionPanel.GREY, false);

        for (int i = 0; i < weapons.size(); i++) {
            Skill skill = weapons.get(i);
            boolean on = ticked.contains(skill.id());
            int y = listTop + i * row;
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
        g.fill(11, ringTop - 4, w - 10, ringTop - 3, 0xFF000000 | AdditionPanel.RULE);

        g.drawString(font, Component.translatable("gui.shooting_star_addition.convergence.where").withStyle(ChatFormatting.BOLD), 11,
            ringTop + 2, 0xFF000000 | AdditionPanel.INK, false);
        AdditionPanel.keyCap(g, font, w - 24, ringTop + 1, "R", true);
        Component where = Component.translatable(ring ? "gui.shooting_star_addition.convergence.ring" : "gui.shooting_star_addition.convergence.point");
        g.drawString(font, where, w - 30 - font.width(where), ringTop + 2, 0xFF000000 | AdditionPanel.GOLD, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.convergence.shake").withStyle(ChatFormatting.BOLD), 11,
            ringTop + 16, 0xFF000000 | AdditionPanel.INK, false);
        AdditionPanel.keyCap(g, font, w - 24, ringTop + 15, "X", true);
        Component shake = Component.translatable("gui.shooting_star_addition.convergence.shake."
            + HalleyClientConfig.convergenceShake().name().toLowerCase(Locale.ROOT));
        g.drawString(font, shake, w - 30 - font.width(shake), ringTop + 16, 0xFF000000 | AdditionPanel.GOLD, false);
        g.drawString(font, credit, 11, ringTop + 31, 0xFF000000 | AdditionPanel.GREY, false);
        g.drawString(font, warning, 11, ringTop + 42, 0xFF000000 | AdditionPanel.WARN, false);
        g.pose().popPose();
    }
}
