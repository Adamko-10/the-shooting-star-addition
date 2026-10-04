package dev.ss05.halley.client.render;

import dev.ss05.halley.HalleyPlan;
import java.util.function.DoubleBinaryOperator;
import net.minecraft.world.phys.Vec3;

/**
 * Where the projected marks lie on the land before the strike: the mark itself, the crater's outline, the corridor's
 * arrows and edges, and the touchdown point. Their heights are sampled once from the world (when the mark lands), so
 * the marks hug hills and valleys.
 */
public final class GroundMarks {
    public static final int RIM_POINTS = 96;
    public static final double CHEVRON_STEP = 14.0;
    public static final double EDGE_STEP = 6.0;
    /** How far above the ground the marks float, so they don't flicker into it. */
    private static final double LIFT = 0.07;

    public final Vec3 mark;
    public final Vec3[] rim;
    public final double[] rimAngle;
    public final Vec3[] chevrons;
    public final double[] chevronAlong;
    public final Vec3[] leftEdge;
    public final Vec3[] rightEdge;
    public final double[] edgeAlong;
    public final Vec3 touchdown;

    /** {@code surface} gives the first air block above the ground at (x, z), or NaN where it isn't known. */
    public GroundMarks(HalleyPlan plan, DoubleBinaryOperator surface) {
        this.mark = onGround(plan.target.x, plan.target.z, plan.target.y, surface);

        this.rim = new Vec3[RIM_POINTS];
        this.rimAngle = new double[RIM_POINTS];
        for (int i = 0; i < RIM_POINTS; i++) {
            double theta = i * Math.PI * 2.0 / RIM_POINTS;
            Vec3 p = plan.craterRim(theta);
            this.rim[i] = onGround(p.x, p.z, plan.target.y, surface);
            this.rimAngle[i] = theta;
        }

        int length = plan.params.trenchLength();
        int chevrons = Math.max(1, (int) Math.floor((length - 8.0) / CHEVRON_STEP) + 1);
        this.chevrons = new Vec3[chevrons];
        this.chevronAlong = new double[chevrons];
        for (int i = 0; i < chevrons; i++) {
            double along = 8.0 + i * CHEVRON_STEP;
            Vec3 p = plan.groundPoint(along);
            this.chevrons[i] = onGround(p.x, p.z, p.y, surface);
            this.chevronAlong[i] = along;
        }

        int edges = (int) Math.floor(length / EDGE_STEP) + 1;
        this.leftEdge = new Vec3[edges];
        this.rightEdge = new Vec3[edges];
        this.edgeAlong = new double[edges];
        for (int i = 0; i < edges; i++) {
            double along = i * EDGE_STEP;
            Vec3 p = plan.groundPoint(along);
            double hw = plan.trenchHalfWidth(along);
            this.leftEdge[i] = onGround(p.x + plan.side.x * hw, p.z + plan.side.z * hw, p.y, surface);
            this.rightEdge[i] = onGround(p.x - plan.side.x * hw, p.z - plan.side.z * hw, p.y, surface);
            this.edgeAlong[i] = along;
        }

        Vec3 t = plan.touchdown;
        this.touchdown = onGround(t.x, t.z, t.y, surface);
    }

    private static Vec3 onGround(double x, double z, double fallback, DoubleBinaryOperator surface) {
        double y = surface.applyAsDouble(x, z);
        return new Vec3(x, (Double.isNaN(y) ? fallback : y) + LIFT, z);
    }
}
