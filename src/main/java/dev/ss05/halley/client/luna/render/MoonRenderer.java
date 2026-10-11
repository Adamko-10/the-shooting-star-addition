package dev.ss05.halley.client.luna.render;

import dev.ss05.halley.client.render.Gfx;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.CometVisuals;
import dev.ss05.halley.client.render.DynamicMesh;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.HalleyPipelines;
import dev.ss05.halley.client.render.ShaderPacks;
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
 * Draws every running SS-06 strike's glow ({@link MoonVisuals}: the burning entry, fragments, the detonation, the
 * embers), reusing SS-05's own {@code halley_glow} shader, shapes and shader-pack atlas path ({@code HalleyPipelines}
 * / {@code GlowAtlas} / {@code ShaderPacks}) - only the tint differs, so none of that needed to be duplicated. The
 * solid moon sphere itself is {@link MoonSphere}, drawn first so its depth is already there for this light to test
 * against.
 */
public final class MoonRenderer {
    private static final ByteBufferBuilder WORLD_BYTES = new ByteBufferBuilder(1 << 17);
    private static final ByteBufferBuilder GLARE_BYTES = new ByteBufferBuilder(1 << 14);
    private static final DynamicMesh WORLD_MESH = new DynamicMesh("SS-06 Luna light");
    private static final DynamicMesh GLARE_MESH = new DynamicMesh("SS-06 Luna glare");
    /** The last frame's camera and view-projection, for the HUD's marker pointing at the moon. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;

    private MoonRenderer() {
    }

    /** Fabric's {@code WorldRenderEvents.END_MAIN}: the main pass is over and no render pass is open. */
    public static void render(WorldRenderContext context) {
        if (MoonFx.active().isEmpty() || ShaderPacks.active()) {
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
        CometVisuals.Land land = land(level);
        Vector3f left = new Vector3f(camera.leftVector());
        Vector3f up = new Vector3f(camera.upVector());
        BufferBuilder world = new BufferBuilder(WORLD_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder glare = new BufferBuilder(GLARE_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        GlowBatch worldBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> world.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, left, up, far);
        GlowBatch glareBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> glare.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, left, up, far);

        for (MoonFx fx : MoonFx.active()) {
            if (!fx.finished()) {
                MoonSphere.Placement placement = MoonSphere.placement(fx, eye, far, partial);
                MoonVisuals.draw(fx, placement, worldBatch, glareBatch, fx.time(partial), land, fx.filmWeight());
            }
        }

        // both uploaded before the pass opens (uploads aren't allowed inside one)
        boolean drawWorld = WORLD_MESH.upload(world.build());
        boolean drawGlare = GLARE_MESH.upload(glare.build());
        if (!drawWorld && !drawGlare) {
            return;
        }
        GpuBufferSlice transforms = Gfx.levelTransforms();
        try (RenderPass pass = Gfx.levelPass("SS-06 Luna")) {
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
     * Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}, while a shader pack is on: the same light handed to
     * Minecraft as glowing-eyes geometry from the painted shapes ({@link GlowAtlas}), which the pack then draws with
     * its own programs. The flashes that are seen through everything are drawn right in front of the camera instead.
     */
    public static void submitForShaderPack(WorldRenderContext context) {
        if (MoonFx.active().isEmpty() || !ShaderPacks.active() || !GlowAtlas.ready()) {
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
        CometVisuals.Land land = land(level);
        Vector3f left = new Vector3f(camera.leftVector());
        Vector3f up = new Vector3f(camera.upVector());
        context.commandQueue().submitCustomGeometry(context.matrices(), RenderTypes.eyes(GlowAtlas.location()), (pose, out) -> {
            Matrix4f matrix = pose.pose();
            GlowBatch world = new GlowBatch(quads(GlowAtlas.sink(out, matrix), false), eye, left, up, far);
            GlowBatch glare = new GlowBatch(quads(GlowAtlas.sink(out, matrix), true), eye, left, up, far);
            for (MoonFx fx : MoonFx.active()) {
                if (!fx.finished()) {
                    MoonSphere.Placement placement = MoonSphere.placement(fx, eye, far, partial);
                    MoonVisuals.draw(fx, placement, world, glare, fx.time(partial), land, fx.filmWeight());
                }
            }
        });
    }

    /**
     * Passes quads on with both windings, so they show from either side. {@code near}: also moves each quad toward
     * the camera until it is half a block away, scaling it down to match.
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

    /** Where a world point was on screen last frame, exactly as {@code CometRenderer.toScreen} (see there). */
    public static float @Nullable [] toScreen(Vec3 world) {
        if (lastCamera == Vec3.ZERO) {
            return null;
        }
        Vec3 rel = world.subtract(lastCamera);
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
        float w = Math.max(Math.abs(clip.w), 1.0E-4F);
        return new float[]{clip.x / w, clip.y / w, clip.w <= 1.0E-4F ? 1.0F : 0.0F};
    }

    /** When the world is left: the GPU buffers go (they come back with the next strike). */
    public static void release() {
        WORLD_MESH.close();
        GLARE_MESH.close();
    }
}
