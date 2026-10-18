package dev.ss05.halley.client.luna.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.WorldFrame;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * SS-06's moon as a solid, textured sphere: {@code surface.png} for the albedo, {@code seams.png} (an emissive RGBA
 * mask) for the molten cracks on top, and a glowing shell of heat round its leading edge as it comes down, all drawn
 * with vanilla render types so shader packs draw it as they draw anything else - no custom shader needed here, unlike
 * the glow round it ({@link MoonVisuals}, {@code halley_glow}).
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
    private static final ResourceLocation SURFACE = HalleyAddon.id("textures/luna/surface.png");
    private static final ResourceLocation SEAMS = HalleyAddon.id("textures/luna/seams.png");
    private static final ResourceLocation GLOW = HalleyAddon.id("textures/luna/glow.png");
    private static final RenderType EMISSIVE = RenderType.entityTranslucentEmissive(SEAMS);
    /** The heat round its leading edge and the seams' own glow: added light (vanilla's glowing-eyes type). */
    private static final RenderType HEAT = RenderType.eyes(GLOW);
    private static final RenderType SEAM_GLOW = RenderType.eyes(SEAMS);
    /**
     * Its surface: solid and full-bright, a beacon's beam's way, lit only by its own shading (below). Entity geometry
     * gets the world's lighting - dull brown at night, and with a shader pack on the pack's own night lighting all but
     * hides it (seen with Complementary Reimagined); packs draw a beacon's beam solid and bright too.
     */
    private static final RenderType BASE = RenderType.beaconBeam(SURFACE, false);
    /** The same, writing no depth and blending: while it crumbles away, and when it must stay behind the clouds. */
    private static final RenderType BASE_BLEND = RenderType.beaconBeam(SURFACE, true);
    private static final ByteBufferBuilder BASE_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder BLEND_BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder SEAM_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder HEAT_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder SEAM_GLOW_BYTES = new ByteBufferBuilder(1 << 19);

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
        boolean depth = trueDistance <= fits || !hiddenByClouds(camera, dir, distance, radius, trueDistance, trueRadius);
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
                                          double trueRadius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.getCloudsType() == CloudStatus.OFF) {
            return false;
        }
        double clouds = minecraft.level.effects().getCloudHeight();
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

    /** Among the entities, solid and depth-tested (see {@code HalleyClient}). */
    public static void renderSolid(WorldFrame frame) {
        if (MoonFx.active().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = frame.camera().getPosition();
        double maxDistance = minecraft.gameRenderer.getDepthFar() * 0.9;
        float partial = frame.partial();

        BufferBuilder base = new BufferBuilder(BASE_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        BufferBuilder blend = new BufferBuilder(BLEND_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        BufferBuilder seam = new BufferBuilder(SEAM_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        BufferBuilder heat = new BufferBuilder(HEAT_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        BufferBuilder seamGlow = new BufferBuilder(SEAM_GLOW_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (MoonFx fx : MoonFx.active()) {
            if (fx.finished()) {
                continue;
            }
            Placement placement = placement(fx, camera, maxDistance, partial);
            if (placement.radius() > 0.05 && placement.solid() > 0.002F) {
                boolean opaque = placement.depth() && placement.solid() >= 0.999F;
                build(new Sinks(opaque ? base : blend, seam, heat, seamGlow), camera, placement, (float) fx.time(partial));
            }
        }
        MeshData[] meshes = {base.build(), blend.build(), seam.build(), seamGlow.build(), heat.build()};
        RenderType[] types = {BASE, BASE_BLEND, EMISSIVE, SEAM_GLOW, HEAT};
        // the moon is drawn pulled toward the camera (or, far off, well beyond the fog's own end) - fog would wash
        // it out to the night fog colour (a faint grey disc) long before that, so it's switched off for this draw
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        try {
            frame.drawInView(() -> {
                for (int i = 0; i < meshes.length; i++) {
                    if (meshes[i] != null) {
                        types[i].draw(meshes[i]);
                    }
                }
            });
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    private record Sinks(BufferBuilder base, BufferBuilder seam, BufferBuilder heat, BufferBuilder seamGlow) {
    }

    private static void build(Sinks out, Vec3 camera, Placement placement, float time) {
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
                // only the half facing the camera: all of its types are drawn one-sided, and while it blends (writing
                // no depth) its far half would otherwise show through
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
                // (d, c, b, a winds counter-clockwise seen from outside: the side the culled types draw)
                for (Vertex v : new Vertex[]{d, c, b, a}) {
                    vertex(out, frame, v, left, time);
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
        out.base().addVertex(v.x(), v.y(), v.z())
            .setColor(red, green, blue, left)
            .setUv(v.u(), v.v())
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal((float) n.x, (float) n.y, (float) n.z);

        // the crack mask tiles several times over the sphere, so the molten network spreads over the whole visible
        // surface instead of showing once (see SEAM_TILE_U/V); it blazes as it burns
        float su = v.u() * SEAM_TILE_U;
        float sv = v.v() * SEAM_TILE_V;
        float crack = Math.max(placement.crack(), cooling);
        out.seam().addVertex(v.x(), v.y(), v.z())
            .setColor(1.0F, 1.0F, 1.0F, crack * left)
            .setUv(su, sv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal((float) n.x, (float) n.y, (float) n.z);
        float throb = 0.85F + 0.15F * (float) Math.sin(time * 0.9 + v.lon() * 3.0);
        float seamGlow = Mth.clamp(crack * (0.3F + 0.25F * approach + 0.7F * burn * (0.5F + 0.5F * lead) * throb) + cooling * 0.6F,
            0.0F, 1.0F) * left;
        out.seamGlow().addVertex(v.x(), v.y(), v.z())
            .setColor(seamGlow, seamGlow * 0.72F, seamGlow * 0.38F, 1.0F)
            .setUv(su, sv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal((float) n.x, (float) n.y, (float) n.z);

        // a shell of fire round its leading edge: strongest at its outline on the side it leads with, a gentle warmth
        // over the face itself (head-on it is a ring of fire round dark-glowing rock, side-on a burning crescent); before
        // it burns, only a faint pale rim of air lit by it
        float edge = rim * rim;
        float fire = burn * (edge * (0.35F + 0.75F * lead) + 0.22F * molten);
        float air = (1.0F - burn) * 0.12F * edge * (0.4F + 0.6F * approach);
        float shell = Mth.clamp(fire + air + cooling * 0.35F * (0.4F + edge), 0.0F, 1.0F) * left;
        float white = Math.max(0.0F, burn - 0.6F) * 1.8F * lead;
        float cool = 1.0F - burn;
        out.heat().addVertex(v.x(), v.y(), v.z())
            .setColor(shell, shell * Math.min(1.0F, 0.5F + 0.35F * white + 0.4F * cool),
                shell * Math.min(1.0F, 0.18F + 0.5F * white * white + 0.5F * cool), 1.0F)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal((float) n.x, (float) n.y, (float) n.z);
    }
}
