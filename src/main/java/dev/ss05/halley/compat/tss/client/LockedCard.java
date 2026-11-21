package dev.ss05.halley.compat.tss.client;

import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.compat.tss.LockedSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The card under the settings plate while one of TON 618 Addition's locked skills is selected (1.9.0): what it does and
 * what to install, each mod ticked green once it's there. In the plates' own look (white/gold, AdditionPanel).
 */
final class LockedCard {
    private LockedCard() {
    }

    static int draw(GuiGraphics g, Font font, int top, int w, LockedSkill skill) {
        if (skill == null) {
            return 0;
        }
        int needsTop = top + 36;
        int aboutTop = needsTop + 12 + skill.needs().size() * 12 + 6;
        int h = aboutTop - top + 2 * 11 + 6 + 14;

        AdditionPanel.plate(g, 2, top + 3, w + 2, top + h + 3, 0x26000000);
        AdditionPanel.plate(g, 0, top, w, top + h, 0xF4000000 | AdditionPanel.PLATE);
        g.fill(0, top + 2, 3, top + h - 2, 0xFF000000 | skill.color());
        g.drawString(font, Component.literal(skill.title()).withStyle(ChatFormatting.BOLD), 11, top + 7, 0xFF000000 | AdditionPanel.INK, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.locked.tier"), 11, top + 18, 0xFF000000 | AdditionPanel.WARN, false);
        g.fill(11, top + 30, w - 10, top + 31, 0xFF000000 | AdditionPanel.RULE);

        g.drawString(font, Component.translatable("gui.shooting_star_addition.group.install").withStyle(ChatFormatting.BOLD), 11, needsTop,
            0xFF000000 | AdditionPanel.GREY, false);
        int y = needsTop + 12;
        for (LockedSkill.Need need : skill.needs()) {
            boolean installed = need.installed();
            AdditionPanel.plate(g, 12, y + 2, 18, y + 8, 0xFF000000 | (installed ? 0x3FAE62 : AdditionPanel.GREY));
            g.drawString(font, need.name(), 23, y + 1, 0xFF000000 | (installed ? AdditionPanel.INK : AdditionPanel.GREY), false);
            Component state = Component.translatable(installed ? "gui.shooting_star_addition.addon.fires" : "gui.shooting_star_addition.addon.missing");
            g.drawString(font, state, w - 12 - font.width(state), y + 1, 0xFF000000 | (installed ? 0x2E8A4C : AdditionPanel.GOLD), false);
            y += 12;
        }
        g.fill(11, aboutTop - 4, w - 10, aboutTop - 3, 0xFF000000 | AdditionPanel.RULE);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.locked." + skill.id()), 11, aboutTop, 0xFF000000 | AdditionPanel.INK, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.locked." + skill.id() + ".2"), 11, aboutTop + 11,
            0xFF000000 | AdditionPanel.INK, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.locked.where"), 11, top + h - 13, 0xFF000000 | AdditionPanel.GREY, false);
        return h;
    }
}
