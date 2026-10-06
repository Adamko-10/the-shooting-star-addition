package dev.ss05.halley.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A texture SS-05 paints itself, every frame it's needed (see {@link ShaderPacks}): an offscreen render target,
 * registered under a name so render types can bind it like any other texture. Always smoothly filtered.
 */
public final class BakedTexture {
    private final ResourceLocation location;
    private final int width;
    private final int height;
    @Nullable
    private TextureTarget target;

    public BakedTexture(ResourceLocation location, int width, int height) {
        this.location = location;
        this.width = width;
        this.height = height;
    }

    public ResourceLocation location() {
        return this.location;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /**
     * Binds the target for drawing into (its whole size as the viewport), creating it the first time. {@code clear}:
     * start from transparent black (otherwise what's drawn replaces what was there, where it's drawn).
     */
    public RenderTarget begin(boolean clear) {
        RenderSystem.assertOnRenderThread();
        TextureTarget t = this.target;
        if (t == null) {
            t = new TextureTarget(this.width, this.height, false, Minecraft.ON_OSX);
            t.setFilterMode(GlConst.GL_LINEAR);
            t.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.target = t;
            Minecraft.getInstance().getTextureManager().register(this.location, new View(t));
            clear = true;
        }
        if (clear) {
            t.clear(Minecraft.ON_OSX);
        }
        t.bindWrite(true);
        return t;
    }

    /** Back to drawing on the screen. */
    public void end() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    /** True once it has been painted at least once. */
    public boolean ready() {
        return this.target != null;
    }

    /** The render target seen as a texture: never deletes it, and never lets anything make it blocky. */
    private static final class View extends AbstractTexture {
        private final RenderTarget target;

        View(RenderTarget target) {
            this.target = target;
        }

        @Override
        public int getId() {
            return this.target.getColorTextureId();
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            super.setFilter(true, false);
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void releaseId() {
        }
    }
}
