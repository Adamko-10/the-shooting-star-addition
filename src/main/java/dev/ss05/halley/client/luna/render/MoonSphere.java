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
 * SS-06's moon as a solid, lit, textured sphere: {@code surface.png} for the albedo, {@code seams.png} (an emissive
 * RGBA mask) for the molten cracks on top, and {@code glow.png} for a glowing shell of heat over its leading face as
 * it comes down. Unlike SS-05's comet ({@code CometRenderer}), which draws additive glow with its own procedural
 * shader, this needs real textures, so it has its own tiny pipeline pair ({@code halley_moon},
 * {@link HalleyPipelines#MOON} / {@link HalleyPipelines#MOON_EMISSIVE}): a plain texture-times-vertex-colour shader
 * that never reads Minecraft's Fog uniform, so the sphere is never fogged out while it's drawn pulled toward the
 * camera at a faked distance (see the class doc below on {@link #placement}). The surface is solid and full-bright,
 * lit only by its own shading below - unlike lit entity geometry, which left it a dull brown at night.
 *
 * <p>While a shader pack is on, Iris drops pipelines it doesn't know, same as SS-05's own light - but unlike that
 * light, this sphere already has real textures, so no atlas needs painting: it is simply submitted a second way, with
 * the same real textures, the surface the beacon-beam way and the seams/glow as glowing-eyes geometry
 * ({@link #submitForShaderPack}), which every shader pack draws.
 *
 * <h2>Always the same size</h2>
 * Far away the sphere is drawn pulled toward the camera (like {@code GlowBatch}'s billboards), scaled down to keep
 * exactly the same apparent angular size, so it never crosses the far plane (never further than
 * {@link #PULLED_IN}, and never above the cloud layer, which would otherwise poke through its gaps -
 * {@link #underClouds}). Its starting apparent size matches the vanilla moon's disc; as {@link MoonPlan#fall}
 * progresses that grows steadily into the true {@code radius / distance}, and from the moment the true size is the
 * bigger one it is drawn at its real size and position, correctly occluded by (and occluding) the terrain. It only
 * ever grows on its way down - never swollen past its real size and shrunk back as it lands. At rest it fades away as
 * the server's block moon takes over ({@link MoonFx#sphereVisibilityAt}).
 *
 * <h2>Heating up</h2>
 * As it falls the face it leads with warms - a faint glow round its rim as it tears loose, orange as it nears the
 * atmosphere, white-hot through entry - and its molten seams burn brighter; its surface is rugged (craters dented into
 * its outline) while it falls and settles to the true sphere the block moon is built as.
 */
public final class MoonSphere {
    private static final Identifier SURFACE = HalleyAddon.id("textures/luna/surface.png");
    private static final Identifier SEAMS = HalleyAddon.id("textures/luna/seams.png");
    private static final Identifier GLOW = HalleyAddon.id("textures/luna/glow.png");
    private static final ByteBufferBuilder BASE_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder SEAM_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder HEAT_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder SEAM_GLOW_BYTES = new ByteBufferBuilder(1 << 19);
    private static final DynamicMesh BASE_MESH = new DynamicMesh("SS-06 Luna moon");
    private static final DynamicMesh SEAM_MESH = new DynamicMesh("SS-06 Luna moon seams");
    private static final DynamicMesh HEAT_MESH = new DynamicMesh("SS-06 Luna moon heat");
    private static final DynamicMesh SEAM_GLOW_MESH = new DynamicMesh("SS-06 Luna moon seam glow");

    /** The fewest and the most latitude bands (longitude has about twice as many): more when it looms large. */
    private static final int MIN_LAT = 16;
    private static final int MAX_LAT = 40;
    /**
     * The sphere's starting apparent size, as a ratio of radius/distance. The vanilla moon quad (half-size 20 at
     * distance 100, ratio 0.2) draws much bigger than the disc actually visible in it (the quad's corners are empty
     * sky), so the disc you actually see is about half that: ratio 0.1.
     */
    private static final double START_RATIO = 0.1;
    /** The most the falling moon is ever drawn at while still pulled in, as radius over distance. */
    private static final double MAX_DISPLAY_RATIO = 0.75;
    /** The furthest the moon is drawn while it is pulled in (it is the same size on screen wherever it is drawn). */
    private static final double PULLED_IN = 256.0;
    /** The nearest it is drawn while pulled in, however low the clouds (see {@link #underClouds}). */
    private static final double PULLED_IN_MIN = 24.0;
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
     * Where and how big to draw the moon this frame; {@code radius <= 0} once it has fully faded away. {@code heading}
     * is the way it is travelling, {@code approach} 0..1 how far down it has come, {@code relief} 0..1 how rugged its
     * outline is still drawn.
     */
    public record Placement(Vec3 centre, double radius, Vec3 axis, double spin, float crack, float burn, Vec3 heading,
                            float approach, float relief) {
    }

    /**
     * Far away the sphere is drawn pulled toward the camera and scaled to an apparent size that starts at
     * {@link #START_RATIO} and grows, as {@link MoonPlan#fall} progresses, toward the ratio the moon will actually have
     * once it reaches {@code contactCentre} (computed fresh each frame, so a moving camera still sees a steady
     * approach). The blend is geometric (an equal factor each step), so it only ever grows; whichever of that and the
     * true size is bigger is drawn, and the true one wins smoothly as it closes in - at the true position, so it is
     * exactly real size and place from then on (and always from {@link MoonPlan#CONTACT}).
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
        double displayRatio = Math.min(START_RATIO * Math.pow(endRatio / START_RATIO, fall), MAX_DISPLAY_RATIO);

        double distance;
        double radius;
        if (t >= MoonPlan.CONTACT || trueRatio >= displayRatio) {
            distance = trueDistance;
            radius = trueRadius;
        } else {
            distance = Math.min(trueDistance, Math.min(maxDistance, PULLED_IN));
            distance = underClouds(camera, trueCentre, distance, displayRatio, partial);
            radius = displayRatio * distance;
        }
        radius = Math.max(0.0, radius - MoonFx.radiusShrinkAt(t)) * fx.sphereVisibilityAt(t);

        Vec3 dir = trueCentre.subtract(camera);
        double dl = dir.length();
        dir = dl < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : dir.scale(1.0 / dl);
        Vec3 drawCentre = camera.add(dir.scale(distance));
        Vec3 axis = new Vec3(-p.travel.z, 0.0, p.travel.x);
        float flicker = 0.85F + 0.15F * (float) Math.sin(t * 1.7 + (p.seed & 0xFF));
        float approach = (float) Math.pow(fall, 0.8);

        // once the block moon has taken over, the rendered shell cross-fades into a glowing, cooling molten look
        // (orange-white dying down to gold) instead of just popping out - see the class doc
        int handover = fx.handoverAt();
        double cooling = handover < 0 ? 0.0 : Mth.clamp(1.0 - (t - handover) / MoonFx.COOLING_TICKS, 0.0, 1.0);
        float heat = (float) Math.max(MoonPlan.burn(t), cooling);
        float relief = (float) (1.0 - MoonPlan.settle(t));
        return new Placement(drawCentre, radius, axis, p.spin(t), MoonPlan.crack(t) * flicker, heat, MoonFx.headingAt(p, t),
            approach, relief);
    }

    /**
     * The clouds are one big plane whose depth is written all over, the gaps between them too, so whatever is drawn
     * above their layer only shows where a cloud is drawn (the vanilla moon is part of the sky, drawn without depth, so
     * it never meets this). While it is pulled in the moon may be drawn at any distance, so from under the clouds it is
     * drawn just below them. {@code ratio} is its apparent radius/distance (its screen size doesn't change with distance
     * while it's pulled in, so the clearance has to account for it).
     */
    private static double underClouds(Vec3 camera, Vec3 trueCentre, double distance, double ratio, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return distance;
        }
        double clouds = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, partial);
        Vec3 dir = trueCentre.subtract(camera).normalize();
        if (Double.isNaN(clouds) || camera.y >= clouds || dir.y + ratio <= 1.0E-3) {
            return distance;
        }
        // its top (centre + radius) four blocks under the layer
        double fits = (clouds - 4.0 - camera.y) / (dir.y + ratio);
        return Math.max(PULLED_IN_MIN, Math.min(distance, fits));
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
        BufferBuilder heat = new BufferBuilder(HEAT_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder seamGlow = new BufferBuilder(SEAM_GLOW_BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Sinks sinks = new Sinks(
            (x, y, z, u, v, r, g, b, a) -> base.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            (x, y, z, u, v, r, g, b, a) -> seam.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            (x, y, z, u, v, r, g, b, a) -> heat.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a),
            (x, y, z, u, v, r, g, b, a) -> seamGlow.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a));
        for (Placement placement : placements) {
            // its own pipelines have culling off already, so one winding (no mirrored back face) is enough here -
            // unlike submitForShaderPack's vanilla eyes types, which are one-sided
            build(sinks, camera, placement, false);
        }

        boolean drawBase = BASE_MESH.upload(base.build());
        boolean drawSeam = SEAM_MESH.upload(seam.build());
        boolean drawHeat = HEAT_MESH.upload(heat.build());
        boolean drawSeamGlow = SEAM_GLOW_MESH.upload(seamGlow.build());
        if (!drawBase && !drawSeam && !drawHeat && !drawSeamGlow) {
            return;
        }
        var target = minecraft.gameRenderer.mainRenderTarget();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        AbstractTexture baseTex = minecraft.getTextureManager().getTexture(SURFACE);
        AbstractTexture seamTex = minecraft.getTextureManager().getTexture(SEAMS);
        AbstractTexture glowTex = minecraft.getTextureManager().getTexture(GLOW);
        // halley_moon.fsh never reads the Fog uniform (see the class doc), so unlike the vanilla render types this
        // used to rely on, nothing here needs to be switched off to keep the pulled-in sphere from fogging out
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SS-06 Luna moon",
            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (drawBase) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(HalleyPipelines.MOON));
                pass.setUniform("Sampler0", baseTex.getTextureView(), baseTex.getSampler());
                BASE_MESH.draw(pass);
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

    /**
     * Fabric's {@code LevelRenderEvents.COLLECT_SUBMITS}, while a shader pack is on: the same sphere, with its own
     * real textures (no atlas needed, unlike the glow), submitted so every shader pack draws it. The surface goes the
     * beacon-beam way (solid, full-bright, its own shading - like the custom pipeline's base, see the class doc); the
     * seams, their glow and the heat shell go as glowing-eyes geometry (added light). Both are one-sided, so both
     * windings are given here.
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
        submitPart(context, RenderTypes.beaconBeam(SURFACE, false), placements, camera, Part.BASE);
        submitPart(context, RenderTypes.eyes(SEAMS), placements, camera, Part.SEAM);
        submitPart(context, RenderTypes.eyes(SEAMS), placements, camera, Part.SEAM_GLOW);
        submitPart(context, RenderTypes.eyes(GLOW), placements, camera, Part.HEAT);
    }

    private enum Part { BASE, SEAM, SEAM_GLOW, HEAT }

    private static void submitPart(LevelRenderContext context, RenderType type, List<Placement> placements, Vec3 camera, Part part) {
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
                build(sinks, camera, placement, true);
            }
        });
    }

    /** A camera-relative vertex: position, uv on the sphere's own textures, colour (the baked lighting/alpha). */
    private interface Sink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);

        Sink NONE = (x, y, z, u, v, r, g, b, a) -> {
        };
    }

    /** base/seam (opaque albedo, molten crack reveal), heat (the leading-face glow shell) and seamGlow (the seams'
     * own brightening as the moon nears), each a separate additive pass over the base. */
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

        /** {@code seamU} is the u to use at the seam of the texture (0 or 1), so the last column doesn't wrap back. */
        Vertex at(double lat, double lon, double seamU) {
            double cl = Math.cos(lat);
            Vec3 local = new Vec3(cl * Math.cos(lon), Math.sin(lat), cl * Math.sin(lon));
            // Rodrigues' rotation: local spun about the axis by the sphere's own turn
            Vec3 axis = this.placement.axis();
            Vec3 rotated = local.scale(this.cos).add(axis.cross(local).scale(this.sin)).add(axis.scale(axis.dot(local) * (1.0 - this.cos)));
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

    private static void build(Sinks out, Vec3 camera, Placement placement, boolean doubleSided) {
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
            for (int j = 0; j < lon; j++) {
                double lon0 = Math.PI * 2.0 * (double) j / lon;
                double lon1 = Math.PI * 2.0 * (double) (j + 1) / lon;
                Vertex a = frame.at(lat0, lon0, 0.0);
                Vertex b = frame.at(lat0, lon1, 1.0);
                Vertex c = frame.at(lat1, lon1, 1.0);
                Vertex d = frame.at(lat1, lon0, 0.0);
                quad(out, frame, a, b, c, d);
                if (doubleSided) {
                    // the glowing-eyes type is drawn one-sided: give it both sides (the far half is hidden behind
                    // the near one all the same)
                    quad(out, frame, d, c, b, a);
                }
            }
        }
    }

    private static void quad(Sinks out, Frame frame, Vertex a, Vertex b, Vertex c, Vertex d) {
        for (Vertex v : new Vertex[]{a, b, c, d}) {
            vertex(out, frame, v);
        }
    }

    private static void vertex(Sinks out, Frame frame, Vertex v) {
        Placement placement = frame.placement;
        Vec3 n = v.normal();
        float burn = placement.burn();
        float approach = placement.approach();
        // how squarely this point faces the way it is falling (its leading face heats first and most)
        float lead = (float) Math.max(0.0, n.dot(placement.heading()));
        // how close to its outline it is, seen from here (the limb catches the glow of the air round it)
        float rim = (float) Math.pow(1.0 - Math.min(1.0, Math.abs(n.dot(frame.view))), 3.0);
        float heat = Mth.clamp(burn * (0.3F + 0.7F * lead) + approach * approach * 0.3F * lead, 0.0F, 1.0F);

        // bright and self-lit - more so than the vanilla moon, so it's never lost against a dark sky - with a soft
        // terminator so it reads as a ball (and a faint cool "earthshine" on the unlit side, never truly dark), warming
        // toward the leading face's colour as it burns through the atmosphere, or glowing molten-hot during the
        // hand-over to the block moon (see placement)
        float light = 0.74F + 0.26F * (float) Math.max(0.0, n.dot(LIGHT_DIR));
        float hot = Math.min(1.0F, heat * 1.5F);
        float red = Math.min(1.0F, light + hot * 0.3F);
        float green = Math.min(1.0F, Mth.lerp(hot, light, 0.62F + 0.3F * heat));
        float blue = Math.max(0.06F, Mth.lerp(hot, light * 0.97F, 0.22F + 0.35F * Math.max(0.0F, heat - 0.6F)));
        // out.base()/out.seam() are a no-op (Sink.NONE) wherever that channel isn't the one being drawn right now
        // (see build/submitEyes); the custom pipelines draw everything two-sided already (cull off), so the single
        // call there writes once, same as a one-sided vanilla type being given both windings (see quad/build)
        out.base().vertex(v.x(), v.y(), v.z(), v.u(), v.v(), red, green, blue, 1.0F);

        // the crack mask tiles several times over the sphere, so the molten network spreads over the whole visible
        // surface instead of showing once (see SEAM_TILE_U/V); it burns brighter the nearer the moon comes
        float su = v.u() * SEAM_TILE_U;
        float sv = v.v() * SEAM_TILE_V;
        float crack = placement.crack();
        out.seam().vertex(v.x(), v.y(), v.z(), su, sv, 1.0F, 1.0F, 1.0F, crack);
        float seamGlow = crack * (0.45F + 0.4F * approach + 0.3F * burn);
        out.seamGlow().vertex(v.x(), v.y(), v.z(), su, sv, seamGlow, seamGlow * 0.75F, seamGlow * 0.45F, 1.0F);

        // a shell of heat over the leading face, and a glow round the rim that builds the whole way down
        float glow = Mth.clamp(heat * (0.5F + 0.5F * lead) * 0.8F + rim * (0.12F * approach + 0.35F * burn), 0.0F, 1.0F);
        float white = Math.max(0.0F, heat - 0.55F) * 1.6F;
        out.heat().vertex(v.x(), v.y(), v.z(), 0.5F, 0.5F,
            glow, glow * Math.min(1.0F, 0.45F + 0.5F * white), glow * Math.min(1.0F, 0.16F + 0.6F * white * white), 1.0F);
    }

    /** When the world is left: the GPU buffers go (they come back with the next strike). */
    public static void release() {
        BASE_MESH.close();
        SEAM_MESH.close();
        HEAT_MESH.close();
        SEAM_GLOW_MESH.close();
    }
}
