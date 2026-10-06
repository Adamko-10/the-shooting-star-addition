package dev.ss05.halley.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * How SS-05's light is drawn: the halley_glow shader, added onto the scene (light only ever adds), tested against the
 * world's depth but never writing to it. With Fabulous graphics it lands in the particle layer, so it sorts with water
 * and clouds like particles do.
 *
 * <p>While a shader pack is on ({@link ShaderPacks}) the same light is drawn from painted textures with Minecraft's own
 * glowing-eyes shaders instead (the {@code PACK_} types), since those are the ones a shader pack replaces with its own
 * and draws.
 */
public final class HalleyRenderTypes extends RenderType {
    @Nullable
    private static ShaderInstance shader;
    private static float fogStart;

    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("shooting_star_addition_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** Hidden behind terrain, like anything else in the world. */
    public static final RenderType GLOW = create("shooting_star_addition_glow", DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS, 1 << 16, false, false, CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> shader))
            .setTransparencyState(ADDITIVE)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false));

    /** Seen through everything: only for the short, blinding flashes. */
    public static final RenderType GLARE = create("shooting_star_addition_glare", DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS, 1 << 14, false, false, CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> shader))
            .setTransparencyState(ADDITIVE)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false));

    // ---- With a shader pack on. -------------------------------------------------------------------------------------

    /**
     * Adds colour, and leaves the alpha alone (a shader pack may keep something of its own there). Iris draws every
     * glowing-eyes draw adding its colour anyway, so this is what the light gets with or without a shader pack.
     */
    private static final TransparencyStateShard PACK_ADDITIVE = new TransparencyStateShard("shooting_star_addition_pack_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /**
     * Darkens what's there by the alpha drawn (and leaves the target's alpha alone): the first half of painting something
     * over it, the second half being {@link #PACK_ADDITIVE} with its colour already multiplied by its alpha.
     */
    private static final TransparencyStateShard PACK_DARKEN = new TransparencyStateShard("shooting_star_addition_pack_darken", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /**
     * No fog. The comet is far away on purpose (pulled in from further still, see GlowBatch) and the sky is the sky:
     * Minecraft's own glowing-eyes shader would fade both out into the distance.
     */
    private static final TexturingStateShard NO_FOG = new TexturingStateShard("shooting_star_addition_no_fog", () -> {
        fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
    }, () -> RenderSystem.setShaderFogStart(fogStart));

    /** {@link #GLOW} from the painted shapes ({@link GlowAtlas}). */
    public static final RenderType PACK_GLOW = packGlow("shooting_star_addition_pack_glow", LEQUAL_DEPTH_TEST);
    /** {@link #GLARE} from the painted shapes. */
    public static final RenderType PACK_GLARE = packGlow("shooting_star_addition_pack_glare", NO_DEPTH_TEST);

    private HalleyRenderTypes() {
        super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {
        }, () -> {
        });
    }

    private static RenderType packGlow(String name, DepthTestStateShard depth) {
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 16, false, false, CompositeState.builder()
            .setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new TextureStateShard(GlowAtlas.location(), true, false))
            .setTransparencyState(PACK_ADDITIVE)
            .setDepthTestState(depth)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setTexturingState(NO_FOG)
            .createCompositeState(false));
    }

    /**
     * A cube round the camera darkening the sky by its vertices' alpha: the first half of painting the new sky over the
     * old (the second is adding its colour, {@link #packSky}). Drawn with the leash's shader, as shader packs force
     * glowing-eyes draws to add (Iris draws them with its own blending) but leave this one's blending alone.
     */
    public static final RenderType PACK_SKY_DARKEN = create("shooting_star_addition_pack_sky_darken",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 1 << 10, false, false, CompositeState.builder()
            .setShaderState(RENDERTYPE_LEASH_SHADER)
            .setTransparencyState(PACK_DARKEN)
            .setLightmapState(LIGHTMAP)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setTexturingState(NO_FOG)
            .createCompositeState(false));

    /** The sky painted onto {@code texture}, on a cube round the camera, added on top. */
    public static RenderType packSky(String name, ResourceLocation texture) {
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 10, false, false, CompositeState.builder()
            .setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new TextureStateShard(texture, true, false))
            .setTransparencyState(PACK_ADDITIVE)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setTexturingState(NO_FOG)
            .createCompositeState(false));
    }

    public static void setShader(@Nullable ShaderInstance loaded) {
        shader = loaded;
    }

    @Nullable
    public static ShaderInstance shader() {
        return shader;
    }

    /** False if the shader failed to load (then nothing is drawn, rather than crashing the frame). */
    public static boolean ready() {
        return shader != null;
    }
}
