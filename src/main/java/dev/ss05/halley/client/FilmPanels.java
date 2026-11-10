package dev.ss05.halley.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Stellar Convergence's film panels: each weapon's shot, frozen as it is cut away from (a copy of the frame on the
 * screen, so no second camera has to draw), slides into its place in a grid of comic panels until the screen is full,
 * then the grid shatters as everything hits. The copies are half the screen's size; {@link #clear} frees them.
 */
public final class FilmPanels {
    /** Ticks a frozen panel takes to slide from full screen into its place. */
    private static final float SLIDE = 7.0F;
    /** Ticks the grid takes to shatter. */
    private static final float SHATTER = 14.0F;
    private static final int BORDER = 0xFFFFFFFF;

    private record Panel(TextureTarget frame, int color, float frozenAt) {
    }

    private static final List<Panel> PANELS = new ArrayList<>();
    private static int slots;
    /** Set by the film that has panels while it plays: run at the end of every frame, over everything else. */
    public static volatile Consumer<GuiGraphics> frame;

    private FilmPanels() {
    }

    /**
     * From the end of every frame (HalleyClient): the playing film freezes and draws its panels over the finished
     * picture, whatever else draws overlays (The Shooting Star's passes only run for its own effects' films).
     *
     * <p>Open (2026-11-05): while any of The Shooting Star's own weapons' effects run, nothing drawn here shows (the
     * panels are frozen and drawn, the frame doesn't change); with only Halley and Luna it works. The resets below
     * (GL state that other passes set behind GlStateManager's cache, the cached shader program and vertex arrays) didn't
     * fix it; they are kept because they cost nothing. See CLAUDE.md §18.
     */
    public static void endOfFrame() {
        Consumer<GuiGraphics> hook = frame;
        Minecraft minecraft = Minecraft.getInstance();
        if (hook == null || minecraft.level == null) {
            return;
        }
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableScissor();
        RenderSystem.enableCull();
        RenderSystem.disableCull();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
        RenderSystem.clearDepth(1.0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.clear();
        }
        BufferUploader.invalidate();
        VertexBuffer.unbind();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Window window = minecraft.getWindow();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float) (window.getWidth() / window.getGuiScale()),
            (float) (window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.translate(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();
        try {
            GuiGraphics g = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            hook.accept(g);
            g.flush();
        } finally {
            view.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** A new grid with room for {@code count} panels. */
    public static void begin(int count) {
        clear();
        slots = Math.max(1, count);
    }

    public static int frozen() {
        return PANELS.size();
    }

    /**
     * Freezes the frame now on the screen into the next panel ({@code color}: its border's accent; {@code now}: the
     * film's time). Called from the film's overlay pass, before anything of ours is drawn over the frame.
     */
    public static void freeze(GuiGraphics g, int color, float now) {
        if (PANELS.size() >= slots) {
            return;
        }
        g.flush();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        TextureTarget frame = new TextureTarget(Math.max(1, main.width / 2), Math.max(1, main.height / 2), false, Minecraft.ON_OSX);
        frame.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frame.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, frame.width, frame.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        // a frozen frame is opaque: the screen's alpha (whatever the world and shader passes left in it) would let the
        // white panel behind show through (washed-out panels, 2026-11-10)
        RenderSystem.colorMask(false, false, false, true);
        frame.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        frame.clear(Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        main.bindWrite(false);
        PANELS.add(new Panel(frame, color, now));
    }

    /**
     * Draws the panels at film time {@code now} over a screen {@code w} x {@code h} (overlay units); {@code shatterAt}:
     * when the grid breaks apart (NaN: not yet).
     */
    public static void draw(GuiGraphics g, int w, int h, float now, float shatterAt) {
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
        g.flush();
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
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(x, y, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            float border = Math.max(2.0F, h * 0.006F);
            fill(g, -pw * 0.5F - border, -ph * 0.5F - border, pw * 0.5F + border, ph * 0.5F + border, fade(BORDER, alpha));
            fill(g, -pw * 0.5F - border, ph * 0.5F, pw * 0.5F + border, ph * 0.5F + border * 2.5F, fade(0xFF000000 | panel.color(), alpha));
            image(g, panel.frame(), -pw * 0.5F, -ph * 0.5F, pw * 0.5F, ph * 0.5F, alpha);
            pose.popPose();
        }
    }

    /** Frees the frozen frames. */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (Panel panel : PANELS) {
            panel.frame().destroyBuffers();
        }
        PANELS.clear();
        slots = 0;
    }

    private static void image(GuiGraphics g, TextureTarget frame, float x0, float y0, float x1, float y1, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, frame.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // a frame buffer's rows run bottom up
        b.vertex(m, x0, y0, 0.0F).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        b.vertex(m, x0, y1, 0.0F).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        b.vertex(m, x1, y1, 0.0F).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        b.vertex(m, x1, y0, 0.0F).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0.0F);
        pose.scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F, 1.0F);
        g.fill(0, 0, 64, 64, argb);
        pose.popPose();
        g.flush();
    }

    private static int fade(int argb, float alpha) {
        return Mth.clamp((int) ((argb >>> 24) * alpha), 0, 255) << 24 | argb & 0xFFFFFF;
    }

    private static float smooth(float x) {
        float k = Mth.clamp(x, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }
}
