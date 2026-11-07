package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaPlan;
import net.minecraft.world.phys.Vec3;

/** Shared scene measurements retained for the captions and the client-test camera on Fabric 26.3. */
public final class ProximaCosmos {
    private ProximaCosmos() {
    }

    // TODO(port): Port the space/Earth scene to RenderPipelines and an extracted GUI texture with its own depth.
    // Main's immediate ShaderInstance/VertexBuffer draws cannot run in GuiGraphicsExtractor. Until that renderer
    // is validated, ProximaStoryboard's world camera stays visible throughout ORBIT..FLASH. The source shader and
    // Earth images are preserved unchanged; no partial space pass is registered.

    /** How high over Earth (in its radii) the orbit shot is, q of the way through it. */
    public static double altitude(double q) {
        return 0.5 * Math.pow(4.2, q);
    }

    // TODO(port): Port the sky gate's opaque/additive/translucent materials to the 26.3 world depth pipeline.
    // It is deliberately not drawn until the complete gate (ring, incoming bolt and refocused beam) can be verified.

    /** The gate's intended location, also used by the proximasky client-test view. */
    public static Vec3 gatePosition(ProximaPlan plan) {
        Vec3 from = plan.frontDirection().scale(-1.0);
        Vec3 flat = new Vec3(from.x, 0.0, from.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        return plan.target.add(flat.scale(180.0)).add(0.0, 360.0, 0.0);
    }
}
