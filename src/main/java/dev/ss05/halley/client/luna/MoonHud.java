package dev.ss05.halley.client.luna;

import dev.ss05.halley.world.Codenames;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.render.MoonRenderer;
import dev.ss05.halley.world.MoonInfo;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

/**
 * SS-06's on-screen text: the big glitching "EARTH SYSTEM SHUT DOWN" the caster gets (also shown on their film) and
 * the off-screen marker anyone near the strike gets while the moon isn't in view (modelled on
 * {@code client/HalleyHud}; other players nearby get their own warning in the action bar, from the server).
 */
public final class MoonHud {
    private static final int RED = 0xFF3030;
    private static final int CYAN = 0x30E0FF;
    private static final int GOLD = MoonInfo.COLOR;

    private MoonHud() {
    }

    /**
     * Outside the film (either the caster skipped the cutscene, or this is somebody else): the glitching alert (the
     * caster only), the SS-05-style "INBOUND" panel at the top of the screen with a countdown, and the off-screen
     * marker pointing at the moon while it's out of view.
     */
    public static void hud(GuiGraphics g, MoonFx fx, float partial, int w, int h) {
        if (fx.filmWeight() > 0.05F) {
            return;
        }
        if (fx.mine) {
            alert(g, fx, fx.time(partial), w, h);
        }
        world(g, fx, partial, w);
        marker(g, fx, partial, w, h);
    }

    /** For anyone near the strike, outside the film: what is coming, and how long until it lands (modelled on
     * {@code HalleyHud.world}). */
    private static void world(GuiGraphics g, MoonFx fx, float partial, int w) {
        Minecraft minecraft = Minecraft.getInstance();
        MoonPlan p = fx.plan;
        float t = fx.time(partial);
        if (minecraft.player == null || t < MoonPlan.BREAK || t >= MoonPlan.CONTACT) {
            return;
        }
        Vec3 me = minecraft.player.position();
        double blast = p.blastRadius();
        double fromMark = Math.hypot(me.x - p.target.x, me.z - p.target.z);
        if (fromMark > blast + p.params.moonRadius() + 160.0) {
            return;
        }
        boolean erase = fromMark < p.eraseRadius();
        boolean inBlast = fromMark < blast;
        Font font = minecraft.font;
        float rate = 1.0F + 3.0F * Mth.clamp((t - MoonPlan.ENTRY) / (MoonPlan.CONTACT - MoonPlan.ENTRY), 0.0F, 1.0F);
        float blink = (t / 20.0F * rate) % 1.0F;
        int a = (int) (255.0F * (0.55F + 0.45F * (1.0F - blink)));
        String title = erase ? "INSIDE THE ERASE RADIUS" : inBlast ? "INSIDE THE BLAST RADIUS" : "MOON INBOUND";
        int colour = erase || inBlast ? RED : CYAN;
        String clock = String.format(Locale.ROOT, "T-%05.2f", Math.max(0.0F, (MoonPlan.CONTACT - t) / 20.0F));
        bracketed(g, font, title, w / 2, 26, a << 24 | colour, 1.5F);
        centered(g, font, clock, w / 2, 44, 0xFFFFFFFF, 1.25F);
    }

    /** "EARTH SYSTEM SHUT DOWN": huge, red, glitching, for ~3.5 seconds from the alarm. {@code t} is in ticks since
     * the button was pressed - the live clock outside the film, the cutscene's own clock inside it. */
    private static void alert(GuiGraphics g, MoonFx fx, float t, int w, int h) {
        float since = t - MoonPlan.ALARM;
        if (since < 0.0F || since > MoonFx.ALARM_TICKS) {
            return;
        }
        float fade = Curve.window(since, 0.0F, 5.0F, MoonFx.ALARM_TICKS - 16.0F, MoonFx.ALARM_TICKS);
        if (fade <= 0.01F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String text = MoonInfo.ALERT;
        long seed = fx.plan.seed ^ (long) (since * 971);
        boolean burst = pseudo(seed) < 0.22F;
        int jx = burst ? (int) ((pseudo(seed ^ 11L) - 0.5F) * 14.0F) : 0;
        int jy = burst ? (int) ((pseudo(seed ^ 29L) - 0.5F) * 5.0F) : 0;
        float scale = 4.0F;
        int cx = w / 2 + jx;
        int cy = h / 2 - 20 + jy;
        int a = (int) (255.0F * fade);

        // scanlines, across the whole screen, so it reads like the remote's display failing
        for (int y = 0; y < h; y += 4) {
            g.fill(0, y, w, y + 1, (int) (10.0F * fade) << 24);
        }
        // a chromatic-split ghost either side of the main text, the way a torn signal smears
        int split = burst ? 3 : 1;
        text(g, font, text, cx - split, cy, (a * 2 / 3) << 24 | RED, scale);
        text(g, font, text, cx + split, cy, (a * 2 / 3) << 24 | CYAN, scale);
        text(g, font, text, cx, cy, a << 24 | 0xFFFFFF, scale);

        String sub = "// " + Codenames.LUNA + " LUNA · ALL SYSTEMS DOWN //";
        text(g, font, sub, w / 2 - font.width(sub) * 1.5F / 2.0F, cy + 20.0F * scale / 4.0F + 14.0F, (a * 3 / 4) << 24 | RED, 1.5F);
    }

    /** An arrow at the edge of the screen pointing at the moon whenever it's out of view. */
    private static void marker(GuiGraphics g, MoonFx fx, float partial, int w, int h) {
        if (!HalleyClientConfig.cometMarker()) {
            return;
        }
        MoonPlan p = fx.plan;
        float t = fx.time(partial);
        Minecraft minecraft = Minecraft.getInstance();
        if (t < MoonPlan.BREAK || t >= MoonPlan.CONTACT || minecraft.player == null) {
            return;
        }
        Vec3 me = minecraft.player.position();
        if (Math.hypot(me.x - p.target.x, me.z - p.target.z) > 4000.0) {
            return;
        }
        Vec3 moon = p.centre(t);
        float[] at = MoonRenderer.toScreen(moon);
        if (at == null || at[2] < 0.5F && Math.abs(at[0]) < 0.94F && Math.abs(at[1]) < 0.9F) {
            return;
        }
        double angle = Math.atan2(at[1] * h, at[0] * w);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double rx = w * 0.5 - 30.0;
        double ry = h * 0.5 - 30.0;
        double reach = Math.min(rx / Math.max(Math.abs(c), 1.0E-4), ry / Math.max(Math.abs(s), 1.0E-4));
        float x = (float) (w * 0.5 + c * reach);
        float y = (float) (h * 0.5 - s * reach);
        int a = (int) (255.0F * (0.6F + 0.4F * (float) Math.abs(Math.sin(t * 0.22))));
        int ink = a << 24 | GOLD;

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) -angle);
        for (int i = 0; i < 14; i++) {
            g.fill(i - 14, -(14 - i), i - 13, 14 - i, ink);
        }
        g.fill(-24, -2, -14, 2, ink);
        pose.popMatrix();

        Font font = minecraft.font;
        String range = String.format(Locale.ROOT, "%.1f KM", moon.distanceTo(MoonFx.camera()) / 1000.0);
        float lx = (float) Mth.clamp(x - c * 48.0, 60.0, w - 60.0);
        float ly = (float) Mth.clamp(y + s * 40.0, 20.0, h - 30.0);
        String name = Codenames.LUNA + " · LUNA";
        text(g, font, name, lx - font.width(name) * 1.25F / 2.0F, ly - 12.0F, ink, 1.25F);
        text(g, font, range, lx - font.width(range) / 2.0F, ly + 1.0F, (int) (a * 0.7F) << 24 | 0xE2FBFF, 1.0F);
    }

    /**
     * The caster's film: an uplink overlay - thin letterbox bars, the phase of the strike typed out as each begins, the
     * moon's telemetry (range, altitude, velocity, ETA), a descent bar along the bottom with the impact at its end, the
     * target lock, a "high-speed capture" tag while the impact plays in slow motion, and the big alert re-shown (the
     * cutscene starts at age 0) - modelled on {@code HalleyHud.film}.
     */
    public static void film(GuiGraphics g, MoonFx fx, float partial, int w, int h, HalleyHud.@Nullable Projector project) {
        float ct = fx.filmTime(partial);
        if (Float.isNaN(ct)) {
            return;
        }
        float fade = Mth.clamp(fx.filmWeight() * 1.6F - 0.3F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        MoonPlan p = fx.plan;
        Font font = Minecraft.getInstance().font;
        int a = (int) (fade * 255.0F);
        boolean flicker = (int) (ct * 3.0F) % 11 == 0;
        int ink = a << 24 | GOLD;
        int dim = (int) (a * 0.55F) << 24 | 0xFFE2C8;

        // letterbox bars, eased in as the film starts
        int bar = (int) (h * 0.055F * Mth.clamp(ct / 12.0F, 0.0F, 1.0F) * fade);
        if (bar > 0) {
            g.fill(0, 0, w, bar, (int) (a * 0.92F) << 24);
            g.fill(0, h - bar, w, h, (int) (a * 0.92F) << 24);
        }
        if (ct >= MoonPlan.ALARM && ct <= MoonPlan.ALARM + MoonFx.ALARM_TICKS) {
            alert(g, fx, ct, w, h);
        }
        int m = 12 + bar;
        int arm = 22;
        corner(g, 12, m, arm, 1, 1, ink);
        corner(g, w - 12, m, arm, -1, 1, ink);
        corner(g, 12, h - m, arm, 1, -1, ink);
        corner(g, w - 12, h - m, arm, -1, -1, ink);
        text(g, font, "UPLINK // " + Codenames.LUNA + " > LUNA", 18, m + 6, flicker ? dim : ink, 1.0F);
        text(g, font, String.format(Locale.ROOT, "T+%05.2f", ct / 20.0F), 18, m + 17, dim, 1.0F);
        String clock = ct < MoonPlan.CONTACT ? String.format(Locale.ROOT, "IMPACT T-%05.2f", Math.max(0.0F, (MoonPlan.CONTACT - ct) / 20.0F))
            : "IMPACT CONFIRMED";
        right(g, font, clock, w - 18, m + 6, ink, 1.0F);
        right(g, font, String.format(Locale.ROOT, "LOCK %d / %d / %d", Mth.floor(p.target.x), Mth.floor(p.target.y), Mth.floor(p.target.z)),
            w - 18, m + 17, dim, 1.0F);
        phase(g, font, ct, h, m, a);

        if (ct >= MoonPlan.ALARM && ct < MoonPlan.ALARM + 70.0F && project != null) {
            float[] at = project.project(p.target.add(0.0, 0.5, 0.0));
            if (at != null) {
                float k = Mth.clamp((ct - MoonPlan.ALARM) / 10.0F, 0.0F, 1.0F);
                int size = (int) Mth.lerp(k, 70.0F, 10.0F);
                box(g, (int) at[0], (int) at[1], size, ink);
                text(g, font, "TARGET ACQUIRED", at[0] + size + 4, at[1] - 9, ink, 1.0F);
                text(g, font, String.format(Locale.ROOT, "ERASE %d M · BLAST %d M", Mth.ceil(p.eraseRadius()), Mth.ceil(p.blastRadius())),
                    at[0] + size + 4, at[1] + 1, dim, 1.0F);
            }
        }

        if (ct >= MoonPlan.BREAK && ct < MoonPlan.CONTACT) {
            Vec3 moon = p.centre(ct);
            if (project != null) {
                float[] at = project.project(moon);
                if (at != null) {
                    double d = moon.distanceTo(MoonFx.camera());
                    int size = (int) Mth.clamp(20.0 - d / 300.0, 8.0, 20.0);
                    box(g, (int) at[0], (int) at[1], size, ink);
                    text(g, font, Codenames.LUNA + " · LUNA", at[0] + size + 4, at[1] - 9, ink, 1.0F);
                    text(g, font, String.format(Locale.ROOT, "RANGE %.2f KM · ALT %,.0f M", d / 1000.0, moon.y - p.target.y),
                        at[0] + size + 4, at[1] + 1, dim, 1.0F);
                }
            }
            double eta = Math.max(0.0, MoonPlan.CONTACT - ct) / 20.0;
            right(g, font, String.format(Locale.ROOT, "V %,.0f M/S", p.speed(ct) * 20.0), w - 18, h - m - 44, ink, 1.5F);
            right(g, font, String.format(Locale.ROOT, "ETA %.1f S · CLOSING", eta), w - 18, h - m - 28, dim, 1.0F);
            if (ct >= MoonPlan.ENTRY - 30.0F) {
                boolean blink = (int) (ct / 2.0F) % 2 == 0;
                bracketed(g, font, "ENTRY · PLASMA SHEATH", w / 2, m + 22, blink ? ink : dim, 1.5F);
            }
        }
        descent(g, font, ct, w, h, m, a);

        // the impact in slow motion: a high-speed camera's tag, and its frame rate
        float rate = fx.filmRate(fx.realTime(partial));
        if (rate < 0.97F && ct >= MoonPlan.CONTACT) {
            float k = Mth.clamp((1.0F - rate) / 0.6F, 0.0F, 1.0F);
            int b = (int) (a * k);
            if ((int) (ct / 6.0F) % 2 == 0) {
                g.fill(18, m + 32, 24, m + 38, b << 24 | 0xE0302A);
            }
            text(g, font, String.format(Locale.ROOT, "HIGH-SPEED CAPTURE · %.2f×", rate), 28, m + 31, b << 24 | 0xFFFFFF, 1.0F);
            text(g, font, String.format(Locale.ROOT, "%d FPS", Math.round(24.0F / Math.max(0.05F, rate))), 28, m + 41,
                (int) (b * 0.6F) << 24 | 0xFFE2C8, 1.0F);
        }

        if (ct >= MoonPlan.CONTACT + 4.0F && ct < MoonPlan.CONTACT + 60.0F) {
            int b = (int) (a * Mth.clamp((MoonPlan.CONTACT + 60.0F - ct) / 12.0F, 0.0F, 1.0F));
            bracketed(g, font, "IMPACT CONFIRMED", w / 2, h / 2 + 50, b << 24 | GOLD, 2.0F);
        }
        if (ct >= MoonPlan.CONTACT + 100.0F) {
            int b = (int) (a * Mth.clamp((ct - MoonPlan.CONTACT - 100.0F) / 10.0F, 0.0F, 1.0F));
            centered(g, font, String.format(Locale.ROOT, "IMPACT ZONE Ø%d · MOON Ø%d · %d%% BURIED",
                p.params.craterRadius() * 2, p.params.moonRadius() * 2, p.params.buriedPercent()), w / 2, h - m - 30, b << 24 | 0xFFE2C8, 1.25F);
        }
    }

    /** One phase of the strike: when it starts, its caption and the line under it. */
    private record Phase(int at, String title, String sub) {
    }

    private static final Phase[] PHASES = {
        new Phase(MoonPlan.PRESS, "PHASE 1 · UPLINK", "EARTH SYSTEM SHUTDOWN AUTHORISED"),
        new Phase(MoonPlan.BREAK, "PHASE 2 · FRACTURE", "LUNAR CRUST FAILING ALONG MOLTEN SEAMS"),
        new Phase(MoonPlan.FALL, "PHASE 3 · DESCENT", "ORBIT LOST · ON COURSE FOR THE MARK"),
        new Phase(MoonPlan.ENTRY, "PHASE 4 · ENTRY", "ATMOSPHERIC INTERFACE · HULL AT 9000 K"),
        new Phase(MoonPlan.CONTACT, "PHASE 5 · IMPACT", "THE MOON IS DOWN"),
        new Phase(MoonPlan.SETTLE, "AFTERMATH", "IMPACT ZONE ERASED · THE MOON AT REST"),
    };

    /** A lower-third caption typed out as each phase begins, then fading. */
    private static void phase(GuiGraphics g, Font font, float ct, int h, int m, int a) {
        for (int i = PHASES.length - 1; i >= 0; i--) {
            Phase phase = PHASES[i];
            float since = ct - phase.at();
            if (since < 0.0F) {
                continue;
            }
            float k = Curve.window(since, 0.0F, 3.0F, 56.0F, 70.0F);
            int b = (int) (a * k);
            if (b < 4) {
                return;
            }
            int typed = Math.min(phase.title().length(), (int) (since * 1.6F));
            int typedSub = Math.min(phase.sub().length(), Math.max(0, (int) ((since - 6.0F) * 2.4F)));
            boolean cursor = typed < phase.title().length() && (int) (ct * 2.0F) % 2 == 0;
            int x = 18;
            int y = h - m - 78;
            g.fill(x, y - 3, x + 2, y + 21, b << 24 | GOLD);
            text(g, font, phase.title().substring(0, typed) + (cursor ? "_" : ""), x + 7, y, b << 24 | 0xFFFFFF, 1.25F);
            text(g, font, phase.sub().substring(0, typedSub), x + 7, y + 13, (int) (b * 0.7F) << 24 | 0xFFE2C8, 1.0F);
            return;
        }
    }

    /** Along the bottom: how far down the moon has come, with the fracture, the fall, entry and the impact marked. */
    private static void descent(GuiGraphics g, Font font, float ct, int w, int h, int m, int a) {
        if (ct < MoonPlan.ALARM || ct > MoonPlan.CONTACT + 40.0F) {
            return;
        }
        float out = Mth.clamp((MoonPlan.CONTACT + 40.0F - ct) / 20.0F, 0.0F, 1.0F);
        int b = (int) (a * out * Mth.clamp((ct - MoonPlan.ALARM) / 10.0F, 0.0F, 1.0F));
        if (b < 4) {
            return;
        }
        int x0 = w / 2 - Math.min(160, w / 4);
        int x1 = w / 2 + Math.min(160, w / 4);
        int y = h - m - 8;
        g.fill(x0, y, x1, y + 1, (int) (b * 0.5F) << 24 | 0xFFE2C8);
        float k = Mth.clamp((ct - MoonPlan.ALARM) / (MoonPlan.CONTACT - MoonPlan.ALARM), 0.0F, 1.0F);
        int filled = x0 + Math.round((x1 - x0) * k);
        g.fill(x0, y - 1, filled, y + 2, b << 24 | GOLD);
        for (int at : new int[]{MoonPlan.BREAK, MoonPlan.FALL, MoonPlan.ENTRY, MoonPlan.CONTACT}) {
            int tx = x0 + Math.round((x1 - x0) * (at - MoonPlan.ALARM) / (float) (MoonPlan.CONTACT - MoonPlan.ALARM));
            boolean passed = ct >= at;
            g.fill(tx, y - 4, tx + 1, y + 5, (passed ? b : (int) (b * 0.5F)) << 24 | (passed ? GOLD : 0xFFE2C8));
        }
        // the moon's marker riding the bar, pulsing faster as it closes in
        float pulse = 0.6F + 0.4F * (float) Math.abs(Math.sin(ct * (0.15F + 0.5F * k)));
        g.fill(filled - 2, y - 3, filled + 3, y + 4, (int) (b * pulse) << 24 | 0xFFFFFF);
        centered(g, font, "DESCENT", (x0 + x1) / 2, y - 14, (int) (b * 0.6F) << 24 | 0xFFE2C8, 1.0F);
    }

    private static float pseudo(long seed) {
        long h = seed * 0x2545F4914F6CDD1DL;
        h ^= h >>> 33;
        return (float) ((h >>> 40 & 0xFFFF) / 65536.0);
    }

    private static void text(GuiGraphics g, Font font, String s, float x, float y, int color, float scale) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.drawString(font, s, 0, 0, color, false);
        pose.popMatrix();
    }

    // ---- Drawing helpers (the same look as HalleyHud's own; duplicated here to keep this package independent). ----

    private static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm), y + dy, color);
        g.fill(x, Math.min(y, y + dy * arm), x + dx, Math.max(y, y + dy * arm), color);
    }

    private static void box(GuiGraphics g, int x, int y, int size, int color) {
        int arm = Math.max(3, size / 3);
        corner(g, x - size, y - size, arm, 1, 1, color);
        corner(g, x + size, y - size, arm, -1, 1, color);
        corner(g, x - size, y + size, arm, 1, -1, color);
        corner(g, x + size, y + size, arm, -1, -1, color);
    }

    private static void right(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        text(g, font, s, x - font.width(s) * scale, y, color, scale);
    }

    private static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - font.width(s) * scale / 2.0F, y, color, scale);
    }

    private static void bracketed(GuiGraphics g, Font font, String s, int cx, int y, int color, float scale) {
        centered(g, font, "[ " + s + " ]", cx, y, color, scale);
    }

    /** Tiny stand-in for {@code HalleyFx.window}, duplicated here to keep this package independent. */
    private static final class Curve {
        static float window(float t, float in0, float in1, float out0, float out1) {
            return (float) (Mth.clamp((t - in0) / Math.max(in1 - in0, 0.001), 0.0, 1.0)
                * (1.0 - Mth.clamp((t - out0) / Math.max(out1 - out0, 0.001), 0.0, 1.0)));
        }
    }
}
