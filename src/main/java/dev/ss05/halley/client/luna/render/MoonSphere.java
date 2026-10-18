package dev.ss05.halley.client.luna.render;

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
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * SS-06's moon as a solid, textured sphere: {@code surface.png} for the albedo, {@code seams.png} (an emissive RGBA
 * mask) for the molten cracks on top, and {@code glow.png} for a shell of fire round its leading edge as it comes down.
 * Unlike SS-05's comet ({@code CometRenderer}), which draws additive glow with its own procedural shader, this needs
 * real textures, so it has its own tiny pipelines ({@code halley_moon}, {@link HalleyPipelines#MOON},
 * {@link HalleyPipelines#MOON_BLEND} and {@link HalleyPipelines#MOON_EMISSIVE}): a plain texture-times-vertex-colour
 * shader that never reads Minecraft's Fog uniform, so the sphere is never fogged out while it's drawn pulled toward the
 * camera (see {@link #placement}). The surface is full-bright, lit only by its own shading below.
 *
 * <p>While a shader pack is on, Iris drops pipelines it doesn't know, same as SS-05's own light - but unlike that
 * light, this sphere already has real textures, so no atlas needs painting: it is simply submitted a second way, with
 * the same real textures, the surface the beacon-beam way and the seams/glow as glowing-eyes geometry
 * ({@link #submitForShaderPack}), which every shader pack draws.
 *
 * <h2>Always its true size</h2>
 * It is drawn exactly as big as it is, where it is ({@link MoonPlan#centre}): it starts ten of its own radii away, so it
 * looks like the moon in the sky whatever its size (the remote's power slider), and only ever grows as it comes down,
 * the same from every camera. Beyond what the camera draws it is pulled in along the line of sight and scaled down by
 * the same factor, which looks exactly the same. Its depth is written, so the terrain and the clouds hide it where they
 * stand in front of it: high up it shows only between the clouds, and once it is under them, in front of them. Where it
 * truly is above the clouds but can't be drawn far enough out to stay behind them (a low moon, a short render
 * distance), it is drawn without writing depth, so the clouds still cover it.
 *
 * <h2>Heating up</h2>
 * While it comes on steadily it is the pale moon, lit from one side, with glowing seams. From {@link MoonPlan#ENTRY} it
 * burns: its leading face turns molten orange (still rock - its craters stay readable), its seams blaze, and a shell of
 * fire wraps its leading edge, brightest at its outline; the plasma round it and the trail behind it are
 * {@link MoonVisuals}'. Its surface is rugged while it falls and settles to the true sphere the block moon is built as.
 *
 * <h2>Handing over to the block moon</h2>
 * At rest it is drawn a block bigger (so the server's blocks don't show through it piece by piece while they are
 * built); once they are all there it crumbles away, patch by patch, while glowing as it cools
 * ({@link MoonFx#sphereVisibilityAt}).
 */
public final class MoonSphere {
    private static final Identifier SURFACE = HalleyAddon.id("textures/luna/surface.png");
    private static final Identifier SEAMS = HalleyAddon.id("textures/luna/seams.png");
    private static final Identifier GLOW = HalleyAddon.id("textures/luna/glow.png");
    private static final ByteBufferBuilder BASE_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder BLEND_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder SEAM_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder HEAT_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder SEAM_GLOW_BYTES = new ByteBufferBuilder(1 << 19);
    private static final DynamicMesh BASE_MESH = new DynamicMesh("SS-06 Luna moon");
    private static final DynamicMesh BLEND_MESH = new DynamicMesh("SS-06 Luna moon, blending");
    private static final DynamicMesh SEAM_MESH = new DynamicMesh("SS-06 Luna moon seams");
    private static final DynamicMesh HEAT_MESH = new DynamicMesh("SS-06 Luna moon heat");
    private static final DynamicMesh SEAM_GLOW_MESH = new DynamicMesh("SS-06 Luna moon seam glow");

    /** The fewest and the most latitude bands (longitude has about twice as many): more when it looms large. */
    private static final int MIN_LAT = 18;
    private static final int MAX_LAT = 44;
    /** How many times the molten-seam texture tiles across the sphere, so its cracks spread over the whole surface
     * instead of appearing once (equirectangular, so a wider tile than tall keeps roughly square cells). */
    private static final int SEAM_TILE_U = 4;
    private static final int SEAM_TILE_V = 2;
    /** Where its light comes from, for its own shading (it is lit by the sun beyond the horizon, as the moon is). */
    private static final Vec3 LIGHT_DIR = new Vec3(0.42, 0.82, 0.39).normalize();
    /** How deep its surface relief is while it falls, as a share of its radius. */
    private static final double RELIEF = 0.022;

    private MoonSphere() {
    }

    /**
     * Where and how big to draw the moon this frame; {@code radius <= 0} once it has gone. {@code heading} is the way it
     * is travelling, {@code approach} 0..1 how far down it has come, {@code relief} 0..1 how rugged its outline is still
     * drawn, {@code solid} 0..1 how much of it is left as it crumbles away at the hand-over, {@code cooling} 0..1 the
     * molten glow of that hand-over, {@code depth} whether it writes depth (see the class doc).
     */
    public record Placement(Vec3 centre, double radius, Vec3 axis, double spin, float crack, float burn, Vec3 heading,
                            float approach, float relief, float solid, float cooling, boolean depth) {
    }

    /** See the class doc: its true place and size, or pulled in past the far plane and scaled down to look the same. */
    public static Placement placement(MoonFx fx, Vec3 camera, double maxDistance, float partial) {
        MoonPlan p = fx.plan;
        double t = fx.time(partial);
        Vec3 trueCentre = p.centre(t);
        double trueRadius = p.params.moonRadius() + MoonFx.restOversizeAt(t);
        Vec3 toMoon = trueCentre.subtract(camera);
        double trueDistance = Math.max(1.0E-3, toMoon.length());
        Vec3 dir = toMoon.scale(1.0 / trueDistance);

        // the furthest it can be drawn whole: its far side inside what the camera draws
        double fits = maxDistance / (1.0 + trueRadius / trueDistance);
        double distance = Math.min(trueDistance, fits);
        double radius = trueRadius * distance / trueDistance;
        boolean depth = trueDistance <= fits || !hiddenByClouds(camera, dir, distance, radius, trueDistance, trueRadius, partial);
        Vec3 drawCentre = camera.add(dir.scale(distance));

        Vec3 axis = new Vec3(-p.travel.z, 0.0, p.travel.x);
        float flicker = 0.85F + 0.15F * (float) Math.sin(t * 1.7 + (p.seed & 0xFF));
        float approach = (float) MoonPlan.fall(t);
        int handover = fx.handoverAt();
        float cooling = handover < 0 ? 0.0F : (float) Mth.clamp(1.0 - (t - handover) / MoonFx.COOLING_TICKS, 0.0, 1.0);
        float relief = (float) (1.0 - MoonPlan.settle(t));
        return new Placement(drawCentre, radius, axis, p.spin(t), MoonPlan.crack(t) * flicker, MoonPlan.burn(t),
            MoonFx.headingAt(p, t), approach, relief, fx.sphereVisibilityAt(t), cooling, depth);
    }

    /**
     * Whether a moon pulled in to {@code distance} has come out in front of the clouds when it truly is behind them -
     * the camera under the clouds, the moon wholly above them, drawn too near to stay above their layer (or the same
     * with the camera above the clouds and the moon under them).
     */
    private static boolean hiddenByClouds(Vec3 camera, Vec3 dir, double distance, double radius, double trueDistance,
                                          double trueRadius, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.getCloudsType() == CloudStatus.OFF) {
            return false;
        }
        double clouds = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, partial);
        if (Double.isNaN(clouds)) {
            return false;
        }
        double trueBottom = camera.y + dir.y * trueDistance - trueRadius;
        double trueTop = camera.y + dir.y * trueDistance + trueRadius;
        double drawnBottom = camera.y + dir.y * distance - radius;
        double drawnTop = camera.y + dir.y * distance + radius;
        if (camera.y < clouds) {
            return trueBottom > clouds && drawnBottom <= clouds;
        }
        return trueTop < clouds && drawnTop >= clouds;
    }

    private static List<Placement> placements(Vec3 camera, double maxDistance, float partial) {
        List<Placement> out = new ArrayList<>();
        for (MoonFx fx : MoonFx.active()) {
            if (fx.finished()) {
                continue;
            }
            Placement placement = placement(fx, camera, maxDistance, partial);
            if (placement.radius() > 0.05 && placement.solid() > 0.002F) {
                out.add(placement);
            }
        }
        return out;
    }

    /** Whether its surface is drawn solid (writing depth) or blending (crumbling away, or kept behind the clouds). */
    private static boolean opaque(Placement placement) {
        return placement.depth() && placement.solid() >= 0.999F;
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
        BufferBuilder blend = new BufferBuilder(BLEND_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder seam = new BufferBuilder(SEAM_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder heat = new BufferBuilder(HEAT_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder seamGlow = new BufferBuilder(SEAM_GLOW_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Sink baseSink = (x, y, z, u, v, r, g, b, a) -> base.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        Sink blendSink = (x, y, z, u, v, r, g, b, a) -> blend.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        Sink seamSink = (x, y, z, u, v, r, g, b, a) -> seam.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        Sink heatSink = (x, y, z, u, v, r, g, b, a) -> heat.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        Sink seamGlowSink = (x, y, z, u, v, r, g, b, a) -> seamGlow.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        for (Placement placement : placements) {
            // its own pipelines have culling off already, so one winding is enough here - unlike submitForShaderPack's
            // vanilla types, which are one-sided
            build(new Sinks(opaque(placement) ? baseSink : blendSink, seamSink, heatSink, seamGlowSink), camera, placement,
                false, time(partial));
        }

        boolean drawBase = BASE_MESH.upload(base.build());
        boolean drawBlend = BLEND_MESH.upload(blend.build());
        boolean drawSeam = SEAM_MESH.upload(seam.build());
        boolean drawHeat = HEAT_MESH.upload(heat.build());
        boolean drawSeamGlow = SEAM_GLOW_MESH.upload(seamGlow.build());
        if (!drawBase && !drawBlend && !drawSeam && !drawHeat && !drawSeamGlow) {
            return;
        }
        var target = minecraft.gameRenderer.mainRenderTarget();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        AbstractTexture baseTex = minecraft.getTextureManager().getTexture(SURFACE);
        AbstractTexture seamTex = minecraft.getTextureManager().getTexture(SEAMS);
        AbstractTexture glowTex = minecraft.getTextureManager().getTexture(GLOW);
        // halley_moon.fsh never reads the Fog uniform (see the class doc), so nothing here needs to be switched off to
        // keep the pulled-in sphere from fogging out
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-06 Luna moon",
            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (drawBase) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON));
                pass.setUniform("Sampler0", baseTex.getTextureView(), baseTex.getSampler());
                BASE_MESH.draw(pass);
            }
            if (drawBlend) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON_BLEND));
                pass.setUniform("Sampler0", baseTex.getTextureView(), baseTex.getSampler());
                BLEND_MESH.draw(pass);
            }
            pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON_EMISSIVE));
            if (drawSeam) {
                pass.setUniform("Sampler0", seamTex.getTextureView(), seamTex.getSampler());
                SEAM_MESH.draw(pass);
            }
            if (drawSeamGlow) {
                pass.setUniform("Sampler0", seamTex.getTextureView(), seamTex.getSampler());
                SEAM_GLOW_MESH.draw(pass);
            }
            if (drawHeat) {
                pass.setUniform("Sampler0", glowTex.getTextureView(), glowTex.getSampler());
                HEAT_MESH.draw(pass);
            }
        }
    }

    /** The strike time the seams throb to (any running moonfall's; they only differ in phase). */
    private static float time(float partial) {
        List<MoonFx> active = MoonFx.active();
        return active.isEmpty() ? 0.0F : active.get(0).time(partial);
    }

    /**
     * Fabric's {@code LevelRenderEvents.COLLECT_SUBMITS}, while a shader pack is on: the same sphere, with its own
     * real textures (no atlas needed, unlike the glow), submitted so every shader pack draws it. The surface goes the
     * beacon-beam way (solid, full-bright, its own shading - like the custom pipeline's base, see the class doc; the
     * translucent beam while it blends); the seams, their glow and the heat shell go as glowing-eyes geometry (added
     * light). Both are one-sided, so both windings are given here.
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
        float time = time(partial);
        List<Placement> solid = placements.stream().filter(MoonSphere::opaque).toList();
        List<Placement> blending = placements.stream().filter(p -> !opaque(p)).toList();
        submitPart(context, RenderTypes.beaconBeam(SURFACE, false), solid, camera, Part.BASE, time);
        submitPart(context, RenderTypes.beaconBeam(SURFACE, true), blending, camera, Part.BASE, time);
        submitPart(context, RenderTypes.eyes(SEAMS), placements, camera, Part.SEAM, time);
        submitPart(context, RenderTypes.eyes(SEAMS), placements, camera, Part.SEAM_GLOW, time);
        submitPart(context, RenderTypes.eyes(GLOW), placements, camera, Part.HEAT, time);
    }

    private enum Part { BASE, SEAM, SEAM_GLOW, HEAT }

    private static void submitPart(LevelRenderContext context, RenderType type, List<Placement> placements, Vec3 camera, Part part,
                                   float time) {
        if (placements.isEmpty()) {
            return;
        }
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), type, (pose, out) -> {
            Sink sink = (x, y, z, u, v, r, g, b, a) -> out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
            Sinks sinks = switch (part) {
                case BASE -> new Sinks(sink, Sink.NONE, Sink.NONE, Sink.NONE);
                case SEAM -> new Sinks(Sink.NONE, sink, Sink.NONE, Sink.NONE);
                case SEAM_GLOW -> new Sinks(Sink.NONE, Sink.NONE, Sink.NONE, sink);
                case HEAT -> new Sinks(Sink.NONE, Sink.NONE, sink, Sink.NONE);
            };
            for (Placement placement : placements) {
                build(sinks, camera, placement, true, time);
            }
        });
    }

    /** A camera-relative vertex: position, uv on the sphere's own textures, colour (the baked lighting/alpha). */
    private interface Sink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);

        Sink NONE = (x, y, z, u, v, r, g, b, a) -> {
        };
    }

    /** base/seam (the albedo, the molten cracks), heat (the shell of fire round its leading edge) and seamGlow (the
     * seams' own light), each drawn as its own layer over the base. */
    private record Sinks(Sink base, Sink seam, Sink heat, Sink seamGlow) {
    }

    /** One sphere point, worked out once per quad corner. */
    private record Vertex(float x, float y, float z, float u, float v, Vec3 normal, double lon) {
    }

    /** What every vertex of one moon this frame needs. */
    private static final class Frame {
        final Placement placement;
        final Vec3 camera;
        final Vec3 view;
        final double cos;
        final double sin;

        Frame(Placement placement, Vec3 camera, Vec3 view) {
            this.placement = placement;
            this.camera = camera;
            this.view = view;
            this.cos = Math.cos(placement.spin());
            this.sin = Math.sin(placement.spin());
        }

        /** The unit direction from the centre of a sphere point, spun with it. */
        Vec3 direction(double lat, double lon) {
            double cl = Math.cos(lat);
            return this.spun(new Vec3(cl * Math.cos(lon), Math.sin(lat), cl * Math.sin(lon)));
        }

        /** Rodrigues' rotation: a direction spun about the axis by the sphere's own turn. */
        private Vec3 spun(Vec3 local) {
            Vec3 axis = this.placement.axis();
            return local.scale(this.cos).add(axis.cross(local).scale(this.sin)).add(axis.scale(axis.dot(local) * (1.0 - this.cos)));
        }

        /** {@code seamU} is the u to use at the seam of the texture (0 or 1), so the last column doesn't wrap back. */
        Vertex at(double lat, double lon, double seamU) {
            double cl = Math.cos(lat);
            Vec3 local = new Vec3(cl * Math.cos(lon), Math.sin(lat), cl * Math.sin(lon));
            Vec3 rotated = this.spun(local);
            double r = this.placement.radius() * (1.0 + RELIEF * this.placement.relief() * relief(local));
            Vec3 world = this.placement.centre().add(rotated.scale(r));
            float u = (float) (lon / (Math.PI * 2.0));
            if (u >= 0.999F) {
                u = (float) seamU;
            }
            return new Vertex((float) (world.x - this.camera.x), (float) (world.y - this.camera.y), (float) (world.z - this.camera.z),
                u, (float) (0.5 - lat / Math.PI), rotated, lon);
        }
    }

    /**
     * -1..1: how far the surface stands proud of (or is dented into) the true sphere at this point (its own, unspun
     * direction): broad uplands and basins and a scatter of craters, the same every frame.
     */
    private static double relief(Vec3 d) {
        double broad = 0.5 * Math.sin(d.x * 3.1 + 0.7) * Math.sin(d.y * 2.7 - 0.3) + 0.3 * Math.sin(d.z * 4.3 + d.x * 1.9);
        double fine = 0.25 * Math.sin(d.x * 9.7 + d.z * 4.1) * Math.sin(d.y * 8.3 + 1.1);
        // craters: shallow pits with a lip, round a few fixed directions
        double craters = 0.0;
        for (int i = 0; i < CRATERS.length; i += 4) {
            double dot = d.x * CRATERS[i] + d.y * CRATERS[i + 1] + d.z * CRATERS[i + 2];
            double size = CRATERS[i + 3];
            double k = (1.0 - dot) / size;
            if (k < 1.6) {
                craters += k < 1.0 ? -(1.0 - k * k) : 0.35 * Math.sin((k - 1.0) / 0.6 * Math.PI);
            }
        }
        return Mth.clamp(broad + fine + 0.8 * craters, -1.0, 1.0);
    }

    /** Crater centres (unit vectors) and sizes (1 - cos of their angular radius). */
    private static final double[] CRATERS = craters();

    private static double[] craters() {
        double[] out = new double[11 * 4];
        long h = 0x6C756E61L;
        for (int i = 0; i < out.length; i += 4) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            double z = ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
            h = h * 6364136223846793005L + 1442695040888963407L;
            double a = ((h >>> 11) * 0x1.0p-53) * Math.PI * 2.0;
            double s = Math.sqrt(1.0 - z * z);
            out[i] = s * Math.cos(a);
            out[i + 1] = z;
            out[i + 2] = s * Math.sin(a);
            h = h * 6364136223846793005L + 1442695040888963407L;
            out[i + 3] = 0.006 + ((h >>> 11) * 0x1.0p-53) * 0.03;
        }
        return out;
    }

    private static void build(Sinks out, Vec3 camera, Placement placement, boolean doubleSided, float time) {
        Vec3 centre = placement.centre();
        double r = placement.radius();
        double ratio = r / Math.max(1.0, centre.distanceTo(camera));
        int lat = Mth.clamp((int) Math.round(MIN_LAT + ratio * 60.0), MIN_LAT, MAX_LAT);
        int lon = lat * 2 - 4;
        Vec3 view = centre.subtract(camera).normalize();
        Frame frame = new Frame(placement, camera, view);

        for (int i = 0; i < lat; i++) {
            double lat0 = Math.PI * ((double) i / lat - 0.5);
            double lat1 = Math.PI * ((double) (i + 1) / lat - 0.5);
            double latMid = (lat0 + lat1) * 0.5;
            for (int j = 0; j < lon; j++) {
                double lon0 = Math.PI * 2.0 * (double) j / lon;
                double lon1 = Math.PI * 2.0 * (double) (j + 1) / lon;
                // only the half facing the camera: while it blends (writing no depth) its far half would otherwise
                // show through
                Vec3 mid = frame.direction(latMid, (lon0 + lon1) * 0.5);
                Vec3 at = centre.add(mid.scale(r));
                if (mid.dot(at.subtract(camera)) > r * 0.02) {
                    continue;
                }
                // crumbling away at the hand-over: each patch goes at its own moment, so it breaks up rather than fades
                float left = crumble(placement.solid(), i * 131 + j * 977);
                if (left <= 0.0F) {
                    continue;
                }
                Vertex a = frame.at(lat0, lon0, 0.0);
                Vertex b = frame.at(lat0, lon1, 1.0);
                Vertex c = frame.at(lat1, lon1, 1.0);
                Vertex d = frame.at(lat1, lon0, 0.0);
                quad(out, frame, d, c, b, a, left, time);
                if (doubleSided) {
                    // the vanilla types are drawn one-sided: give them both windings
                    quad(out, frame, a, b, c, d, left, time);
                }
            }
        }
    }

    /** How much of a patch is left (0..1) when {@code solid} of the moon is: patches go one by one, never all at once. */
    private static float crumble(float solid, int patch) {
        if (solid >= 0.999F) {
            return 1.0F;
        }
        long h = patch * 0x9E3779B97F4A7C15L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        float threshold = (float) ((h >>> 40) * 0x1.0p-24);
        return Mth.clamp((solid - threshold * 0.7F) / 0.3F, 0.0F, 1.0F);
    }

    private static void quad(Sinks out, Frame frame, Vertex a, Vertex b, Vertex c, Vertex d, float left, float time) {
        for (Vertex v : new Vertex[]{a, b, c, d}) {
            vertex(out, frame, v, left, time);
        }
    }

    private static void vertex(Sinks out, Frame frame, Vertex v, float left, float time) {
        Placement placement = frame.placement;
        Vec3 n = v.normal();
        float burn = placement.burn();
        float approach = placement.approach();
        float cooling = placement.cooling();
        // how squarely this point faces the way it is falling (its leading face heats first and most)
        float lead = (float) Math.max(0.0, n.dot(placement.heading()));
        // how close to its outline it is, seen from here (the limb catches the fire of the air round it)
        float facing = (float) Math.min(1.0, Math.abs(n.dot(frame.view)));
        float rim = 1.0F - facing;

        // the pale moon, lit from one side with a soft terminator (never quite dark: a faint earthshine), dimming a
        // little as it burns so the fire round it and in its cracks reads; its leading face glows molten as it burns
        // through the air - tinting the rock, never washing it out, so its craters stay readable
        float sun = (float) Math.max(0.0, n.dot(LIGHT_DIR));
        float light = (0.4F + 0.6F * sun) * (1.0F - 0.3F * burn);
        float molten = Mth.clamp(burn * (float) Math.pow(lead, 1.3) * 1.15F, 0.0F, 1.0F);
        float glowing = Math.max(molten, cooling * 0.85F);
        float red = Math.min(1.0F, Mth.lerp(glowing, light, 1.0F));
        float green = Math.min(1.0F, Mth.lerp(glowing, light * 0.98F, 0.5F + 0.25F * glowing));
        float blue = Mth.lerp(glowing, light * 0.95F, 0.18F + 0.1F * glowing);
        out.base().vertex(v.x(), v.y(), v.z(), v.u(), v.v(), red, green, blue, left);

        // the crack mask tiles several times over the sphere, so the molten network spreads over the whole visible
        // surface instead of showing once (see SEAM_TILE_U/V); it blazes as it burns
        float su = v.u() * SEAM_TILE_U;
        float sv = v.v() * SEAM_TILE_V;
        float crack = Math.max(placement.crack(), cooling);
        out.seam().vertex(v.x(), v.y(), v.z(), su, sv, 1.0F, 1.0F, 1.0F, crack * left);
        float throb = 0.85F + 0.15F * (float) Math.sin(time * 0.9 + v.lon() * 3.0);
        float seamGlow = Mth.clamp(crack * (0.3F + 0.25F * approach + 0.7F * burn * (0.5F + 0.5F * lead) * throb) + cooling * 0.6F,
            0.0F, 1.0F) * left;
        out.seamGlow().vertex(v.x(), v.y(), v.z(), su, sv, seamGlow, seamGlow * 0.72F, seamGlow * 0.38F, 1.0F);

        // a shell of fire round its leading edge: strongest at its outline on the side it leads with, a gentle warmth
        // over the face itself (head-on it is a ring of fire round dark-glowing rock, side-on a burning crescent); before
        // it burns, only a faint pale rim of air lit by it
        float edge = rim * rim;
        float fire = burn * (edge * (0.35F + 0.75F * lead) + 0.22F * molten);
        float air = (1.0F - burn) * 0.12F * edge * (0.4F + 0.6F * approach);
        float shell = Mth.clamp(fire + air + cooling * 0.35F * (0.4F + edge), 0.0F, 1.0F) * left;
        float white = Math.max(0.0F, burn - 0.6F) * 1.8F * lead;
        float cool = 1.0F - burn;
        out.heat().vertex(v.x(), v.y(), v.z(), 0.5F, 0.5F, shell, shell * Math.min(1.0F, 0.5F + 0.35F * white + 0.4F * cool),
            shell * Math.min(1.0F, 0.18F + 0.5F * white * white + 0.5F * cool), 1.0F);
    }

    /** When the world is left: the GPU buffers go (they come back with the next strike). */
    public static void release() {
        BASE_MESH.close();
        BLEND_MESH.close();
        SEAM_MESH.close();
        HEAT_MESH.close();
        SEAM_GLOW_MESH.close();
    }
}
