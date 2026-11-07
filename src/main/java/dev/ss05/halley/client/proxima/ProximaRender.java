package dev.ss05.halley.client.proxima;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.render.DynamicMesh;
import dev.ss05.halley.client.render.HalleyPipelines;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** 1.21.11's render passes for the unchanged Proxima scene, including its deferred GUI image. */
final class ProximaRender implements AutoCloseable {
    private static final Identifier IMAGE = HalleyAddon.id("proxima_film_frame");
    private static final Identifier[] EARTH = {
        HalleyAddon.id("textures/proxima/earth_day.png"), HalleyAddon.id("textures/proxima/earth_detail.png")
    };
    // Model-view, camera/time, sun/mode, parameters: the ProximaScene shader block.
    private static final int UNIFORM_SIZE = 64 + 3 * 16;
    int mode;
    int blend;
    Vec3 sun = Vec3.ZERO;
    final Vector4f params = new Vector4f();
    private GpuBuffer uniforms;
    private PerspectiveProjectionMatrixBuffer projection;
    private Scene scene;
    private boolean world;

    void beginWorld() {
        this.world = true;
        preload();
    }

    void beginFilm(int width, int height) {
        this.world = false;
        preload();
        if (this.scene == null || !this.scene.ready() || this.scene.width != width || this.scene.height != height) {
            this.scene = new Scene(width, height);
            Minecraft.getInstance().getTextureManager().register(IMAGE, this.scene);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(this.scene.getTexture(), 0xFF000000,
            this.scene.depth, 1.0);
    }

    GpuBufferSlice projection(Matrix4f matrix) {
        if (this.projection == null) {
            this.projection = new PerspectiveProjectionMatrixBuffer("SA-04 Proxima film projection");
        }
        return this.projection.getBuffer(matrix);
    }

    void draw(DynamicMesh mesh, VertexFormat.Mode topology, Matrix4f view, GpuBufferSlice proj, Vec3 eye, float time) {
        var device = RenderSystem.getDevice();
        if (this.uniforms == null || this.uniforms.isClosed()) {
            this.uniforms = device.createBuffer(() -> "SA-04 Proxima scene", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, UNIFORM_SIZE).putMat4f(view)
                .putVec4((float) eye.x, (float) eye.y, (float) eye.z, time)
                .putVec4((float) this.sun.x, (float) this.sun.y, (float) this.sun.z, this.mode)
                .putVec4(this.params).get();
            device.createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView color = this.world ? main.getColorTextureView() : this.scene.getTextureView();
        GpuTextureView depth = this.world ? main.getDepthTextureView() : this.scene.depthView;
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "SA-04 Proxima", color,
                OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(HalleyPipelines.proxima(this.blend, topology));
            pass.setUniform("Projection", proj);
            pass.setUniform("ProximaScene", this.uniforms);
            for (int i = 0; i < EARTH.length; i++) {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(EARTH[i]);
                pass.bindTexture("Sampler" + i, texture.getTextureView(), texture.getSampler());
            }
            mesh.draw(pass);
        }
    }

    void present(GuiGraphics graphics) {
        // The GUI is collected now and drawn later: put the scene between earlier overlays and its captions.
        graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED, IMAGE, 0, 0, 0.0F, -graphics.guiHeight(),
            graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), -graphics.guiHeight());
        graphics.nextStratum();
    }

    static void preload() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (Identifier id : EARTH) {
            if (!(textures.getTexture(id) instanceof EarthTexture)) {
                textures.registerAndLoad(id, new EarthTexture(id));
            }
        }
    }

    @Override
    public void close() {
        if (this.uniforms != null) {
            this.uniforms.close();
            this.uniforms = null;
        }
        if (this.projection != null) {
            this.projection.close();
            this.projection = null;
        }
        if (this.scene != null) {
            Minecraft.getInstance().getTextureManager().release(IMAGE);
            this.scene = null;
        }
    }

    /** Registered with the texture manager so resource reloads regenerate the runtime mip chain too. */
    private static final class EarthTexture extends SimpleTexture {
        private GpuSampler ownedSampler;

        EarthTexture(Identifier id) {
            super(id);
        }

        @Override
        public void apply(TextureContents contents) {
            this.close();
            NativeImage base = contents.image();
            int levels = Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.min(base.getWidth(), base.getHeight())));
            NativeImage[] images = MipmapGenerator.generateMipLevels(this.resourceId(), new NativeImage[]{base}, levels,
                MipmapStrategy.MEAN, 0.0F);
            try {
                var device = RenderSystem.getDevice();
                this.texture = device.createTexture(this.resourceId()::toString,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                    base.getWidth(), base.getHeight(), 1, levels + 1);
                this.textureView = device.createTextureView(this.texture);
                this.sampler = this.ownedSampler = device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR,
                    Math.min(8, device.getMaxSupportedAnisotropy()), OptionalDouble.of(levels));
                for (int level = 0; level < images.length; level++) {
                    NativeImage image = images[level];
                    device.createCommandEncoder().writeToTexture(this.texture, image, level, 0, 0, 0,
                        image.getWidth(), image.getHeight(), 0, 0);
                }
            } finally {
                for (NativeImage image : images) {
                    image.close();
                }
            }
        }

        @Override
        public void close() {
            super.close();
            if (this.ownedSampler != null) {
                this.ownedSampler.close();
                this.ownedSampler = null;
                this.sampler = null;
            }
        }
    }

    private static final class Scene extends AbstractTexture {
        final int width;
        final int height;
        private GpuTexture depth;
        private GpuTextureView depthView;

        Scene(int width, int height) {
            this.width = width;
            this.height = height;
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture(IMAGE::toString, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            this.depth = device.createTexture(() -> "SA-04 Proxima film depth", GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1);
            this.depthView = device.createTextureView(this.depth);
        }

        boolean ready() {
            return this.texture != null && !this.texture.isClosed();
        }

        @Override
        public void close() {
            super.close();
            if (this.depthView != null) {
                this.depthView.close();
                this.depthView = null;
            }
            if (this.depth != null) {
                this.depth.close();
                this.depth = null;
            }
        }
    }
}
