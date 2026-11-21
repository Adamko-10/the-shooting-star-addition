package dev.ss05.halley.client;

import dev.ss05.halley.net.ChoicePayload;
import dev.ss05.halley.net.ServerSettings;
import java.util.HashMap;
import java.util.Map;
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
 * The addon's settings in the Stellar Remote's menu (H), "look B" of 1.9.0 (the user's pick of 2026-11-12, built after
 * his OK of the mock-up on 2026-11-19): in the menu's top left, a plate with the general settings (music, stacking,
 * units, title cards, no cooldown), and under it a card with only the selected skill's own settings,
 * grouped, the less used ones folded into a group of their own (0 opens it). Stellar Convergence's card is drawn by
 * {@code compat/tss/client/ConvergencePlate} in the same place. Backspace folds everything to a small tag (the
 * in-game switch players asked for on CurseForge; {@code menu_plates = false} still hides it all).
 *
 * <p>The menu holds the mouse (you pick things in it by looking at them), and RimuLib's own menu uses the arrow keys
 * (its sliders, its list), Enter (its pages) and Escape, so every setting is flipped by its key, shown on its key cap:
 * the old letters, and digits for the rows that never had a key. A line at the bottom of the card says what the last
 * flipped setting does.
 *
 * <p>No-cooldown is the server's to allow ({@code no_cooldown}, and {@code no_cooldown_survival} for survival players);
 * the server tells this game its settings as it joins ({@link ServerSettings}), and this game tells it the player's own
 * switches back ({@link ChoicePayload}).
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
    /** A little smaller than the remote's other plates (the user, 2026-11-07). */
    private static final float SIZE = 0.82F;
    private static final int ROW = 14;
    /** 1.9.0 (Adam: "add a little explanation under each config"): each row's explanation under it, and the step to the next. */
    private static final int DESC = 9;
    private static final int LINE = ROW + DESC;
    /** The less-used settings' bar (taller than a group's heading, so it isn't missed). */
    private static final int BAR = 17;
    /** How long the card's bottom line explains the last flipped setting. */
    private static final long HINT_MILLIS = 6000L;
    private static final float SLIDE_MILLIS = 160.0F;

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
    /** . : the films' title cards. */
    public static final int KEY_TITLES = 46;
    /** Backspace folds the plates (RimuLib uses it only while a key is being rebound, when ours stand aside). */
    public static final int KEY_FOLD = 259;
    /** 0 opens and closes the selected skill's "Less used" group; 1-6 the rows that had no key (Halley's card). */
    public static final int KEY_0 = 48;

    /** Which of our skills is selected in the menu (the caller knows: compat/tss reads RimuLib's selection). */
    public enum Card { NONE, HALLEY, LUNA, PROXIMA, CONVERGENCE, LOCKED }

    /** Draws Stellar Convergence's card (or a locked skill's: what to install) at {@code top} in the plate's space, {@code width} wide; returns its height. */
    public interface CardBody {
        int draw(GuiGraphics g, Font font, int top, int width);
    }

    private static final Map<String, Long> FLIPPED = new HashMap<>();
    private static String lastHint;
    private static long lastHintAt;

    private AdditionPanel() {
    }

    // ---- Input. ---------------------------------------------------------------------------------------------------

    /** A key pressed while the menu is open: true if it was one of ours. */
    public static boolean key(int key, Card card) {
        if (key == KEY_FOLD) {
            HalleyClientConfig.setPlatesFolded(!HalleyClientConfig.platesFolded());
            click(true);
            return true;
        }
        if (HalleyClientConfig.platesFolded()) {
            return false;
        }
        // the general plate: always
        if (key == KEY_MUSIC) {
            return flip("music", () -> HalleyClientConfig.setLunaMusic(!HalleyClientConfig.lunaMusic()), false);
        }
        if (key == KEY_STACK) {
            return flip("stack_music", () -> HalleyClientConfig.setStackMusic(!HalleyClientConfig.stackMusic()), false);
        }
        if (key == KEY_UNITS) {
            return flip("units", () -> HalleyClientConfig.setImperial(!HalleyClientConfig.imperial()), false);
        }
        if (key == KEY_TITLES) {
            return flip("title_cards", () -> HalleyClientConfig.setTitleCards(!HalleyClientConfig.titleCards()), false);
        }
        if (key == KEY_COOLDOWN) {
            if (!allowedHere()) {
                hint("no_cooldown");
                click(false);
                return true;
            }
            return flip("no_cooldown", () -> HalleyClientConfig.setNoCooldown(!HalleyClientConfig.noCooldown()), true);
        }
        // 1.9.0 (Adam: G on Luna's card picked SS-21 Quasar, whose key it is): a card's keys win over the skills' own
        // keys, so each acts only while its row is shown (G on another card still picks Quasar)
        if (key == KEY_EPIC && card == Card.LUNA) {
            return flip("epic", () -> HalleyClientConfig.setEpicMode(!HalleyClientConfig.epicMode()), true);
        }
        if (key == KEY_KEEP_MOON && card == Card.LUNA) {
            return flip("keep_moon", () -> HalleyClientConfig.setKeepMoon(!HalleyClientConfig.keepMoon()), false);
        }
        if (key == KEY_FRAMES && (card == Card.HALLEY || card == Card.LUNA)) {
            HalleyClientConfig.ImpactFrames was = HalleyClientConfig.impactFrames();
            HalleyClientConfig.setImpactFrames(was.next());
            // the switch only slides between on and off (FULL -> SOFT stays on)
            if (was == HalleyClientConfig.ImpactFrames.SOFT || was == HalleyClientConfig.ImpactFrames.OFF) {
                FLIPPED.put("frames", Util.getMillis());
            }
            hint("frames");
            click(true);
            return true;
        }
        if (key == KEY_EVAC && (card == Card.HALLEY || card == Card.LUNA) && HalleyClientConfig.lessUsedOpen()) {
            return flip("evac", () -> HalleyClientConfig.setEvacLift(!HalleyClientConfig.evacLift()), true);
        }
        // the card's own
        if (card == Card.NONE || card == Card.LOCKED) {
            return false;
        }
        if (key == KEY_0 && hasLessUsed(card)) {
            HalleyClientConfig.setLessUsedOpen(!HalleyClientConfig.lessUsedOpen());
            click(true);
            return true;
        }
        if (card == Card.HALLEY) {
            switch (key - KEY_0) {
                case 1:
                    return flip("sky", () -> HalleyClientConfig.setSky(!HalleyClientConfig.sky()), false);
                case 2:
                    return flip("darken_land", () -> HalleyClientConfig.setDarkenLand(!HalleyClientConfig.darkenLand()), false);
                case 3:
                    return flip("extra_effects", () -> HalleyClientConfig.setExtraEffects(!HalleyClientConfig.extraEffects()), false);
                case 4:
                    return flip("world_flashes", () -> HalleyClientConfig.setWorldFlashes(!HalleyClientConfig.worldFlashes()), false);
                case 5:
                    return flip("comet_marker", () -> HalleyClientConfig.setCometMarker(!HalleyClientConfig.cometMarker()), false);
                default:
                    break;
            }
        }
        if (key == KEY_0 + 6 && hasLessUsed(card) && HalleyClientConfig.lessUsedOpen()) {
            HalleyClientConfig.PackMode[] modes = HalleyClientConfig.PackMode.values();
            HalleyClientConfig.setShaderPacks(modes[(HalleyClientConfig.shaderPacks().ordinal() + 1) % modes.length]);
            hint("shader_packs");
            click(true);
            return true;
        }
        return false;
    }

    private static boolean flip(String id, Runnable change, boolean tellServer) {
        change.run();
        FLIPPED.put(id, Util.getMillis());
        hint(id);
        if (tellServer) {
            sendChoice();
        }
        click(true);
        return true;
    }

    private static void hint(String id) {
        lastHint = id;
        lastHintAt = Util.getMillis();
    }

    private static boolean hasLessUsed(Card card) {
        return card == Card.HALLEY || card == Card.LUNA || card == Card.PROXIMA;
    }

    /** Tells the server this player's own switches (as they join, and whenever they flip one). */
    public static void sendChoice() {
        if (ClientPlayNetworking.canSend(ChoicePayload.TYPE)) {
            ClientPlayNetworking.send(new ChoicePayload(HalleyClientConfig.noCooldown(), HalleyClientConfig.evacLift(),
                HalleyClientConfig.epicMode(), dev.ss05.halley.StellarQuartet.installedHere()));
        }
    }

    /** A symbol key as the keyboard layout prints it (the key right of P is [ in the US, Ü in Germany). */
    private static String keyName(int key) {
        String name = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        return name.length() == 1 ? name.toUpperCase(java.util.Locale.ROOT) : name;
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

    /** The plates: the general one and, under it, the selected skill's card ({@code body} draws SA-00's or a locked one's). */
    public static void draw(GuiGraphics g, Card card, CardBody body) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        g.pose().pushPose();
        // over the menu, in its top left corner
        g.pose().translate(8.0F, 8.0F, 400.0F);
        // never below one screen pixel per font pixel (smaller text turns to mush)
        float scale = Math.max(Mth.clamp(g.guiWidth() / 860.0F, MIN_SCALE, MAX_SCALE) * SIZE, 1.0F / (float) minecraft.getWindow().getGuiScale());
        g.pose().scale(scale, scale, 1.0F);
        if (HalleyClientConfig.platesFolded()) {
            folded(g, font);
        } else {
            int w = width(font);
            int h = general(g, font, w);
            int top = h + 6;
            switch (card) {
                case HALLEY -> halley(g, font, top, w);
                case LUNA -> luna(g, font, top, w);
                case PROXIMA -> proxima(g, font, top, w);
                case CONVERGENCE, LOCKED -> body.draw(g, font, top, w);
                default -> {
                }
            }
        }
        g.pose().popPose();
    }

    private static int width(Font font) {
        int w = WIDTH;
        for (String key : new String[] {"no_cooldown.disabled", "no_cooldown.not_survival", "card.help"}) {
            w = Math.max(w, font.width(Component.translatable("gui.shooting_star_addition." + key)) + 22);
        }
        return w;
    }

    private static void folded(GuiGraphics g, Font font) {
        Component title = Component.translatable("gui.shooting_star_addition.panel.short").withStyle(ChatFormatting.BOLD);
        Component show = Component.translatable("gui.shooting_star_addition.fold.show");
        String cap = keyName(KEY_FOLD);
        int w = 11 + font.width(title) + 8 + font.width(show) + 8 + capWidth(font, cap) + 10;
        plate(g, 2, 3, w + 2, 23, 0x26000000);
        plate(g, 0, 0, w, 20, 0xF4000000 | PLATE);
        g.fill(0, 2, 3, 18, 0xFF000000 | GOLD);
        g.drawString(font, title, 11, 6, 0xFF000000 | INK, false);
        int x = 11 + font.width(title) + 8;
        g.drawString(font, show, x, 6, 0xFF000000 | GREY, false);
        keyCap(g, font, x + font.width(show) + 8, 5, cap, true);
    }

    /** The general plate; returns its height. */
    private static int general(GuiGraphics g, Font font, int w) {
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
        int h = 26 + 5 * LINE + 14 + 13;
        plate(g, 2, 3, w + 2, h + 3, 0x26000000);
        plate(g, 0, 0, w, h, 0xF4000000 | PLATE);
        g.fill(0, 2, 3, h - 2, 0xFF000000 | GOLD);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.panel").withStyle(ChatFormatting.BOLD), 11, 7, 0xFF000000 | INK, false);
        String cap = keyName(KEY_FOLD);
        Component fold = Component.translatable("gui.shooting_star_addition.fold");
        int capX = w - 10 - capWidth(font, cap);
        keyCap(g, font, capX, 6, cap, true);
        g.drawString(font, fold, capX - 4 - font.width(fold), 7, 0xFF000000 | GREY, false);
        g.fill(11, 19, w - 10, 20, 0xFF000000 | RULE);

        int y = 26;
        row(g, font, y, "music", "M", HalleyClientConfig.lunaMusic(), true, w);
        y += LINE;
        row(g, font, y, "stack_music", keyName(KEY_STACK), HalleyClientConfig.stackMusic(), true, w);
        y += LINE;
        row(g, font, y, "units", keyName(KEY_UNITS), HalleyClientConfig.imperial(), true, w, "units.miles", "units.km");
        y += LINE;
        row(g, font, y, "title_cards", keyName(KEY_TITLES), HalleyClientConfig.titleCards(), true, w);
        y += LINE;
        row(g, font, y, "no_cooldown", "C", cooldownOff, allowed, w);
        y += LINE;
        g.drawString(font, status, 11, y, 0xFF000000 | statusColor, false);
        // 1.9.0 (Adam: "make it say somewhere in a good spot that u can right click to see a description of the skill"):
        // RimuLib's menu shows a skill's description on a right click, which its own hint bar only mentions in passing
        g.fill(11, y + 11, w - 10, y + 12, 0xFF000000 | RULE);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.right_click"), 11, y + 15, 0xFF000000 | GOLD, false);
        return h;
    }

    /** A card's frame and title (in the skill's colour); returns the y its rows start at. */
    private static int cardTop(GuiGraphics g, Font font, int top, int w, int h, String titleKey, int color) {
        plate(g, 2, top + 3, w + 2, top + h + 3, 0x26000000);
        plate(g, 0, top, w, top + h, 0xF4000000 | PLATE);
        g.fill(0, top + 2, 3, top + h - 2, 0xFF000000 | color);
        g.drawString(font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), 11, top + 7, 0xFF000000 | INK, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.card.only"), 11, top + 18, 0xFF000000 | GREY, false);
        g.fill(11, top + 30, w - 10, top + 31, 0xFF000000 | RULE);
        return top + 36;
    }

    /** A group's heading; {@code fold} is its key when it folds (0), else null. Returns the y under it. */
    private static int group(GuiGraphics g, Font font, int y, int w, String nameKey, String fold, int count) {
        Component name = Component.translatable(nameKey).withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, 11, y, 0xFF000000 | GREY, false);
        if (fold != null) {
            boolean open = HalleyClientConfig.lessUsedOpen();
            Component state = Component.translatable(open ? "gui.shooting_star_addition.group.open" : "gui.shooting_star_addition.group.closed", count);
            int capX = w - 10 - capWidth(font, fold);
            keyCap(g, font, capX, y - 1, fold, true);
            g.drawString(font, state, capX - 4 - font.width(state), y, 0xFF000000 | GREY, false);
        }
        return y + 12;
    }

    /** The card's last line: what the last flipped setting does, else how the card works. */
    private static void footer(GuiGraphics g, Font font, int y, int w) {
        g.fill(11, y - 3, w - 10, y - 2, 0xFF000000 | RULE);
        boolean recent = lastHint != null && Util.getMillis() - lastHintAt < HINT_MILLIS;
        Component line = Component.translatable(recent ? "gui.shooting_star_addition." + lastHint + ".hint" : "gui.shooting_star_addition.card.help");
        g.drawString(font, line, 11, y + 1, 0xFF000000 | (recent ? INK : GREY), false);
    }

    private static int lessUsedRows(Card card) {
        return card == Card.PROXIMA ? 1 : 2;
    }

    private static void halley(GuiGraphics g, Font font, int top, int w) {
        boolean less = HalleyClientConfig.lessUsedOpen();
        int h = 36 + 12 + 5 * LINE + 4 + 12 + LINE + 4 + BAR + (less ? 2 * LINE : 0) + 18;
        int y = cardTop(g, font, top, w, h, "gui.shooting_star_addition.card.halley", 0x8FD8FF);
        y = group(g, font, y, w, "gui.shooting_star_addition.group.look", null, 0);
        row(g, font, y, "sky", "1", HalleyClientConfig.sky(), true, w);
        y += LINE;
        row(g, font, y, "darken_land", "2", HalleyClientConfig.darkenLand(), true, w);
        y += LINE;
        row(g, font, y, "extra_effects", "3", HalleyClientConfig.extraEffects(), true, w);
        y += LINE;
        row(g, font, y, "world_flashes", "4", HalleyClientConfig.worldFlashes(), true, w);
        y += LINE;
        row(g, font, y, "comet_marker", "5", HalleyClientConfig.cometMarker(), true, w);
        y += LINE + 4;
        y = group(g, font, y, w, "gui.shooting_star_addition.group.impact", null, 0);
        frames(g, font, y, w);
        y += LINE + 4;
        y = lessUsed(g, font, y, w, Card.HALLEY);
        footer(g, font, top + h - 14, w);
    }

    private static void luna(GuiGraphics g, Font font, int top, int w) {
        boolean less = HalleyClientConfig.lessUsedOpen();
        int h = 36 + 12 + 2 * LINE + 4 + 12 + LINE + 4 + BAR + (less ? 2 * LINE : 0) + 18;
        int y = cardTop(g, font, top, w, h, "gui.shooting_star_addition.card.luna", 0xFF8A3D);
        y = group(g, font, y, w, "gui.shooting_star_addition.group.fall", null, 0);
        row(g, font, y, "epic", "G", HalleyClientConfig.epicMode(), true, w);
        y += LINE;
        row(g, font, y, "keep_moon", "L", HalleyClientConfig.keepMoon(), true, w);
        y += LINE + 4;
        y = group(g, font, y, w, "gui.shooting_star_addition.group.impact", null, 0);
        frames(g, font, y, w);
        y += LINE + 4;
        y = lessUsed(g, font, y, w, Card.LUNA);
        footer(g, font, top + h - 14, w);
    }

    private static void proxima(GuiGraphics g, Font font, int top, int w) {
        boolean less = HalleyClientConfig.lessUsedOpen();
        int h = 36 + 2 * 11 + 6 + BAR + (less ? LINE : 0) + 18;
        int y = cardTop(g, font, top, w, h, "gui.shooting_star_addition.card.proxima", 0xFF2E63);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.card.proxima.general"), 11, y, 0xFF000000 | GREY, false);
        g.drawString(font, Component.translatable("gui.shooting_star_addition.card.proxima.general2"), 11, y + 11, 0xFF000000 | GREY, false);
        y += 2 * 11 + 6;
        y = lessUsed(g, font, y, w, Card.PROXIMA);
        footer(g, font, top + h - 14, w);
    }

    private static int lessUsed(GuiGraphics g, Font font, int y, int w, Card card) {
        y = lessUsedBar(g, font, y, w, lessUsedRows(card));
        if (!HalleyClientConfig.lessUsedOpen()) {
            return y;
        }
        valueRow(g, font, y, "shader_packs", "6", "gui.shooting_star_addition.shader_packs."
            + HalleyClientConfig.shaderPacks().name().toLowerCase(java.util.Locale.ROOT), w);
        y += LINE;
        if (card != Card.PROXIMA) {
            row(g, font, y, "evac", "E", HalleyClientConfig.evacLift(), true, w, "evac.up", "evac.edge");
            y += LINE;
        }
        return y;
    }

    private static void frames(GuiGraphics g, Font font, int y, int w) {
        HalleyClientConfig.ImpactFrames frames = HalleyClientConfig.impactFrames();
        row(g, font, y, "frames", "F", frames != HalleyClientConfig.ImpactFrames.OFF, true, w,
            frames == HalleyClientConfig.ImpactFrames.SOFT ? "frames.soft" : "frames.full", "off");
    }

    private static void row(GuiGraphics g, Font font, int y, String id, String key, boolean on, boolean enabled, int w) {
        row(g, font, y, id, key, on, enabled, w, "on", "off");
    }

    /** A label, its state, a key cap and a switch, on one line; {@code onText}/{@code offText} name its two states. */
    private static void row(GuiGraphics g, Font font, int y, String id, String key, boolean on, boolean enabled, int w, String onText,
                            String offText) {
        g.drawString(font, Component.translatable("gui.shooting_star_addition." + id), 11, y + 1, 0xFF000000 | (enabled ? INK : GREY), false);
        int sw = 26;
        int sx = w - 12 - sw;
        int capX = sx - 6 - capWidth(font, key);
        keyCap(g, font, capX, y, key, enabled);
        // the switch: its knob slides across as it is flipped
        float k = Mth.clamp((Util.getMillis() - FLIPPED.getOrDefault(id, 0L)) / SLIDE_MILLIS, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        float at = on ? k : 1.0F - k;
        int track = !enabled ? RULE : lerp(0xC9CDD4, GOLD, at);
        plate(g, sx, y, sx + sw, y + 10, 0xFF000000 | track);
        int knob = sx + 1 + Math.round((sw - 10) * at);
        plate(g, knob, y + 1, knob + 8, y + 9, 0xFF000000 | (enabled ? 0xFFFFFF : 0xF0F1F3));
        Component state = Component.translatable("gui.shooting_star_addition." + (on ? onText : offText));
        g.drawString(font, state, capX - 4 - font.width(state), y + 1, 0xFF000000 | (on ? GOLD : GREY), false);
        describe(g, font, y, id);
    }

    /** 1.9.0: what a row's setting does, small and grey under it (its hint). */
    public static void describe(GuiGraphics g, Font font, int y, String id) {
        g.pose().pushPose();
        g.pose().translate(11.0F, y + 12.0F, 0.0F);
        g.pose().scale(0.8F, 0.8F, 1.0F);
        g.drawString(font, Component.translatable("gui.shooting_star_addition." + id + ".hint"), 0, 0, 0xFF000000 | GREY, false);
        g.pose().popPose();
    }

    /**
     * 1.9.0 (Adam: "make it more obvious ... so people dont miss it"): the less-used settings behind a gold bar with an
     * arrow, how many there are and the key that opens it; it breathes gently while closed.
     */
    private static int lessUsedBar(GuiGraphics g, Font font, int y, int w, int count) {
        boolean open = HalleyClientConfig.lessUsedOpen();
        Component name = Component.literal(open ? "▼ " : "▶ ")
            .append(Component.translatable("gui.shooting_star_addition.group.less").withStyle(ChatFormatting.BOLD));
        Component state = Component.translatable(open ? "gui.shooting_star_addition.group.open" : "gui.shooting_star_addition.group.closed", count);
        return goldBar(g, font, y, w, name, state, "0", !open);
    }

    /**
     * A gold bar across the card with a name on the left and, on the right, what its key does and the key (the Less used
     * bar; Stellar Convergence's pages, 1.9.0); it breathes gently while it asks to be pressed. Returns the y under it.
     */
    public static int goldBar(GuiGraphics g, Font font, int y, int w, Component name, Component state, String key, boolean breathe) {
        float breath = breathe ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 320.0) : 0.0F;
        plate(g, 8, y - 3, w - 7, y + BAR - 4, 0xFF000000 | lerp(0xFFF4DA, 0xFFE2A0, breath));
        g.fill(8, y - 2, 10, y + BAR - 5, 0xFF000000 | GOLD);
        g.drawString(font, name, 14, y + 1, 0xFF000000 | INK, false);
        int capX = w - 12 - capWidth(font, key);
        keyCap(g, font, capX, y, key, true);
        g.drawString(font, state, capX - 4 - font.width(state), y + 1, 0xFF000000 | (breathe ? INK : GREY), false);
        return y + BAR;
    }

    /** The height of a {@link #goldBar}. */
    public static int goldBarHeight() {
        return BAR;
    }

    /** A label, a key cap and a value (no switch: more than two values). */
    private static void valueRow(GuiGraphics g, Font font, int y, String id, String key, String valueKey, int w) {
        g.drawString(font, Component.translatable("gui.shooting_star_addition." + id), 11, y + 1, 0xFF000000 | INK, false);
        int capX = w - 10 - capWidth(font, key);
        keyCap(g, font, capX, y, key, true);
        Component value = Component.translatable(valueKey);
        g.drawString(font, value, capX - 4 - font.width(value), y + 1, 0xFF000000 | GOLD, false);
        describe(g, font, y, id);
    }

    private static int capWidth(Font font, String key) {
        return Math.max(12, font.width(key) + 6);
    }

    /** A key's cap, at least 12 wide (wider for a name like Backspace), its top at {@code y - 1}. */
    public static void keyCap(GuiGraphics g, Font font, int x, int y, String key, boolean enabled) {
        int w = capWidth(font, key);
        plate(g, x, y - 1, x + w, y + 11, 0xFF000000 | (enabled ? INK : GREY));
        plate(g, x + 1, y, x + w - 1, y + 10, 0xFF000000 | PLATE);
        g.drawString(font, key, x + w / 2 - font.width(key) / 2, y + 2, 0xFF000000 | (enabled ? INK : GREY), false);
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
