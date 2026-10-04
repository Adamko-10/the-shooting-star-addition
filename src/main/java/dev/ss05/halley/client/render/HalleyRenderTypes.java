package dev.ss05.halley.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * How SS-05's light is drawn: the halley_glow shader, added onto the scene (light only ever adds), tested against the
 * world's depth but never writing to it. With Fabulous graphics it lands in the particle layer, so it sorts with water
 * and clouds like particles do.
 */
public final class HalleyRenderTypes extends RenderType {
    @Nullable
    private static ShaderInstance shader;

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

    private HalleyRenderTypes() {
        super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {
        }, () -> {
        });
    }

    public static void setShader(@Nullable ShaderInstance loaded) {
        shader = loaded;
    }

    /** False if the shader failed to load (then nothing is drawn, rather than crashing the frame). */
    public static boolean ready() {
        return shader != null;
    }
}
