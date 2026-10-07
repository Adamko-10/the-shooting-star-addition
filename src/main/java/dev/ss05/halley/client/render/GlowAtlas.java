package dev.ss05.halley.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.ss05.halley.HalleyAddon;
import java.util.Arrays;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Every shape of the {@code halley_glow} shader painted onto one texture, for drawing SS-05's light while a shader
 * pack is on ({@link ShaderPacks}). The shapes that move (the tails' streaming, the bow shock's flicker, the turning
 * mark, the ragged shock fronts, the wall of snow) are repainted every frame; the rest only once.
 *
 * <p>{@link #sink} turns the batch's vertices (whose uv names a shape, see {@link GlowBatch}) into vertices on this
 * texture, for Minecraft's glowing-eyes render type (which every shader pack draws, adding its light).
 */
public final class GlowAtlas {
    private static final int TILE = 256;
    private static final int COLUMNS = 8;
    private static final int ROWS = 3;
    /** The shapes (shape, variant) on the texture, in order. Other variants fall back to variant 0. */
    private static final int[][] TILES = {
        {Shapes.GLOW, 0}, {Shapes.GLOW, Shapes.HAZE}, {Shapes.RING, 0}, {Shapes.RING, 1}, {Shapes.RING, 2},
        {Shapes.GLINT, 0}, {Shapes.STREAK, Shapes.ION}, {Shapes.STREAK, Shapes.DUST}, {Shapes.STREAK, Shapes.PLASMA},
        {Shapes.BOW, 0}, {Shapes.CHEVRON, 0}, {Shapes.RETICLE, 0}, {Shapes.DISC, 0}, {Shapes.DASH, 0},
        {Shapes.SHOCK, 0}, {Shapes.SHOCK, 1}, {Shapes.SHOCK, 2}, {Shapes.SHOCK, 3}, {Shapes.FLARE, 0},
        {Shapes.WALL, 0}, {Shapes.WALL, 1}, {Shapes.WALL, 2}, {Shapes.WALL, 3},
    };
    private static final int MAX_VARIANTS = 4;
    private static final int[] TILE_OF = new int[16 * MAX_VARIANTS];
    /** Half a texel, in tiles: keeps every lookup inside its own tile. */
    private static final float INSET = 0.5F / TILE;

    private static final BakedTexture TEXTURE = new BakedTexture(HalleyAddon.id("glow_atlas"), TILE * COLUMNS, TILE * ROWS);
    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(TILES.length * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
    private static final DynamicMesh MESH = new DynamicMesh("SS-05 Halley glow atlas");
    @Nullable
    private static ProjectionMatrixBuffer projection;
    private static boolean paintedStill;

    static {
        Arrays.fill(TILE_OF, -1);
        for (int i = 0; i < TILES.length; i++) {
            TILE_OF[TILES[i][0] * MAX_VARIANTS + TILES[i][1]] = i;
        }
    }

    private GlowAtlas() {
    }

    public static Identifier location() {
        return TEXTURE.location();
    }

    public static boolean ready() {
        return TEXTURE.ready() && paintedStill;
    }

    private static boolean moves(int shape) {
        return shape == Shapes.STREAK || shape == Shapes.BOW || shape == Shapes.RETICLE || shape == Shapes.SHOCK
            || shape == Shapes.WALL;
    }

    /**
     * Paints the shapes (all of them the first time, then only the moving ones). Called before the world is drawn:
     * while a shader pack draws the world, only the pack's own programs draw into it.
     */
    public static void paint() {
        boolean all = !paintedStill || !TEXTURE.ready();
        BufferBuilder b = new BufferBuilder(BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < TILES.length; i++) {
            int shape = TILES[i][0];
            if (!all && !moves(shape)) {
                continue;
            }
            float x = i % COLUMNS;
            float y = (float) (i / COLUMNS);
            float u = shape * 4.0F;
            float v = TILES[i][1] * 4.0F;
            b.addVertex(x, y, 0.0F).setUv(u, v).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            b.addVertex(x + 1.0F, y, 0.0F).setUv(u + 2.0F, v).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            b.addVertex(x + 1.0F, y + 1.0F, 0.0F).setUv(u + 2.0F, v + 2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            b.addVertex(x, y + 1.0F, 0.0F).setUv(u, v + 2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (!MESH.upload(b.build())) {
            return;
        }
        if (projection == null) {
            projection = new ProjectionMatrixBuffer("SS-05 Halley glow atlas");
        }
        GpuBufferSlice ortho = projection.getBuffer(new Matrix4f().setOrtho(0.0F, COLUMNS, 0.0F, ROWS, -1.0F, 1.0F));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        try (RenderPass pass = TEXTURE.begin(all)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", ortho);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.BAKE));
            MESH.draw(pass);
        }
        paintedStill = true;
    }

    /**
     * A sink for {@link GlowBatch} that writes Minecraft's entity vertices onto this texture: the strength goes into
     * the colour (the glowing-eyes shader draws colour times texture), lighting full, no overlay.
     */
    public static GlowBatch.Sink sink(VertexConsumer out, Matrix4fc pose) {
        return (x, y, z, u, v, r, g, b, a) -> {
            int shape = Math.max(0, Math.min(15, (int) Math.floor(u * 0.25F)));
            int variant = Math.max(0, (int) Math.floor(v * 0.25F));
            float lx = (u - shape * 4.0F) * 0.5F;
            float ly = (v - variant * 4.0F) * 0.5F;
            int tile = TILE_OF[shape * MAX_VARIANTS + Math.min(variant, MAX_VARIANTS - 1)];
            if (tile < 0) {
                tile = Math.max(0, TILE_OF[shape * MAX_VARIANTS]);
            }
            float tu = (tile % COLUMNS + INSET + lx * (1.0F - 2.0F * INSET)) / COLUMNS;
            float tv = ((float) (tile / COLUMNS) + INSET + ly * (1.0F - 2.0F * INSET)) / ROWS;
            out.addVertex(pose, x, y, z)
                .setColor(r * a, g * a, b * a, 1.0F)
                .setUv(tu, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
        };
    }

    /** When the world is left. */
    public static void release() {
        MESH.close();
        paintedStill = false;
    }
}
