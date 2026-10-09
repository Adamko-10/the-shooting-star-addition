package dev.ss05.halley.client.sky;

import net.minecraft.world.phys.Vec3;

/**
 * What one strike does to the sky at one moment, already scaled by how close the camera is to it. All 0..1 unless
 * said otherwise. Made by {@code HalleyFx.sky}; drawn by {@link HalleySky}.
 *
 * @param night     how far the sky has gone dark
 * @param stars     stars and the band of the galaxy
 * @param aurora    the aurora
 * @param veil      the pale haze of ice in the air after the impact
 * @param halo      the halo, sun dogs and pillar in that haze
 * @param flash     the sky flaring white (touchdown, impact)
 * @param cometDir  unit vector from the camera toward the comet's light
 * @param cometGlow how strongly it lights the air round it (can go above 1)
 * @param cometHeat 0 = the comet's icy blue, 1 = the white-hot orange of the fireball
 * @param tailDir   unit vector the tails stream away along
 * @param darkLand  how far the land's daylight is turned down
 * @param seed      picks this strike's stars and aurora
 * @param timeShift ticks to wind the sky's clock on by, while a shader pack draws the night instead (0..24000)
 * @param palette   which colours {@code night} and {@code veil} are painted in: {@link #PALETTE_HALLEY} (icy blue,
 *                  SS-05's own) or {@link #PALETTE_LUNA} (SS-06's sick red-orange)
 */
public record SkyLook(float night, float stars, float aurora, float veil, float halo, float flash, Vec3 cometDir,
                      float cometGlow, float cometHeat, Vec3 tailDir, float darkLand, float seed, float timeShift,
                      int palette) {
    /** SS-05 Halley's own night and veil: cold navy blue going dark, a pale icy haze after the impact. */
    public static final int PALETTE_HALLEY = 0;
    /** SS-06 Luna's: a sick red-orange night under the alarm, a warm dusty haze in the aftermath. */
    public static final int PALETTE_LUNA = 1;

    /** How much of the vanilla sky this covers. */
    public float cover() {
        return 1.0F - (1.0F - this.night) * (1.0F - this.veil * 0.75F);
    }

    public boolean visible() {
        return this.cover() > 0.002F || this.stars > 0.002F || this.aurora > 0.002F || this.halo > 0.002F
            || this.flash > 0.002F || this.cometGlow > 0.002F;
    }
}
