package dev.ss05.halley.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.render.MoonSphere;
import dev.ss05.halley.client.luna.render.MoonVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1.9.0, Adam's test with Complementary on: "do not display it over clouds" (Luna's moon stood in front of a cloud; so
 * did Halley's comet). A shader pack draws its clouds late, over whatever is still sky, and our sky things are drawn in
 * the world pass (the moon even writes depth), so they came out in front of them. So while a pack is on and the camera
 * is under the cloud layer, those of ours wholly above it are drawn here instead: inside the game's own sky drawing
 * ({@code compat/mixin/LevelRendererSkyMixin}, where the stars are drawn), with the game's textured sky shader, which a
 * pack swaps for its own sky program; the pack keeps them in its sky and draws its clouds and the land over them. The
 * world pass leaves them out meanwhile ({@link #above}). TON 618 Addition does the same for its stars
 * ({@code QuasarAtlas.drawSky}). Complementary's sky program squares the colour it is given, so colours are given as
 * their square roots. Without a pack nothing changes: the game's own clouds already cover the moon (MoonSphere).
 * Proxima's sky gate joined them later the same release (its own shader can't run here: it is painted onto a card at the
 * frame's start, ProximaCosmos.paintSkyCard).
 */
public final class SkyPass {
    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 20);
    private static final ByteBufferBuilder GLARE_BYTES = new ByteBufferBuilder(1 << 18);
    private static boolean failed;

    private SkyPass() {
    }

    /** The cloud layer's height while the sky pass is on (a pack on, the camera under the clouds), else NaN. */
    public static double clouds(Vec3 camera) {
        if (!ShaderPacks.active()) {
            return Double.NaN;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Double.NaN;
        }
        double height = level.effects().getCloudHeight();
        return !Double.isNaN(height) && camera.y < height ? height : Double.NaN;
    }

    /** Whether something whose lowest point is at {@code bottom} is drawn in the sky pass this frame, not the world's. */
    public static boolean above(Vec3 camera, double bottom) {
        double height = clouds(camera);
        return !Double.isNaN(height) && bottom > height + 4.0;
    }

    /** From inside the game's sky drawing, where the stars are drawn; {@code pose}: the sky's camera turn. */
    public static void draw(Matrix4f pose, float partial, Camera camera) {
        Vec3 eye = camera.getPosition();
        if (failed || Double.isNaN(clouds(eye)) || MoonFx.active().isEmpty() && HalleyFx.active().isEmpty()
            && !dev.ss05.halley.client.proxima.ProximaCosmos.inSky()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double far = minecraft.gameRenderer.getDepthFar() * 0.9;
        float fogStart = RenderSystem.getShaderFogStart();
        float[] colour = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(false);
        // as in the world pass: ribbons are seen from either side
        RenderSystem.disableCull();
        try {
            // Proxima's gate (painted onto a card at the frame's start, ProximaCosmos.paintSkyCard), farthest off
            dev.ss05.halley.client.proxima.ProximaCosmos.renderSkyPass(pose, eye, far);
            // the bodies first (the moon hides the stars behind it), then the light round them
            for (MoonFx fx : MoonFx.active()) {
                if (!fx.finished() && MoonSphere.inSky(fx, eye, partial)) {
                    MoonSphere.renderSky(fx, eye, far, partial, pose);
                }
            }
            if (!GlowAtlas.ready()) {
                return;
            }
            BufferBuilder world = new BufferBuilder(BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            BufferBuilder glare = new BufferBuilder(GLARE_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            GlowBatch worldBatch = new GlowBatch(GlowAtlas.sink(new Sky(world, pose)), eye, camera.getLeftVector(), camera.getUpVector(), far);
            GlowBatch glareBatch = new GlowBatch(GlowAtlas.sink(new Sky(glare, pose)), eye, camera.getLeftVector(), camera.getUpVector(), far);
            for (MoonFx fx : MoonFx.active()) {
                if (!fx.finished() && MoonSphere.inSky(fx, eye, partial)) {
                    MoonVisuals.drawAttached(fx, MoonSphere.placement(fx, eye, far, partial), worldBatch, glareBatch, fx.time(partial));
                }
            }
            for (HalleyFx fx : HalleyFx.active()) {
                if (!fx.finished()) {
                    fx.renderSky(worldBatch, glareBatch, partial);
                }
            }
            drawMesh(world, GlowAtlas.location(), true);
            drawMesh(glare, GlowAtlas.location(), true);
        } catch (RuntimeException | LinkageError broken) {
            failed = true;
            HalleyAddon.LOG.error("The sky things drawn behind a shader pack's clouds stopped drawing (they go back to the world pass)", broken);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3]);
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    /** Whether the sky pass has stopped working (then everything is drawn in the world pass, as before 1.9.0). */
    public static boolean broken() {
        return failed;
    }

    /** Draws what {@code builder} gathered with the game's textured sky shader: added light, or ({@code added} false) over. */
    public static void drawMesh(BufferBuilder builder, ResourceLocation texture, boolean added) {
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        if (added) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        } else {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
        BufferUploader.drawWithShader(mesh);
    }

    /**
     * Where the sky's things go: each camera-relative vertex turned by the sky's camera turn (the game puts it into each
     * corner in its sky too), its colour square-rooted for the pack's sky program; only position, uv and colour kept.
     */
    public static final class Sky implements VertexConsumer {
        private final VertexConsumer out;
        private final Matrix4f pose;
        private final Vector3f at = new Vector3f();

        public Sky(VertexConsumer out, Matrix4f pose) {
            this.out = out;
            this.pose = pose;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.pose.transformPosition(x, y, z, this.at);
            this.out.addVertex(this.at.x, this.at.y, this.at.z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.out.setColor(root(r), root(g), root(b), a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        private static int root(int channel) {
            return (int) Math.round(Math.sqrt(Math.max(0, Math.min(255, channel)) / 255.0) * 255.0);
        }
    }
}
