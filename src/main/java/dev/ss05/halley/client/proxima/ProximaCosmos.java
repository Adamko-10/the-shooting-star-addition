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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

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
 * ({@code proxima_space}), a material per draw (its {@code Mode}). Earth is NASA's imagery (art/make_earth.sh).
 */
public final class ProximaCosmos {
    private static final ResourceLocation EARTH_DAY = HalleyAddon.id("textures/proxima/earth_day.png");
    private static final ResourceLocation EARTH_LIGHTS = HalleyAddon.id("textures/proxima/earth_lights.png");
    private static final ResourceLocation EARTH_CLOUDS = HalleyAddon.id("textures/proxima/earth_clouds.png");

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
    private static final int CME = 9;
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
    private static final Vec3 BEAM_FROM = GATE.add(new Vec3(-1.5, 1.9, 1.3).normalize().scale(30.0));
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
    private static boolean filtered;

    private ProximaCosmos() {
    }

    public static void setShader(@Nullable ShaderInstance loaded) {
        shader = loaded;
    }

    /** How high over Earth (in its radii) the orbit shot is, {@code q} of the way through it. */
    public static double altitude(double q) {
        return 0.3 * Math.pow(7.0, q);
    }

    /** Whether the film is in its space and Earth shots at film tick {@code ct}: the scene covers the world then. */
    public static boolean covers(float ct) {
        return ct >= ProximaPlan.ORBIT && ct < ProximaPlan.FLASH;
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
        if (ct < ProximaPlan.SPACE) {
            // shot 2's second half: from over the caster's spot on Earth pulling back until its curve fills the frame
            // (from 0.3 of its radius up: any closer and its images are blurred)
            double k = (t - 1.6) / 3.8;
            double q = clamp((k - 0.36) / 0.64);
            double alt = altitude(q);
            Vec3 spot = new Vec3(-0.62, 0.62, 0.48).normalize();
            eye = spot.scale(1.0 + alt).add(new Vec3(0.06, -0.03, 0.05).scale(alt));
            look = spot.scale(1.0 - smooth(q * 1.25));
            fov = lerp(62.0, 42.0, smooth(q));
            near = 0.0005;
            far = 200.0;
            earthScene = true;
        } else if (ct < ProximaPlan.EARTH) {
            near = 0.01;
            far = 2000.0;
            earthScene = false;
            if (t < 7.8) {
                // 3. Proxima Centauri: a slow wide drift past the star in its shell
                double e = smooth((t - 5.4) / 2.4);
                double a = lerp(-0.35, 0.15, e);
                double r = lerp(8.6, 7.2, e);
                eye = new Vec3(Math.sin(a) * r, lerp(1.6, 1.0, e), Math.cos(a) * r);
                look = Vec3.ZERO;
                fov = lerp(40.0, 36.0, e);
            } else if (t < 10.0) {
                // 4. the Dyson sphere: closer, square on to the iris as it opens
                double e = smooth((t - 7.8) / 2.2);
                eye = AXIS.scale(lerp(6.2, 4.7, e)).add(lerp(0.4, 0.15, e), 0.1, 0.0);
                look = Vec3.ZERO;
                fov = 38.0;
            } else if (t < 11.4) {
                // 5. reconnection: close on the star's face through the open iris
                double e = smooth((t - 10.0) / 1.4);
                eye = AXIS.scale(lerp(3.4, 3.0, e)).add(0.25, 0.12, 0.0);
                look = AXIS.scale(0.9);
                fov = lerp(42.0, 38.0, e);
            } else {
                // 6. the lens: side on, so the beam crosses the frame
                double e = smooth((t - 11.4) / 1.6);
                Vec3 side = AXIS.cross(UP).normalize();
                eye = AXIS.scale(lerp(3.4, 4.0, e)).add(side.scale(lerp(4.0, 4.6, e))).add(0.0, 0.9, 0.0);
                look = AXIS.scale(lerp(2.6, 3.6, e));
                fov = lerp(48.0, 54.0, e);
            }
        } else {
            // 7. Earth and the gate: Earth from orbit, the gate between us and the planet
            double e = smooth((t - 13.0) / 2.0);
            eye = new Vec3(lerp(1.15, 1.0, e), lerp(0.62, 0.55, e), lerp(3.4, 3.15, e));
            look = new Vec3(0.22, 0.24, 0.9);
            fov = lerp(44.0, 40.0, e);
            near = 0.001;
            far = 400.0;
            earthScene = true;
        }

        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, (float) near, (float) far);
        Matrix4f view = new Matrix4f().lookAt((float) eye.x, (float) eye.y, (float) eye.z, (float) look.x, (float) look.y,
            (float) look.z, 0.0F, 1.0F, 0.0F);
        Vec3 forward = look.subtract(eye).normalize();
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
        double open = smooth((t - 8.05) / 1.7);
        double reconnect = pulse(t, 10.85, 0.18);
        double fire = pulse(t, 12.45, 0.35);
        backdrop(program, p, 1.0F, 1.0F);

        // the star: boiling, brighter at the reconnection and as the lens fires
        opaque();
        set(program, p, STAR, (float) (2.2 + reconnect + fire), 0.0F, 0.0F, 0.0F);
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
        double gather = t < 11.4 ? -1.0 : (t - 11.4) / 0.85;
        double charge = smooth((t - 12.1) / 0.35) * (1.0 - smooth((t - 12.9) / 0.4));
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
        glow(b, p, LENS_CENTRE, 0.6 + 0.9 * charge + 1.2 * fire, 0xFFE6F0, (float) (charge * 0.55 + fire * 0.5));
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

        // the infinity loops: faint, drawn on, a brief flare where they cross, then collapsing to a point
        if (t > 10.0 && t < 11.35) {
            double drawn = smooth((t - 10.0) / 0.55);
            double collapse = smooth((t - 10.95) / 0.35);
            double scale = 0.62 * (1.0 - 0.9 * collapse);
            Vec3 centre = AXIS.scale(1.03);
            Vec3 x = UP.cross(AXIS).normalize();
            Vec3 y = AXIS.cross(x).normalize();
            int n = (int) Math.round(240 * drawn);
            if (n > 1) {
                Vec3[] loop = new Vec3[n + 1];
                for (int s = 0; s <= n; s++) {
                    double a = s / 240.0 * Math.PI * 2.0;
                    double den = 1.0 + Math.sin(a) * Math.sin(a);
                    loop[s] = centre.add(x.scale(scale * Math.cos(a) / den)).add(y.scale(scale * Math.sin(a) * Math.cos(a) / den))
                        .add(AXIS.scale(scale * 0.08 * Math.sin(a * 2.0)));
                }
                // thin and dim against the star (the user: the infinity sign fainter)
                b = begin(VertexFormat.Mode.QUADS);
                band(b, p, loop, 0.014 * scale / 0.62, 0xFF80CC, (float) (0.2 * (1.0 - collapse)), 1.0);
                set(program, p, BAND, 1.0F, 0.0F, 0.0F, 0.0F);
                draw(program, p, b);
            }
            b = begin(VertexFormat.Mode.QUADS);
            glow(b, p, centre, scale * (0.25 + 1.1 * reconnect), 0xFFD0E6, (float) (0.55 * reconnect));
            set(program, p, GLOW, 1.0F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }

        // the beam: a white core in a red sheath, two shock rings racing out along it
        if (t >= 12.45) {
            double on = smooth((t - 12.45) / 0.12);
            double length = Math.max(0.001, 60.0 * smooth((t - 12.45) / 0.35));
            Vec3[] line = new Vec3[25];
            for (int s = 0; s <= 24; s++) {
                line[s] = LENS_CENTRE.add(AXIS.scale(length * s / 24.0));
            }
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.5, 0xB31A33, (float) on, 1.0);
            set(program, p, BAND, 1.8F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            band(b, p, line, 0.12, 0xFFD5E6, (float) on, 1.0);
            set(program, p, BAND, 2.5F, 3.0F, 0.0F, 0.0F);
            draw(program, p, b);
            b = begin(VertexFormat.Mode.QUADS);
            Vec3 u = AXIS.cross(UP).normalize();
            Vec3 v = u.cross(AXIS).normalize();
            for (int i = 0; i < 2; i++) {
                double since = t - 12.45 - i * 0.18;
                if (since <= 0.0) {
                    continue;
                }
                double radius = 0.3 + since * 6.0;
                band(b, p, circle(LENS_CENTRE.add(AXIS.scale(0.2 + since * 2.0)), u, v, radius, 96), 0.04 * radius,
                    0xFF73A6, (float) (Math.max(0.0, 1.0 - since / 0.6) * 0.9), 1.0);
            }
            set(program, p, BAND, 1.6F, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }
    }

    // ---- Earth, and the gate. ---------------------------------------------------------------------------------------

    private static void earth(ShaderInstance program, Pass p, double t) {
        backdrop(program, p, 0.0F, 0.8F);
        textures();

        opaque();
        RenderSystem.setShaderTexture(0, EARTH_DAY);
        RenderSystem.setShaderTexture(1, EARTH_LIGHTS);
        set(program, p, EARTH, 0.0F, 0.0F, 0.0F, 0.0F);
        draw(program, p, earthMesh());

        // the gate: a ring of panels unfolding in orbit, turning slowly
        double gateOpen = smooth((t - 13.35) / 0.6);
        Vec3 z = GATE.scale(-1.0).normalize();
        Vec3 x = UP.cross(z).normalize();
        Vec3 y = z.cross(x).normalize();
        double size = 0.42;
        double spin = t * 0.15;
        boolean gateShown = t >= 13.0;
        BufferBuilder seams = null;
        if (gateShown) {
            set(program, p, SOLID, 0.0F, 0.0F, 0.0F, 0.0F);
            BufferBuilder b = begin(VertexFormat.Mode.QUADS);
            Vec3[][] seamLines = new Vec3[16][];
            for (int i = 0; i < 16; i++) {
                double grow = smooth((t - 13.1 - i * 0.02) / 0.3);
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
        RenderSystem.setShaderTexture(0, EARTH_CLOUDS);
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
        double disc = smooth((t - 13.75) / 0.25) * (1.0 - smooth((t - 14.85) / 0.2));
        if (disc > 0.002) {
            b = begin(VertexFormat.Mode.QUADS);
            double r = 0.33 * size;
            Vec3 n = z;
            vertex(b, GATE.subtract(x.scale(r)).subtract(y.scale(r)), -1.0F, -1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.add(x.scale(r)).subtract(y.scale(r)), 1.0F, -1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.add(x.scale(r)).add(y.scale(r)), 1.0F, 1.0F, 0xFFFFFF, 1.0F, n);
            vertex(b, GATE.subtract(x.scale(r)).add(y.scale(r)), -1.0F, 1.0F, 0xFFFFFF, 1.0F, n);
            set(program, p, GATE_DISC, (float) disc, 0.0F, 0.0F, 0.0F);
            draw(program, p, b);
        }

        // the beam streaking in from deep space onto the gate
        if (t > 13.2 && t < 14.9) {
            double arrive = smooth((t - 13.25) / 0.55);
            Vec3 dir = GATE.subtract(BEAM_FROM);
            double length = dir.length();
            dir = dir.normalize();
            float a = (float) (1.0 - smooth((t - 14.6) / 0.3));
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
        }

        // the coronal mass ejection: the gate spreads the beam into a red cloud streaming down onto Earth
        double spread = smooth((t - 14.05) / 0.9);
        if (spread > 0.0) {
            double toEarth = GATE.length() - 1.0;
            double width = 0.12 + 0.55 * spread;
            b = begin(VertexFormat.Mode.QUADS);
            int around = 96;
            int rows = 24;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < around; j++) {
                    int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    for (int[] c : corners) {
                        double along = c[0] / (double) rows;
                        double a = c[1] / (double) around * Math.PI * 2.0;
                        double r = lerp(0.02, 1.0, along) * width;
                        Vec3 radial = x.scale(Math.cos(a)).add(y.scale(Math.sin(a)));
                        Vec3 at = GATE.add(z.scale(toEarth * along)).add(radial.scale(r));
                        vertex(b, at, c[1] / (float) around, (float) along, 0xFFFFFF, 1.0F, radial);
                    }
                }
            }
            set(program, p, CME, (float) (0.9 * (1.0 - smooth((t - 14.9) / 0.2))), (float) spread, 0.0F, 0.0F);
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
        set(program, p, BACKDROP, nebula, stars, 0.0F, 0.0F);
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

    /** Earth's images, smoothed (they're seen close up). */
    private static void textures() {
        if (!filtered) {
            filtered = true;
            for (ResourceLocation image : new ResourceLocation[]{EARTH_DAY, EARTH_LIGHTS, EARTH_CLOUDS}) {
                Minecraft.getInstance().getTextureManager().getTexture(image).setFilter(true, false);
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
