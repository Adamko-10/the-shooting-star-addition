package dev.ss05.halley.client.luna;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.render.MoonRenderer;
import dev.ss05.halley.world.MoonInfo;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public static void hud(GuiGraphicsExtractor g, MoonFx fx, float partial, int w, int h) {
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
    private static void world(GuiGraphicsExtractor g, MoonFx fx, float partial, int w) {
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
    private static void alert(GuiGraphicsExtractor g, MoonFx fx, float t, int w, int h) {
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

        String sub = "// SS-06 LUNA · ALL SYSTEMS DOWN //";
        text(g, font, sub, w / 2 - font.width(sub) * 1.5F / 2.0F, cy + 20.0F * scale / 4.0F + 14.0F, (a * 3 / 4) << 24 | RED, 1.5F);
    }

    /** An arrow at the edge of the screen pointing at the moon whenever it's out of view. */
    private static void marker(GuiGraphicsExtractor g, MoonFx fx, float partial, int w, int h) {
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
        text(g, font, "SS-06 · LUNA", lx - font.width("SS-06 · LUNA") * 1.25F / 2.0F, ly - 12.0F, ink, 1.25F);
        text(g, font, range, lx - font.width(range) / 2.0F, ly + 1.0F, (int) (a * 0.7F) << 24 | 0xE2FBFF, 1.0F);
    }

    /**
     * The caster's film: an uplink overlay with the moon's telemetry - distance, ETA to impact and velocity readouts,
     * warning text, and the big alert re-shown (the cutscene starts at age 0) - modelled on {@code HalleyHud.film}.
     */
    public static void film(GuiGraphicsExtractor g, MoonFx fx, float partial, int w, int h, HalleyHud.@Nullable Projector project) {
        float ct = fx.filmTime(partial);
        if (Float.isNaN(ct)) {
            return;
        }
        float fade = Mth.clamp(fx.filmWeight() * 1.6F - 0.3F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        if (ct >= MoonPlan.ALARM && ct <= MoonPlan.ALARM + MoonFx.ALARM_TICKS) {
            alert(g, fx, ct, w, h);
        }
        MoonPlan p = fx.plan;
        Font font = Minecraft.getInstance().font;
        int a = (int) (fade * 255.0F);
        boolean flicker = (int) (ct * 3.0F) % 11 == 0;
        int ink = a << 24 | GOLD;
        int dim = (int) (a * 0.55F) << 24 | 0xFFE2C8;
        int m = 12;
        int arm = 22;
        corner(g, m, m, arm, 1, 1, ink);
        corner(g, w - m, m, arm, -1, 1, ink);
        corner(g, m, h - m, arm, 1, -1, ink);
        corner(g, w - m, h - m, arm, -1, -1, ink);
        text(g, font, "UPLINK // SS-06 > LUNA", m + 6, m + 6, flicker ? dim : ink, 1.0F);
        text(g, font, String.format(Locale.ROOT, "T+%05.2f", ct / 20.0F), m + 6, m + 17, dim, 1.0F);
        String clock = ct < MoonPlan.CONTACT ? String.format(Locale.ROOT, "IMPACT T-%05.2f", Math.max(0.0F, (MoonPlan.CONTACT - ct) / 20.0F))
            : "IMPACT CONFIRMED";
        right(g, font, clock, w - m - 6, m + 6, ink, 1.0F);
        right(g, font, String.format(Locale.ROOT, "LOCK %d / %d / %d", Mth.floor(p.target.x), Mth.floor(p.target.y), Mth.floor(p.target.z)),
            w - m - 6, h - m - 14, dim, 1.0F);

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

        if (ct >= MoonPlan.BREAK && ct < MoonPlan.CONTACT && project != null) {
            Vec3 moon = p.centre(ct);
            float[] at = project.project(moon);
            if (at != null) {
                double d = moon.distanceTo(MoonFx.camera());
                int size = (int) Mth.clamp(20.0 - d / 300.0, 8.0, 20.0);
                box(g, (int) at[0], (int) at[1], size, ink);
                text(g, font, "SS-06 · LUNA", at[0] + size + 4, at[1] - 9, ink, 1.0F);
                text(g, font, String.format(Locale.ROOT, "RANGE %.2f KM · ALT %,.0f M", d / 1000.0, moon.y - p.target.y),
                    at[0] + size + 4, at[1] + 1, dim, 1.0F);
            }
            double eta = Math.max(0.0, MoonPlan.CONTACT - ct) / 20.0;
            text(g, font, String.format(Locale.ROOT, "V %,.0f M/S", p.speed(ct) * 20.0), 18, h - 40, ink, 1.5F);
            text(g, font, String.format(Locale.ROOT, "ETA %.1f S · CLOSING", eta), 18, h - 24, dim, 1.0F);
            if (ct >= MoonPlan.ENTRY - 30.0F) {
                boolean blink = (int) (ct / 2.0F) % 2 == 0;
                bracketed(g, font, "ENTRY · PLASMA SHEATH", w / 2, 30, blink ? ink : dim, 1.5F);
            }
        }

        if (ct >= MoonPlan.CONTACT + 4.0F && ct < MoonPlan.CONTACT + 60.0F) {
            int b = (int) (a * Mth.clamp((MoonPlan.CONTACT + 60.0F - ct) / 12.0F, 0.0F, 1.0F));
            bracketed(g, font, "IMPACT CONFIRMED", w / 2, h / 2 + 50, b << 24 | GOLD, 2.0F);
        }
        if (ct >= MoonPlan.CONTACT + 100.0F) {
            int b = (int) (a * Mth.clamp((ct - MoonPlan.CONTACT - 100.0F) / 10.0F, 0.0F, 1.0F));
            centered(g, font, String.format(Locale.ROOT, "CRATER Ø%d · DEPTH %d · MOON Ø%d",
                p.params.craterRadius() * 2, p.params.craterDepth(), p.params.moonRadius() * 2), w / 2, h - m - 30, b << 24 | 0xFFE2C8, 1.25F);
        }
    }

    private static float pseudo(long seed) {
        long h = seed * 0x2545F4914F6CDD1DL;
        h ^= h >>> 33;
        return (float) ((h >>> 40 & 0xFFFF) / 65536.0);
    }

    private static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, int color, float scale) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        pose.popMatrix();
    }

    // ---- Drawing helpers (the same look as HalleyHud's own; duplicated here to keep this package independent). ----

    private static void corner(GuiGraphicsExtractor g, int x, int y, int arm, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm), y + dy, color);
        g.fill(x, Math.min(y, y + dy * arm), x + dx, Math.max(y, y + dy * arm), color);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int arm = Math.max(3, size / 3);
        corner(g, x - size, y - size, arm, 1, 1, color);
        corner(g, x + size, y - size, arm, -1, 1, color);
        corner(g, x - size, y + size, arm, 1, -1, color);
        corner(g, x + size, y + size, arm, -1, -1, color);
    }

    private static void right(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale) {
        text(g, font, s, x - font.width(s) * scale, y, color, scale);
    }

    private static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - font.width(s) * scale / 2.0F, y, color, scale);
    }

    private static void bracketed(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, float scale) {
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
