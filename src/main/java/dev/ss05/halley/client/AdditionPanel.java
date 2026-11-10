package dev.ss05.halley.client;

import dev.ss05.halley.net.AddonNetwork;
import dev.ss05.halley.net.ChoicePayload;
import dev.ss05.halley.net.ServerSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The addon's own plate in the Stellar Remote's menu (H, The Shooting Star's "White Space"), in the menu's colours, in
 * its top left corner: six switches - the skills' music (M, Luna's), Luna's Epic mode (G: a slower fall), keeping Luna's moon model after it lands (L), the impact
 * frames (F: full, soft, off), where the evac takes you (E: up, or to the edge) and no-cooldown (C) - and what the
 * server allows. The menu holds
 * the mouse (you pick things in it by looking at them), so these are flipped with their keys, shown on the plate.
 *
 * <p>No-cooldown is the server's to allow ({@code no_cooldown}, and {@code no_cooldown_survival} for survival players);
 * the server tells this game its settings as it joins ({@link ServerSettings}), and this game tells it the player's own
 * switch back ({@link ChoicePayload}).
 */
public final class AdditionPanel {
    /** The menu's own plate white, ink, grey text and gold. */
    public static final int PLATE = 0xFBFAFC;
    public static final int INK = 0x3B4048;
    public static final int GREY = 0x8A8F99;
    public static final int RULE = 0xE4E6EB;
    public static final int GOLD = 0xF4B73F;
    public static final int WARN = 0xC0392B;
    /** Its size: smaller on a small screen, where the menu itself takes up more of it (0.75 on a screen 645 or more wide, in GUI pixels). */
    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 0.75F;
    private static final int WIDTH = 236;
    /** This plate a little smaller than the remote's other plates (the user, 2026-11-07). */
    private static final float SIZE = 0.82F;
    /** GLFW key codes. */
    public static final int KEY_MUSIC = 77;
    public static final int KEY_COOLDOWN = 67;
    public static final int KEY_EVAC = 69;
    public static final int KEY_KEEP_MOON = 76;
    public static final int KEY_FRAMES = 70;
    public static final int KEY_EPIC = 71;
    /** - and =: every free letter is taken in the menu (WASD move, Q drops, P is Stellar Quartet's, the rest skills). */
    public static final int KEY_UNITS = 45;
    public static final int KEY_STACK = 61;
    private static final float SLIDE_MILLIS = 160.0F;

    private static long musicFlippedAt;
    private static long keepMoonFlippedAt;
    private static long framesFlippedAt;
    private static long cooldownFlippedAt;
    private static long evacFlippedAt;
    private static long epicFlippedAt;
    private static long unitsFlippedAt;
    private static long stackFlippedAt;

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
        if (key == KEY_EPIC) {
            HalleyClientConfig.setEpicMode(!HalleyClientConfig.epicMode());
            epicFlippedAt = Util.getMillis();
            sendChoice();
            click(true);
            return true;
        }
        if (key == KEY_KEEP_MOON) {
            HalleyClientConfig.setKeepMoon(!HalleyClientConfig.keepMoon());
            keepMoonFlippedAt = Util.getMillis();
            click(true);
            return true;
        }
        if (key == KEY_FRAMES) {
            HalleyClientConfig.ImpactFrames was = HalleyClientConfig.impactFrames();
            HalleyClientConfig.setImpactFrames(was.next());
            // the switch only slides between on and off (FULL -> SOFT stays on)
            if (was == HalleyClientConfig.ImpactFrames.SOFT || was == HalleyClientConfig.ImpactFrames.OFF) {
                framesFlippedAt = Util.getMillis();
            }
            click(true);
            return true;
        }
        if (key == KEY_EVAC) {
            HalleyClientConfig.setEvacLift(!HalleyClientConfig.evacLift());
            evacFlippedAt = Util.getMillis();
            sendChoice();
            click(true);
            return true;
        }
        if (key == KEY_STACK) {
            HalleyClientConfig.setStackMusic(!HalleyClientConfig.stackMusic());
            stackFlippedAt = Util.getMillis();
            click(true);
            return true;
        }
        if (key == KEY_UNITS) {
            HalleyClientConfig.setImperial(!HalleyClientConfig.imperial());
            unitsFlippedAt = Util.getMillis();
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

    /** Tells the server this player's own switches (as they join, and whenever they flip one). */
    public static void sendChoice() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && AddonNetwork.remotePresent(connection.getConnection())) {
            AddonNetwork.sendChoice(new ChoicePayload(HalleyClientConfig.noCooldown(), HalleyClientConfig.evacLift(),
                HalleyClientConfig.epicMode(), dev.ss05.halley.StellarQuartet.installedHere()));
        }
    }

    public static void click(boolean ok) {
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

        Component title = Component.translatable("gui.shooting_star_addition.panel").withStyle(ChatFormatting.BOLD);
        int w = Math.max(WIDTH, Math.max(Math.max(font.width(status), font.width(title)),
            font.width(Component.translatable("gui.shooting_star_addition.no_cooldown.disabled"))) + 22);
        int h = cooldownOff ? 296 : 284;
        g.pose().pushPose();
        // over the menu, in its top left corner
        g.pose().translate(8.0F, 8.0F, 400.0F);
        // never below one screen pixel per font pixel (smaller text turns to mush)
        float scale = Math.max(Mth.clamp(g.guiWidth() / 860.0F, MIN_SCALE, MAX_SCALE) * SIZE, 1.0F / (float) minecraft.getWindow().getGuiScale());
        g.pose().scale(scale, scale, 1.0F);
        // a soft shadow, the plate, its gold edge and title rule
        plate(g, 2, 3, w + 2, h + 3, 0x26000000);
        plate(g, 0, 0, w, h, 0xF4000000 | PLATE);
        g.fill(0, 2, 3, h - 2, 0xFF000000 | GOLD);
        g.drawString(font, title, 11, 7, 0xFF000000 | INK, false);
        g.fill(11, 19, w - 10, 20, 0xFF000000 | RULE);

        // the skills' music
        row(g, font, 26, Component.translatable("gui.shooting_star_addition.music"), "M", music, true, musicFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.music.hint"), 11, 38, 0xFF000000 | GREY, false);
        g.fill(11, 51, w - 10, 52, 0xFF000000 | RULE);

        // strikes' music stacking, or the newest taking over
        row(g, font, 58, Component.translatable("gui.shooting_star_addition.stack_music"), "=", HalleyClientConfig.stackMusic(), true,
            stackFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.stack_music.hint"), 11, 70, 0xFF000000 | GREY, false);
        g.fill(11, 83, w - 10, 84, 0xFF000000 | RULE);

        // Luna's Epic mode: a slower fall, for everyone watching
        row(g, font, 90, Component.translatable("gui.shooting_star_addition.epic"), "G", HalleyClientConfig.epicMode(), true, epicFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.epic.hint"), 11, 102, 0xFF000000 | GREY, false);
        g.fill(11, 115, w - 10, 116, 0xFF000000 | RULE);

        // keep Luna's moon model after it lands
        row(g, font, 122, Component.translatable("gui.shooting_star_addition.keep_moon"), "L", HalleyClientConfig.keepMoon(), true,
            keepMoonFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.keep_moon.hint"), 11, 134, 0xFF000000 | GREY, false);
        g.fill(11, 147, w - 10, 148, 0xFF000000 | RULE);

        // the impact frames: full or soft (on), or off
        HalleyClientConfig.ImpactFrames frames = HalleyClientConfig.impactFrames();
        row(g, font, 154, Component.translatable("gui.shooting_star_addition.frames"), "F", frames != HalleyClientConfig.ImpactFrames.OFF,
            true, framesFlippedAt, w, frames == HalleyClientConfig.ImpactFrames.SOFT ? "gui.shooting_star_addition.frames.soft"
                : "gui.shooting_star_addition.frames.full", "gui.shooting_star_addition.off");
        g.drawString(font, Component.translatable("gui.shooting_star_addition.frames.hint"), 11, 166, 0xFF000000 | GREY, false);
        g.fill(11, 179, w - 10, 180, 0xFF000000 | RULE);

        // where the evac takes you: up (the switch on) or to the edge
        row(g, font, 186, Component.translatable("gui.shooting_star_addition.evac"), "E", HalleyClientConfig.evacLift(), true,
            evacFlippedAt, w, "gui.shooting_star_addition.evac.up", "gui.shooting_star_addition.evac.edge");
        g.drawString(font, Component.translatable("gui.shooting_star_addition.evac.hint"), 11, 198, 0xFF000000 | GREY, false);
        g.fill(11, 211, w - 10, 212, 0xFF000000 | RULE);

        // the films' distances: kilometres (off) or miles (on)
        row(g, font, 218, Component.translatable("gui.shooting_star_addition.units"), "-", HalleyClientConfig.imperial(), true,
            unitsFlippedAt, w, "gui.shooting_star_addition.units.miles", "gui.shooting_star_addition.units.km");
        g.drawString(font, Component.translatable("gui.shooting_star_addition.units.hint"), 11, 230, 0xFF000000 | GREY, false);
        g.fill(11, 243, w - 10, 244, 0xFF000000 | RULE);

        // no cooldown, and what the server allows
        row(g, font, 250, Component.translatable("gui.shooting_star_addition.no_cooldown"), "C", cooldownOff, allowed, cooldownFlippedAt, w);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.no_cooldown.hint"), 11, 262, 0xFF000000 | GREY, false);
        g.drawString(font, status, 11, 273, 0xFF000000 | statusColor, false);
        if (cooldownOff) {
            g.drawString(font, Component.translatable("gui.shooting_star_addition.no_cooldown.careful"), 11, 284, 0xFF000000 | WARN, false);
        }
        g.pose().popPose();
    }

    private static void row(GuiGraphics g, Font font, int y, Component label, String key, boolean on, boolean enabled, long flippedAt, int w) {
        row(g, font, y, label, key, on, enabled, flippedAt, w, "gui.shooting_star_addition.on", "gui.shooting_star_addition.off");
    }

    /** A label, a key cap and a switch, on one line; {@code onText}/{@code offText} name its two states. */
    private static void row(GuiGraphics g, Font font, int y, Component label, String key, boolean on, boolean enabled, long flippedAt, int w,
                            String onText, String offText) {
        g.drawString(font, label.copy().withStyle(ChatFormatting.BOLD), 11, y + 1, 0xFF000000 | (enabled ? INK : GREY), false);
        int sw = 26;
        int sx = w - 12 - sw;
        keyCap(g, font, sx - 18, y, key, enabled);
        // the switch: its knob slides across as it is flipped
        float k = Mth.clamp((Util.getMillis() - flippedAt) / SLIDE_MILLIS, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        float at = on ? k : 1.0F - k;
        int track = !enabled ? RULE : lerp(0xC9CDD4, GOLD, at);
        plate(g, sx, y, sx + sw, y + 10, 0xFF000000 | track);
        int knob = sx + 1 + Math.round((sw - 10) * at);
        plate(g, knob, y + 1, knob + 8, y + 9, 0xFF000000 | (enabled ? 0xFFFFFF : 0xF0F1F3));
        Component state = Component.translatable(on ? onText : offText);
        g.drawString(font, state, sx - 22 - font.width(state), y + 1, 0xFF000000 | (on ? GOLD : GREY), false);
    }

    /** A key's cap, 12 wide, its top at {@code y - 1}. */
    public static void keyCap(GuiGraphics g, Font font, int x, int y, String key, boolean enabled) {
        plate(g, x, y - 1, x + 12, y + 11, 0xFF000000 | (enabled ? INK : GREY));
        plate(g, x + 1, y, x + 11, y + 10, 0xFF000000 | PLATE);
        g.drawString(font, key, x + 6 - font.width(key) / 2, y + 2, 0xFF000000 | (enabled ? INK : GREY), false);
    }

    /** A rectangle with its corners cut by a pixel (the menu's own plates are rounded). */
    public static void plate(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
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
