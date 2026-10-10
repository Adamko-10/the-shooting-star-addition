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

    /** For the caster, outside the film: the glitching alert. For anyone, outside the film: the off-screen marker. */
    public static void hud(GuiGraphicsExtractor g, MoonFx fx, float partial, int w, int h) {
        if (fx.filmWeight() > 0.05F) {
            return;
        }
        if (fx.mine) {
            alert(g, fx, fx.time(partial), w, h);
        }
        marker(g, fx, partial, w, h);
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

    /** The caster's film: a brief uplink line, and the big alert re-shown (the cutscene starts at age 0). */
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
        Font font = Minecraft.getInstance().font;
        int a = (int) (fade * 180.0F);
        text(g, font, "UPLINK // SS-06 > LUNA", 18.0F, 18.0F, a << 24 | GOLD, 1.0F);
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

    /** Tiny stand-in for {@code HalleyFx.window}, duplicated here to keep this package independent. */
    private static final class Curve {
        static float window(float t, float in0, float in1, float out0, float out1) {
            return (float) (Mth.clamp((t - in0) / Math.max(in1 - in0, 0.001), 0.0, 1.0)
                * (1.0 - Mth.clamp((t - out0) / Math.max(out1 - out0, 0.001), 0.0, 1.0)));
        }
    }
}
