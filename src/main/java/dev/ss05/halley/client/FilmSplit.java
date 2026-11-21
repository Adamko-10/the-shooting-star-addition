package dev.ss05.halley.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.ss05.halley.HalleyAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Stellar Convergence's split-screen film (1.9.0; Adam's idea of 2026-11-05, his pick: a second film style, F on its
 * card): for the last seconds before the hit the screen is split into one part per weapon, two rows, each part showing
 * that weapon's own film camera live, with a border half in the weapon's colour running round it, its code and name in
 * the corner and a countdown; a banner says when everything lands. The camera visits the weapons in turn, one a frame
 * (two with a shader pack, whose smoothing over frames needs one to settle), and each part keeps the last picture of
 * its weapon ({@link #capture}); the parts cover the whole screen, so the switching never shows. At the hit the parts
 * are flung apart onto the wide shot, as the comic panels are.
 *
 * <p>26.3 (as this branch's {@link FilmPanels}): the GUI is gathered before the frame is drawn, so a capture asked for
 * while gathering is copied later in that frame ({@link #copyPending}, from GameRendererMixin), once the world and its
 * screen effects are drawn and before the GUI is (so the copy never holds the split itself), whole-size, into a GPU
 * texture of its own that the GUI draws like any other, opaque (the screen's alpha would let the dark behind show
 * through: a part's picture doesn't fade as it comes in or flies apart, its frame and border do).
 */
public final class FilmSplit {
    /** Ticks the split takes to come in, and to fly apart at the hit. */
    private static final float IN = 6.0F;
    private static final float SHATTER = 14.0F;
    /** Ticks a border's coloured half takes to run once round its side. */
    private static final float RUN = 28.0F;
    /** GpuTexture usage: copied into and sampled (COPY_DST | TEXTURE_BINDING, as FilmPanels' frames). */
    private static final int USAGE = 5;
    /** The grid the part pictures' texture coordinates are given in (the blit takes whole numbers). */
    private static final int UV = 4096;

    private record Pane(int color, String code, String name) {
    }

    /** One part's picture, registered under its own name so the GUI can draw it. */
    private static final class Frame extends AbstractTexture {
        final Identifier location;
        int width;
        int height;
        boolean registered;

        Frame(int index) {
            this.location = HalleyAddon.id("film_split_" + index);
        }

        boolean ready() {
            return this.texture != null && !this.texture.isClosed();
        }

        /** Copies the main target's colour into this frame (made, or made again, at its size). */
        void copy(GpuTexture from) {
            int w = from.getWidth(0);
            int h = from.getHeight(0);
            if (!this.ready() || this.width != w || this.height != h) {
                this.releaseTextures();
                GpuDevice device = RenderSystem.getDevice();
                this.texture = device.createTexture(() -> this.location.toString(), USAGE, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
                this.textureView = device.createTextureView(this.texture);
                this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                this.width = w;
                this.height = h;
                if (!this.registered) {
                    Minecraft.getInstance().getTextureManager().register(this.location, this);
                    this.registered = true;
                }
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from, this.texture, 0, 0, 0, 0, 0, w, h);
        }

        void release() {
            this.releaseTextures();
        }

        /** The texture manager closes it with the rest when resources reload; it is made again at the next capture. */
        @Override
        public void close() {
            this.releaseTextures();
            this.registered = false;
        }
    }

    private static final List<Pane> PANES = new ArrayList<>();
    /** One picture per part slot, kept and reused from film to film. */
    private static final List<Frame> FRAMES = new ArrayList<>();
    /** The parts whose picture is copied this frame ({@link #copyPending}). */
    private static final List<Integer> PENDING = new ArrayList<>();
    /** Whether each part holds a picture yet. */
    private static final List<Boolean> HELD = new ArrayList<>();
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
            while (FRAMES.size() <= i) {
                FRAMES.add(new Frame(FRAMES.size()));
            }
            HELD.add(false);
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

    /** Keeps this frame's picture as part {@code i}'s (copied once the world is drawn, {@link #copyPending}). */
    public static void capture(int i) {
        if (i < 0 || i >= PANES.size() || PENDING.contains(i)) {
            return;
        }
        PENDING.add(i);
    }

    /** From GameRendererMixin, once the world is drawn and before the GUI: the captures asked for, copied. */
    public static void copyPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture color = main.getColorTexture();
        if (color != null && !color.isClosed()) {
            for (int i : PENDING) {
                if (i < FRAMES.size() && i < HELD.size()) {
                    FRAMES.get(i).copy(color);
                    HELD.set(i, true);
                }
            }
        }
        PENDING.clear();
    }

    /**
     * Draws the split at film time {@code now} over a screen {@code w} x {@code h} (GUI units): in from {@code from},
     * the weapons landing at {@code hit}, flying apart from {@code end}.
     */
    public static void draw(GuiGraphicsExtractor g, int w, int h, float now, float from, float hit, float end) {
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
            pose.rotate((float) Math.toRadians(angle));
            float hw = cellW * 0.5F;
            float hh = rowH * 0.5F;
            Pane pane = PANES.get(i);
            boolean isLive = i == live;
            fill(g, -hw, -hh, hw, hh, argb(0x0B0D18, alpha));
            if (i < HELD.size() && HELD.get(i) && FRAMES.get(i).ready() && alpha > 0.02F) {
                // a held picture drifts in slowly, so it keeps moving between its refreshes
                float drift = isLive ? 1.0F : 1.0F + 0.04F * (float) Math.sin(now * 0.05F + i * 1.7F) * 0.5F + 0.02F;
                image(g, FRAMES.get(i), -hw, -hh, hw, hh, cellW / rowH, drift);
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

    /** Frees the parts' pictures (their textures are made again at the next capture). */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (Frame frame : FRAMES) {
            frame.release();
        }
        PANES.clear();
        PENDING.clear();
        HELD.clear();
        live = -1;
    }

    // ---- Drawing helpers. ------------------------------------------------------------------------------------------

    /** A border whose coloured half runs round each side (top and right one way, bottom and left the other). */
    private static void border(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float t, int color, float phase, float alpha) {
        int line = argb(color, alpha);
        float p = phase - (float) Math.floor(phase);
        runner(g, x0, y0, x1 - x0, t, true, p, line);
        runner(g, x0, y1 - t, x1 - x0, t, true, 1.0F - p, line);
        runner(g, x0, y0, y1 - y0, t, false, p, line);
        runner(g, x1 - t, y0, y1 - y0, t, false, 1.0F - p, line);
    }

    /** Half a side, starting {@code p} of the way along it and wrapping round. */
    private static void runner(GuiGraphicsExtractor g, float x, float y, float length, float t, boolean across, float p, int argb) {
        float start = p * length;
        float end = start + length * 0.5F;
        segment(g, x, y, start, Math.min(end, length), t, across, argb);
        if (end > length) {
            segment(g, x, y, 0.0F, end - length, t, across, argb);
        }
    }

    private static void segment(GuiGraphicsExtractor g, float x, float y, float a, float b, float t, boolean across, int argb) {
        if (across) {
            fill(g, x + a, y, x + b, y + t, argb);
        } else {
            fill(g, x, y + a, x + t, y + b, argb);
        }
    }

    private static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int argb, boolean bold) {
        if (s.isEmpty() || (argb >>> 24) < 4) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        Component line = bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
        g.text(font, line, 0, 0, argb, false);
        pose.popMatrix();
    }

    /**
     * The picture, cropped from its middle to the part's shape ({@code aspect}: the part's width over its height),
     * {@code zoom} times closer: a 64 x 64 blit scaled to the rectangle, its rows flipped by v (a render target's rows
     * run bottom up; a negative scale would turn the quad round and the GUI pipelines cull it, as FilmPanels found).
     */
    private static void image(GuiGraphicsExtractor g, Frame frame, float x0, float y0, float x1, float y1, float aspect, float zoom) {
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
        float v1 = 0.5F + vh * 0.5F;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        g.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, frame.location, 0, 0, u0 * UV, v1 * UV, 64, 64,
            Math.round(uw * UV), -Math.round(vh * UV), UV, UV, 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int argb) {
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

    private static void gradient(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int fromArgb, int toArgb) {
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

    private static float smooth(float x) {
        float k = Mth.clamp(x, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }
}
