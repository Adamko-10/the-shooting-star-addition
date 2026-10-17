package dev.ss05.halley.client;

import dev.ss05.halley.net.ChoicePayload;
import dev.ss05.halley.net.ServerSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The addon's own plate in the Stellar Remote's menu (H, The Shooting Star's "White Space"), in the menu's colours, in
 * its top left corner: two switches - Luna's music (M) and no-cooldown (C) - and what the server allows. The menu holds
 * the mouse (you pick things in it by looking at them), so these are flipped with their keys, shown on the plate.
 *
 * <p>No-cooldown is the server's to allow ({@code no_cooldown}, and {@code no_cooldown_survival} for survival players);
 * the server tells this game its settings as it joins ({@link ServerSettings}), and this game tells it the player's own
 * switch back ({@link ChoicePayload}).
 */
public final class AdditionPanel {
    /** The menu's own plate white, ink, grey text and gold. */
    private static final int PLATE = 0xFBFAFC;
    private static final int INK = 0x3B4048;
    private static final int GREY = 0x8A8F99;
    private static final int RULE = 0xE4E6EB;
    private static final int GOLD = 0xF4B73F;
    private static final int WARN = 0xC0392B;
    private static final float SCALE = 0.75F;
    private static final int WIDTH = 236;
    /** GLFW key codes. */
    public static final int KEY_MUSIC = 77;
    public static final int KEY_COOLDOWN = 67;
    private static final float SLIDE_MILLIS = 160.0F;

    private static long musicFlippedAt;
    private static long cooldownFlippedAt;

    private AdditionPanel() {
    }

    // ---- Input. ---------------------------------------------------------------------------------------------------

    /** A key pressed while the menu is open (from compat/tss/mixin/WhiteSpaceMixin): true if it was one of ours. */
    public static boolean key(int key) {
        if (key == KEY_MUSIC) {
            HalleyClientConfig.setLunaMusic(!HalleyClientConfig.lunaMusic());
            musicFlippedAt = Util.getMillis();
            click(true);
            return true;
        }
        if (key == KEY_COOLDOWN) {
            if (!allowedHere()) {
                click(false);
                return true;
            }
            HalleyClientConfig.setNoCooldown(!HalleyClientConfig.noCooldown());
            cooldownFlippedAt = Util.getMillis();
            sendChoice();
            click(true);
            return true;
        }
        return false;
    }

    /** Tells the server this player's own switch (as they join, and whenever they flip it). */
    public static void sendChoice() {
        if (ClientPlayNetworking.canSend(ChoicePayload.TYPE)) {
            ClientPlayNetworking.send(new ChoicePayload(HalleyClientConfig.noCooldown()));
        }
    }

    private static void click(boolean ok) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), ok ? 1.0F : 0.6F, 0.5F));
    }

    /** Whether the server lets this player switch the cooldowns off right now (their game mode included). */
    private static boolean allowedHere() {
        LocalPlayer player = Minecraft.getInstance().player;
        return ServerSettings.known() && ServerSettings.noCooldown()
            && (ServerSettings.survival() || player != null && (player.isCreative() || player.isSpectator()));
    }

    // ---- Drawing. -------------------------------------------------------------------------------------------------

    public static void draw(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        boolean music = HalleyClientConfig.lunaMusic();
        boolean allowed = allowedHere();
        boolean cooldownOff = allowed && HalleyClientConfig.noCooldown();

        Component status;
        int statusColor = GREY;
        if (!ServerSettings.known()) {
            status = Component.translatable("gui.shooting_star_addition.no_cooldown.unknown");
        } else if (!ServerSettings.noCooldown()) {
            status = Component.translatable("gui.shooting_star_addition.no_cooldown.disabled");
        } else if (!allowed) {
            status = Component.translatable("gui.shooting_star_addition.no_cooldown.not_survival");
            statusColor = WARN;
        } else {
            status = Component.translatable("gui.shooting_star_addition.no_cooldown.yours", Component.translatable(ServerSettings.survival()
                ? "gui.shooting_star_addition.no_cooldown.everyone" : "gui.shooting_star_addition.no_cooldown.creative"));
            statusColor = INK;
        }

        int w = Math.max(WIDTH, Math.max(font.width(status), font.width(Component.translatable("gui.shooting_star_addition.no_cooldown.disabled"))) + 22);
        int h = cooldownOff ? 104 : 92;
        g.pose().pushPose();
        // over the menu, in its top left corner
        g.pose().translate(8.0F, 8.0F, 400.0F);
        g.pose().scale(SCALE, SCALE, 1.0F);
        // a soft shadow, the plate, its gold edge and title rule
        plate(g, 2, 3, w + 2, h + 3, 0x26000000);
        plate(g, 0, 0, w, h, 0xF4000000 | PLATE);
        g.fill(0, 2, 3, h - 2, 0xFF000000 | GOLD);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.panel").withStyle(ChatFormatting.BOLD), 11, 7, 0xFF000000 | INK, false);
        g.fill(11, 19, w - 10, 20, 0xFF000000 | RULE);

        // Luna's music
        row(g, font, 26, Component.translatable("gui.shooting_star_addition.music"), "M", music, true, musicFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.music.hint"), 11, 38, 0xFF000000 | GREY, false);
        g.fill(11, 51, w - 10, 52, 0xFF000000 | RULE);

        // no cooldown, and what the server allows
        row(g, font, 58, Component.translatable("gui.shooting_star_addition.no_cooldown"), "C", cooldownOff, allowed, cooldownFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.no_cooldown.hint"), 11, 70, 0xFF000000 | GREY, false);
        g.drawString(font, status, 11, 81, 0xFF000000 | statusColor, false);
        if (cooldownOff) {
            g.drawString(font, Component.translatable("gui.shooting_star_addition.no_cooldown.careful"), 11, 92, 0xFF000000 | WARN, false);
        }
        g.pose().popPose();
    }

    /** A label, a key cap and a switch, on one line. */
    private static void row(GuiGraphics g, Font font, int y, Component label, String key, boolean on, boolean enabled, long flippedAt, int w) {
        g.drawString(font, label.copy().withStyle(ChatFormatting.BOLD), 11, y + 1, 0xFF000000 | (enabled ? INK : GREY), false);
        int sw = 26;
        int sx = w - 12 - sw;
        // the key cap
        int kx = sx - 18;
        plate(g, kx, y - 1, kx + 12, y + 11, 0xFF000000 | (enabled ? INK : GREY));
        plate(g, kx + 1, y, kx + 11, y + 10, 0xFF000000 | PLATE);
        g.drawString(font, key, kx + 6 - font.width(key) / 2, y + 2, 0xFF000000 | (enabled ? INK : GREY), false);
        // the switch: its knob slides across as it is flipped
        float k = Mth.clamp((Util.getMillis() - flippedAt) / SLIDE_MILLIS, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        float at = on ? k : 1.0F - k;
        int track = !enabled ? RULE : lerp(0xC9CDD4, GOLD, at);
        plate(g, sx, y, sx + sw, y + 10, 0xFF000000 | track);
        int knob = sx + 1 + Math.round((sw - 10) * at);
        plate(g, knob, y + 1, knob + 8, y + 9, 0xFF000000 | (enabled ? 0xFFFFFF : 0xF0F1F3));
        Component state = Component.translatable(on ? "gui.shooting_star_addition.on" : "gui.shooting_star_addition.off");
        g.drawString(font, state, sx - 22 - font.width(state), y + 1, 0xFF000000 | (on ? GOLD : GREY), false);
    }

    /** A rectangle with its corners cut by a pixel (the menu's own plates are rounded). */
    private static void plate(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0 + 1, y0, x1 - 1, y1, argb);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    private static int lerp(int a, int b, float k) {
        int r = Math.round(Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int gr = Math.round(Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
        return r << 16 | gr << 8 | bl;
    }
}
