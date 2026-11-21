package dev.ss05.halley.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.ss05.halley.HalleyAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Stellar Convergence's split-screen film (1.9.0; Adam's idea of 2026-11-05, his pick: a second film style, F on its
 * card): for the last seconds before the hit the screen is split into one part per weapon, two rows, each part showing
 * that weapon's own film camera live, shaking slightly, with a border half in the weapon's colour running round it,
 * its code and name in the corner and a countdown; a banner says when everything lands. The camera visits the weapons
 * in turn, one a frame (two with a shader pack, whose smoothing over frames needs one to settle), and each part keeps
 * the last picture of its weapon ({@link #capture}); the parts cover the whole screen, so the switching never shows.
 * At the hit the parts are flung apart onto the wide shot, as the comic panels are. The mock-up it follows is on the
 * 1.9.0 settings canvas ("Idea: Convergence's split-screen look").
 *
 * <p>Minecraft 1.21.11 (Fabric 1.21.11 branch): the parts' pictures are whole-size textures copied from the main target
 * on the GPU, as {@link FilmPanels}' frames (1.21.11 copies without scaling), and drawn into the deferred GUI by name.
 * The picture is kept just before the HUD is gathered (ConvergenceFilm.beforeHud, from HudRescueMixin), when the main
 * target holds the finished world and nothing of the GUI yet (the GUI, this split with it, is drawn into it later in the
 * frame), as main keeps it before its HUD. Drawn opaque, as this branch's panels: the screen's own alpha would show
 * through, so a picture doesn't fade in or out with its part (its frame and the dark behind it do).
 */
public final class FilmSplit {
    /** Ticks the split takes to come in, and to fly apart at the hit. */
    private static final float IN = 6.0F;
    private static final float SHATTER = 14.0F;
    /** Ticks a border's coloured half takes to run once round its side. */
    private static final float RUN = 28.0F;

    private record Pane(int color, String code, String name) {
    }

    private static final List<Pane> PANES = new ArrayList<>();
    private static final List<FilmPanels.Frame> FRAMES = new ArrayList<>();
    /** The part shown live now (the camera stays on it), or -1. */
    private static int live = -1;

    private FilmSplit() {
    }

    /** A new split for these weapons: their colours, and each one's title ("SS-01 · Railgun"; may be empty). */
    public static void begin(int[] colors, List<String> titles) {
        clear();
        for (int i = 0; i < colors.length; i++) {
            String title = i < titles.size() ? titles.get(i) : "";
            int dot = title.indexOf(" · ");
            PANES.add(new Pane(colors[i], dot > 0 ? title.substring(0, dot) : "",
                (dot > 0 ? title.substring(dot + 3) : title).toUpperCase(Locale.ROOT)));
            FRAMES.add(null);
        }
    }

    public static int count() {
        return PANES.size();
    }

    /** Which part is live now (it gets a full border and a LIVE tag; the others are dimmed a little). */
    public static void live(int i) {
        live = i;
    }

    public static int liveIndex() {
        return live;
    }

    /** Keeps the frame now on the screen as part {@code i}'s picture (at the end of the frame, before the split is drawn). */
    public static void capture(int i) {
        if (i < 0 || i >= FRAMES.size()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null) {
            return;
        }
        // whole size (a GPU copy doesn't scale); the screen's alpha is ignored when it is drawn (opaque, see the class)
        FilmPanels.Frame frame = FRAMES.get(i);
        if (frame == null || frame.width != main.width || frame.height != main.height) {
            if (frame != null) {
                frame.release();
            }
            frame = new FilmPanels.Frame(HalleyAddon.id("film_split_" + i), main.width, main.height);
            FRAMES.set(i, frame);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, frame.gpu(), 0, 0, 0, 0, 0, main.width, main.height);
    }

    /**
     * Draws the split at film time {@code now} over a screen {@code w} x {@code h} (overlay units): in from {@code from},
     * the weapons landing at {@code hit}, flying apart from {@code end}.
     */
    public static void draw(GuiGraphics g, int w, int h, float now, float from, float hit, float end) {
        int n = PANES.size();
        if (n == 0 || now < from) {
            return;
        }
        float in = smooth((now - from) / IN);
        float broken = Mth.clamp((now - end) / SHATTER, 0.0F, 1.0F);
        if (broken >= 1.0F) {
            return;
        }
        float u = h / 720.0F;
        // the dark behind the parts: it hides the camera visiting the weapons in turn
        fill(g, 0, 0, w, h, argb(0x05060C, in * (1.0F - broken)));
        int rows = n <= 3 ? 1 : 2;
        int top = (n + 1) / rows;
        float margin = 18.0F * u;
        float gap = 12.0F * u;
        // room under the parts for the banner (over a part it hid that part's name)
        float rowH = (h - margin * 2.0F - 40.0F * u - gap * (rows - 1)) / rows;
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < n; i++) {
            int row = rows == 1 || i < top ? 0 : 1;
            int inRow = row == 0 ? (rows == 1 ? n : top) : n - top;
            int col = row == 0 ? i : i - top;
            float cellW = (w - margin * 2.0F - gap * (inRow - 1)) / inRow;
            float x0 = margin + col * (cellW + gap);
            float y0 = margin + row * (rowH + gap);
            float cx = x0 + cellW * 0.5F;
            float cy = y0 + rowH * 0.5F;
            // (no shaking: Adam, 2026-11-20: "get rid of the shake on the individual screens")
            float dx = 0.0F;
            float dy = 0.0F;
            float alpha = in * (1.0F - broken);
            float angle = 0.0F;
            if (broken > 0.0F) {
                float ox = cx - w * 0.5F;
                float oy = cy - h * 0.5F;
                float len = Math.max(1.0F, (float) Math.hypot(ox, oy));
                float fling = broken * broken * w * 0.9F;
                dx += ox / len * fling;
                dy += oy / len * fling + broken * broken * h * 0.3F;
                angle = broken * (i % 2 == 0 ? 30.0F : -30.0F);
            }
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx + dx, cy + dy);
            pose.rotate(angle * Mth.DEG_TO_RAD);
            float hw = cellW * 0.5F;
            float hh = rowH * 0.5F;
            Pane pane = PANES.get(i);
            FilmPanels.Frame frame = FRAMES.get(i);
            boolean isLive = i == live;
            fill(g, -hw, -hh, hw, hh, argb(0x0B0D18, alpha));
            if (frame != null) {
                // a held picture drifts in slowly, so it keeps moving between its refreshes
                float drift = isLive ? 1.0F : 1.0F + 0.04F * (float) Math.sin(now * 0.05F + i * 1.7F) * 0.5F + 0.02F;
                image(g, frame, -hw, -hh, hw, hh, cellW / rowH, drift, alpha);
            }
            if (!isLive && live >= 0) {
                fill(g, -hw, -hh, hw, hh, argb(0x000000, 0.22F * alpha));
            }
            // the weapon's colour, faint, rising from the bottom (under its name)
            gradient(g, -hw, hh - rowH * 0.45F, hw, hh, argb(pane.color(), 0.0F), argb(pane.color(), 0.45F * alpha));
            border(g, -hw, -hh, hw, hh, 3.0F * u, pane.color(), now / RUN, alpha);
            if (isLive) {
                // the live part: a full frame in its colour and a tag
                float t2 = 1.5F * u;
                fill(g, -hw, -hh, hw, -hh + t2, argb(pane.color(), 0.85F * alpha));
                fill(g, -hw, hh - t2, hw, hh, argb(pane.color(), 0.85F * alpha));
                fill(g, -hw, -hh, -hw + t2, hh, argb(pane.color(), 0.85F * alpha));
                fill(g, hw - t2, -hh, hw, hh, argb(pane.color(), 0.85F * alpha));
                float blink = 0.6F + 0.4F * (float) Math.sin(now * 0.6F);
                fill(g, -hw + 14.0F * u, -hh + 14.0F * u, -hw + 20.0F * u, -hh + 20.0F * u, argb(0xFF3B3B, blink * alpha));
                text(g, font, "LIVE", -hw + 24.0F * u, -hh + 13.0F * u, 1.1F * u, argb(0xFFFFFF, 0.9F * alpha), true);
            }
            // its code and name, bottom left; the countdown, top right
            text(g, font, pane.code(), -hw + 14.0F * u, hh - 12.0F * u - 30.0F * u, 0.9F * u * 1.25F, argb(0xFFFFFF, 0.7F * alpha), false);
            text(g, font, pane.name(), -hw + 14.0F * u, hh - 12.0F * u - 20.0F * u, 2.0F * u, argb(0xF2F0FF, alpha), true);
            String count = now >= hit ? "IMPACT" : String.format(Locale.ROOT, "T-%04.1f", Math.max(0.0F, (hit - now) / 20.0F));
            float cs = 1.2F * u;
            text(g, font, count, hw - 14.0F * u - font.width(count) * cs, -hh + 12.0F * u, cs, argb(0xFFFFFF, 0.8F * alpha), false);
            pose.popMatrix();
        }
        // the banner, bottom middle
        float bannerAlpha = in * (1.0F - broken);
        String label = "SA-00";
        String title = "STELLAR CONVERGENCE";
        String when = now >= hit ? "ALL WEAPONS HIT" : String.format(Locale.ROOT, "ALL IMPACTS IN %.1f s", Math.max(0.0F, (hit - now) / 20.0F));
        float s1 = 1.0F * u;
        float s2 = 1.5F * u;
        float wide = (font.width(label) * s1 + font.width(title) * s2 + font.width(when) * s1) + 28.0F * u + 36.0F * u;
        float bx = (w - wide) * 0.5F;
        float by = h - margin - 30.0F * u;
        fill(g, bx - 1.0F * u, by - 1.0F * u, bx + wide + 1.0F * u, by + 30.0F * u + 1.0F * u, argb(0xE6DCFF, 0.4F * bannerAlpha));
        fill(g, bx, by, bx + wide, by + 30.0F * u, argb(0x05060C, 0.82F * bannerAlpha));
        float tx = bx + 18.0F * u;
        text(g, font, label, tx, by + 11.0F * u, s1, argb(0xB9AEE0, bannerAlpha), false);
        tx += font.width(label) * s1 + 14.0F * u;
        text(g, font, title, tx, by + 8.0F * u, s2, argb(0xF2F0FF, bannerAlpha), true);
        tx += font.width(title) * s2 + 14.0F * u;
        text(g, font, when, tx, by + 11.0F * u, s1, argb(0xE6DCFF, bannerAlpha), false);
    }

    /** Frees the parts' pictures. */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (FilmPanels.Frame frame : FRAMES) {
            if (frame != null) {
                frame.release();
            }
        }
        FRAMES.clear();
        PANES.clear();
        live = -1;
    }

    // ---- Drawing helpers. ------------------------------------------------------------------------------------------

    /** A border whose coloured half runs round each side (top and right one way, bottom and left the other). */
    private static void border(GuiGraphics g, float x0, float y0, float x1, float y1, float t, int color, float phase, float alpha) {
        int line = argb(color, alpha);
        float p = phase - (float) Math.floor(phase);
        runner(g, x0, y0, x1 - x0, t, true, p, line);
        runner(g, x0, y1 - t, x1 - x0, t, true, 1.0F - p, line);
        runner(g, x0, y0, y1 - y0, t, false, p, line);
        runner(g, x1 - t, y0, y1 - y0, t, false, 1.0F - p, line);
    }

    /** Half a side, starting {@code p} of the way along it and wrapping round. */
    private static void runner(GuiGraphics g, float x, float y, float length, float t, boolean across, float p, int argb) {
        float start = p * length;
        float end = start + length * 0.5F;
        segment(g, x, y, start, Math.min(end, length), t, across, argb);
        if (end > length) {
            segment(g, x, y, 0.0F, end - length, t, across, argb);
        }
    }

    private static void segment(GuiGraphics g, float x, float y, float a, float b, float t, boolean across, int argb) {
        if (across) {
            fill(g, x + a, y, x + b, y + t, argb);
        } else {
            fill(g, x, y + a, x + t, y + b, argb);
        }
    }

    private static void text(GuiGraphics g, Font font, String s, float x, float y, float scale, int argb, boolean bold) {
        if (s.isEmpty() || (argb >>> 24) < 4) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        Component line = bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
        g.drawString(font, line, 0, 0, argb, false);
        pose.popMatrix();
    }

    /**
     * The picture, cropped from its middle to the part's shape ({@code aspect}: the part's width over its height),
     * {@code zoom} times closer.
     */
    private static void image(GuiGraphics g, FilmPanels.Frame frame, float x0, float y0, float x1, float y1, float aspect, float zoom,
                              float alpha) {
        float frameAspect = frame.width / (float) Math.max(1, frame.height);
        float uw = 1.0F;
        float vh = 1.0F;
        if (aspect < frameAspect) {
            uw = aspect / frameAspect;
        } else {
            vh = frameAspect / aspect;
        }
        uw /= Math.max(1.0F, zoom);
        vh /= Math.max(1.0F, zoom);
        float u0 = 0.5F - uw * 0.5F;
        float u1 = 0.5F + uw * 0.5F;
        float v0 = 0.5F - vh * 0.5F;
        float v1 = 0.5F + vh * 0.5F;
        // through a 64-unit quad scaled to fit (as FilmPanels.image); the uv in steps of 1/UV of the picture
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        // a frame buffer's rows run bottom up: v1 at the top, v0 at the bottom (a negative region height)
        int regionW = Math.max(1, Math.round((u1 - u0) * UV));
        int regionH = Math.max(1, Math.round((v1 - v0) * UV));
        g.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, frame.location, 0, 0, u0 * UV, v1 * UV, 64, 64, regionW, -regionH, UV, UV,
            argb(0xFFFFFF, 1.0F));
        pose.popMatrix();
    }

    /** The picture's uv, counted in this many steps (the GUI's blit takes whole-number regions). */
    private static final int UV = 8192;

    private static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        g.fill(0, 0, 64, 64, argb);
        pose.popMatrix();
    }

    private static void gradient(GuiGraphics g, float x0, float y0, float x1, float y1, int fromArgb, int toArgb) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        g.fillGradient(0, 0, 64, 64, fromArgb, toArgb);
        pose.popMatrix();
    }

    private static int argb(int rgb, float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }

    private static float hash(int n) {
        int x = n * 0x27D4EB2D;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        return (x & 0xFFFF) / 65535.0F;
    }

    private static float smooth(float x) {
        float k = Mth.clamp(x, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }
}
