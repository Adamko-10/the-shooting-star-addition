package dev.ss05.halley.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0's sky pass (main: Luna's moon, Halley's comet and Proxima's sky gate drawn from inside the game's own sky
 * drawing while a shader pack is on, so the pack keeps them behind its clouds).
 *
 * <p>TODO(port): not built on Fabric 26.3. 26.3 draws its sky its own way (no {@code LevelRenderer.renderSky} with
 * {@code getStarBrightness} to hook, no {@code ShaderInstance}s), and Iris on 26.3 has no stage reachable there that a
 * pack treats as its sky: TON 618 Addition's own 26.3 port found the same and leaves its sky things in the world pass
 * ({@code QuasarAtlas}, "There is no drawing inside the sky with a pack here"). So here the sky pass is always off: the
 * moon, the comet and the gate are drawn as in 1.8.3 (with a pack, over the pack's clouds), and
 * {@code ProximaCosmos.paintSkyCard} (main's offscreen cards for the gate) isn't ported either.
 */
public final class SkyPass {
    private SkyPass() {
    }

    /** Always true on 26.3: there is no sky pass here (see the class comment), so everything stays in the world pass. */
    public static boolean broken() {
        return true;
    }

    /** Whether something whose lowest point is {@code bottom} would be drawn in the sky pass: never on 26.3. */
    public static boolean above(Vec3 camera, double bottom) {
        return false;
    }
}
