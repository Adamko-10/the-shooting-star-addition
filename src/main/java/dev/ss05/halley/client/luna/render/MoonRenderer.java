package dev.ss05.halley.client.luna.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.CometVisuals;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.client.render.ShaderPacks;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws every running SS-06 strike's glow ({@link MoonVisuals}: the burning entry, fragments, the detonation, the
 * embers), reusing SS-05's own {@code halley_glow} shader, shapes and shader-pack atlas path ({@code HalleyRenderTypes}
 * / {@code GlowAtlas} / {@code ShaderPacks}) - only the tint differs, so none of that needed to be duplicated. The
 * solid moon sphere itself is {@link MoonSphere}, hooked separately (it draws depth-tested, among the entities, not
 * additively among the particles).
 */
public final class MoonRenderer {
    /** Reused and grown across frames (1.20.1's BufferBuilder owns and grows its own backing memory). */
    private static final BufferBuilder WORLD_BUILDER = new BufferBuilder(1 << 17);
    private static final BufferBuilder GLARE_BUILDER = new BufferBuilder(1 << 14);
    /** The last frame's camera and view-projection, for the HUD's marker pointing at the moon. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;

    private MoonRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MoonFx.active().isEmpty() || !HalleyRenderTypes.ready()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        VIEW_PROJECTION.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        lastCamera = eye;
        float partial = event.getPartialTick();
        double far = minecraft.gameRenderer.getDepthFar() * 0.9;
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

        boolean pack = ShaderPacks.active();
        if (pack && !GlowAtlas.ready()) {
            return;
        }
        VertexFormat format = pack ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR;
        BufferBuilder world = WORLD_BUILDER;
        BufferBuilder glare = GLARE_BUILDER;
        world.begin(VertexFormat.Mode.QUADS, format);
        glare.begin(VertexFormat.Mode.QUADS, format);
        GlowBatch worldBatch = new GlowBatch(pack ? GlowAtlas.sink(world)
            : (x, y, z, u, v, r, g, b, a) -> world.vertex(x, y, z).uv(u, v).color(r, g, b, a).endVertex(),
            eye, camera.getLeftVector(), camera.getUpVector(), far);
        GlowBatch glareBatch = new GlowBatch(pack ? GlowAtlas.sink(glare)
            : (x, y, z, u, v, r, g, b, a) -> glare.vertex(x, y, z).uv(u, v).color(r, g, b, a).endVertex(),
            eye, camera.getLeftVector(), camera.getUpVector(), far);

        for (MoonFx fx : MoonFx.active()) {
            if (!fx.finished()) {
                MoonSphere.Placement placement = MoonSphere.placement(fx, eye, far, partial);
                MoonVisuals.draw(fx, placement, worldBatch, glareBatch, fx.time(partial), land, fx.filmWeight());
            }
        }

        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        // the quads are camera-relative; in 1.20.1 the camera's rotation is only in the event's PoseStack (the shared
        // model-view is back to identity after the particles), so it is put on the model-view for these two draws
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.mulPoseMatrix(event.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        (pack ? HalleyRenderTypes.PACK_GLOW : HalleyRenderTypes.GLOW).end(world, RenderSystem.getVertexSorting());
        (pack ? HalleyRenderTypes.PACK_GLARE : HalleyRenderTypes.GLARE).end(glare, RenderSystem.getVertexSorting());
        view.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(fogStart);
    }

    /** Where a world point was on screen last frame, exactly as {@code CometRenderer.toScreen} (see there). */
    @Nullable
    public static float[] toScreen(Vec3 world) {
        if (lastCamera == Vec3.ZERO) {
            return null;
        }
        Vec3 rel = world.subtract(lastCamera);
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
        float w = Math.max(Math.abs(clip.w), 1.0E-4F);
        return new float[]{clip.x / w, clip.y / w, clip.w <= 1.0E-4F ? 1.0F : 0.0F};
    }
}
