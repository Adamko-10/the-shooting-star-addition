package dev.ss05.halley.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.ss05.halley.HalleyAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Stellar Convergence's film panels: each weapon's shot, frozen as it is cut away from (a copy of the frame on the
 * screen, so no second camera has to draw), slides into its place in a grid of comic panels until the screen is full,
 * then the grid shatters as everything hits. {@link #clear} frees the copies.
 *
 * <p>Minecraft 1.21.11 (Fabric 1.21.11 branch): the copies are whole-size textures copied from the main target on the
 * GPU (1.21.11 copies without scaling; main keeps half-size ones) and registered under a name, so the deferred GUI can
 * draw them; the frame's GUI is drawn last, so they are drawn into it from {@code client/mixin/ToastManagerMixin}.
 * The GUI is gathered before it is drawn, so a freeze is copied at the very end of that frame ({@link #copyPending},
 * from GameRendererMixin), with the films' scenes and overlays the GUI draws; copying while it was gathered (until
 * 2026-11-11) missed them, as on 26.3.
 */
public final class FilmPanels {
    /** Ticks a frozen panel takes to slide from full screen into its place. */
    private static final float SLIDE = 7.0F;
    /** Ticks the grid takes to shatter. */
    private static final float SHATTER = 14.0F;
    private static final int BORDER = 0xFFFFFFFF;

    private record Panel(Frame frame, int color, float frozenAt) {
    }

    /** A freeze asked for, copied at the end of the frame. */
    private record Freeze(int color, float frozenAt) {
    }

    private static final List<Panel> PANELS = new ArrayList<>();
    private static final List<Freeze> PENDING = new ArrayList<>();
    private static int slots;
    /** Set by the film that has panels while it plays: run once a frame, over everything else. */
    public static volatile Consumer<GuiGraphics> frame;

    private FilmPanels() {
    }

    /**
     * From the frame's GUI, after everything else in it (client/mixin): the playing film freezes and draws its panels
     * over the picture, whatever else draws overlays (The Shooting Star's passes only run for its own effects' films).
     */
    public static void endOfFrame(GuiGraphics g) {
        Consumer<GuiGraphics> hook = frame;
        if (hook == null || Minecraft.getInstance().level == null) {
            return;
        }
        g.nextStratum();
        hook.accept(g);
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
     * film's time). The copy itself is made at the end of this frame ({@link #copyPending}).
     */
    public static void freeze(GuiGraphics g, int color, float now) {
        if (frozen() < slots) {
            PENDING.add(new Freeze(color, now));
        }
    }

    /** From the very end of the frame (GameRendererMixin): the freezes asked for, copied from the frame as shown. */
    public static void copyPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null) {
            PENDING.clear();
            return;
        }
        for (Freeze freeze : PENDING) {
            Frame frame = new Frame(HalleyAddon.id("film_panel_" + PANELS.size()), main.width, main.height);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, frame.gpu(), 0, 0, 0, 0, 0,
                main.width, main.height);
            PANELS.add(new Panel(frame, freeze.color(), freeze.frozenAt()));
        }
        PENDING.clear();
    }

    /**
     * Draws the panels at film time {@code now} over a screen {@code w} x {@code h} (GUI units); {@code shatterAt}:
     * when the grid breaks apart (NaN: not yet).
     */
    public static void draw(GuiGraphics g, int w, int h, float now, float shatterAt) {
        // not on a frame that freezes a shot: its copy is taken after the GUI is drawn, and must not hold the grid itself
        if (PANELS.isEmpty() || !PENDING.isEmpty()) {
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
            pose.rotate(angle * Mth.DEG_TO_RAD);
            float border = Math.max(2.0F, h * 0.006F);
            fill(g, -pw * 0.5F - border, -ph * 0.5F - border, pw * 0.5F + border, ph * 0.5F + border, fade(BORDER, alpha));
            fill(g, -pw * 0.5F - border, ph * 0.5F, pw * 0.5F + border, ph * 0.5F + border * 2.5F, fade(0xFF000000 | panel.color(), alpha));
            image(g, panel.frame(), -pw * 0.5F, -ph * 0.5F, pw * 0.5F, ph * 0.5F, alpha);
            pose.popMatrix();
        }
    }

    /** Frees the frozen frames. */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (Panel panel : PANELS) {
            panel.frame().release();
        }
        PANELS.clear();
        PENDING.clear();
        slots = 0;
    }

    /** The whole frozen frame over (x0, y0)-(x1, y1), drawn through a 64-unit quad scaled to fit. */
    private static void image(GuiGraphics g, Frame frame, float x0, float y0, float x1, float y1, float alpha) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F);
        // a frame buffer's rows run bottom up: v from 1 at the top to 0 at the bottom (a texture "height" of -64)
        // drawn opaque: the screen's alpha copied with the frame would let the white panel behind show through
        g.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, frame.location, 0, 0, 0.0F, -64.0F, 64, 64, 64, -64, fade(0xFFFFFFFF, alpha));
        pose.popMatrix();
    }

    private static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
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

    /** One frozen frame: a texture the frame is copied into, registered under a name so the GUI can draw it. */
    private static final class Frame extends AbstractTexture {
        final Identifier location;

        Frame(Identifier location, int width, int height) {
            this.location = location;
            GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(location::toString, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            // replaces (and closes) the frame frozen into this slot before
            Minecraft.getInstance().getTextureManager().register(location, this);
        }

        GpuTexture gpu() {
            return this.texture;
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(this.location);
        }

        @Override
        public void close() {
            if (this.textureView != null) {
                this.textureView.close();
                this.textureView = null;
            }
            if (this.texture != null) {
                this.texture.close();
                this.texture = null;
            }
        }
    }
}
