package dev.ss05.halley.client;

import dev.ss05.halley.HalleyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The note this addon adds to the Stellar Remote's menu (H, The Shooting Star's "White Space"): the no-cooldown option
 * ({@code no_cooldown} in the common config), whether it is on, how to switch it on, and a word of warning. A small
 * plate in the menu's bottom left corner, in the menu's own colours, drawn over it while it is open.
 *
 * <p>In single player the world runs on this game, so its config is the one in force and the note says whether the
 * option is on. On a server it's the server's config that counts, which this game can't see, so the note says that.
 */
public final class NoCooldownNote {
    /** The menu's own plate white, ink, grey text and gold. */
    private static final int PLATE = 0xFBFAFC;
    private static final int INK = 0x3B4048;
    private static final int GREY = 0x8A8F99;
    private static final int GOLD = 0xF4B73F;
    /** The warning. */
    private static final int WARN = 0xC0392B;

    private NoCooldownNote() {
    }

    public static void draw(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        // null: up to a server this game doesn't run
        Boolean on = minecraft.hasSingleplayerServer() ? HalleyConfig.noCooldown() : null;
        String state = on == null ? "server" : on ? "on" : "off";
        Component title = Component.translatable("gui.shooting_star_addition.no_cooldown").withStyle(ChatFormatting.BOLD);
        Component value = Component.translatable("gui.shooting_star_addition.no_cooldown." + state).withStyle(ChatFormatting.BOLD);
        Component hint = Component.translatable("gui.shooting_star_addition.no_cooldown.hint." + state);
        Component careful = Component.translatable("gui.shooting_star_addition.no_cooldown.careful");
        int w = Math.max(font.width(title) + 12 + font.width(value), Math.max(font.width(hint), font.width(careful))) + 18;
        int h = 40;
        int x = 8;
        int y = g.guiHeight() - h - 8;
        // over the menu
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x30000000);
        g.fill(x, y, x + w, y + h, 0xF0000000 | PLATE);
        g.fill(x, y, x + 3, y + h, 0xFF000000 | (Boolean.TRUE.equals(on) ? GOLD : GREY));
        g.drawString(font, title, x + 10, y + 6, 0xFF000000 | INK, false);
        g.drawString(font, value, x + w - 8 - font.width(value), y + 6, 0xFF000000 | (Boolean.TRUE.equals(on) ? GOLD : GREY), false);
        g.drawString(font, hint, x + 10, y + 18, 0xFF000000 | GREY, false);
        g.drawString(font, careful, x + 10, y + 29, 0xFF000000 | WARN, false);
        g.pose().popPose();
    }
}
