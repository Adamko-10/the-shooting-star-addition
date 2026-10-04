package dev.ss05.halley.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.client.HalleyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws every running SS-05 strike, once a frame, right after the world's particles (so it sorts with them, also
 * under Fabulous graphics). The quads themselves come from {@link CometVisuals}.
 */
public final class CometRenderer {
    private static final ByteBufferBuilder WORLD_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder GLARE_BYTES = new ByteBufferBuilder(1 << 15);

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
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        // anything further out is pulled in along its line of sight to just inside the far plane (see GlowBatch)
        double far = minecraft.gameRenderer.getDepthFar() * 0.9;
        double landRange = minecraft.options.getEffectiveRenderDistance() * 16.0;
        CometVisuals.Land land = (x, z) -> level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);

        BufferBuilder world = new BufferBuilder(WORLD_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder glare = new BufferBuilder(GLARE_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        GlowBatch worldBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> world.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, camera.getLeftVector(), camera.getUpVector(), far);
        GlowBatch glareBatch = new GlowBatch((x, y, z, u, v, r, g, b, a) -> glare.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            eye, camera.getLeftVector(), camera.getUpVector(), far);

        for (HalleyFx fx : HalleyFx.active()) {
            if (!fx.finished()) {
                fx.render(worldBatch, glareBatch, partial, landRange, land);
            }
        }

        MeshData mesh = world.build();
        if (mesh != null) {
            HalleyRenderTypes.GLOW.draw(mesh);
        }
        mesh = glare.build();
        if (mesh != null) {
            HalleyRenderTypes.GLARE.draw(mesh);
        }
    }
}
