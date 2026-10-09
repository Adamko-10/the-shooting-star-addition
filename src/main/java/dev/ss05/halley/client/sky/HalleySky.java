package dev.ss05.halley.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.render.BakedTexture;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.client.render.ShaderPacks;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

/**
 * SS-05's sky: while a strike runs, a dome drawn right after the vanilla sky (before the land, so the land still
 * covers it) with the {@code halley_sky} shader. The fog takes on the new sky's horizon so the land fades into it, and
 * through an optional mixin the land's daylight dims under it and the clouds darken.
 *
 * <p>With a shader pack on ({@link ShaderPacks}) the night is the pack's own instead: the sky's clock is wound on into
 * the night and back ({@link #dayTime}), and the rest of the dome (the stars, the aurora, the comet's light, the haze
 * and the halo) is painted onto textures before the world is drawn ({@link #paint}) and drawn from those.
 *
 * <p>What the sky does when is up to each strike ({@code HalleyFx.sky}); this only draws it. Players can turn it off
 * in the client config.
 */
public final class HalleySky {
    /** The dark sky's colour at the horizon (HORIZON in halley_sky.fsh): the fog takes it on. */
    private static final Vec3 HORIZON = new Vec3(0.055, 0.110, 0.215);
    /** The icy haze's colour at the horizon (veilSky in halley_sky.fsh at the horizon). */
    private static final Vec3 VEIL_HORIZON = new Vec3(0.63, 0.84, 0.98);
    /** Half the size of the cube drawn round the camera (well inside the far plane at any render distance). */
    private static final float SIZE = 50.0F;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static SkyLook current;

    private HalleySky() {
    }

    public static void setShader(@Nullable ShaderInstance loaded) {
        shader = loaded;
    }

    /** The sky this frame (null when no strike is changing it). */
    @Nullable
    public static SkyLook current() {
        return current;
    }

    /** Once a frame, before anything is drawn: the look of the strike that changes the sky most from here. */
    public static void update(TickEvent.RenderTickEvent event) {
        current = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || HalleyFx.active().isEmpty() || !HalleyClientConfig.sky()) {
            return;
        }
        float partial = event.renderTickTime;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        SkyLook best = null;
        float bestStrength = 0.0F;
        for (HalleyFx fx : HalleyFx.active()) {
            if (fx.finished()) {
                continue;
            }
            SkyLook look = fx.sky(partial, camera);
            float strength = look.cover() + look.flash() + look.aurora() * 0.2F + look.cometGlow() * 0.1F;
            if (look.visible() && strength >= bestStrength) {
                best = look;
                bestStrength = strength;
            }
        }
        current = best;
    }

    // ---- The dome. -------------------------------------------------------------------------------------------------

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        SkyLook look = current;
        ShaderInstance program = shader;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (look == null || program == null || level == null || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE || camera.getEntity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            // as with the vanilla sky: nothing to see under water, or blinded
            return;
        }
        if (ShaderPacks.active()) {
            renderPainted(event);
            return;
        }
        uniforms(program, look, level, event.getPartialTick(), false);

        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.mulPoseMatrix(event.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> program);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder cube = Tesselator.getInstance().getBuilder();
        cube.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        cube(cube);
        BufferUploader.drawWithShader(cube.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        view.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** {@code packNight}: a shader pack draws the night (see {@link #dayTime}), so only the haze is painted. */
    private static void uniforms(ShaderInstance program, SkyLook look, ClientLevel level, float partial, boolean packNight) {
        // the light the ice halo forms round: the sun, or the moon at night
        double angle = level.getTimeOfDay(partial) * Math.PI * 2.0;
        Vec3 sun = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
        boolean day = sun.y > -0.05;
        Vec3 light = day ? sun : sun.scale(-1.0);
        float lightUp = day ? smooth((sun.y + 0.05) / 0.15) : 0.55F * smooth((-sun.y - 0.05) / 0.15);

        set(program, "SkyClock", (float) (((level.getGameTime() % 72000L) + partial) / 20.0));
        set(program, "Night", packNight ? 0.0F : look.night());
        set(program, "Stars", look.stars());
        // shader packs make bright cyan glow even brighter
        set(program, "Aurora", look.aurora() * (packNight ? 0.6F : 1.0F));
        set(program, "Veil", look.veil());
        set(program, "Halo", look.halo());
        set(program, "Flash", look.flash());
        set(program, "CometDir", look.cometDir());
        set(program, "CometGlow", look.cometGlow());
        set(program, "CometHeat", look.cometHeat());
        set(program, "TailDir", look.tailDir());
        set(program, "LightDir", light);
        set(program, "LightUp", lightUp);
        set(program, "Seed", look.seed());
        set(program, "Layer", 0.0F);
    }

    // ---- With a shader pack on: the dome painted onto textures first. ---------------------------------------------

    /** The six faces of the cube: outward normal, then the axes across it (their uv on the painted textures). */
    private static final float[][] FACES = {
        {1, 0, 0, 0, 0, -1, 0, 1, 0},
        {-1, 0, 0, 0, 0, 1, 0, 1, 0},
        {0, 1, 0, 1, 0, 0, 0, 0, -1},
        {0, -1, 0, 1, 0, 0, 0, 0, 1},
        {0, 0, 1, 1, 0, 0, 0, 1, 0},
        {0, 0, -1, -1, 0, 0, 0, 1, 0},
    };
    /** The new sky's colour is a smooth gradient: a little texture holds it. */
    private static final int BASE_FACE = 64;
    /** The light needs the detail: stars, the aurora's folds, the halo's thin rings. */
    private static final int LIGHT_FACE = 512;
    private static final BakedTexture BASE = new BakedTexture(HalleyAddon.id("sky_base"), BASE_FACE * 3, BASE_FACE * 2);
    private static final BakedTexture LIGHT = new BakedTexture(HalleyAddon.id("sky_light"), LIGHT_FACE * 3, LIGHT_FACE * 2);
    private static final RenderType BASE_ADD = HalleyRenderTypes.packSky("shooting_star_addition_pack_sky", BASE.location());
    private static final RenderType LIGHT_ADD = HalleyRenderTypes.packSky("shooting_star_addition_pack_sky_light", LIGHT.location());
    private static boolean paintedBase;
    private static boolean paintedLight;
    /** How much of the old sky the painted one covers (the same everywhere). */
    private static float paintedCover;

    /**
     * Once a frame, before the world is drawn (where Iris still lets the sky shader run): paints the dome's six faces
     * onto two textures, its colour and its light, for {@link #renderPainted}.
     */
    public static void paint(float partial) {
        paintedBase = false;
        paintedLight = false;
        SkyLook look = current;
        ShaderInstance program = shader;
        ClientLevel level = Minecraft.getInstance().level;
        if (look == null || program == null || level == null) {
            return;
        }
        // the night itself is the shader pack's own (dayTime): only the haze after the impact covers its sky
        float cover = look.veil() * 0.75F;
        boolean base = cover > 0.002F;
        boolean light = look.stars() > 0.001F || look.aurora() > 0.001F || look.cometGlow() > 0.001F
            || look.halo() > 0.001F || look.flash() > 0.001F;
        if (!base && !light) {
            return;
        }
        uniforms(program, look, level, partial, true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> program);
        if (base) {
            BASE.begin(false);
            paintFaces(program, view, BASE_FACE, 1.0F);
            paintedBase = true;
            paintedCover = cover;
        }
        if (light) {
            LIGHT.begin(false);
            paintFaces(program, view, LIGHT_FACE, 2.0F);
            paintedLight = true;
        }
        set(program, "Layer", 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        view.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        LIGHT.end();
    }

    private static void paintFaces(ShaderInstance program, PoseStack view, int size, float layer) {
        set(program, "Layer", layer);
        for (int f = 0; f < 6; f++) {
            float[] face = FACES[f];
            RenderSystem.viewport(f % 3 * size, f / 3 * size, size, size);
            // a point on this face -> (where it is across the face, 0, 1): the face fills the viewport
            view.last().pose().set(face[3], face[6], 0.0F, 0.0F,
                face[4], face[7], 0.0F, 0.0F,
                face[5], face[8], 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.applyModelViewMatrix();
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            for (int c = 0; c < 4; c++) {
                float s = c == 1 || c == 2 ? 1.0F : -1.0F;
                float t = c >= 2 ? 1.0F : -1.0F;
                b.vertex(face[0] + face[3] * s + face[6] * t, face[1] + face[4] * s + face[7] * t, face[2] + face[5] * s + face[8] * t).endVertex();
            }
            BufferUploader.drawWithShader(b.end());
        }
    }

    /**
     * The painted faces on a cube round the camera: the old sky darkened where the new one covers it and the new one's
     * colour added (together, the new sky painted over the old), then its light added on top.
     */
    private static void renderPainted(RenderLevelStageEvent event) {
        if (!paintedBase && !paintedLight) {
            return;
        }
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.mulPoseMatrix(event.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        if (paintedBase) {
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
            darkeningCube(b, paintedCover);
            HalleyRenderTypes.PACK_SKY_DARKEN.end(b, RenderSystem.getVertexSorting());
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            paintedCube(b, BASE_FACE);
            BASE_ADD.end(b, RenderSystem.getVertexSorting());
        }
        if (paintedLight) {
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            paintedCube(b, LIGHT_FACE);
            LIGHT_ADD.end(b, RenderSystem.getVertexSorting());
        }
        view.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void darkeningCube(BufferBuilder b, float alpha) {
        for (float[] face : FACES) {
            for (int c = 0; c < 4; c++) {
                float s = c == 1 || c == 2 ? 1.0F : -1.0F;
                float t = c >= 2 ? 1.0F : -1.0F;
                // white, not black: some shader packs read a dark, faint quad as the block outline and drop it
                b.vertex(SIZE * (face[0] + face[3] * s + face[6] * t), SIZE * (face[1] + face[4] * s + face[7] * t),
                        SIZE * (face[2] + face[5] * s + face[8] * t))
                    .color(1.0F, 1.0F, 1.0F, alpha)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .endVertex();
            }
        }
    }

    private static void paintedCube(BufferBuilder b, int size) {
        float inset = 0.5F / size;
        for (int f = 0; f < 6; f++) {
            float[] face = FACES[f];
            for (int c = 0; c < 4; c++) {
                float s = c == 1 || c == 2 ? 1.0F : -1.0F;
                float t = c >= 2 ? 1.0F : -1.0F;
                float u = (f % 3 + inset + (s + 1.0F) * 0.5F * (1.0F - 2.0F * inset)) / 3.0F;
                float v = ((float) (f / 3) + inset + (t + 1.0F) * 0.5F * (1.0F - 2.0F * inset)) / 2.0F;
                b.vertex(SIZE * (face[0] + face[3] * s + face[6] * t), SIZE * (face[1] + face[4] * s + face[7] * t),
                        SIZE * (face[2] + face[5] * s + face[8] * t))
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(0.0F, 1.0F, 0.0F)
                    .endVertex();
            }
        }
    }

    private static void cube(BufferBuilder b) {
        float s = SIZE;
        // six faces; the shader only cares about the direction, so the winding doesn't matter (culling is off)
        float[][] faces = {
            {s, -s, -s, s, s, -s, s, s, s, s, -s, s},
            {-s, -s, s, -s, s, s, -s, s, -s, -s, -s, -s},
            {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
            {-s, -s, s, -s, -s, -s, s, -s, -s, s, -s, s},
            {s, -s, s, s, s, s, -s, s, s, -s, -s, s},
            {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s},
        };
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                b.vertex(f[i], f[i + 1], f[i + 2]).endVertex();
            }
        }
    }

    private static void set(ShaderInstance program, String name, float value) {
        program.safeGetUniform(name).set(value);
    }

    private static void set(ShaderInstance program, String name, Vec3 value) {
        program.safeGetUniform(name).set((float) value.x, (float) value.y, (float) value.z);
    }

    // ---- The rest of the world: fog, daylight, clouds, the time of day. ---------------------------------------------

    /**
     * From compat/mixin: the day time the sky (and with it the sun, the moon and the light) is drawn at. While a shader
     * pack is on, the dark sky is the pack's own night: the clock is wound on into it (SkyLook.timeShift) and back.
     */
    public static long dayTime(long real) {
        SkyLook look = current;
        if (look == null || look.timeShift() <= 0.0F || !ShaderPacks.active()) {
            return real;
        }
        return real + (long) look.timeShift();
    }

    /** The fog takes on the new sky's colour at the horizon, so the land fades into it. */
    public static void fog(ViewportEvent.ComputeFogColor event) {
        SkyLook look = current;
        if (look == null || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        // the same mix as the shader's at the horizon: the night, the haze over it, what's left of the vanilla sky
        float keep = 1.0F - look.cover();
        float veil = look.veil() * 0.75F;
        float night = look.night() * (1.0F - veil);
        float flash = look.flash() * 0.6F;
        event.setRed(Math.min(1.0F, (float) (HORIZON.x * night + VEIL_HORIZON.x * veil) + event.getRed() * keep + flash));
        event.setGreen(Math.min(1.0F, (float) (HORIZON.y * night + VEIL_HORIZON.y * veil) + event.getGreen() * keep + flash));
        event.setBlue(Math.min(1.0F, (float) (HORIZON.z * night + VEIL_HORIZON.z * veil) + event.getBlue() * keep + flash));
    }

    /** From compat/mixin: the land's daylight (ClientLevel.getSkyDarken), turned down under the dark sky. */
    public static float skyDarken(float vanilla) {
        SkyLook look = current;
        if (look == null || look.darkLand() <= 0.0F || !HalleyClientConfig.darkenLand()) {
            return vanilla;
        }
        return Mth.lerp(look.darkLand(), vanilla, Math.min(vanilla, 0.22F));
    }

    /** From compat/mixin: the clouds dim under the dark sky and whiten in the haze. */
    public static Vec3 cloudColor(Vec3 vanilla) {
        SkyLook look = current;
        if (look == null) {
            return vanilla;
        }
        // faint, starlit blue: a little lighter than the dark sky behind them, so they don't read as holes in it
        Vec3 c = vanilla.lerp(new Vec3(0.085, 0.13, 0.24), look.night());
        return c.lerp(new Vec3(0.88, 0.95, 1.0), look.veil() * 0.4);
    }

    static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }
}
