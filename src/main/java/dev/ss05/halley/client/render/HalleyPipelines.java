package dev.ss05.halley.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.HalleyAddon;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * How SS-05's light and sky (and SS-06's moon) are drawn: their own shaders ({@code shaders/core/halley_glow},
 * {@code halley_sky}, {@code halley_moon}), in render passes of their own ({@link CometRenderer}, {@code HalleySky},
 * {@code MoonSphere}). The light is added onto the scene (light only ever adds), tested against the world's depth but
 * never writing to it, and leaves the target's alpha alone.
 *
 * <p>Minecraft 1.21.11's pipelines (Fabric 1.21.11 branch): uniform blocks and samplers are named on the builder,
 * and depth is the classic "less or equal" (26.x moved to reversed depth).
 */
public final class HalleyPipelines {
    /** Premultiplied "over": the new sky's colour plus the old one times what it leaves showing. */
    private static final BlendFunction OVER = new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);

    /** The light, hidden behind terrain like anything else in the world. */
    public static final RenderPipeline GLOW = glow("glow", true, false);
    /** The light seen through everything: only for the short, blinding flashes. */
    public static final RenderPipeline GLARE = glow("glare", false, false);
    /** The shapes alone, painted onto {@link GlowAtlas} for shader packs. */
    public static final RenderPipeline BAKE = glow("bake", false, true);

    /** SS-06's moon sphere: {@code surface.png}, opaque, depth-tested and written, so it occludes and is occluded
     * like any solid block of the world. */
    public static final RenderPipeline MOON = moon("moon", true);
    /** The molten seams on top: {@code seams.png}, additive, depth-tested but not written (it rides on the base). */
    public static final RenderPipeline MOON_EMISSIVE = moon("moon_emissive", false);

    /** The dome over the vanilla sky. */
    public static final RenderPipeline SKY = sky("sky", false);
    /** The dome painted onto textures for shader packs. */
    public static final RenderPipeline SKY_PAINT = sky("sky_paint", true);

    private HalleyPipelines() {
    }

    private static RenderPipeline glow(String name, boolean depthTest, boolean bake) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_glow"))
            .withFragmentShader(HalleyAddon.id("core/halley_glow"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false);
        if (bake) {
            // writes straight onto a texture (painting the shapes for shader packs)
            b.withoutBlend().withColorWrite(true, true).withShaderDefine("BAKE");
        } else {
            // adds colour, leaves alpha
            b.withBlend(BlendFunction.ADDITIVE).withColorWrite(true, false);
        }
        return b.build();
    }

    private static RenderPipeline moon(String name, boolean base) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_moon"))
            .withFragmentShader(HalleyAddon.id("core/halley_moon"))
            // reads a texture, no lightmap, no fog: the moon's own baked-in shading is all it needs
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(base)
            .withCull(false);
        if (base) {
            b.withoutBlend().withColorWrite(true, false);
        } else {
            b.withBlend(BlendFunction.ADDITIVE).withColorWrite(true, false);
        }
        return b.build();
    }

    private static RenderPipeline sky(String name, boolean paint) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_sky"))
            .withFragmentShader(HalleyAddon.id("core/halley_sky"))
            // the sky's values for the frame (HalleySky in halley_sky.fsh)
            .withUniform("HalleySky", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false);
        if (paint) {
            b.withoutBlend().withColorWrite(true, true);
        } else {
            b.withBlend(OVER).withColorWrite(true, false);
        }
        return b.build();
    }
}
