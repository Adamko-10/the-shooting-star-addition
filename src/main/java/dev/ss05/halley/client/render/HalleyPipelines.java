package dev.ss05.halley.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.ss05.halley.HalleyAddon;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * How SS-05's light and sky are drawn: its own shaders ({@code shaders/core/halley_glow} and {@code halley_sky}), in
 * render passes of its own ({@link CometRenderer}, {@code HalleySky}). The light is added onto the scene (light only
 * ever adds), tested against the world's depth but never writing to it, and leaves the target's alpha alone.
 */
public final class HalleyPipelines {
    /** Adds colour, leaves alpha. */
    private static final ColorTargetState ADD = new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA8_UNORM,
        ColorTargetState.WRITE_COLOR);
    /** Premultiplied "over": the new sky's colour plus the old one times what it leaves showing. Leaves alpha. */
    private static final ColorTargetState OVER = new ColorTargetState(
        Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR);
    /** Writes straight onto a texture (painting the shapes for shader packs). */
    private static final ColorTargetState REPLACE = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL);
    /** Minecraft draws with reversed depth (near = 1): in front of what's there means greater or equal. */
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    /** The light, hidden behind terrain like anything else in the world. */
    public static final RenderPipeline GLOW = glow("glow", ADD, Optional.of(TEST_ONLY), false);
    /** The light seen through everything: only for the short, blinding flashes. */
    public static final RenderPipeline GLARE = glow("glare", ADD, Optional.empty(), false);
    /** The shapes alone, painted onto {@link GlowAtlas} for shader packs. */
    public static final RenderPipeline BAKE = glow("bake", REPLACE, Optional.empty(), true);

    /** Minecraft draws with reversed depth (near = 1): writes depth the same way, so the sphere occludes and is
     * occluded like any solid block of the world. */
    private static final DepthStencilState TEST_AND_WRITE = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);
    /** Straight alpha: the base layer is opaque, so blending is off (it still has an alpha channel to leave alone). */
    private static final ColorTargetState OPAQUE = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR);
    /** Reads a texture, no lightmap, no fog: {@link SkyLook} and the moon's own baked-in shading are all it needs. */
    public static final BindGroupLayout MOON_SAMPLER = BindGroupLayouts.SAMPLER0;
    /** SS-06's moon sphere: {@code surface.png}, opaque, depth-tested and written. */
    public static final RenderPipeline MOON = moon("moon", OPAQUE, true);
    /** The molten seams on top: {@code seams.png}, additive, depth-tested but not written (it rides on the base). */
    public static final RenderPipeline MOON_EMISSIVE = moon("moon_emissive", ADD, false);

    /** The sky's values for the frame (HalleySky in halley_sky.fsh). */
    public static final BindGroupLayout SKY_UNIFORMS = BindGroupLayout.builder().withUniform("HalleySky", UniformType.UNIFORM_BUFFER).build();
    /** The dome over the vanilla sky. */
    public static final RenderPipeline SKY = sky("sky", OVER);
    /** The dome painted onto textures for shader packs. */
    public static final RenderPipeline SKY_PAINT = sky("sky_paint", REPLACE);

    private HalleyPipelines() {
    }

    private static RenderPipeline glow(String name, ColorTargetState target, Optional<DepthStencilState> depth, boolean bake) {
        RenderPipeline.Builder b = RenderPipeline.builder()
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_glow"))
            .withFragmentShader(HalleyAddon.id("core/halley_glow"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(target)
            .withDepthStencilState(depth)
            .withCull(false);
        if (bake) {
            b.withShaderDefine("BAKE");
        }
        return b.build();
    }

    private static RenderPipeline moon(String name, ColorTargetState target, boolean write) {
        return RenderPipeline.builder()
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_moon"))
            .withFragmentShader(HalleyAddon.id("core/halley_moon"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(MOON_SAMPLER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(target)
            .withDepthStencilState(Optional.of(write ? TEST_AND_WRITE : TEST_ONLY))
            .withCull(false)
            .build();
    }

    private static RenderPipeline sky(String name, ColorTargetState target) {
        return RenderPipeline.builder()
            .withLocation(HalleyAddon.id("pipeline/halley_" + name))
            .withVertexShader(HalleyAddon.id("core/halley_sky"))
            .withFragmentShader(HalleyAddon.id("core/halley_sky"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(SKY_UNIFORMS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(target)
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();
    }
}
