package dev.ss05.halley.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.ss05.halley.client.HalleyFx;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws every running SS-05 strike, once a frame, at the end of the world's main pass (after the terrain, the
 * entities, the particles, the water and the clouds), in a render pass of its own on the main target. The quads
 * themselves come from {@link CometVisuals} and {@link CometExtras}.
 */
public final class CometRenderer {
    private static final ByteBufferBuilder WORLD_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder GLARE_BYTES = new ByteBufferBuilder(1 << 15);
    private static final DynamicMesh WORLD_MESH = new DynamicMesh("SS-05 Halley light");
    private static final DynamicMesh GLARE_MESH = new DynamicMesh("SS-05 Halley glare");
    /** The last frame's camera and view-projection, for the HUD's marker pointing at the comet. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;

    private CometRenderer() {
    }

    /** Fabric's {@code LevelRenderEvents.END_MAIN}: the main pass is over and no render pass is open. */
    public static void render(LevelRenderContext context) {
        if (HalleyFx.active().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        CameraRenderState state = context.levelState().cameraRenderState;
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 eye = state.pos;
        VIEW_PROJECTION.set(state.projectionMatrix).mul(state.viewRotationMatrix);
        lastCamera = eye;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        // anything further out is pulled in along its line of sight to just inside the far plane (see GlowBatch)
        double far = state.depthFar * 0.9;
        double landRange = minecraft.options.getEffectiveRenderDistance() * 16.0;
        CometVisuals.Land land = new CometVisuals.Land() {
            @Override
            public boolean has(double x, double z) {
                return level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
            }

            @Override
            public double surface(double x, double z) {
                int bx = Mth.floor(x);
                int bz = Mth.floor(z);
                if (!level.hasChunk(bx >> 4, bz >> 4)) {
                    return Double.NaN;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                return y <= level.getMinY() ? Double.NaN : y;
            }
        };

        Vector3f left = new Vector3f(camera.leftVector());
        Vector3f up = new Vector3f(camera.upVector());
        BufferBuilder world = new BufferBuilder(WORLD_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder glare = new BufferBuilder(GLARE_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        GlowBatch worldBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> world.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, left, up, far);
        GlowBatch glareBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> glare.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, left, up, far);

        for (HalleyFx fx : HalleyFx.active()) {
            if (!fx.finished()) {
                fx.render(worldBatch, glareBatch, partial, landRange, land);
            }
        }

        // both uploaded before the pass opens (uploads aren't allowed inside one)
        boolean drawWorld = WORLD_MESH.upload(world.build());
        boolean drawGlare = GLARE_MESH.upload(glare.build());
        if (!drawWorld && !drawGlare) {
            return;
        }
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-05 Halley",
            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (drawWorld) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.GLOW));
                WORLD_MESH.draw(pass);
            }
            if (drawGlare) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.GLARE));
                GLARE_MESH.draw(pass);
            }
        }
    }

    /**
     * Where a world point was on screen last frame: {x, y} from -1..1 (y up), plus 1 if it is behind the camera (then
     * x and y still point the way to turn), or null before anything has been drawn.
     */
    public static float @Nullable [] toScreen(Vec3 world) {
        if (lastCamera == Vec3.ZERO) {
            return null;
        }
        Vec3 rel = world.subtract(lastCamera);
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
        // dividing by |w| keeps x and y pointing the right way for points behind the camera too
        float w = Math.max(Math.abs(clip.w), 1.0E-4F);
        return new float[]{clip.x / w, clip.y / w, clip.w <= 1.0E-4F ? 1.0F : 0.0F};
    }

    /** When the world is left: the GPU buffers go (they come back with the next strike). */
    public static void release() {
        WORLD_MESH.close();
        GLARE_MESH.close();
    }
}
