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
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Stellar Convergence's film panels: each weapon's shot, frozen as it is cut away from (a copy of the frame on the
 * screen, so no second camera has to draw), slides into its place in a grid of comic panels until the screen is full,
 * then the grid shatters as everything hits. {@link #clear} lets the copies go.
 *
 * <p>26.3: the GUI is drawn by Minecraft's own GUI renderer from what is handed to a {@link GuiGraphicsExtractor}, so
 * the panels are drawn from the film's overlay pass ({@code StarClientBridge.renderFilmHud}) rather than at the very
 * end of the frame as on the other versions; and a freeze copies the world as drawn ({@link #copyPending}, at the end
 * of the world's main pass), whole-size, into a texture of its own that the GUI draws like any other (the way RimuLib's
 * own screen mirror does it).
 */
public final class FilmPanels {
    /** Ticks a frozen panel takes to slide from full screen into its place. */
    private static final float SLIDE = 7.0F;
    /** Ticks the grid takes to shatter. */
    private static final float SHATTER = 14.0F;
    private static final int BORDER = 0xFFFFFFFF;
    /** GpuTexture usage: copied into and sampled (COPY_DST | TEXTURE_BINDING, as RimuLib's screen mirror makes its own). */
    private static final int USAGE = 5;

    /** One panel's frozen frame, registered under its own name so the GUI can draw it. */
    private static final class Frame extends AbstractTexture {
        final Identifier location;
        int width;
        int height;
        boolean registered;

        Frame(int index) {
            this.location = HalleyAddon.id("film_panel_" + index);
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

        /** Lets its texture go (made again at the next freeze). */
        void release() {
            this.releaseTextures();
        }

        /** The texture manager closes it with the rest when resources reload; it is made again at the next freeze. */
        @Override
        public void close() {
            this.releaseTextures();
            this.registered = false;
        }
    }

    private record Panel(Frame frame, int color, float frozenAt) {
    }

    /** One frame per panel slot, kept and reused from film to film. */
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final List<Panel> PANELS = new ArrayList<>();
    /** Freezes asked for, made at the end of the next world pass. */
    private static final List<Panel> PENDING = new ArrayList<>();
    private static int slots;
    /** Set by the film that has panels while it plays: run in its overlay pass, over everything else. */
    public static volatile Consumer<GuiGraphicsExtractor> frame;

    private FilmPanels() {
    }

    /** From the film's overlay pass (StarClientBridge): the playing film freezes and draws its panels. */
    public static void overlay(GuiGraphicsExtractor g) {
        Consumer<GuiGraphicsExtractor> hook = frame;
        if (hook != null && Minecraft.getInstance().level != null) {
            hook.accept(g);
        }
    }

    /** A new grid with room for {@code count} panels. */
    public static void begin(int count) {
        clear();
        slots = Math.max(1, count);
    }

    public static int frozen() {
        return PANELS.size() + PENDING.size();
    }

    /**
     * Freezes the frame now on the screen into the next panel ({@code color}: its border's accent; {@code now}: the
     * film's time). The copy itself is made at the end of the world's next main pass ({@link #copyPending}).
     */
    public static void freeze(GuiGraphicsExtractor g, int color, float now) {
        int i = frozen();
        if (i >= slots) {
            return;
        }
        while (FRAMES.size() <= i) {
            FRAMES.add(new Frame(FRAMES.size()));
        }
        PENDING.add(new Panel(FRAMES.get(i), color, now));
    }

    /** From the end of the world's main pass (HalleyClient): the freezes asked for, copied from the frame as drawn. */
    public static void copyPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture color = main.getColorTexture();
        if (color == null || color.isClosed()) {
            return;
        }
        for (Panel panel : PENDING) {
            panel.frame().copy(color);
            PANELS.add(panel);
        }
        PENDING.clear();
    }

    /**
     * Draws the panels at film time {@code now} over a screen {@code w} x {@code h} (GUI units); {@code shatterAt}:
     * when the grid breaks apart (NaN: not yet).
     */
    public static void draw(GuiGraphicsExtractor g, int w, int h, float now, float shatterAt) {
        if (PANELS.isEmpty()) {
            return;
        }
        int cols = Mth.ceil(Math.sqrt(slots));
        int rows = Mth.ceil(slots / (float) cols);
        float gap = h * 0.012F;
        float cellW = (w - gap * (cols + 1)) / cols;
        float cellH = (h - gap * (rows + 1)) / rows;
        float broken = Float.isNaN(shatterAt) ? 0.0F : Mth.clamp((now - shatterAt) / SHATTER, 0.0F, 1.0F);
        if (broken >= 1.0F) {
            return;
        }
        for (int i = 0; i < PANELS.size(); i++) {
            Panel panel = PANELS.get(i);
            if (!panel.frame().ready()) {
                continue;
            }
            int col = i % cols;
            int row = i / cols;
            // each panel's cell, a little askew like a comic page's
            float cx = gap + col * (cellW + gap) + cellW * 0.5F;
            float cy = gap + row * (cellH + gap) + cellH * 0.5F;
            float tilt = ((i * 37) % 7 - 3) * 0.6F;
            float slide = smooth((now - panel.frozenAt()) / SLIDE);
            float x = Mth.lerp(slide, w * 0.5F, cx);
            float y = Mth.lerp(slide, h * 0.5F, cy);
            float pw = Mth.lerp(slide, w, cellW);
            float ph = Mth.lerp(slide, h, cellH);
            float angle = Mth.lerp(slide, 0.0F, tilt);
            // shattering: flung out from the middle, spinning, fading
            if (broken > 0.0F) {
                float dx = cx - w * 0.5F;
                float dy = cy - h * 0.5F;
                float len = Math.max(1.0F, (float) Math.hypot(dx, dy));
                float fling = broken * broken * w * 0.9F;
                x += dx / len * fling;
                y += dy / len * fling + broken * broken * h * 0.3F;
                angle += broken * (i % 2 == 0 ? 40.0F : -40.0F);
            }
            float alpha = 1.0F - broken;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate((float) Math.toRadians(angle));
            float border = Math.max(2.0F, h * 0.006F);
            fill(g, -pw * 0.5F - border, -ph * 0.5F - border, pw * 0.5F + border, ph * 0.5F + border, fade(BORDER, alpha));
            fill(g, -pw * 0.5F - border, ph * 0.5F, pw * 0.5F + border, ph * 0.5F + border * 2.5F, fade(0xFF000000 | panel.color(), alpha));
            image(g, panel.frame(), -pw * 0.5F, -ph * 0.5F, pw * 0.5F, ph * 0.5F, fade(0xFFFFFFFF, alpha));
            pose.popMatrix();
        }
    }

    /** Lets go of the frozen frames (their textures are made again at the next freeze). */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (Frame f : FRAMES) {
            f.release();
        }
        PANELS.clear();
        PENDING.clear();
        slots = 0;
    }

    /** A frozen frame stretched over the rectangle (a 64 x 64 blit of the whole texture, scaled to it). */
    private static void image(GuiGraphicsExtractor g, Frame frame, float x0, float y0, float x1, float y1, int argb) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        // a render target's rows run bottom up (as on the other versions): drawn flipped
        pose.translate(x0, y1);
        pose.scale((x1 - x0) / 64.0F, -(y1 - y0) / 64.0F);
        g.blit(RenderPipelines.GUI_TEXTURED, frame.location, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64, argb);
        pose.popMatrix();
    }

    private static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int argb) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        g.fill(0, 0, 64, 64, argb);
        pose.popMatrix();
    }

    private static int fade(int argb, float alpha) {
        return Mth.clamp((int) ((argb >>> 24) * alpha), 0, 255) << 24 | argb & 0xFFFFFF;
    }

    private static float smooth(float x) {
        float k = Mth.clamp(x, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }
}
