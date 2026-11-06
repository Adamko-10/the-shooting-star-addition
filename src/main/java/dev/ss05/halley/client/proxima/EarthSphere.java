package dev.ss05.halley.client.proxima;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Earth in SA-04 Proxima's film (docs/SS-07-PROXIMA.md, stage 3 part 3): the space shot after the beam fires
 * ({@link ProximaPlan#EARTH} to the cut before the flash), Earth hanging in space behind the gate that catches the beam,
 * from NASA's imagery as The Shooting Star's own space Earth is (art/make_earth.sh): its day side lit from the west
 * with a soft terminator, the night side's city lights, clouds, a blue rim of air. A backdrop, so it is drawn over
 * whatever is behind it (no depth) and only for the caster's film in that shot; placed behind the gate as this
 * frame's camera sees it, and pulled in past the far plane like Luna's moon (only its angle counts).
 */
public final class EarthSphere {
    private static final ByteBufferBuilder DAY_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder LIGHTS_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder CLOUDS_BYTES = new ByteBufferBuilder(1 << 19);
    private static final ByteBufferBuilder AIR_BYTES = new ByteBufferBuilder(1 << 19);
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    /** How big it looks: the sine of its angular radius (16 degrees). */
    private static final double SIZE = Math.sin(Math.toRadians(16.0));
    /** How far behind the gate it hangs, in its own radii. */
    private static final double BEHIND = 1.8;
    private static final int LAT = 48;
    private static final int LON = 96;
    /** The longitude facing the camera: the Atlantic, Europe and Africa toward the night side. */
    private static final double FACING = Math.toRadians(-25.0);

    private EarthSphere() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ProximaFx.active().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (ProximaFx fx : ProximaFx.active()) {
            float t = fx.filmTime(partial);
            if (!Float.isNaN(t) && t >= ProximaPlan.EARTH && t < ProximaPlan.FLASH - 7 && fx.filmWeight() > 0.5F) {
                draw(fx.plan, event.getCamera().getPosition(), minecraft.gameRenderer.getDepthFar() * 0.9, t);
                return;
            }
        }
    }

    private static void draw(ProximaPlan plan, Vec3 camera, double maxDistance, float t) {
        Vec3 toGate = plan.gate().subtract(camera);
        double gateDistance = toGate.length();
        // a little below the gate, so the gate rides high on its disc
        Vec3 dir = toGate.scale(1.0 / gateDistance).subtract(0.0, 0.12, 0.0).normalize();
        double trueRadius = SIZE * gateDistance / (1.0 - BEHIND * SIZE);
        double trueDistance = gateDistance + BEHIND * trueRadius;
        double fits = maxDistance / (1.0 + trueRadius / trueDistance);
        double distance = Math.min(trueDistance, fits);
        double r = trueRadius * distance / trueDistance;
        Vec3 centre = camera.add(dir.scale(distance));

        // its frame as seen: f toward the camera, north up the screen, east to the right; lit from the west
        Vec3 f = dir.scale(-1.0);
        Vec3 north = UP.subtract(f.scale(UP.dot(f))).normalize();
        Vec3 east = north.cross(f);
        Vec3 sun = east.scale(-0.9).add(f.scale(0.3)).add(north.scale(0.2)).normalize();
        // turning slowly eastward
        double facing = FACING - t * 0.0006;

        BufferBuilder day = new BufferBuilder(DAY_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        BufferBuilder lights = new BufferBuilder(LIGHTS_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        BufferBuilder clouds = new BufferBuilder(CLOUDS_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        BufferBuilder air = new BufferBuilder(AIR_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        Frame frame = new Frame(centre, camera, f, north, east, sun, facing, distance);
        for (int i = 0; i < LAT; i++) {
            double lat0 = Math.PI * ((double) i / LAT - 0.5);
            double lat1 = Math.PI * ((double) (i + 1) / LAT - 0.5);
            for (int j = 0; j < LON; j++) {
                double lon0 = Math.PI * 2.0 * j / LON - Math.PI;
                double lon1 = Math.PI * 2.0 * (j + 1) / LON - Math.PI;
                double[][] corners = {{lat0, lon0}, {lat0, lon1}, {lat1, lon1}, {lat1, lon0}};
                // only the cap this camera sees (no depth test: a far-side patch drawn later would show through)
                Vec3 mid = frame.direction((lat0 + lat1) * 0.5, (lon0 + lon1) * 0.5);
                if (mid.dot(f) >= r / distance - 0.03) {
                    for (double[] c : corners) {
                        frame.surface(day, lights, c[0], c[1], r);
                    }
                    for (double[] c : corners) {
                        frame.cloud(clouds, c[0], c[1], r * 1.008);
                    }
                }
                if (mid.dot(f) >= r * 1.035 / distance - 0.03) {
                    for (double[] c : corners) {
                        frame.air(air, c[0], c[1], r * 1.035);
                    }
                }
            }
        }
        MeshData[] meshes = {day.build(), lights.build(), clouds.build(), air.build()};
        RenderType[] types = {HalleyRenderTypes.EARTH_DAY, HalleyRenderTypes.EARTH_LIGHTS, HalleyRenderTypes.EARTH_CLOUDS,
            HalleyRenderTypes.EARTH_AIR};
        for (int i = 0; i < meshes.length; i++) {
            if (meshes[i] != null) {
                types[i].draw(meshes[i]);
            }
        }
    }

    private record Frame(Vec3 centre, Vec3 camera, Vec3 f, Vec3 north, Vec3 east, Vec3 sun, double facing, double distance) {
        /** The unit direction from the centre to a place on Earth. */
        Vec3 direction(double lat, double lon) {
            double cl = Math.cos(lat);
            double a = lon - this.facing;
            return this.f.scale(cl * Math.cos(a)).add(this.east.scale(cl * Math.sin(a))).add(this.north.scale(Math.sin(lat)));
        }

        /** How much daylight a place gets: a soft terminator. */
        float day(Vec3 n) {
            return smooth((n.dot(this.sun) + 0.12) / 0.34);
        }

        void surface(BufferBuilder day, BufferBuilder lights, double lat, double lon, double r) {
            Vec3 n = this.direction(lat, lon);
            float u = (float) ((lon + Math.PI) / (Math.PI * 2.0));
            float v = (float) (0.5 - lat / Math.PI);
            float lit = this.day(n);
            float shade = 0.035F + 0.965F * lit;
            this.vertex(day, n, r, u, v, shade, shade, Math.min(1.0F, shade * 1.03F), 1.0F);
            float night = 1.0F - smooth((n.dot(this.sun) + 0.25) / 0.3);
            this.vertex(lights, n, r, u, v, night * 1.3F, night * 1.05F, night * 0.7F, 1.0F);
        }

        void cloud(BufferBuilder clouds, double lat, double lon, double r) {
            Vec3 n = this.direction(lat, lon);
            float lit = this.day(n);
            float shade = 0.05F + 0.95F * lit;
            this.vertex(clouds, n, r, (float) ((lon + Math.PI) / (Math.PI * 2.0)), (float) (0.5 - lat / Math.PI), shade, shade, shade,
                0.95F * (0.2F + 0.8F * lit));
        }

        /** The air's rim: brightest at the outline on the lit side, nothing face-on. */
        void air(BufferBuilder air, double lat, double lon, double r) {
            Vec3 n = this.direction(lat, lon);
            float rim = (float) Math.pow(Mth.clamp(1.0 - n.dot(this.f), 0.0, 1.0), 3.0);
            float k = rim * (0.2F + 0.8F * smooth((n.dot(this.sun) + 0.3) / 0.6)) * 1.3F;
            this.vertex(air, n, r, 0.5F, 0.5F, 0.32F * k, 0.58F * k, k, 1.0F);
        }

        private void vertex(BufferBuilder out, Vec3 n, double r, float u, float v, float red, float green, float blue, float alpha) {
            Vec3 at = this.centre.add(n.scale(r)).subtract(this.camera);
            out.addVertex((float) at.x, (float) at.y, (float) at.z)
                .setColor(Mth.clamp(red, 0.0F, 1.0F), Mth.clamp(green, 0.0F, 1.0F), Mth.clamp(blue, 0.0F, 1.0F), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal((float) n.x, (float) n.y, (float) n.z);
        }
    }

    private static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }
}
