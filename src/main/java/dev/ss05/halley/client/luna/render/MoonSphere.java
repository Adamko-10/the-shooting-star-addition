package dev.ss05.halley.client.luna.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.DynamicMesh;
import dev.ss05.halley.client.render.HalleyPipelines;
import dev.ss05.halley.client.render.ShaderPacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * SS-06's moon as a solid, lit, textured sphere: {@code surface.png} for the albedo, {@code seams.png} (an emissive
 * RGBA mask) for the molten cracks on top. Unlike SS-05's comet ({@code CometRenderer}), which draws additive glow
 * with its own procedural shader, this needs real textures, so it has its own tiny pipeline pair ({@code halley_moon},
 * {@link HalleyPipelines#MOON} / {@link HalleyPipelines#MOON_EMISSIVE}): a plain texture-times-vertex-colour shader
 * that never reads Minecraft's Fog uniform, so the sphere is never fogged out while it's drawn pulled toward the
 * camera at a faked distance (see the class doc below on {@link #placement}).
 *
 * <p>While a shader pack is on, Iris drops pipelines it doesn't know, same as SS-05's own light - but unlike that
 * light, this sphere already has real textures, so no atlas needs painting: it is simply submitted a second way,
 * with the same real textures, as glowing-eyes geometry ({@link #submitForShaderPack}), which every shader pack
 * draws.
 *
 * <h2>Never clipped, always the right size</h2>
 * Far away the sphere is drawn pulled toward the camera (like {@code GlowBatch}'s billboards), scaled down to keep
 * exactly the same apparent angular size, so it never crosses the far plane. Its starting apparent size matches the
 * vanilla moon's (a quad at distance 100, half-size 20 - ratio 0.2); as {@link MoonPlan#fall} progresses that ratio
 * blends smoothly into the true {@code radius / distance}, so by touchdown it is drawn at its real world size and
 * position, correctly occluded by (and occluding) the terrain. At rest it shrinks a little more and fades away as
 * the server's block moon takes over ({@link MoonFx#sphereVisibilityAt}).
 */
public final class MoonSphere {
    private static final Identifier SURFACE = HalleyAddon.id("textures/luna/surface.png");
    private static final Identifier SEAMS = HalleyAddon.id("textures/luna/seams.png");
    private static final ByteBufferBuilder BASE_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder SEAM_BYTES = new ByteBufferBuilder(1 << 18);
    private static final DynamicMesh BASE_MESH = new DynamicMesh("SS-06 Luna moon");
    private static final DynamicMesh SEAM_MESH = new DynamicMesh("SS-06 Luna moon seams");

    private static final int LONG_SEGMENTS = 28;
    private static final int LAT_SEGMENTS = 16;
    /**
     * The sphere's starting apparent size, as a ratio of radius/distance. The vanilla moon quad (half-size 20 at
     * distance 100, ratio 0.2) draws much bigger than the disc actually visible in it (the quad's corners are empty
     * sky), so the disc you actually see is about half that: ratio 0.1.
     */
    private static final double START_RATIO = 0.1;
    /** How many times the molten-seam texture tiles across the sphere (equirectangular, wider than tall tiling
     * keeps roughly square cells). */
    private static final int SEAM_TILE_U = 4;
    private static final int SEAM_TILE_V = 2;
    /** A fixed "sun" direction for the sphere's own faked shading: gentle limb darkening only. */
    private static final Vec3 LIGHT_DIR = new Vec3(0.42, 0.82, 0.39).normalize();

    private MoonSphere() {
    }

    /** Where and how big to draw the moon this frame; {@code radius <= 0} once it has fully faded away. */
    public record Placement(Vec3 centre, double radius, Vec3 axis, double spin, float crack, float burn) {
    }

    /** A camera-relative vertex: position, uv on the sphere's own textures, colour (the baked lighting/alpha). */
    private interface Sink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);

        Sink NONE = (x, y, z, u, v, r, g, b, a) -> {
        };
    }

    /**
     * Far away the sphere is drawn pulled toward the camera and scaled to keep a faked apparent size that starts at
     * {@link #START_RATIO} and grows, as {@link MoonPlan#fall} progresses, toward {@code endRatio} - the ratio the
     * moon will actually have once it reaches {@code contactCentre}. The blend is geometric, so it only ever grows.
     * The moment the moon's true size/position would already look at least as big as that curve, this switches to
     * the true ratio and the true, unpulled position - exactly real size and place once close.
     */
    public static Placement placement(MoonFx fx, Vec3 camera, double maxDistance, float partial) {
        MoonPlan p = fx.plan;
        double t = fx.time(partial);
        Vec3 trueCentre = p.centre(t);
        double trueDistance = Math.max(1.0, trueCentre.distanceTo(camera));
        double trueRadius = p.params.moonRadius();
        double trueRatio = trueRadius / trueDistance;

        double endDistance = Math.max(1.0, camera.distanceTo(p.contactCentre));
        double endRatio = Math.max(trueRadius / endDistance, 1.0E-6);
        double fall = MoonPlan.fall(t);
        double displayRatio = START_RATIO * Math.pow(endRatio / START_RATIO, fall);
        // a cinematic exaggeration before contact, so it swells and rushes in rather than just sliding closer;
        // collapsed back to 1 in CONTACT's first few ticks, hidden by the white flash and the impact frames
        double exaggeration = t < MoonPlan.CONTACT ? 1.0 + 1.4 * Math.pow(fall, 1.6)
            : Mth.lerp(Mth.clamp((t - MoonPlan.CONTACT) / 4.0, 0.0, 1.0), 2.4, 1.0);
        displayRatio *= exaggeration;

        double distance;
        double radius;
        if (trueRatio >= displayRatio) {
            distance = trueDistance;
            radius = trueRadius;
        } else {
            distance = Math.min(trueDistance, maxDistance);
            radius = displayRatio * distance;
        }
        radius = Math.max(0.0, radius - MoonFx.radiusShrinkAt(t)) * fx.sphereVisibilityAt(t);

        Vec3 dir = trueCentre.subtract(camera);
        double dl = dir.length();
        dir = dl < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : dir.scale(1.0 / dl);
        Vec3 drawCentre = camera.add(dir.scale(distance));
        Vec3 axis = new Vec3(-p.travel.z, 0.0, p.travel.x);
        float flicker = 0.82F + 0.18F * (float) Math.sin(t * 1.7 + (p.seed & 0xFF));
        return new Placement(drawCentre, radius, axis, p.spin(t), MoonPlan.crack(t) * flicker, MoonPlan.burn(t));
    }

    private static List<Placement> placements(Vec3 camera, double maxDistance, float partial) {
        List<Placement> out = new ArrayList<>();
        for (MoonFx fx : MoonFx.active()) {
            if (fx.finished()) {
                continue;
            }
            Placement placement = placement(fx, camera, maxDistance, partial);
            if (placement.radius() > 0.05) {
                out.add(placement);
            }
        }
        return out;
    }

    /** Fabric's {@code LevelRenderEvents.END_MAIN}, registered before {@code MoonRenderer}'s glow so the glow's
     * depth test already sees this sphere. Without a shader pack: its own render pass, its own pipelines. */
    public static void renderSolid(LevelRenderContext context) {
        if (MoonFx.active().isEmpty() || ShaderPacks.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        CameraRenderState state = context.levelState().cameraRenderState;
        Vec3 camera = state.pos;
        double maxDistance = state.depthFar * 0.9;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        List<Placement> placements = placements(camera, maxDistance, partial);
        if (placements.isEmpty()) {
            return;
        }

        BufferBuilder base = new BufferBuilder(BASE_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder seam = new BufferBuilder(SEAM_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Sink baseSink = (x, y, z, u, v, r, g, b, a) -> base.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        Sink seamSink = (x, y, z, u, v, r, g, b, a) -> seam.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        for (Placement placement : placements) {
            build(baseSink, seamSink, camera, placement);
        }

        boolean drawBase = BASE_MESH.upload(base.build());
        boolean drawSeam = SEAM_MESH.upload(seam.build());
        if (!drawBase && !drawSeam) {
            return;
        }
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        AbstractTexture baseTex = minecraft.getTextureManager().getTexture(SURFACE);
        AbstractTexture seamTex = minecraft.getTextureManager().getTexture(SEAMS);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-06 Luna moon",
            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (drawBase) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON));
                pass.setUniform("Sampler0", baseTex.getTextureView(), baseTex.getSampler());
                BASE_MESH.draw(pass);
            }
            if (drawSeam) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON_EMISSIVE));
                pass.setUniform("Sampler0", seamTex.getTextureView(), seamTex.getSampler());
                SEAM_MESH.draw(pass);
            }
        }
    }

    /**
     * Fabric's {@code LevelRenderEvents.COLLECT_SUBMITS}, while a shader pack is on: the same sphere, with its own
     * real textures (no atlas needed, unlike the glow), submitted as glowing-eyes geometry, which every shader pack
     * draws adding its own light.
     */
    public static void submitForShaderPack(LevelRenderContext context) {
        if (MoonFx.active().isEmpty() || !ShaderPacks.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        CameraRenderState state = context.levelState().cameraRenderState;
        Vec3 camera = state.pos;
        double maxDistance = state.depthFar * 0.9;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        List<Placement> placements = placements(camera, maxDistance, partial);
        if (placements.isEmpty()) {
            return;
        }
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.eyes(SURFACE), (pose, out) -> {
            Sink sink = (x, y, z, u, v, r, g, b, a) -> out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
            for (Placement placement : placements) {
                build(sink, Sink.NONE, camera, placement);
            }
        });
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.eyes(SEAMS), (pose, out) -> {
            Sink sink = (x, y, z, u, v, r, g, b, a) -> out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
            for (Placement placement : placements) {
                build(Sink.NONE, sink, camera, placement);
            }
        });
    }

    private static void build(Sink base, Sink seam, Vec3 camera, Placement placement) {
        Vec3 centre = placement.centre();
        double r = placement.radius();
        Vec3 axis = placement.axis();
        double angle = placement.spin();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float crackAlpha = placement.crack();
        float burn = placement.burn();

        for (int i = 0; i < LAT_SEGMENTS; i++) {
            double lat0 = Math.PI * ((double) i / LAT_SEGMENTS - 0.5);
            double lat1 = Math.PI * ((double) (i + 1) / LAT_SEGMENTS - 0.5);
            for (int j = 0; j < LONG_SEGMENTS; j++) {
                double lon0 = Math.PI * 2.0 * (double) j / LONG_SEGMENTS;
                double lon1 = Math.PI * 2.0 * (double) (j + 1) / LONG_SEGMENTS;
                vertex(base, seam, camera, centre, r, axis, cos, sin, lat0, lon0, crackAlpha, burn);
                vertex(base, seam, camera, centre, r, axis, cos, sin, lat0, lon1, crackAlpha, burn);
                vertex(base, seam, camera, centre, r, axis, cos, sin, lat1, lon1, crackAlpha, burn);
                vertex(base, seam, camera, centre, r, axis, cos, sin, lat1, lon0, crackAlpha, burn);
            }
        }
    }

    private static void vertex(Sink base, Sink seam, Vec3 camera, Vec3 centre, double r, Vec3 axis,
                               double cos, double sin, double lat, double lon, float crackAlpha, float burn) {
        double cl = Math.cos(lat);
        Vec3 local = new Vec3(cl * Math.cos(lon), Math.sin(lat), cl * Math.sin(lon));
        // Rodrigues' rotation: local spun about axis by the sphere's own turn
        Vec3 rotated = local.scale(cos).add(axis.cross(local).scale(sin)).add(axis.scale(axis.dot(local) * (1.0 - cos)));
        Vec3 world = centre.add(rotated.scale(r));
        float x = (float) (world.x - camera.x);
        float y = (float) (world.y - camera.y);
        float z = (float) (world.z - camera.z);
        float u = (float) (lon / (Math.PI * 2.0));
        float v = (float) (0.5 - lat / Math.PI);

        // bright and self-lit, like the vanilla moon - only a gentle limb darkening, warming toward the leading
        // edge's colour as it burns through the atmosphere
        float limb = 0.86F + 0.14F * (float) Math.max(0.0, rotated.dot(LIGHT_DIR));
        float r1 = Math.min(1.0F, limb + burn * 0.22F);
        float g1 = Math.min(1.0F, limb - burn * (limb - 0.75F) * 0.5F);
        float b1 = Math.max(0.0F, limb - burn * limb * 0.6F);
        base.vertex(x, y, z, u, v, r1, g1, b1, 1.0F);

        // the crack mask tiles several times over the sphere, so the molten network spreads over the whole visible
        // surface instead of showing once (see SEAM_TILE_U/V)
        float su = frac(u * SEAM_TILE_U);
        float sv = frac(v * SEAM_TILE_V);
        seam.vertex(x, y, z, su, sv, 1.0F, 1.0F, 1.0F, crackAlpha);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /** When the world is left: the GPU buffers go (they come back with the next strike). */
    public static void release() {
        BASE_MESH.close();
        SEAM_MESH.close();
    }
}
