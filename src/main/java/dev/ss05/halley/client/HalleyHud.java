package dev.ss05.halley.client;

import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.client.render.CometRenderer;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

/**
 * SS-05's on-screen text, in the remote's HUD style: the warning anyone near the strike sees, and the uplink overlay
 * on the caster's film. Coordinates are The Shooting Star's overlay units (the caller sets up its scaling).
 */
public final class HalleyHud {
    /** World position to overlay position, or null when it is behind the camera. */
    public interface Projector {
        @Nullable
        float[] project(Vec3 world);
    }

    private static final int ICE = 0x6FE8FF;
    private static final int PALE = 0xE2FBFF;
    private static final int WARN = 0xFF5A6A;

    private HalleyHud() {
    }

    /** For anyone near the strike, outside the film: what is coming, and how long until it lands. */
    public static void world(GuiGraphics g, HalleyFx fx, float partial, int w) {
        Minecraft minecraft = Minecraft.getInstance();
        HalleyPlan p = fx.plan;
        float t = fx.time(partial);
        if (minecraft.player == null || t < HalleyPlan.MARK || t >= p.impact) {
            return;
        }
        Vec3 me = minecraft.player.position();
        double reach = p.params.craterRadius() * p.params.blastReach();
        double fromMark = Math.hypot(me.x - p.target.x, me.z - p.target.z);
        if (fromMark > reach + p.params.trenchLength() + 120.0) {
            return;
        }
        boolean corridor = p.inScar(me.x, me.z, 6.0);
        boolean blast = fromMark < reach;
        Font font = minecraft.font;
        float rate = 1.0F + 3.0F * Mth.clamp((t - HalleyPlan.COUNTDOWN) / (p.impact - HalleyPlan.COUNTDOWN), 0.0F, 1.0F);
        float blink = (t / 20.0F * rate) % 1.0F;
        int a = (int) (255.0F * (0.55F + 0.45F * (1.0F - blink)));
        String title = corridor ? "INSIDE THE IMPACT CORRIDOR" : blast ? "INSIDE THE BLAST RADIUS" : "COMET INBOUND";
        int colour = corridor || blast ? WARN : ICE;
        String clock = String.format(Locale.ROOT, "T-%05.2f", Math.max(0.0F, (p.impact - t) / 20.0F));
        bracketed(g, font, title, w / 2, 26, a << 24 | colour, 1.5F);
        centered(g, font, clock, w / 2, 44, 0xFFFFFFFF, 1.25F);
    }

    /**
     * Outside the film, while the comet is coming in: an arrow at the edge of the screen pointing at it whenever it is
     * out of view, so nobody misses it.
     */
    public static void marker(GuiGraphics g, HalleyFx fx, float partial, int w, int h) {
        HalleyPlan p = fx.plan;
        float t = fx.time(partial);
        Minecraft minecraft = Minecraft.getInstance();
        if (t < HalleyPlan.SIGHT + 10.0F || t >= HalleyPlan.TOUCHDOWN || fx.filmWeight() > 0.05F || minecraft.player == null
            || !HalleyClientConfig.cometMarker()) {
            return;
        }
        Vec3 me = minecraft.player.position();
        if (Math.hypot(me.x - p.target.x, me.z - p.target.z) > 1500.0) {
            return;
        }
        Vec3 comet = p.comet(t);
        float[] at = CometRenderer.toScreen(comet);
        if (at == null || at[2] < 0.5F && Math.abs(at[0]) < 0.94F && Math.abs(at[1]) < 0.9F) {
            return;
        }
        // the way to turn, on screen (y up), and where that meets a frame just inside the screen's edge
        double angle = Math.atan2(at[1] * h, at[0] * w);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double rx = w * 0.5 - 30.0;
        double ry = h * 0.5 - 30.0;
        double reach = Math.min(rx / Math.max(Math.abs(c), 1.0E-4), ry / Math.max(Math.abs(s), 1.0E-4));
        float x = (float) (w * 0.5 + c * reach);
        float y = (float) (h * 0.5 - s * reach);
        int a = (int) (255.0F * (0.6F + 0.4F * (float) Math.abs(Math.sin(t * 0.25))));
        int ink = a << 24 | ICE;

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) -angle);
        // an arrowhead pointing outward, with a short tail
        for (int i = 0; i < 14; i++) {
            g.fill(i - 14, -(14 - i), i - 13, 14 - i, ink);
        }
        g.fill(-24, -2, -14, 2, ink);
        pose.popMatrix();

        Font font = minecraft.font;
        String range = String.format(Locale.ROOT, at[2] > 0.5F ? "%.1f KM · BEHIND YOU" : "%.1f KM",
            comet.distanceTo(HalleyFx.camera()) / 1000.0);
        float lx = (float) Mth.clamp(x - c * 48.0, 60.0, w - 60.0);
        float ly = (float) Mth.clamp(y + s * 40.0, 20.0, h - 30.0);
        centered(g, font, "1P/HALLEY", (int) lx, (int) ly - 12, ink, 1.25F);
        centered(g, font, range, (int) lx, (int) ly + 1, (int) (a * 0.7F) << 24 | PALE, 1.0F);
    }

    /** The caster's film: an uplink overlay with the comet's telemetry. */
    public static void film(GuiGraphics g, HalleyFx fx, float partial, int w, int h, Projector project) {
        float ct = fx.filmTime(partial);
        if (Float.isNaN(ct)) {
            return;
        }
        float fade = Mth.clamp(fx.filmWeight() * 1.6F - 0.3F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        HalleyPlan p = fx.plan;
        Font font = Minecraft.getInstance().font;
        int a = (int) (fade * 255.0F);
        boolean flicker = (int) (ct * 3.0F) % 11 == 0;
        int ink = a << 24 | ICE;
        int dim = (int) (a * 0.55F) << 24 | PALE;
        int m = 12;
        int arm = 22;
        corner(g, m, m, arm, 1, 1, ink);
        corner(g, w - m, m, arm, -1, 1, ink);
        corner(g, m, h - m, arm, 1, -1, ink);
        corner(g, w - m, h - m, arm, -1, -1, ink);
        text(g, font, "UPLINK // SS-05 > HALLEY", m + 6, m + 6, flicker ? dim : ink, 1.0F);
        text(g, font, String.format(Locale.ROOT, "T+%05.2f", ct / 20.0F), m + 6, m + 17, dim, 1.0F);
        String clock = ct < p.impact ? String.format(Locale.ROOT, "IMPACT T-%05.2f", Math.max(0.0F, (p.impact - ct) / 20.0F)) : "IMPACT CONFIRMED";
        right(g, font, clock, w - m - 6, m + 6, ink, 1.0F);
        right(g, font, String.format(Locale.ROOT, "LOCK %d / %d / %d", Mth.floor(p.target.x), Mth.floor(p.target.y), Mth.floor(p.target.z)),
            w - m - 6, h - m - 14, dim, 1.0F);

        if (ct >= HalleyPlan.MARK && ct < 64.0F) {
            float[] at = project.project(p.target.add(0.0, 0.5, 0.0));
            if (at != null) {
                float k = Mth.clamp((ct - HalleyPlan.MARK) / 8.0F, 0.0F, 1.0F);
                int size = (int) Mth.lerp(k, 60.0F, 9.0F);
                box(g, (int) at[0], (int) at[1], size, ink);
                text(g, font, "TARGET ACQUIRED", (int) at[0] + size + 4, (int) at[1] - 9, ink, 1.0F);
                text(g, font, String.format(Locale.ROOT, "CRATER Ø%d · CORRIDOR %d", p.params.craterRadius() * 2, p.params.trenchLength()),
                    (int) at[0] + size + 4, (int) at[1] + 1, dim, 1.0F);
            }
        }

        if (ct >= HalleyPlan.SIGHT + 20.0F && ct < HalleyPlan.TOUCHDOWN) {
            Vec3 comet = p.comet(ct);
            float[] at = project.project(comet);
            if (at != null) {
                double d = comet.distanceTo(HalleyFx.camera());
                int size = (int) Mth.clamp(18.0 - d / 400.0, 8.0, 18.0);
                box(g, (int) at[0], (int) at[1], size, ink);
                text(g, font, "1P/HALLEY", (int) at[0] + size + 4, (int) at[1] - 9, ink, 1.0F);
                text(g, font, String.format(Locale.ROOT, "RANGE %.2f KM · ALT %,.0f M", comet.distanceTo(p.target) / 1000.0, comet.y - p.target.y),
                    (int) at[0] + size + 4, (int) at[1] + 1, dim, 1.0F);
            }
            text(g, font, String.format(Locale.ROOT, "V %,.0f M/S", p.speed(ct) * 20.0), 18, h - 40, ink, 1.5F);
            text(g, font, String.format(Locale.ROOT, "DESCENT %.1f° · BEARING %s", HalleyPlan.DESCENT_DEGREES, p.sideSign > 0 ? "R" : "L"),
                18, h - 24, dim, 1.0F);
            if (ct >= HalleyPlan.ENTRY - 4.0F) {
                boolean blink = (int) (ct / 2.0F) % 2 == 0;
                bracketed(g, font, "ENTRY · PLASMA SHEATH", w / 2, 30, blink ? ink : dim, 1.5F);
            }
        }

        if (ct >= HalleyPlan.TOUCHDOWN && ct < p.impact) {
            String line = ct < HalleyPlan.TOUCHDOWN + 8.0F ? "TOUCHDOWN"
                : String.format(Locale.ROOT, "PLOUGHING · %d M TO MARK", Mth.ceil(p.frontAlong(ct)));
            bracketed(g, font, line, w / 2, 30, ink, 1.5F);
            float[] at = project.project(p.comet(ct));
            if (at != null) {
                box(g, (int) at[0], (int) at[1], 12, ink);
            }
        }

        if (ct >= p.impact + 4.0F && ct < p.impact + 60.0F) {
            int b = (int) (a * Mth.clamp((p.impact + 60.0F - ct) / 12.0F, 0.0F, 1.0F));
            bracketed(g, font, "IMPACT CONFIRMED", w / 2, h / 2 + 50, b << 24 | ICE, 2.0F);
        }
        if (ct >= p.impact + 100.0F) {
            int b = (int) (a * Mth.clamp((ct - p.impact - 100.0F) / 10.0F, 0.0F, 1.0F));
            centered(g, font, String.format(Locale.ROOT, "CRATER Ø%d · DEPTH %d · TRENCH %d M",
                p.params.craterRadius() * 2, p.params.craterDepth(), p.params.trenchLength()), w / 2, h - m - 30, b << 24 | PALE, 1.25F);
        }
    }

    // ---- Drawing helpers (the same look as the remote's own HUD). ---------------------------------------------------

    static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm), y + dy, color);
        g.fill(x, Math.min(y, y + dy * arm), x + dx, Math.max(y, y + dy * arm), color);
    }

    static void box(GuiGraphics g, int x, int y, int size, int color) {
        int arm = Math.max(3, size / 3);
        corner(g, x - size, y - size, arm, 1, 1, color);
        corner(g, x + size, y - size, arm, -1, 1, color);
        corner(g, x - size, y + size, arm, 1, -1, color);
        corner(g, x + size, y + size, arm, -1, -1, color);
    }

    static void text(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale, scale);
        g.drawString(font, s, 0, 0, color, false);
        pose.popMatrix();
    }

    static void right(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        text(g, font, s, x - (int) (font.width(s) * scale), y, color, scale);
    }

    static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - (int) (font.width(s) * scale / 2.0F), y, color, scale);
    }

    static void bracketed(GuiGraphics g, Font font, String s, int cx, int y, int color, float scale) {
        centered(g, font, "[ " + s + " ]", cx, y, color, scale);
    }
}
