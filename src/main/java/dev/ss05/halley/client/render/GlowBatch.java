package dev.ss05.halley.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Builds SS-05's light out of quads for the {@code halley_glow} shader. World positions go in; camera-relative
 * vertices come out. Knows nothing about Minecraft's rendering, so it can also be fed to a preview harness.
 *
 * <p>Anything further away than {@code maxDistance} (the comet, hundreds or thousands of blocks out) is pulled in
 * along its line of sight, vertex by vertex. That leaves it exactly where it was on screen, at the same size, but
 * inside the camera's far plane, and still behind all the terrain that is actually loaded.
 */
public final class GlowBatch {
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);
    }

    private final Sink sink;
    private final Vec3 camera;
    private final Vector3f right;
    private final Vector3f up;
    private final double maxDistance;

    /** {@code left} and {@code up} are the camera's unit vectors (Minecraft's Camera gives the left one). */
    public GlowBatch(Sink sink, Vec3 camera, Vector3f left, Vector3f up, double maxDistance) {
        this.sink = sink;
        this.camera = camera;
        this.right = new Vector3f(left).negate();
        this.up = new Vector3f(up);
        this.maxDistance = maxDistance;
    }

    public Vec3 camera() {
        return this.camera;
    }

    private void vertex(double wx, double wy, double wz, float u, float v, int rgb, float strength) {
        double x = wx - this.camera.x;
        double y = wy - this.camera.y;
        double z = wz - this.camera.z;
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d > this.maxDistance) {
            double k = this.maxDistance / d;
            x *= k;
            y *= k;
            z *= k;
        }
        this.sink.vertex((float) x, (float) y, (float) z, u, v,
            (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, Math.min(1.0F, Math.max(0.0F, strength)));
    }

    /** A quad from a centre and two half-axes, uv -1..1 across each axis. */
    private void quad(double cx, double cy, double cz, double ax, double ay, double az, double bx, double by, double bz,
                      int shape, int variant, int rgb, float strength) {
        if (strength <= 0.003F) {
            return;
        }
        float u0 = shape * 4.0F;
        float v0 = variant * 4.0F;
        this.vertex(cx - ax - bx, cy - ay - by, cz - az - bz, u0, v0, rgb, strength);
        this.vertex(cx + ax - bx, cy + ay - by, cz + az - bz, u0 + 2.0F, v0, rgb, strength);
        this.vertex(cx + ax + bx, cy + ay + by, cz + az + bz, u0 + 2.0F, v0 + 2.0F, rgb, strength);
        this.vertex(cx - ax + bx, cy - ay + by, cz - az + bz, u0, v0 + 2.0F, rgb, strength);
    }

    /** A camera-facing square of half-size {@code size}, turned by {@code rotation} radians on screen. */
    public void billboard(Vec3 at, double size, int shape, int variant, int rgb, float strength, double rotation) {
        double c = Math.cos(rotation) * size;
        double s = Math.sin(rotation) * size;
        double ax = this.right.x * c + this.up.x * s;
        double ay = this.right.y * c + this.up.y * s;
        double az = this.right.z * c + this.up.z * s;
        double bx = -this.right.x * s + this.up.x * c;
        double by = -this.right.y * s + this.up.y * c;
        double bz = -this.right.z * s + this.up.z * c;
        this.quad(at.x, at.y, at.z, ax, ay, az, bx, by, bz, shape, variant, rgb, strength);
    }

    public void billboard(Vec3 at, double size, int shape, int rgb, float strength) {
        this.billboard(at, size, shape, 0, rgb, strength, 0.0);
    }

    /**
     * A camera-facing square drawn {@code pull} blocks nearer the camera than {@code at}, shrunk so it looks exactly the
     * same on screen. Depth-tested there, ground close round the light (a trench wall) no longer cuts it off, but
     * anything near the camera (the player, in third person) still stands in front of it.
     */
    public void billboardPulled(Vec3 at, double pull, double size, int shape, int variant, int rgb, float strength, double rotation) {
        Vec3 view = at.subtract(this.camera);
        double d = view.length();
        if (d < 1.0E-3) {
            return;
        }
        double near = Math.max(d - pull, Math.min(d, 6.0));
        double k = near / d;
        this.billboard(this.camera.add(view.scale(k)), size * k, shape, variant, rgb, strength, rotation);
    }

    /** A rectangle lying flat on the screen, {@code halfWidth} across it and {@code halfHeight} up it (lens flares). */
    public void rect(Vec3 at, double halfWidth, double halfHeight, int shape, int variant, int rgb, float strength) {
        this.quad(at.x, at.y, at.z, this.right.x * halfWidth, this.right.y * halfWidth, this.right.z * halfWidth,
            this.up.x * halfHeight, this.up.y * halfHeight, this.up.z * halfHeight, shape, variant, rgb, strength);
    }

    /** An upright quad standing between two points on the ground, {@code height} tall (uv x along it, y up it). */
    public void wall(Vec3 a, Vec3 b, double height, int shape, int variant, int rgb, float strength) {
        double h = height * 0.5;
        this.quad((a.x + b.x) * 0.5, (a.y + b.y) * 0.5 + h, (a.z + b.z) * 0.5, (b.x - a.x) * 0.5, (b.y - a.y) * 0.5, (b.z - a.z) * 0.5,
            0.0, h, 0.0, shape, variant, rgb, strength);
    }

    /**
     * The on-screen angle of a world direction seen from {@code at}, so a billboard's +y can be turned to point
     * along it (0 = straight up the screen... measured from +x).
     */
    public double screenAngle(Vec3 at, Vec3 direction) {
        Vec3 view = at.subtract(this.camera);
        // remove the part of the direction that points along the line of sight
        double len = view.length();
        Vec3 forward = len < 1.0E-6 ? new Vec3(0, 0, 1) : view.scale(1.0 / len);
        Vec3 flat = direction.subtract(forward.scale(direction.dot(forward)));
        double x = flat.x * this.right.x + flat.y * this.right.y + flat.z * this.right.z;
        double y = flat.x * this.up.x + flat.y * this.up.y + flat.z * this.up.z;
        return Math.atan2(y, x) - Math.PI / 2.0;
    }

    /**
     * A ribbon that always faces the camera, from {@code head} to {@code tail}, cut into {@code pieces} so it can
     * bend (each point is head + (tail - head) * k + bend * k^2) and taper. Its uv x runs -1 at the head to 1 at the
     * tail, as the STREAK shape expects.
     */
    public void ribbon(Vec3 head, Vec3 tail, Vec3 bend, double headWidth, double tailWidth, int pieces,
                       int shape, int variant, int rgb, float strength) {
        if (strength <= 0.003F) {
            return;
        }
        float u0 = shape * 4.0F;
        float v0 = variant * 4.0F;
        Vec3 axis = tail.subtract(head);
        Vec3 prev = null;
        Vec3 prevSide = null;
        float prevU = 0.0F;
        for (int i = 0; i <= pieces; i++) {
            double k = (double) i / pieces;
            Vec3 p = head.add(axis.scale(k)).add(bend.scale(k * k));
            // the ribbon's local direction, for its width to lie across it as seen from the camera
            double k2 = Math.min(1.0, k + 1.0 / pieces);
            double k1 = Math.max(0.0, k - 1.0 / pieces);
            Vec3 dir = head.add(axis.scale(k2)).add(bend.scale(k2 * k2)).subtract(head.add(axis.scale(k1)).add(bend.scale(k1 * k1)));
            Vec3 toCamera = this.camera.subtract(p);
            Vec3 side = dir.cross(toCamera);
            double sl = side.length();
            side = sl < 1.0E-9 ? new Vec3(this.up.x, this.up.y, this.up.z) : side.scale(1.0 / sl);
            double width = headWidth + (tailWidth - headWidth) * k;
            side = side.scale(width);
            float u = u0 + (float) (2.0 * k);
            if (prev != null) {
                this.vertex(prev.x - prevSide.x, prev.y - prevSide.y, prev.z - prevSide.z, prevU, v0, rgb, strength);
                this.vertex(p.x - side.x, p.y - side.y, p.z - side.z, u, v0, rgb, strength);
                this.vertex(p.x + side.x, p.y + side.y, p.z + side.z, u, v0 + 2.0F, rgb, strength);
                this.vertex(prev.x + prevSide.x, prev.y + prevSide.y, prev.z + prevSide.z, prevU, v0 + 2.0F, rgb, strength);
            }
            prev = p;
            prevSide = side;
            prevU = u;
        }
    }

    /** A horizontal quad (lying on the ground), half-sizes along a horizontal unit axis and across it. */
    public void flat(Vec3 centre, Vec3 axis, double halfAlong, double halfAcross, int shape, int variant, int rgb, float strength) {
        double ax = axis.x * halfAlong;
        double az = axis.z * halfAlong;
        double bx = -axis.z * halfAcross;
        double bz = axis.x * halfAcross;
        // uv y runs along the axis, so arrow shapes (pointing +y) point along it
        this.quad(centre.x, centre.y, centre.z, bx, 0.0, bz, ax, 0.0, az, shape, variant, rgb, strength);
    }

    /** A vertical quad standing on {@code base}, facing the camera around the vertical axis (a column of light). */
    public void column(Vec3 base, double height, double halfWidth, int shape, int variant, int rgb, float strength) {
        Vec3 toCamera = this.camera.subtract(base);
        Vec3 flat = new Vec3(toCamera.x, 0.0, toCamera.z);
        double l = flat.length();
        Vec3 side = l < 1.0E-6 ? new Vec3(1, 0, 0) : new Vec3(-flat.z / l, 0.0, flat.x / l);
        double h = height * 0.5;
        // uv x = -1 at the base .. 1 at the top, so a STREAK fades upward
        this.quad(base.x, base.y + h, base.z, 0.0, h, 0.0, side.x * halfWidth, 0.0, side.z * halfWidth, shape, variant, rgb, strength);
    }
}
