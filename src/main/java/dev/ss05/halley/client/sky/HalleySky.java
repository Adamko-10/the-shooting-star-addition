package dev.ss05.halley.client.sky;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.BakedTexture;
import dev.ss05.halley.client.render.HalleyPipelines;
import dev.ss05.halley.client.render.ShaderPacks;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * SS-05's sky: while a strike runs, a dome drawn right after the vanilla sky (before the land, so the land still
 * covers it) with the {@code halley_sky} shader. The fog takes on the new sky's horizon so the land fades into it, the
 * land's daylight dims under it and the clouds darken (through the client level's environment attributes).
 *
 * <p>What the sky does when is up to each strike ({@code HalleyFx.sky}); this only draws it. Players can turn it off
 * in the client config. The hooks into Minecraft are in {@code client/mixin}.
 */
public final class HalleySky {
    /** The dark sky's colour at the horizon, by {@link SkyLook#palette()} (HORIZON in halley_sky.fsh): the fog takes
     * it on. SS-06 Luna's is a warm dusty red instead of SS-05's cold navy. */
    private static final Vec3[] HORIZON_BY_PALETTE = {new Vec3(0.055, 0.110, 0.215), new Vec3(0.360, 0.090, 0.020)};
    /** The haze's colour at the horizon, by palette (veilSky in halley_sky.fsh at the horizon). */
    private static final Vec3[] VEIL_HORIZON_BY_PALETTE = {new Vec3(0.63, 0.84, 0.98), new Vec3(0.62, 0.38, 0.20)};
    /** The land's light at night (the overworld's sky_light_factor and sky_light_color then). */
    private static final float NIGHT_LIGHT = 0.24F;
    private static final Vector3fc NIGHT_TINT = new Vector3f(0x7A / 255.0F, 0x7A / 255.0F, 1.0F);
    /** Faint, starlit blue: a little lighter than the dark sky behind them, so clouds don't read as holes in it. */
    private static final Vector4fc NIGHT_CLOUDS = new Vector4f(0.085F, 0.13F, 0.24F, 1.0F);
    private static final Vector4fc ICY_CLOUDS = new Vector4f(0.88F, 0.95F, 1.0F, 1.0F);
    /** Half the size of the cube drawn round the camera (well inside the far plane at any render distance). */
    private static final float SIZE = 50.0F;
    /** Six vec4s: see the HalleySky block in halley_sky.fsh. */
    private static final int UNIFORM_SIZE = 6 * 16;

    @Nullable
    private static SkyLook current;
    @Nullable
    private static GpuBuffer cube;
    /** The values for the dome, and for the two painted layers (written at different times in a frame). */
    private static final GpuBuffer[] UNIFORMS = new GpuBuffer[3];

    private HalleySky() {
    }

    /** The sky this frame (null when no strike is changing it). */
    @Nullable
    public static SkyLook current() {
        return current;
    }

    /** Once a frame, before the frame is gathered up for drawing: the look of the strike that changes the sky most. */
    public static void update(float partial) {
        current = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || (HalleyFx.active().isEmpty() && MoonFx.active().isEmpty()) || !HalleyClientConfig.sky()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
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
        for (MoonFx fx : MoonFx.active()) {
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

    /**
     * From client/mixin, right after Minecraft has drawn the sky (its own render pass is closed again, the world's view
     * rotation is on the model-view stack and the world's projection is bound).
     */
    public static void renderDome(SkyRenderState state, RenderTarget target) {
        SkyLook look = current;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (look == null || level == null || state.skybox != DimensionType.Skybox.OVERWORLD) {
            return;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            // as with the vanilla sky: nothing to see under water (blindness skips the whole sky pass already)
            return;
        }
        if (ShaderPacks.active()) {
            // a shader pack draws its own sky over this one: the night is the pack's own then (clockShift)
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        GpuBuffer values = writeUniforms(0, look, level, camera, partial, false, 0.0F);
        GpuBuffer box = cube();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-05 Halley sky",
            target.getColorTextureView(), Optional.empty())) {
            drawCube(pass, HalleyPipelines.SKY, transforms, values, box, indexBuffer, indices);
        }
    }

    private static void drawCube(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms, GpuBuffer values, GpuBuffer box,
                                 GpuBuffer indexBuffer, RenderSystem.AutoStorageIndexBuffer indices) {
        RenderSystem.bindDefaultUniforms(pass);
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("HalleySky", values);
        pass.setVertexBuffer(0, box.slice());
        pass.setIndexBuffer(indexBuffer, indices.type());
        pass.drawIndexed(36, 1, 0, 0, 0);
    }

    /**
     * The values halley_sky.fsh reads (its HalleySky block), for this frame. {@code packNight}: a shader pack draws the
     * night (see {@link #clockShift}), so only the haze is painted.
     */
    private static GpuBuffer writeUniforms(int slot, SkyLook look, ClientLevel level, Camera camera, float partial, boolean packNight,
                                           float layer) {
        // the light the ice halo forms round: the sun, or the moon at night
        float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial) * Mth.DEG_TO_RAD;
        float moonAngle = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partial) * Mth.DEG_TO_RAD;
        Vec3 sun = new Vec3(-Math.sin(sunAngle), Math.cos(sunAngle), 0.0);
        Vec3 moon = new Vec3(-Math.sin(moonAngle), Math.cos(moonAngle), 0.0);
        boolean day = sun.y > -0.05;
        Vec3 light = day ? sun : moon;
        float lightUp = day ? smooth((sun.y + 0.05) / 0.15) : 0.55F * smooth((moon.y - 0.05) / 0.15);
        float skyClock = (float) (((level.getGameTime() % 72000L) + partial) / 20.0);

        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer uniforms = UNIFORMS[slot];
        if (uniforms == null || uniforms.isClosed()) {
            uniforms = device.createBuffer(() -> "SS-05 Halley sky values", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
            UNIFORMS[slot] = uniforms;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Vec3 comet = look.cometDir();
            Vec3 tail = look.tailDir();
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                .putVec4(skyClock, packNight ? 0.0F : look.night(), look.stars(), look.aurora() * (packNight ? 0.6F : 1.0F))
                .putVec4(look.veil(), look.halo(), look.flash(), look.cometGlow())
                .putVec4((float) comet.x, (float) comet.y, (float) comet.z, look.cometHeat())
                .putVec4((float) tail.x, (float) tail.y, (float) tail.z, look.seed())
                .putVec4((float) light.x, (float) light.y, (float) light.z, lightUp)
                .putVec4(layer, (float) look.palette(), 0.0F, 0.0F)
                .get();
            device.createCommandEncoder().writeToBuffer(uniforms.slice(), data);
        }
        return uniforms;
    }

    /** The cube round the camera, built once: the shader only cares about the direction to each pixel. */
    private static GpuBuffer cube() {
        if (cube != null && !cube.isClosed()) {
            return cube;
        }
        float s = SIZE;
        // six faces; the winding doesn't matter (culling is off)
        float[][] faces = {
            {s, -s, -s, s, s, -s, s, s, s, s, -s, s},
            {-s, -s, s, -s, s, s, -s, s, -s, -s, -s, -s},
            {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
            {-s, -s, s, -s, -s, -s, s, -s, -s, s, -s, s},
            {s, -s, s, s, s, s, -s, s, s, -s, -s, s},
            {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s},
        };
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            for (float[] f : faces) {
                for (int i = 0; i < 12; i += 3) {
                    b.addVertex(f[i], f[i + 1], f[i + 2]);
                }
            }
            try (MeshData mesh = b.buildOrThrow()) {
                cube = RenderSystem.getDevice().createBuffer(() -> "SS-05 Halley sky cube", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        return cube;
    }

    /** When the world is left: the GPU buffers go. */
    public static void release() {
        current = null;
        if (cube != null) {
            cube.close();
            cube = null;
        }
        for (int i = 0; i < UNIFORMS.length; i++) {
            if (UNIFORMS[i] != null) {
                UNIFORMS[i].close();
                UNIFORMS[i] = null;
            }
        }
        if (faces != null) {
            faces.close();
            faces = null;
        }
        paintedBase = false;
        paintedLight = false;
    }

    // ---- With a shader pack on: the dome painted onto textures, drawn as glowing-eyes geometry. ----------------------

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
    /** How much of the haze's colour is added over the pack's sky (it can't be laid over it, only added). */
    private static final float PACK_HAZE = 0.5F;
    private static final BakedTexture BASE = new BakedTexture(HalleyAddon.id("sky_base"), BASE_FACE * 3, BASE_FACE * 2);
    private static final BakedTexture LIGHT = new BakedTexture(HalleyAddon.id("sky_light"), LIGHT_FACE * 3, LIGHT_FACE * 2);
    @Nullable
    private static GpuBuffer faces;
    @Nullable
    private static ProjectionMatrixBuffer flat;
    private static boolean paintedBase;
    private static boolean paintedLight;
    /** How much of the old sky the painted one would cover (the same everywhere). */
    private static float paintedCover;

    /**
     * Once a frame, before the world is drawn (while a shader pack is on, only its own programs draw into the world):
     * paints the dome's six faces onto two textures, its colour and its light, for {@link #submitForShaderPack}.
     */
    public static void paint(float partial) {
        paintedBase = false;
        paintedLight = false;
        SkyLook look = current;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (look == null || level == null) {
            return;
        }
        // the night itself is the shader pack's own (clockShift): only the haze after the impact covers its sky
        float cover = look.veil() * 0.75F;
        boolean base = cover > 0.002F;
        boolean light = look.stars() > 0.001F || look.aurora() > 0.001F || look.cometGlow() > 0.001F
            || look.halo() > 0.001F || look.flash() > 0.001F;
        if (!base && !light) {
            return;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        GpuBuffer box = faces();
        if (flat == null) {
            flat = new ProjectionMatrixBuffer("SS-05 Halley sky faces");
        }
        GpuBufferSlice identity = flat.getBuffer(new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        if (base) {
            paintFaces(BASE, writeUniforms(1, look, level, camera, partial, true, 1.0F), box, identity, indexBuffer, indices);
            paintedBase = true;
            paintedCover = cover;
        }
        if (light) {
            paintFaces(LIGHT, writeUniforms(2, look, level, camera, partial, true, 2.0F), box, identity, indexBuffer, indices);
            paintedLight = true;
        }
    }

    private static void paintFaces(BakedTexture target, GpuBuffer values, GpuBuffer box, GpuBufferSlice identity, GpuBuffer indexBuffer,
                                   RenderSystem.AutoStorageIndexBuffer indices) {
        GpuBufferSlice[] transforms = new GpuBufferSlice[FACES.length];
        for (int f = 0; f < FACES.length; f++) {
            float[] face = FACES[f];
            int col = f % 3;
            int row = f / 3;
            // a point on this face -> where it is across the face, squeezed into this face's third and half of the
            // texture (the projection is the identity, so this lands straight in clip space)
            Matrix4f m = new Matrix4f().set(
                face[3] / 3.0F, face[6] / 2.0F, 0.0F, 0.0F,
                face[4] / 3.0F, face[7] / 2.0F, 0.0F, 0.0F,
                face[5] / 3.0F, face[8] / 2.0F, 0.0F, 0.0F,
                (2.0F * col - 2.0F) / 3.0F, row - 0.5F, 0.0F, 1.0F);
            transforms[f] = RenderSystem.getDynamicUniforms().writeTransform(m, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        }
        try (RenderPass pass = target.begin(true)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", identity);
            pass.setUniform("HalleySky", values);
            pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.SKY_PAINT));
            pass.setVertexBuffer(0, box.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            for (int f = 0; f < FACES.length; f++) {
                pass.setUniform("DynamicTransforms", transforms[f]);
                pass.drawIndexed(6, 1, f * 6, 0, 0);
            }
        }
    }

    /**
     * Fabric's {@code LevelRenderEvents.COLLECT_SUBMITS}, while a shader pack is on: the painted faces on a cube round
     * the camera, out beyond the land and the clouds, handed to Minecraft as glowing-eyes geometry. The pack draws it
     * adding its light, so it shows wherever the pack's own sky does.
     */
    public static void submitForShaderPack(LevelRenderContext context) {
        if (!ShaderPacks.active() || !paintedBase && !paintedLight) {
            return;
        }
        float radius = context.levelState().cameraRenderState.depthFar * 0.85F;
        if (paintedLight && LIGHT.ready()) {
            submitCube(context, LIGHT, LIGHT_FACE, radius, 1.0F);
        }
        if (paintedBase && BASE.ready()) {
            submitCube(context, BASE, BASE_FACE, radius * 0.995F, PACK_HAZE);
        }
    }

    private static void submitCube(LevelRenderContext context, BakedTexture texture, int size, float radius, float strength) {
        float inset = 0.5F / size;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.eyes(texture.location()), (pose, out) -> {
            for (int f = 0; f < FACES.length; f++) {
                float[] face = FACES[f];
                // both windings: the glowing-eyes pipeline culls back faces, and this is seen from inside
                for (int i = 0; i < 8; i++) {
                    int c = i < 4 ? i : 7 - i;
                    float s = c == 1 || c == 2 ? 1.0F : -1.0F;
                    float t = c >= 2 ? 1.0F : -1.0F;
                    float u = (f % 3 + inset + (s + 1.0F) * 0.5F * (1.0F - 2.0F * inset)) / 3.0F;
                    float v = ((float) (f / 3) + inset + (t + 1.0F) * 0.5F * (1.0F - 2.0F * inset)) / 2.0F;
                    out.addVertex(pose, radius * (face[0] + face[3] * s + face[6] * t), radius * (face[1] + face[4] * s + face[7] * t),
                            radius * (face[2] + face[5] * s + face[8] * t))
                        .setColor(strength, strength, strength, 1.0F)
                        .setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(0.0F, 1.0F, 0.0F);
                }
            }
        });
    }

    /** The unit cube, face by face in {@link #FACES} order, for painting the faces one at a time. */
    private static GpuBuffer faces() {
        if (faces != null && !faces.isClosed()) {
            return faces;
        }
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            for (float[] face : FACES) {
                for (int c = 0; c < 4; c++) {
                    float s = c == 1 || c == 2 ? 1.0F : -1.0F;
                    float t = c >= 2 ? 1.0F : -1.0F;
                    b.addVertex(face[0] + face[3] * s + face[6] * t, face[1] + face[4] * s + face[7] * t, face[2] + face[5] * s + face[8] * t);
                }
            }
            try (MeshData mesh = b.buildOrThrow()) {
                faces = RenderSystem.getDevice().createBuffer(() -> "SS-05 Halley sky faces", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        return faces;
    }

    // ---- The rest of the world: fog, daylight, clouds, the time of day. ---------------------------------------------

    /** From client/mixin: the fog takes on the new sky's colour at the horizon, so the land fades into it. */
    public static void fogColor(Vector4f fog) {
        SkyLook look = current;
        if (look == null || Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        // the same mix as the shader's at the horizon: the night, the haze over it, what's left of the vanilla sky
        int palette = Math.max(0, Math.min(HORIZON_BY_PALETTE.length - 1, look.palette()));
        Vec3 horizon = HORIZON_BY_PALETTE[palette];
        Vec3 veilHorizon = VEIL_HORIZON_BY_PALETTE[palette];
        float keep = 1.0F - look.cover();
        float veil = look.veil() * 0.75F;
        float night = look.night() * (1.0F - veil);
        float flash = look.flash() * 0.6F;
        fog.x = Math.min(1.0F, (float) (horizon.x * night + veilHorizon.x * veil) + fog.x * keep + flash);
        fog.y = Math.min(1.0F, (float) (horizon.y * night + veilHorizon.y * veil) + fog.y * keep + flash);
        fog.z = Math.min(1.0F, (float) (horizon.z * night + veilHorizon.z * veil) + fog.z * keep + flash);
    }

    /**
     * From client/mixin, as the client level is made: SS-05's own layers on top of its environment attributes. They
     * are read once a tick (and blended between ticks), so the land's light and the clouds follow the sky smoothly.
     */
    public static void addLayers(EnvironmentAttributeSystem.Builder layers) {
        layers.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (value, tick) -> {
            float dark = darkLand();
            return dark <= 0.0F ? value : Mth.lerp(dark, value, Math.min(value, NIGHT_LIGHT));
        });
        layers.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (value, tick) -> {
            float dark = darkLand();
            return dark <= 0.0F ? value : value.lerp(NIGHT_TINT, dark, new Vector3f());
        });
        layers.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) -> {
            SkyLook look = current;
            if (look == null) {
                return value;
            }
            Vector4f c = value.lerp(NIGHT_CLOUDS, look.night(), new Vector4f());
            return c.lerp(ICY_CLOUDS, look.veil() * 0.4F, c).setComponent(3, value.w());
        });
    }

    /** How far the land's daylight is turned down under the dark sky. */
    private static float darkLand() {
        SkyLook look = current;
        return look == null || !HalleyClientConfig.darkenLand() ? 0.0F : look.darkLand();
    }

    /**
     * From client/mixin: ticks to wind the sky's clock on by. While a shader pack is on, SS-05's dark sky is the pack's
     * own night: the clock its sun, moon, stars and light follow is wound on into the night and back during a strike.
     * Only what is drawn changes; the world's real time (on the server) never does.
     */
    public static long clockShift() {
        SkyLook look = current;
        if (look == null || look.timeShift() <= 0.0F || !ShaderPacks.active()) {
            return 0L;
        }
        return (long) look.timeShift();
    }

    static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }
}
