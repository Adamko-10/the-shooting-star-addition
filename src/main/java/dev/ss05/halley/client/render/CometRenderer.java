package dev.ss05.halley.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.client.HalleyFx;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
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

    /** Fabric's {@code WorldRenderEvents.END_MAIN}: the main pass is over and no render pass is open. */
    public static void render(WorldRenderContext context) {
        if (HalleyFx.active().isEmpty() || ShaderPacks.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        CameraRenderState state = context.worldState().cameraRenderState;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = state.pos;
        remember(state);
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        // anything further out is pulled in along its line of sight to just inside the far plane (see GlowBatch)
        double far = Gfx.depthFar() * 0.9;
        double landRange = minecraft.options.getEffectiveRenderDistance() * 16.0;
        CometVisuals.Land land = land(level);
        Vector3f left = new Vector3f(camera.leftVector());
        Vector3f up = new Vector3f(camera.upVector());
        BufferBuilder world = new BufferBuilder(WORLD_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder glare = new BufferBuilder(GLARE_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
        GpuBufferSlice transforms = Gfx.levelTransforms();
        try (RenderPass pass = Gfx.levelPass("SS-05 Halley")) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (drawWorld) {
                pass.setPipeline(HalleyPipelines.GLOW);
                WORLD_MESH.draw(pass);
            }
            if (drawGlare) {
                pass.setPipeline(HalleyPipelines.GLARE);
                GLARE_MESH.draw(pass);
            }
        }
    }

    /**
     * Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}, while a shader pack is on: the same light handed to Minecraft
     * as glowing-eyes geometry from the painted shapes ({@link GlowAtlas}), which the pack then draws with its own
     * programs (adding light, with its own bloom and colour). The flashes that are seen through everything are drawn
     * right in front of the camera instead, where nothing stands in front of them.
     */
    public static void submitForShaderPack(WorldRenderContext context) {
        if (HalleyFx.active().isEmpty() || !ShaderPacks.active() || !GlowAtlas.ready()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        CameraRenderState state = context.worldState().cameraRenderState;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = state.pos;
        remember(state);
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double far = Gfx.depthFar() * 0.9;
        double landRange = minecraft.options.getEffectiveRenderDistance() * 16.0;
        CometVisuals.Land land = land(level);
        Vector3f left = new Vector3f(camera.leftVector());
        Vector3f up = new Vector3f(camera.upVector());
        context.commandQueue().submitCustomGeometry(context.matrices(), RenderTypes.eyes(GlowAtlas.location()), (pose, out) -> {
            Matrix4f matrix = pose.pose();
            // the glowing-eyes pipeline culls back faces, and a ribbon turns whichever way it bends: both sides
            GlowBatch world = new GlowBatch(quads(GlowAtlas.sink(out, matrix), false), eye, left, up, far);
            GlowBatch glare = new GlowBatch(quads(GlowAtlas.sink(out, matrix), true), eye, left, up, far);
            for (HalleyFx fx : HalleyFx.active()) {
                if (!fx.finished()) {
                    fx.render(world, glare, partial, landRange, land);
                }
            }
        });
    }

    /**
     * Passes quads on with both windings, so they show from either side. {@code near}: also moves each quad toward the
     * camera until it is half a block away, scaling it down to match: it covers exactly the same part of the screen,
     * but nothing in the world is in front of it any more.
     */
    private static GlowBatch.Sink quads(GlowBatch.Sink out, boolean near) {
        float[] quad = new float[4 * 9];
        int[] count = {0};
        return (x, y, z, u, v, r, g, b, a) -> {
            int i = count[0] * 9;
            quad[i] = x;
            quad[i + 1] = y;
            quad[i + 2] = z;
            quad[i + 3] = u;
            quad[i + 4] = v;
            quad[i + 5] = r;
            quad[i + 6] = g;
            quad[i + 7] = b;
            quad[i + 8] = a;
            if (++count[0] < 4) {
                return;
            }
            count[0] = 0;
            float cx = (quad[0] + quad[9] + quad[18] + quad[27]) * 0.25F;
            float cy = (quad[1] + quad[10] + quad[19] + quad[28]) * 0.25F;
            float cz = (quad[2] + quad[11] + quad[20] + quad[29]) * 0.25F;
            float d = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float k = near && d > 0.5F ? 0.5F / d : 1.0F;
            for (int j = 0; j < 8; j++) {
                int o = (j < 4 ? j : 7 - j) * 9;
                out.vertex(quad[o] * k, quad[o + 1] * k, quad[o + 2] * k, quad[o + 3], quad[o + 4], quad[o + 5], quad[o + 6], quad[o + 7],
                    quad[o + 8]);
            }
        };
    }

    private static void remember(CameraRenderState state) {
        VIEW_PROJECTION.set(Gfx.projection()).mul(Gfx.viewRotation());
        lastCamera = state.pos;
    }

    private static CometVisuals.Land land(ClientLevel level) {
        return new CometVisuals.Land() {
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
        GlowAtlas.release();
    }
}
