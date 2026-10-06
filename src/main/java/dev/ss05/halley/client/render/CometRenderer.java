package dev.ss05.halley.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.client.HalleyFx;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws every running SS-05 strike, once a frame, right after the world's particles (so it sorts with them, also
 * under Fabulous graphics). The quads themselves come from {@link CometVisuals} and {@link CometExtras}. With a shader
 * pack on ({@link ShaderPacks}) the same quads are drawn from the shapes painted onto {@link GlowAtlas}.
 */
public final class CometRenderer {
    private static final ByteBufferBuilder WORLD_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder GLARE_BYTES = new ByteBufferBuilder(1 << 15);
    /** The last frame's camera and view-projection, for the HUD's marker pointing at the comet. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;

    private CometRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HalleyFx.active().isEmpty() || !HalleyRenderTypes.ready()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        VIEW_PROJECTION.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        lastCamera = eye;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        // anything further out is pulled in along its line of sight to just inside the far plane (see GlowBatch)
        double far = minecraft.gameRenderer.getDepthFar() * 0.9;
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
                return y <= level.getMinBuildHeight() ? Double.NaN : y;
            }
        };

        // with a shader pack on, the same quads are drawn from the painted shapes, the way the pack can show them
        boolean pack = ShaderPacks.active();
        if (pack && !GlowAtlas.ready()) {
            return;
        }
        VertexFormat format = pack ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR;
        BufferBuilder world = new BufferBuilder(WORLD_BYTES, VertexFormat.Mode.QUADS, format);
        BufferBuilder glare = new BufferBuilder(GLARE_BYTES, VertexFormat.Mode.QUADS, format);
        GlowBatch worldBatch = new GlowBatch(pack ? GlowAtlas.sink(world)
            : (x, y, z, u, v, r, g, b, a) -> world.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, camera.getLeftVector(), camera.getUpVector(), far);
        GlowBatch glareBatch = new GlowBatch(pack ? GlowAtlas.sink(glare)
            : (x, y, z, u, v, r, g, b, a) -> glare.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, camera.getLeftVector(), camera.getUpVector(), far);

        for (HalleyFx fx : HalleyFx.active()) {
            if (!fx.finished()) {
                fx.render(worldBatch, glareBatch, partial, landRange, land);
            }
        }

        MeshData mesh = world.build();
        if (mesh != null) {
            (pack ? HalleyRenderTypes.PACK_GLOW : HalleyRenderTypes.GLOW).draw(mesh);
        }
        mesh = glare.build();
        if (mesh != null) {
            (pack ? HalleyRenderTypes.PACK_GLARE : HalleyRenderTypes.GLARE).draw(mesh);
        }
    }

    /**
     * Where a world point was on screen last frame: {x, y} from -1..1 (y up), plus 1 if it is behind the camera (then
     * x and y still point the way to turn), or null before anything has been drawn.
     */
    @Nullable
    public static float[] toScreen(Vec3 world) {
        if (lastCamera == Vec3.ZERO) {
            return null;
        }
        Vec3 rel = world.subtract(lastCamera);
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
        // dividing by |w| keeps x and y pointing the right way for points behind the camera too
        float w = Math.max(Math.abs(clip.w), 1.0E-4F);
        return new float[]{clip.x / w, clip.y / w, clip.w <= 1.0E-4F ? 1.0F : 0.0F};
    }
}
