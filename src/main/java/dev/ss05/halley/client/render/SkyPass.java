package dev.ss05.halley.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0 on main: with a shader pack on and the camera under the cloud layer, Luna's moon, Halley's comet and Proxima's
 * sky gate are drawn from inside the game's own sky drawing (a mixin into {@code LevelRenderer.renderSky}, after
 * {@code getStarBrightness}), so the pack keeps them in its sky and draws its clouds over them.
 *
 * <p>Not on this branch (Fabric 1.21.11): 1.21.11 draws its sky in its own frame graph and has no such method, and Iris
 * on 1.21.11 has no sky stage we can reach (TON 618 Addition's 1.21.11 port, {@code QuasarAtlas}, found the same and left
 * its stars in the world pass). So here nothing is ever "in the sky pass": {@link #above} is always false and the moon,
 * the comet and the gate are drawn as in 1.8.3 (with a pack they can show over the pack's clouds).
 */
// TODO(port): the sky pass behind a shader pack's clouds (main's SkyPass + LevelRendererSkyMixin, ProximaCosmos.paintSkyCard)
//  isn't built on Fabric 1.21.11: no LevelRenderer.renderSky / getStarBrightness here and no Iris sky stage to draw in.
public final class SkyPass {
    private SkyPass() {
    }

    /** The cloud layer's height while the sky pass is on: never on this branch. */
    public static double clouds(Vec3 camera) {
        return Double.NaN;
    }

    /** Whether something whose lowest point is at {@code bottom} is drawn in the sky pass this frame: never here. */
    public static boolean above(Vec3 camera, double bottom) {
        return false;
    }

    /** Whether the sky pass doesn't run (always, on this branch: everything is drawn in the world pass, as before 1.9.0). */
    public static boolean broken() {
        return true;
    }
}
