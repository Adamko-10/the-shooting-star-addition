package dev.ss05.halley.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * A texture SS-05 paints itself (see {@link ShaderPacks}): drawn into like a render target, and registered under a
 * name so render types can bind it like any other texture. Always smoothly filtered, never repeated.
 */
public final class BakedTexture extends AbstractTexture {
    private final Identifier location;
    private final int width;
    private final int height;
    private boolean registered;

    public BakedTexture(Identifier location, int width, int height) {
        this.location = location;
        this.width = width;
        this.height = height;
    }

    public Identifier location() {
        return this.location;
    }

    /** True once the texture exists (it is made the first time it is painted). */
    public boolean ready() {
        return this.texture != null && !this.texture.isClosed();
    }

    /**
     * A render pass drawing onto the texture (the whole of it), creating it the first time. {@code clear}: start from
     * transparent black (otherwise what's drawn replaces what was there, where it's drawn). Close it when done.
     */
    public RenderPass begin(boolean clear) {
        RenderSystem.assertOnRenderThread();
        if (!this.ready()) {
            GpuDevice device = RenderSystem.getDevice();
            this.releaseTextures();
            this.texture = device.createTexture(() -> this.location.toString(),
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, this.width, this.height, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            clear = true;
            if (!this.registered) {
                Minecraft.getInstance().getTextureManager().register(this.location, this);
                this.registered = true;
            }
        }
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-05 Halley " + this.location.getPath(),
            this.getTextureView(), clear ? OptionalInt.of(0) : OptionalInt.empty());
    }

    /** The texture manager closes it with the rest when resources reload; it is simply made again when next painted. */
    @Override
    public void close() {
        this.releaseTextures();
        this.registered = false;
    }

    /** Closes the GPU texture and its view (1.21.11's AbstractTexture has no helper for this). */
    private void releaseTextures() {
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
