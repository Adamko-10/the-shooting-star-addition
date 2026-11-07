package dev.ss05.halley.client.proxima;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.ProximaPlan;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * SA-04 Proxima's film, its space and Earth shots (the approved previz; docs/SS-07-PROXIMA.md): a scene of its own,
 * drawn over the world while they play, from the film's overlay pass (StarClientBridge.renderFilmHud, before the film's
 * captions and title cards, so a shader pack never touches it). With its own camera, run by the film's clock, as the
 * previz's: Earth from orbit at the end of the pull up off the caster's shoulder; Proxima Centauri boiling inside its
 * Dyson sphere, ~980 panels with glowing seams, collector rings and their satellites, prominences arcing out past it;
 * the iris opening from the middle out; two faint plasma loops twisting into an infinity sign, flaring and collapsing;
 * panels flying into a hex lens that charges and fires a beam (a white core, a red sheath, shock rings); Earth again,
 * the gate unfolding in orbit, catching the beam and spreading it into the coronal mass ejection falling to the planet.
 *
 * <p>In the scene's own units: the star's radius is 1 (its Dyson sphere 1.55), Earth's is 1. One shader
 * ({@code proxima_space}), a material per draw (its {@code Mode}). Earth is NASA's and Solar System Scope's imagery (art/make_earth.sh).
 */
public final class ProximaCosmos {
    private static final ResourceLocation EARTH_DAY = HalleyAddon.id("textures/proxima/earth_day.png");
    /** The land's relief, the city lights and the clouds, packed (art/make_earth.sh). */
    private static final ResourceLocation EARTH_DETAIL = HalleyAddon.id("textures/proxima/earth_detail.png");

    // the materials (proxima_space.fsh)
    private static final int BACKDROP = 0;
    private static final int STAR = 1;
    private static final int PANEL = 2;
    private static final int GLOW = 3;
    private static final int BAND = 4;
    private static final int EARTH = 5;
    private static final int CLOUDS = 6;
    private static final int AIR = 7;
    private static final int GATE_DISC = 8;
    private static final int SOLID = 10;
    private static final int TILE = 11;

    /** The way the iris opens (out of the star toward the lens and the camera's side). */
    private static final Vec3 AXIS = new Vec3(0.55, 0.32, 4.6).normalize();
    private static final Vec3 LENS_CENTRE = AXIS.scale(2.35);
    private static final double SHELL = 1.55;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 SUN = new Vec3(-1.0, 0.28, 0.55).normalize();
    /** Where the gate hangs before Earth, and where the beam comes in from. */
    private static final Vec3 GATE = new Vec3(0.62, 0.46, 2.15);
    /** Where the wave streaks in from: high on the right, so the refocused beam (down to the left) reads apart from it. */
    private static final Vec3 BEAM_FROM = GATE.add(new Vec3(1.5, 1.7, 0.2).normalize().scale(30.0));
    /** The caster's spot on Earth (the orbit shot starts over it; the gate's refocused wave lands on it). */
    private static final Vec3 SPOT = new Vec3(-0.62, 0.62, 0.48).normalize();
    /** The film's moments in seconds (ProximaPlan's ticks / 20). */
    private static final double T_ORBIT = ProximaPlan.ORBIT / 20.0;
    private static final double T_WARP = ProximaPlan.WARP / 20.0;
    private static final double T_SPACE = ProximaPlan.SPACE / 20.0;
    private static final double T_IRIS = ProximaPlan.IRIS / 20.0;
    private static final double T_LENS = ProximaPlan.LENS / 20.0;
    private static final double T_EARTH = ProximaPlan.EARTH / 20.0;
    /** The lens: power starting to flow in from the star, the shot, the camera cutting to follow it. */
    private static final double POWER = ProximaPlan.POWER / 20.0;
    private static final double FIRE = ProximaPlan.BEAM / 20.0;
    private static final double FOLLOW = FIRE + 0.15;
    /** How fast the shot flies (scene units a second). */
    private static final double SPEED = 26.0;
    /** The gate catching the shot, and its refocused wave reaching the caster's spot. */
    private static final double CATCH = T_EARTH + 0.75;
    private static final double LANDS = T_EARTH + 1.5;
    /** The gate in the real sky (for everyone not watching the film): its ring's radius (blocks), and how its own units scale. */
    private static final double SKY_GATE_RADIUS = 70.0;
    private static final double SKY_SCALE = SKY_GATE_RADIUS / 0.34;
    private static final double T_FLASH = ProximaPlan.FLASH / 20.0;
    /** This frame's warp (stars drawn out into streaks, 0-1) and the camera's heading, for the backdrop. */
    private static double warp;
    private static Vec3 heading = UP;
    /** Earth's turn, so the Atlantic, Europe and Africa face the camera (as in the previz). */
    private static final double EARTH_TURN = -1.95;

    /** The Dyson sphere's panels: an icosphere's faces (20 x 7 x 7 = 980), corners a, b, c and a number of their own. */
    private static final Vec3[][] PANELS;
    private static final float[] PANEL_SEED;
    /** Prominences: a quadratic arc off the limb each (from, over, to), how thick, its own phase. */
    private static final Vec3[][] PROMINENCES;
    private static final double[] PROMINENCE_WIDTH;
    private static final double[] PROMINENCE_PHASE;
    /** The lens's hex tiles: where each starts on the shell, where it locks in, when it sets off. */
    private static final Vec3[] TILE_START;
    private static final Vec3[] TILE_END;
    private static final double[] TILE_DELAY;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static VertexBuffer starMesh;
    @Nullable
    private static VertexBuffer earthMesh;
    @Nullable
    private static VertexBuffer cloudMesh;
    @Nullable
    private static VertexBuffer airMesh;
    @Nullable
    private static VertexBuffer dynamic;
    /** The texture ids given mipmaps, so a reload (new ids) gets them again. */
    private static final int[] mipmapped = {-1, -1};

    private ProximaCosmos() {
    }

    public static void setShader(@Nullable ShaderInstance loaded) {
        shader = loaded;
    }

    /** How high over Earth (in its radii) the orbit shot is, {@code q} of the way through it. */
    public static double altitude(double q) {
        return 0.5 * Math.pow(4.2, q);
    }

    /** Whether the film is in its space and Earth shots at film tick {@code ct}: the scene covers the world then. */
    public static boolean covers(float ct) {
        return ct >= ProximaPlan.ORBIT && ct < ProximaPlan.FLASH;
    }

    /**
     * For everyone not watching the film (the caster in third person or with films off, and everyone near the mark): the
     * gate (the ring the shot passes through on its way) high over the land, in the world (the user, 2026-11-07: "only the
     * lens ... the thing that the energy passes through", not the star). On the strike's clock: it appears in a flash as
     * the film reaches the lens, unfolds, its ring glowing brighter as the star's power gathers far away; the shot
     * streaks in from deep space, the gate catches it and refocuses it into a narrow beam down onto the mark, landing
     * just before the flare's flash, when it's gone. Drawn after the particles with the world's own depth (land and
     * clouds in front hide it). Not for random flares. Shader packs: not checked.
     */
    public static void renderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        ShaderInstance program = shader;
        Minecraft minecraft = Minecraft.getInstance();
        if (program == null || minecraft.level == null || ProximaFx.active().isEmpty()) {
            return;
        }
        ProximaFx fx = ProximaFx.active().getLast();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double t = fx.time(partial) / 20.0;
        if (fx.plan.params.natural() || fx.filmWeight() > 0.5F || t < T_LENS - 0.3 || t > T_FLASH + 0.5) {
            return;
        }
        Vec3 gate = gatePosition(fx.plan);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        Matrix4f model = new Matrix4f().translate((float) (gate.x - eye.x), (float) (gate.y - eye.y), (float) (gate.z - eye.z))
            .scale((float) SKY_SCALE);
        Matrix4f view = new Matrix4f(event.getModelViewMatrix()).mul(model);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Pass pass = new Pass(view, new Matrix4f(event.getProjectionMatrix()), eye.subtract(gate).scale(1.0 / SKY_SCALE),
            new Vec3(-left.x, -left.y, -left.z), new Vec3(up.x, up.y, up.z), (float) t);
        RenderSystem.disableCull();
        try {
            skyGate(program, pass, t, fx.plan.target.subtract(gate).scale(1.0 / SKY_SCALE));
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    /** Where the gate hangs over the land: 360 blocks up, 180 out toward the side the coronal front will come from. */
    public static Vec3 gatePosition(ProximaPlan plan) {
        Vec3 from = plan.frontDirection().scale(-1.0);
        Vec3 flat = new Vec3(from.x, 0.0, from.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        return plan.target.add(flat.scale(180.0)).add(0.0, 360.0, 0.0);
    }

    /** The gate in the sky, in its own units (its ring 0.34 across), {@code ground}: the mark from it. */
    private static void skyGate(ShaderInstance program, Pass p, double t, Vec3 ground) {
        double open = smooth((t - T_LENS - 0.35) / 0.6);
        double charge = clamp((t - POWER) / (FIRE - POWER));
        Vec3 z = ground.normalize();
        Vec3 x = UP.cross(z).normalize();
        Vec3 y = z.cross(x).normalize();
        double spin = t * 0.15;
        // its panels unfolding into a ring, facing the land
        opaque();
        set(program, p, SOLID, 0.0F, 0.0F, 0.0F, 0.0F);
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        Vec3[][] seamLines = new Vec3[16][];
        for (int i = 0; i < 16; i++) {
            double grow = smooth((t - T_LENS - 0.1 - i * 0.02) / 0.3);
            if (grow <= 0.001) {
                continue;
            }
            double a = i / 16.0 * Math.PI * 2.0 + spin;
            Vec3 out = x.scale(Math.cos(a)).add(y.scale(Math.sin(a)));
            Vec3 along = y.scale(Math.cos(a)).subtract(x.scale(Math.sin(a)));
            double fold = (1.0 - open) * 1.4;
            Vec3 radial = out.scale(Math.cos(fold)).add(z.scale(-Math.sin(fold)));
            Vec3 centre = out.scale(0.39 + 0.12 * (1.0 - open));
            Vec3 hx = radial.scale(0.065 * grow);
            Vec3 hy = along.scale(0.025 * grow);
            Vec3 n = radial.cross(along).normalize();
            vertex(b, centre.subtract(hx).subtract(hy), 0.0F, 0.0F, 0x1C1A1F, 1.0F, n);
            vertex(b, centre.add(hx).subtract(hy), 1.0F, 0.0F, 0x1C1A1F, 1.0F, n);
            vertex(b, centre.add(hx).add(hy), 1.0F, 1.0F, 0x1C1A1F, 1.0F, n);
            vertex(b, centre.subtract(hx).add(hy), 0.0F, 1.0F, 0x1C1A1F, 1.0F, n);
            seamLines[i] = new Vec3[]{centre.subtract(hx).subtract(hy), centre.add(hx).subtract(hy)};
        }
        draw(program, p, b);
        additive();
        b = begin(VertexFormat.Mode.QUADS);
        for (Vec3[] line : seamLines) {
            if (line != null) {
                band(b, p, line, 0.006, 0xFF6090, 1.0F, 1.0);
            }
        }
        // its ring, glowing brighter as the star's power gathers far away
        band(b, p, circle(Vec3.ZERO, x, y, 0.34, 128), 0.024, 0xFF4D80, (float) (open * (0.55 + 0.45 * charge)), 1.0);
        set(program, p, BAND, (float) (1.3 + 1.7 * charge), 0.0F, 0.0F, 0.0F);
        draw(program, p, b);
        // its disc of plasma once the shot is in
        double disc = smooth((t - CATCH) / 0.25) * (1.0 - smooth((t - T_FLASH - 0.2) / 0.3));
        if (disc > 0.002) {
            b = begin(VertexFormat.Mode.QUADS);
            double r = 0.33;
            vertex(b, x.scale(-r).subtract(y.scale(r)), -1.0F, -1.0F, 0xFFFFFF, 1.0F, z);
            vertex(b, x.scale(r).subtract(y.scale(r)), 1.0F, -1.0F, 0xFFFFFF, 1.0F, z);
            vertex(b, x.scale(r).add(y.scale(r)), 1.0F, 1.0F, 0xFFFFFF, 1.0F, z);
            vertex(b, x.scale(-r).add(y.scale(r)), -1.0F, 1.0F, 0xFFFFFF, 1.0F, z);
            set(program, p, GATE_DISC, (float) (0.6 * disc), 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }
        // the shot streaking in from deep space, from above and behind it
        Vec3 from = x.scale(0.45).add(y.scale(0.35)).subtract(z).normalize().scale(30.0);
        if (t > CATCH - 0.6 && t < CATCH + 0.6) {
            double arrive = smooth((t - CATCH + 0.55) / 0.55);
            float fade = (float) (1.0 - smooth((t - CATCH - 0.3) / 0.3));
            Vec3[] line = new Vec3[25];
            for (int s = 0; s <= 24; s++) {
                line[s] = from.scale(1.0 - arrive * s / 24.0);
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.12, 0xB31A33, fade, 1.0);
            band(b, p, line, 0.03, 0xFFD5E6, fade, 1.0);
            set(program, p, BAND, 1.5F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            if (t < CATCH + 0.05) {
                bolt(program, p, from.scale(1.0 - arrive), from.scale(-1.0).normalize(), 0.35, t);
            }
        }
        // refocused into a narrow beam down onto the mark, its bolt racing ahead, a flash where it lands
        double refocus = smooth((t - CATCH - 0.15) / 0.12) * (1.0 - smooth((t - T_FLASH - 0.2) / 0.3));
        if (refocus > 0.0) {
            double reach = smooth((t - CATCH - 0.15) / (LANDS - CATCH - 0.15));
            Vec3[] line = new Vec3[25];
            for (int s = 0; s <= 24; s++) {
                line[s] = ground.scale(reach * s / 24.0);
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.05, 0xB31A33, (float) refocus, 1.0);
            band(b, p, line, 0.015, 0xFFE0EE, (float) refocus, 1.0);
            set(program, p, BAND, 2.0F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            if (reach < 0.999) {
                bolt(program, p, ground.scale(reach), z, 0.2, t);
            }
        }
        b = begin(VertexFormat.Mode.QUADS);
        double landed = pulse(t, LANDS, 0.4);
        if (t > LANDS - 0.05) {
            glow(b, p, ground, 0.2 + 1.2 * landed, 0xFFE6F0, (float) (0.3 + 0.7 * landed));
        }
        // appearing in a flash
        double flash = smooth((t - T_LENS + 0.3) / 0.3) * (1.0 - smooth((t - T_LENS) / 0.6));
        glow(b, p, Vec3.ZERO, 1.2, 0xFFE6EE, (float) flash);
        set(program, p, GLOW, 1.6F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);
    }

    /** The scene's camera for one frame, and what it needs to turn things to face it. */
    private record Pass(Matrix4f view, Matrix4f proj, Vec3 eye, Vec3 right, Vec3 up, float time) {
    }

    /** From the film's overlay pass, before its captions: the space shots, over everything (only while the film shows). */
    public static void render(GuiGraphics graphics, ProximaFx fx, float partial) {
        ShaderInstance program = shader;
        float ct = fx.filmTime(partial);
        if (program == null || Float.isNaN(ct) || !covers(ct) || fx.filmWeight() <= 0.5F) {
            return;
        }
        // whatever the overlay pass has drawn so far goes first, then the scene covers it
        graphics.flush();
        double t = ct / 20.0;
        Vec3 eye;
        Vec3 look;
        double fov;
        double near;
        double far;
        boolean earthScene;
        warp = 0.0;
        if (ct < ProximaPlan.WARP) {
            // shot 2's second half: from over the caster's spot on Earth pulling back until its curve fills the frame
            // (from half its radius up: any closer and its images are blurred)
            double q = clamp((t - T_ORBIT) / (T_WARP - T_ORBIT));
            double alt = altitude(q);
            eye = SPOT.scale(1.0 + alt).add(new Vec3(0.06, -0.03, 0.05).scale(alt));
            look = SPOT.scale(1.0 - smooth(q * 1.25));
            fov = lerp(62.0, 42.0, smooth(q));
            near = 0.0005;
            far = 200.0;
            earthScene = true;
        } else if (ct < ProximaPlan.SPACE) {
            // the zoom out: on away from Earth faster and faster till it is a point of light, the stars drawing out into
            // streaks as the camera leaves for Proxima Centauri
            double w = clamp((t - T_WARP) / (T_SPACE - T_WARP));
            double alt = altitude(1.0);
            Vec3 end = SPOT.scale(1.0 + alt).add(new Vec3(0.06, -0.03, 0.05).scale(alt));
            double out = alt * Math.exp(6.5 * w * w);
            eye = end.scale((1.0 + out) / (1.0 + alt));
            look = Vec3.ZERO;
            fov = lerp(42.0, 64.0, smooth(w));
            near = 0.0005 * (1.0 + out);
            far = Math.max(200.0, 4.0 * (1.0 + out));
            earthScene = true;
            warp = smooth((w - 0.15) / 0.6);
        } else if (ct < ProximaPlan.EARTH) {
            near = 0.01;
            far = 2000.0;
            earthScene = false;
            if (ct < ProximaPlan.IRIS) {
                // 3. Proxima Centauri: arriving out of the warp, the streaks settling, slowing in to a wide drift past
                // the star in its shell
                double e = clamp((t - T_SPACE) / (T_IRIS - T_SPACE));
                double in = 1.0 - Math.pow(1.0 - e, 3.0);
                double a = lerp(-0.35, 0.15, smooth(e));
                double r = lerp(46.0, 7.2, in);
                eye = new Vec3(Math.sin(a) * r, lerp(5.0, 1.0, in), Math.cos(a) * r);
                look = Vec3.ZERO;
                fov = lerp(40.0, 36.0, e);
                warp = 1.0 - smooth(e / 0.3);
            } else if (ct < ProximaPlan.LENS) {
                // 4. the Dyson sphere: closer, square on to the iris as it opens
                double e = smooth((t - T_IRIS) / (T_LENS - T_IRIS));
                eye = AXIS.scale(lerp(6.2, 4.7, e)).add(lerp(0.4, 0.15, e), 0.1, 0.0);
                look = Vec3.ZERO;
                fov = 38.0;
            } else if (t < FOLLOW) {
                // 5. the lens: side on, slowly closing in as it draws the star's power in
                double e = smooth((t - T_LENS) / (FIRE - T_LENS));
                Vec3 side = AXIS.cross(UP).normalize();
                eye = AXIS.scale(lerp(3.4, 3.1, e)).add(side.scale(lerp(4.0, 3.0, e))).add(0.0, lerp(0.9, 0.55, e), 0.0);
                look = AXIS.scale(lerp(2.6, 2.35, e));
                fov = lerp(48.0, 40.0, e);
            } else {
                // 6. the shot: alongside it as it flies, looking just ahead of its head
                Vec3 head = LENS_CENTRE.add(AXIS.scale((t - FIRE) * SPEED));
                Vec3 side = AXIS.cross(UP).normalize();
                Vec3 lift = side.cross(AXIS).normalize();
                double k = smooth((t - FOLLOW) / 0.4);
                eye = head.subtract(AXIS.scale(lerp(2.6, 1.7, k))).add(side.scale(0.9)).add(lift.scale(0.35));
                look = head.add(AXIS.scale(0.6));
                fov = lerp(62.0, 52.0, k);
                warp = 0.3;
            }
        } else {
            // 7. Earth and the gate: Earth from orbit, the gate between us and the planet
            double e = smooth((t - T_EARTH) / 2.0);
            eye = new Vec3(lerp(1.15, 1.0, e), lerp(0.62, 0.55, e), lerp(3.4, 3.15, e));
            look = new Vec3(0.22, 0.24, 0.9);
            fov = lerp(44.0, 40.0, e);
            near = 0.001;
            far = 400.0;
            earthScene = true;
        }

        double shake = shake(t);
        if (shake > 0.001) {
            Vec3 ahead = look.subtract(eye);
            Vec3 across = ahead.normalize().cross(UP).normalize();
            Vec3 upward = across.cross(ahead.normalize());
            double jx = Math.sin(t * 71.0) + 0.6 * Math.sin(t * 113.0 + 1.3) + 0.3 * Math.sin(t * 197.0 + 4.1);
            double jy = Math.sin(t * 89.0 + 0.7) + 0.6 * Math.sin(t * 131.0 + 2.1) + 0.3 * Math.sin(t * 173.0 + 0.4);
            look = look.add(across.scale(jx * shake * ahead.length() * 0.008)).add(upward.scale(jy * shake * ahead.length() * 0.008));
        }
        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, (float) near, (float) far);
        Matrix4f view = new Matrix4f().lookAt((float) eye.x, (float) eye.y, (float) eye.z, (float) look.x, (float) look.y,
            (float) look.z, 0.0F, 1.0F, 0.0F);
        Vec3 forward = look.subtract(eye).normalize();
        heading = forward;
        Vec3 right = forward.cross(UP).normalize();
        Pass pass = new Pass(view, proj, eye, right, right.cross(forward).normalize(), (float) t);

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableCull();
        try {
            if (earthScene) {
                earth(program, pass, t);
            } else {
                proxima(program, pass, t);
            }
        } finally {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    // ---- Proxima Centauri in its Dyson sphere. ----------------------------------------------------------------------

    private static void proxima(ShaderInstance program, Pass p, double t) {
        double open = smooth((t - T_IRIS - 0.25) / 1.7);
        double fire = pulse(t, FIRE, 0.35);
        // how far through drawing the star's power in, and its heartbeat, quickening as the lens fills
        double drawn = clamp((t - POWER) / (FIRE - POWER));
        double beat = t > POWER && t < FIRE ? 0.5 + 0.5 * Math.sin(Math.PI * 2.0 * (2.0 * (t - POWER) + 3.0 * (t - POWER) * drawn)) : 0.0;
        backdrop(program, p, 1.0F, 1.0F);

        // the star: boiling, dimming a little as the lens drains it, flaring as it fires
        opaque();
        set(program, p, STAR, (float) (2.2 * (1.0 - 0.3 * drawn * (1.0 - smooth((t - FIRE) / 0.6))) + fire), (float) t, 0.0F, 0.0F);
        draw(program, p, starMesh());

        // the shell's panels, the iris opening from the middle out (each panel near the hole lifts off and slides away)
        set(program, p, PANEL, 0.0F, 0.0F, 0.0F, 0.0F);
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < PANELS.length; i++) {
            Vec3[] f = PANELS[i];
            Vec3 centre = f[0].add(f[1]).add(f[2]).scale(1.0 / 3.0);
            Vec3 n = centre.normalize();
            double angle = Math.acos(Mth.clamp(n.dot(AXIS), -1.0, 1.0));
            double cap = 0.62;
            double o = angle < cap ? smooth((open * 1.4 - angle / cap) / 0.4) : 0.0;
            if (o > 0.999) {
                continue;
            }
            Vec3 tangent = n.subtract(AXIS.scale(n.dot(AXIS))).add(1.0E-4, 1.0E-4, 1.0E-4).normalize();
            double shrink = lerp(0.955, 0.0, o);
            Vec3 lift = n.scale(o * 0.55).add(tangent.scale(o * 0.5));
            float[][] bary = {{1.0F, 0.0F}, {0.0F, 1.0F}, {0.0F, 0.0F}};
            int seed = (int) (PANEL_SEED[i] * 255.0F);
            for (int c = 0; c < 3; c++) {
                Vec3 at = centre.add(f[c].subtract(centre).scale(shrink)).add(lift);
                vertex(b, at, bary[c][0], bary[c][1], seed << 16, 1.0F, n);
            }
        }
        draw(program, p, b);

        // the collector rings (dark bands) and their satellites
        Vec3[][] rings = {ring(2.25, 1.25, 0.2, 0.0, t), ring(2.6, 1.9, -0.5, 0.4, t)};
        set(program, p, SOLID, 0.0F, 0.0F, 0.0F, 0.0F);
        b = begin(VertexFormat.Mode.QUADS);
        band(b, p, rings[0], 0.028, 0x120F12, 1.0F, 1.0);
        band(b, p, rings[1], 0.018, 0x120F12, 1.0F, 1.0);
        for (int i = 0; i < 160; i++) {
            boolean outer = i % 2 == 1;
            double a = i / 160.0 * Math.PI * 4.0 + t * (outer ? -0.05 : 0.08);
            Vec3 at = ringPoint(outer ? 2.6 : 2.25, outer ? 1.9 : 1.25, outer ? -0.5 : 0.2, outer ? 0.4 : 0.0, t, a);
            glow(b, p, at, 0.05, 0x59504F, 1.0F);
        }
        draw(program, p, b);

        // the lens: hex tiles flying off the shell into a disc before the iris, charging white
        double gather = t < T_LENS ? -1.0 : (t - T_LENS) / 0.6;
        double charge = (0.15 + 0.85 * smooth(drawn)) * (t > POWER ? 1.0 : 0.0) * (0.8 + 0.2 * beat) * (1.0 - smooth((t - FIRE - 0.3) / 0.4));
        if (gather > 0.0) {
            set(program, p, TILE, (float) charge, 0.0F, 0.0F, 0.0F);
            b = begin(VertexFormat.Mode.TRIANGLES);
            Vec3 u = AXIS.cross(UP).normalize();
            Vec3 v = u.cross(AXIS).normalize();
            for (int i = 0; i < TILE_END.length; i++) {
                double k = smooth((gather - TILE_DELAY[i]) / 0.55);
                if (k <= 0.001) {
                    continue;
                }
                Vec3 at = TILE_START[i].lerp(TILE_END[i], k).add(AXIS.scale(Math.sin(k * Math.PI) * 0.4));
                hexagon(b, at, u, v, 0.105, AXIS);
            }
            draw(program, p, b);
        }

        // ---- light, added on ----
        additive();
        b = begin(VertexFormat.Mode.QUADS);
        glow(b, p, Vec3.ZERO, 4.4, 0xFF4A26, (float) (0.75 + 0.2 * open + 0.25 * fire));
        glow(b, p, Vec3.ZERO, 11.0, 0xFF1F3A, (float) (0.28 + 0.15 * open));
        glow(b, p, LENS_CENTRE, 0.6 + 1.4 * charge + 1.2 * fire, 0xFFE6F0, (float) (charge * 0.6 + fire * 0.5));
        set(program, p, GLOW, 1.0F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);

        // prominences arcing off the limb, breathing
        b = begin(VertexFormat.Mode.QUADS);
        for (int i = 0; i < PROMINENCES.length; i++) {
            double phase = PROMINENCE_PHASE[i];
            double scale = 1.0 + 0.08 * Math.sin(t * 1.3 + phase);
            Vec3[] arc = new Vec3[33];
            for (int s = 0; s <= 32; s++) {
                double k = s / 32.0;
                Vec3 a = PROMINENCES[i][0].scale((1.0 - k) * (1.0 - k)).add(PROMINENCES[i][1].scale(2.0 * k * (1.0 - k)))
                    .add(PROMINENCES[i][2].scale(k * k));
                arc[s] = a.scale(scale);
            }
            band(b, p, arc, PROMINENCE_WIDTH[i] * scale, 0xFF431D, (float) (0.55 + 0.25 * Math.sin(t * 0.9 + phase)), 1.0);
        }
        set(program, p, BAND, 1.6F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);

        // the rings' running lights
        for (int r = 0; r < 2; r++) {
            b = begin(VertexFormat.Mode.QUADS);
            double circumference = Math.PI * 2.0 * (r == 0 ? 2.25 : 2.6);
            band(b, p, rings[r], r == 0 ? 0.03 : 0.02, 0xFF8040, 1.0F, 1.0 / circumference);
            set(program, p, BAND, 1.6F, 0.0F, r == 0 ? 90.0F : 140.0F, 0.0F);
            draw(program, p, b);
        }

        // the power flowing in (3 s): streams of plasma pulled off the star through the iris onto the lens's rim, more
        // and brighter as it fills, motes of light riding them in, rings closing on its centre faster and faster, sparks
        // crackling across its face, and at the last a bright ring snapping shut as it fires
        if (t > POWER - 0.1 && t < FIRE + 0.1) {
            double on = smooth((t - POWER + 0.1) / 0.3) * (1.0 - smooth((t - FIRE) / 0.1));
            Vec3 u = AXIS.cross(UP).normalize();
            Vec3 v = u.cross(AXIS).normalize();
            int streams = 4 + (int) Math.floor(8.0 * drawn);
            Vec3[][] arcs = new Vec3[streams][];
            b = begin(VertexFormat.Mode.QUADS);
            for (int i = 0; i < streams; i++) {
                double a = i / 12.0 * Math.PI * 2.0 + t * 0.6 + i * 0.3;
                Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                Vec3 from = AXIS.scale(1.02).add(radial.scale(0.3));
                Vec3 to = LENS_CENTRE.add(radial.scale(1.15));
                Vec3 across = radial.cross(AXIS).normalize();
                Vec3[] arc = new Vec3[21];
                for (int k = 0; k <= 20; k++) {
                    double q = k / 20.0;
                    double wobble = 0.05 * Math.sin(q * 11.0 + t * 19.0 + i * 1.7) * Math.sin(q * Math.PI);
                    arc[k] = from.lerp(to, q).add(radial.scale(Math.sin(q * Math.PI) * 0.4 + wobble)).add(across.scale(wobble));
                }
                arcs[i] = arc;
                band(b, p, arc, 0.025 + 0.035 * drawn, 0xFF8AC0, (float) (on * (0.45 + 0.5 * drawn)), 1.0);
            }
            set(program, p, BAND, 2.0F, 60.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            for (int i = 0; i < streams; i++) {
                for (int m = 0; m < 4; m++) {
                    double q = (t * (1.2 + 1.6 * drawn) + m / 4.0 + i * 0.37) % 1.0;
                    int at = Math.min(19, (int) (q * 20.0));
                    Vec3 mote = arcs[i][at].lerp(arcs[i][at + 1], q * 20.0 - at);
                    glow(b, p, mote, 0.07 + 0.05 * drawn, 0xFFE6F2, (float) (on * Math.sin(q * Math.PI)));
                }
            }
            for (int i = 0; i < 4; i++) {
                double k = (t * (0.8 + 2.4 * drawn) + i / 4.0) % 1.0;
                glow(b, p, LENS_CENTRE.add(u.scale(Math.cos(i * 1.9 + t) * 1.35 * (1.0 - k))).add(v.scale(Math.sin(i * 1.9 + t) * 1.35 * (1.0 - k))),
                    0.06, 0xFFD0E4, (float) (on * k));
            }
            set(program, p, GLOW, 1.6F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            for (int i = 0; i < 3; i++) {
                double k = ((t - POWER) * (0.9 + 2.6 * drawn) + i / 3.0) % 1.0;
                band(b, p, circle(LENS_CENTRE.add(AXIS.scale(0.02)), u, v, Math.max(0.02, 1.35 * (1.0 - k)), 96), 0.025 + 0.02 * drawn,
                    0xFFE0F0, (float) (on * k * 0.9), 1.0);
            }
            // sparks crackling across the lens's face, a new handful every few frames
            long frame = (long) Math.floor(t * 14.0);
            for (int j = 0; j < 3 + (int) (6 * drawn); j++) {
                long h = frame * 7919L + j * 104729L;
                double a0 = (h % 628) / 100.0;
                double r0 = 0.2 + ((h / 628) % 90) / 100.0;
                double a1 = a0 + (((h / 7) % 80) - 40) / 100.0;
                Vec3 pa = LENS_CENTRE.add(AXIS.scale(0.03)).add(u.scale(Math.cos(a0) * r0)).add(v.scale(Math.sin(a0) * r0));
                Vec3 pb = LENS_CENTRE.add(AXIS.scale(0.03)).add(u.scale(Math.cos(a1) * (r0 + 0.25))).add(v.scale(Math.sin(a1) * (r0 + 0.25)));
                Vec3 mid = pa.lerp(pb, 0.5).add(AXIS.scale(0.08));
                band(b, p, new Vec3[]{pa, mid, pb}, 0.012, 0xFFF0F8, (float) (on * (0.5 + 0.5 * drawn)), 1.0);
            }
            double last = smooth((t - FIRE + 0.3) / 0.3);
            if (last > 0.0) {
                band(b, p, circle(LENS_CENTRE.add(AXIS.scale(0.02)), u, v, Math.max(0.02, 2.4 * (1.0 - last)), 128), 0.05,
                    0xFFFFFF, (float) (last * on), 1.0);
            }
            set(program, p, BAND, 2.0F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }

        // the shot: a narrow white core in a thin red sheath from the lens to its head, the muzzle flash, and the head
        // itself (a bolt of plasma)
        if (t >= FIRE) {
            double since = t - FIRE;
            double on = smooth(since / 0.06);
            Vec3 head = LENS_CENTRE.add(AXIS.scale(since * SPEED));
            Vec3[] line = new Vec3[25];
            for (int k = 0; k <= 24; k++) {
                line[k] = LENS_CENTRE.lerp(head, k / 24.0);
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.2, 0xB31A33, (float) on, 1.0);
            set(program, p, BAND, 2.0F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.055, 0xFFE0EE, (float) on, 1.0);
            set(program, p, BAND, 3.0F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            glow(b, p, LENS_CENTRE, 1.6 + 2.5 * fire, 0xFFD6E8, (float) (0.9 * fire));
            set(program, p, GLOW, 1.4F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
            bolt(program, p, head, AXIS, 0.5, t);
        }
    }

    /**
     * The shot's head: a white-hot core stretched back along its path in a pink corona, a double helix of plasma
     * spinning round its tail, a cone of rings trailing it, sparks shed behind. {@code size}: its length (scene units).
     */
    private static void bolt(ShaderInstance program, Pass p, Vec3 head, Vec3 dir, double size, double t) {
        Vec3 u = dir.cross(Math.abs(dir.y) < 0.9 ? UP : new Vec3(1.0, 0.0, 0.0)).normalize();
        Vec3 v = dir.cross(u).normalize();
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        for (int strand = 0; strand < 2; strand++) {
            Vec3[] helix = new Vec3[65];
            for (int k = 0; k <= 64; k++) {
                double back = k / 64.0 * 3.2 * size;
                double r = size * 0.18 * (0.25 + 0.75 * k / 64.0);
                double a = k * 0.28 - t * 40.0 + strand * Math.PI;
                helix[k] = head.subtract(dir.scale(back)).add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
            }
            band(b, p, helix, size * 0.04, 0xFF8AC0, 0.9F, 1.0);
        }
        for (int i = 0; i < 4; i++) {
            double back = (0.45 + i * 0.55) * size;
            band(b, p, circle(head.subtract(dir.scale(back)), u, v, size * (0.12 + 0.17 * i), 48), size * 0.025, 0xFFB0D0,
                (float) (0.85 - 0.18 * i), 1.0);
        }
        set(program, p, BAND, 2.2F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);
        b = begin(VertexFormat.Mode.QUADS);
        for (int i = 0; i < 6; i++) {
            glow(b, p, head.subtract(dir.scale(i * 0.28 * size)), size * (0.6 - i * 0.08), i == 0 ? 0xFFFFFF : 0xFFC0DC,
                (float) (0.95 - i * 0.13));
        }
        glow(b, p, head, size * 1.8, 0xFF5A8C, 0.35F);
        for (int i = 0; i < 12; i++) {
            double age = (t * 3.0 + i / 12.0) % 1.0;
            double a = i * 2.39996;
            Vec3 at = head.subtract(dir.scale((0.3 + 2.8 * age) * size)).add(u.scale(Math.cos(a) * age * size * 0.7))
                .add(v.scale(Math.sin(a) * age * size * 0.7));
            glow(b, p, at, size * 0.07, 0xFFD6E8, (float) (1.0 - age));
        }
        set(program, p, GLOW, 1.6F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);
    }

    // ---- Earth, and the gate. ---------------------------------------------------------------------------------------

    private static void earth(ShaderInstance program, Pass p, double t) {
        backdrop(program, p, 0.0F, 0.8F);
        textures();

        opaque();
        RenderSystem.setShaderTexture(0, EARTH_DAY);
        RenderSystem.setShaderTexture(1, EARTH_DETAIL);
        set(program, p, EARTH, 1.0F, 0.0F, 0.0F, 0.0F);
        draw(program, p, earthMesh());

        // the gate: a ring of panels unfolding in orbit, turning slowly
        double gateOpen = smooth((t - T_EARTH - 0.35) / 0.6);
        Vec3 z = GATE.scale(-1.0).normalize();
        Vec3 x = UP.cross(z).normalize();
        Vec3 y = z.cross(x).normalize();
        double size = 0.42;
        double spin = t * 0.15;
        boolean gateShown = t >= T_EARTH;
        BufferBuilder seams = null;
        if (gateShown) {
            set(program, p, SOLID, 0.0F, 0.0F, 0.0F, 0.0F);
            BufferBuilder b = begin(VertexFormat.Mode.QUADS);
            Vec3[][] seamLines = new Vec3[16][];
            for (int i = 0; i < 16; i++) {
                double grow = smooth((t - T_EARTH - 0.1 - i * 0.02) / 0.3);
                if (grow <= 0.001) {
                    continue;
                }
                double a = i / 16.0 * Math.PI * 2.0 + spin;
                Vec3 out = x.scale(Math.cos(a)).add(y.scale(Math.sin(a)));
                Vec3 along = y.scale(Math.cos(a)).subtract(x.scale(Math.sin(a)));
                // folded out of the gate's plane until it opens
                double fold = (1.0 - gateOpen) * 1.4;
                Vec3 radial = out.scale(Math.cos(fold)).add(z.scale(-Math.sin(fold)));
                Vec3 centre = GATE.add(out.scale((0.39 + 0.12 * (1.0 - gateOpen)) * size));
                Vec3 hx = radial.scale(0.065 * size * grow);
                Vec3 hy = along.scale(0.025 * size * grow);
                Vec3 n = radial.cross(along).normalize();
                vertex(b, centre.subtract(hx).subtract(hy), 0.0F, 0.0F, 0x1C1A1F, 1.0F, n);
                vertex(b, centre.add(hx).subtract(hy), 1.0F, 0.0F, 0x1C1A1F, 1.0F, n);
                vertex(b, centre.add(hx).add(hy), 1.0F, 1.0F, 0x1C1A1F, 1.0F, n);
                vertex(b, centre.subtract(hx).add(hy), 0.0F, 1.0F, 0x1C1A1F, 1.0F, n);
                seamLines[i] = new Vec3[]{centre.subtract(hx).subtract(hy), centre.add(hx).subtract(hy)};
            }
            draw(program, p, b);
            seams = begin(VertexFormat.Mode.QUADS);
            for (Vec3[] line : seamLines) {
                if (line != null) {
                    band(seams, p, line, 0.006 * size, 0xFF6090, 1.0F, 1.0);
                }
            }
        }
        if (seams != null) {
            additive();
            set(program, p, BAND, 2.0F, 0.0F, 0.0F, 0.0F);
            draw(program, p, seams);
        }

        // the clouds over it, then its air
        blended();
        RenderSystem.setShaderTexture(1, EARTH_DETAIL);
        set(program, p, CLOUDS, 0.0F, 0.0F, 0.0F, 0.0F);
        draw(program, p, cloudMesh());
        additive();
        set(program, p, AIR, 0.0F, 0.0F, 0.0F, 0.0F);
        draw(program, p, airMesh());
        if (!gateShown) {
            return;
        }

        // the gate's ring, and its disc of plasma once the beam is in
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        band(b, p, circle(GATE, x, y, 0.34 * size, 128), 0.024 * size, 0xFF4D80, (float) gateOpen, 1.0);
        set(program, p, BAND, 1.3F, 0.0F, 0.0F, 0.0F);
        draw(program, p, b);
        double disc = smooth((t - CATCH) / 0.25) * (1.0 - smooth((t - T_EARTH - 1.85) / 0.2));
        if (disc > 0.002) {
            b = begin(VertexFormat.Mode.QUADS);
            double r = 0.33 * size;
            Vec3 n = z;
            vertex(b, GATE.subtract(x.scale(r)).subtract(y.scale(r)), -1.0F, -1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.add(x.scale(r)).subtract(y.scale(r)), 1.0F, -1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.add(x.scale(r)).add(y.scale(r)), 1.0F, 1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.subtract(x.scale(r)).add(y.scale(r)), -1.0F, 1.0F, 0xFFFFFF, 1.0F, n);
            set(program, p, GATE_DISC, (float) (0.6 * disc), 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }

        // the wave streaking in from deep space onto the gate, its bright head first
        if (t > T_EARTH + 0.2 && t < T_EARTH + 1.9) {
            double arrive = smooth((t - T_EARTH - 0.25) / (CATCH - T_EARTH - 0.25));
            Vec3 dir = GATE.subtract(BEAM_FROM);
            double length = dir.length();
            dir = dir.normalize();
            float a = (float) (1.0 - smooth((t - T_EARTH - 1.6) / 0.3));
            Vec3[] line = new Vec3[25];
            for (int s = 0; s <= 24; s++) {
                line[s] = BEAM_FROM.add(dir.scale(length * arrive * s / 24.0));
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.14, 0xB31A33, a, 1.0);
            set(program, p, BAND, 1.0F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.036, 0xFFD5E6, a, 1.0);
            set(program, p, BAND, 1.5F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            if (t < CATCH + 0.05) {
                bolt(program, p, BEAM_FROM.add(dir.scale(length * arrive)), dir, 0.14, t);
            }
        }

        // the gate refocuses it: a narrow beam from the gate down onto the caster's spot, its wave racing ahead, a flash
        // where it lands and a ring of light running out across the land from there
        double refocus = smooth((t - (CATCH + 0.15)) / 0.12) * (1.0 - smooth((t - T_EARTH - 1.85) / 0.15));
        if (refocus > 0.0) {
            Vec3 ground = SPOT.scale(1.002);
            Vec3 down = ground.subtract(GATE);
            double reach = smooth((t - (CATCH + 0.15)) / (LANDS - CATCH - 0.15));
            Vec3[] line = new Vec3[25];
            for (int s = 0; s <= 24; s++) {
                line[s] = GATE.add(down.scale(reach * s / 24.0));
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.05, 0xB31A33, (float) refocus, 1.0);
            set(program, p, BAND, 1.6F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.014, 0xFFE0EE, (float) refocus, 1.0);
            set(program, p, BAND, 2.4F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            if (reach < 0.999) {
                bolt(program, p, GATE.add(down.scale(reach)), down.normalize(), 0.07, t);
                additive();
            }
            b = begin(VertexFormat.Mode.QUADS);
            double landed = pulse(t, LANDS, 0.4);
            glow(b, p, ground, 0.06 + 0.5 * landed + 0.08 * smooth((t - LANDS) / 0.4), 0xFFE6F0, (float) (0.3 + 0.7 * landed));
            set(program, p, GLOW, 1.4F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }
        if (t > LANDS) {
            // the ring on the ground: a circle on the sphere round the spot, its angle growing
            double k = smooth((t - LANDS) / 0.5);
            double angle = 0.02 + 0.22 * k;
            Vec3 u = SPOT.cross(UP).normalize();
            Vec3 v = u.cross(SPOT).normalize();
            Vec3 centre = SPOT.scale(Math.cos(angle) * 1.003);
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, circle(centre, u, v, Math.sin(angle) * 1.003, 96), 0.006 + 0.01 * k, 0xFF5A80, (float) (1.0 - k * 0.7), 1.0);
            set(program, p, BAND, 1.6F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }
    }

    // ---- Shared parts. ----------------------------------------------------------------------------------------------

    /** The deep sky: a cube round the camera, every pixel lit by the way it looks. */
    private static void backdrop(ShaderInstance program, Pass p, float nebula, float stars) {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        double s = 40.0;
        int[][] faces = {
            {1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1}, {-1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1},
            {-1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1}, {-1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
            {1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1}, {-1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1},
        };
        for (int[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                vertex(b, p.eye().add(f[i] * s, f[i + 1] * s, f[i + 2] * s), 0.0F, 0.0F, 0xFFFFFF, 1.0F, UP);
            }
        }
        set(program, p, BACKDROP, nebula, stars, (float) warp, 0.0F);
        program.safeGetUniform("SunDir").set((float) heading.x, (float) heading.y, (float) heading.z);
        draw(program, p, b);
    }

    /** A ring's points: a circle of {@code radius} turned by Euler angles (XYZ), the rings turning slowly about the star. */
    private static Vec3[] ring(double radius, double ax, double ay, double az, double t) {
        Vec3[] points = new Vec3[241];
        for (int i = 0; i <= 240; i++) {
            points[i] = ringPoint(radius, ax, ay, az, t, i / 240.0 * Math.PI * 2.0);
        }
        return points;
    }

    private static Vec3 ringPoint(double radius, double ax, double ay, double az, double t, double a) {
        Vec3 v = new Vec3(Math.cos(a) * radius, Math.sin(a) * radius, 0.0);
        v = v.zRot((float) az).yRot((float) ay).xRot((float) ax);
        return v.yRot((float) (t * 0.03));
    }

    private static Vec3[] circle(Vec3 centre, Vec3 u, Vec3 v, double radius, int n) {
        Vec3[] points = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = i / (double) n * Math.PI * 2.0;
            points[i] = centre.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
        }
        return points;
    }

    /** A band through the points, {@code width} across, turned to face the camera; uv.y runs along it ({@code perUnit} a block). */
    private static void band(BufferBuilder b, Pass p, Vec3[] points, double width, int rgb, float alpha, double perUnit) {
        if (alpha <= 0.002F || points.length < 2) {
            return;
        }
        Vec3 lastA = null;
        Vec3 lastB = null;
        double along = 0.0;
        double lastAlong = 0.0;
        for (int i = 0; i < points.length; i++) {
            Vec3 tangent = points[Math.min(i + 1, points.length - 1)].subtract(points[Math.max(i - 1, 0)]);
            Vec3 side = tangent.cross(p.eye().subtract(points[i]));
            if (side.lengthSqr() < 1.0E-18) {
                side = p.right();
            }
            side = side.normalize().scale(width * 0.5);
            if (i > 0) {
                along += points[i].distanceTo(points[i - 1]) * perUnit;
            }
            Vec3 a = points[i].subtract(side);
            Vec3 c = points[i].add(side);
            if (lastA != null) {
                vertex(b, lastA, 0.0F, (float) lastAlong, rgb, alpha, UP);
                vertex(b, lastB, 1.0F, (float) lastAlong, rgb, alpha, UP);
                vertex(b, c, 1.0F, (float) along, rgb, alpha, UP);
                vertex(b, a, 0.0F, (float) along, rgb, alpha, UP);
            }
            lastA = a;
            lastB = c;
            lastAlong = along;
        }
    }

    /** A soft round glow {@code width} across, facing the camera. */
    private static void glow(BufferBuilder b, Pass p, Vec3 centre, double width, int rgb, float alpha) {
        if (alpha <= 0.002F) {
            return;
        }
        Vec3 r = p.right().scale(width * 0.5);
        Vec3 u = p.up().scale(width * 0.5);
        vertex(b, centre.subtract(r).subtract(u), 0.0F, 0.0F, rgb, alpha, UP);
        vertex(b, centre.add(r).subtract(u), 1.0F, 0.0F, rgb, alpha, UP);
        vertex(b, centre.add(r).add(u), 1.0F, 1.0F, rgb, alpha, UP);
        vertex(b, centre.subtract(r).add(u), 0.0F, 1.0F, rgb, alpha, UP);
    }

    /** A flat hexagon of {@code radius} in the plane of {@code u} and {@code v}, uv across it for its rim. */
    private static void hexagon(BufferBuilder b, Vec3 centre, Vec3 u, Vec3 v, double radius, Vec3 n) {
        for (int i = 0; i < 6; i++) {
            double a0 = i / 6.0 * Math.PI * 2.0;
            double a1 = (i + 1) / 6.0 * Math.PI * 2.0;
            vertex(b, centre, 0.5F, 0.5F, 0xFFFFFF, 1.0F, n);
            vertex(b, centre.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius)),
                (float) (0.5 + 0.5 * Math.cos(a0)), (float) (0.5 + 0.5 * Math.sin(a0)), 0xFFFFFF, 1.0F, n);
            vertex(b, centre.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius)),
                (float) (0.5 + 0.5 * Math.cos(a1)), (float) (0.5 + 0.5 * Math.sin(a1)), 0xFFFFFF, 1.0F, n);
        }
    }

    // ---- The spheres (built once). ----------------------------------------------------------------------------------

    private static VertexBuffer starMesh() {
        if (starMesh == null) {
            starMesh = upload(sphere(1.0, 64, 128, 0.0));
        }
        return starMesh;
    }

    private static VertexBuffer earthMesh() {
        if (earthMesh == null) {
            earthMesh = upload(sphere(1.0, 80, 160, EARTH_TURN));
        }
        return earthMesh;
    }

    private static VertexBuffer cloudMesh() {
        if (cloudMesh == null) {
            cloudMesh = upload(sphere(1.008, 80, 160, EARTH_TURN));
        }
        return cloudMesh;
    }

    private static VertexBuffer airMesh() {
        if (airMesh == null) {
            airMesh = upload(sphere(1.06, 48, 96, 0.0));
        }
        return airMesh;
    }

    /** A sphere on the origin, mapped like an equirectangular image (north at the top), turned {@code turn} about the axis. */
    private static MeshData sphere(double radius, int rows, int columns, double turn) {
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int[][] corners = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] c : corners) {
                    double u = c[1] / (double) columns;
                    double v = c[0] / (double) rows;
                    double phi = u * Math.PI * 2.0;
                    double theta = v * Math.PI;
                    double x = -Math.cos(phi) * Math.sin(theta);
                    double y = Math.cos(theta);
                    double z = Math.sin(phi) * Math.sin(theta);
                    Vec3 n = new Vec3(x * Math.cos(turn) + z * Math.sin(turn), y, -x * Math.sin(turn) + z * Math.cos(turn));
                    vertex(b, n.scale(radius), (float) u, (float) v, 0xFFFFFF, 1.0F, n);
                }
            }
        }
        return b.buildOrThrow();
    }

    private static VertexBuffer upload(MeshData mesh) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    /** Loads Earth's images ahead of the film (on joining a world). */
    public static void preload() {
        textures();
    }

    /** Earth's images, smoothed close up and mipmapped from afar (4096 wide: unfiltered, the whole disc shimmers). */
    private static void textures() {
        ResourceLocation[] images = {EARTH_DAY, EARTH_DETAIL};
        for (int i = 0; i < images.length; i++) {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(images[i]);
            int id = texture.getId();
            if (mipmapped[i] == id) {
                continue;
            }
            mipmapped[i] = id;
            RenderSystem.bindTexture(id);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 12);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8.0F);
            }
        }
    }

    // ---- Drawing. ---------------------------------------------------------------------------------------------------

    private static BufferBuilder begin(VertexFormat.Mode mode) {
        return Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
    }

    private static void vertex(BufferBuilder b, Vec3 at, float u, float v, int rgb, float alpha, Vec3 n) {
        b.addVertex((float) at.x, (float) at.y, (float) at.z)
            .setUv(u, v)
            .setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, Mth.clamp(alpha, 0.0F, 1.0F))
            .setNormal((float) n.x, (float) n.y, (float) n.z);
    }

    private static void set(ShaderInstance program, Pass p, int mode, float a, float b, float c, float d) {
        program.safeGetUniform("Mode").set((float) mode);
        program.safeGetUniform("Time").set(p.time());
        program.safeGetUniform("CamPos").set((float) p.eye().x, (float) p.eye().y, (float) p.eye().z);
        program.safeGetUniform("SunDir").set((float) SUN.x, (float) SUN.y, (float) SUN.z);
        program.safeGetUniform("Param").set(a, b, c, d);
    }

    private static void draw(ShaderInstance program, Pass p, BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        if (dynamic == null) {
            dynamic = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        dynamic.bind();
        dynamic.upload(mesh);
        dynamic.drawWithShader(p.view(), p.proj(), program);
        VertexBuffer.unbind();
    }

    private static void draw(ShaderInstance program, Pass p, VertexBuffer buffer) {
        buffer.bind();
        buffer.drawWithShader(p.view(), p.proj(), program);
        VertexBuffer.unbind();
    }

    private static void opaque() {
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static void blended() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static double smooth(double x) {
        double k = clamp(x);
        return k * k * (3.0 - 2.0 * k);
    }

    private static double clamp(double x) {
        return Mth.clamp(x, 0.0, 1.0);
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    /** How hard the camera shakes at film second {@code t}: building as the lens charges, kicks as things hit. */
    private static double shake(double t) {
        double charging = t > POWER && t < FIRE ? 0.25 + 0.75 * Math.pow((t - POWER) / (FIRE - POWER), 2.0) : 0.0;
        return 0.7 * charging + 0.5 * pulse(t, POWER, 0.2) + 2.2 * pulse(t, FIRE, 0.3) + 0.9 * pulse(t, CATCH, 0.25)
            + 1.3 * pulse(t, LANDS, 0.35);
    }

    private static double pulse(double t, double at, double decay) {
        return t < at ? 0.0 : Math.exp(-(t - at) / decay);
    }

    // ---- The fixed shapes. ------------------------------------------------------------------------------------------

    static {
        // the Dyson sphere: an icosahedron, each face cut into 7 x 7, pushed out onto the sphere
        double g = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] v = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
            {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        int[] faces = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        int cuts = 7;
        java.util.List<Vec3[]> panels = new java.util.ArrayList<>();
        for (int f = 0; f < faces.length; f += 3) {
            Vec3 a = new Vec3(v[faces[f]][0], v[faces[f]][1], v[faces[f]][2]);
            Vec3 b = new Vec3(v[faces[f + 1]][0], v[faces[f + 1]][1], v[faces[f + 1]][2]);
            Vec3 c = new Vec3(v[faces[f + 2]][0], v[faces[f + 2]][1], v[faces[f + 2]][2]);
            Vec3[][] grid = new Vec3[cuts + 1][];
            for (int i = 0; i <= cuts; i++) {
                grid[i] = new Vec3[cuts + 1 - i];
                for (int j = 0; j <= cuts - i; j++) {
                    grid[i][j] = a.add(b.subtract(a).scale(i / (double) cuts)).add(c.subtract(a).scale(j / (double) cuts))
                        .normalize().scale(SHELL);
                }
            }
            for (int i = 0; i < cuts; i++) {
                for (int j = 0; j < cuts - i; j++) {
                    panels.add(new Vec3[]{grid[i][j], grid[i + 1][j], grid[i][j + 1]});
                    if (j < cuts - i - 1) {
                        panels.add(new Vec3[]{grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]});
                    }
                }
            }
        }
        PANELS = panels.toArray(new Vec3[0][]);
        Random random = new Random(99);
        PANEL_SEED = new float[PANELS.length];
        for (int i = 0; i < PANELS.length; i++) {
            PANEL_SEED[i] = random.nextFloat();
        }

        // prominences: arcs of plasma off the limb
        random = new Random(21);
        PROMINENCES = new Vec3[9][];
        PROMINENCE_WIDTH = new double[9];
        PROMINENCE_PHASE = new double[9];
        for (int i = 0; i < 9; i++) {
            Vec3 a = randomDirection(random);
            Vec3 side = randomDirection(random).cross(a).normalize();
            double span = 0.25 + random.nextDouble() * 0.35;
            double h = 0.25 + random.nextDouble() * 0.55;
            Vec3 from = a.subtract(side.scale(span)).normalize();
            Vec3 to = a.add(side.scale(span)).normalize();
            PROMINENCES[i] = new Vec3[]{from, a.scale(1.0 + h), to};
            PROMINENCE_WIDTH[i] = 2.0 * (0.018 + random.nextDouble() * 0.02);
            PROMINENCE_PHASE[i] = random.nextDouble() * 6.28;
        }

        // the lens: a hexagon of hex tiles before the iris, each flying in from somewhere on the shell
        random = new Random(5);
        Vec3 u = AXIS.cross(UP).normalize();
        Vec3 w = u.cross(AXIS).normalize();
        java.util.List<Vec3> starts = new java.util.ArrayList<>();
        java.util.List<Vec3> ends = new java.util.ArrayList<>();
        java.util.List<Double> delays = new java.util.ArrayList<>();
        for (int q = -6; q <= 6; q++) {
            for (int r = -6; r <= 6; r++) {
                if (Math.abs(q + r) > 6) {
                    continue;
                }
                double x = 0.2 * (q + r / 2.0);
                double y = 0.2 * r * 0.866;
                ends.add(LENS_CENTRE.add(u.scale(x)).add(w.scale(y)));
                starts.add(randomDirection(random).lerp(AXIS, 0.55).normalize().scale(1.6));
                delays.add(random.nextDouble() * 0.35 + Math.hypot(x, y) * 0.12);
            }
        }
        TILE_START = starts.toArray(new Vec3[0]);
        TILE_END = ends.toArray(new Vec3[0]);
        TILE_DELAY = delays.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static Vec3 randomDirection(Random random) {
        return new Vec3(random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0).normalize();
    }
}
