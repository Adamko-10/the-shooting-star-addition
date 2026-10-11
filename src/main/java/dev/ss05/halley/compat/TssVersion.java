package dev.ss05.halley.compat;

import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Which version of The Shooting Star is loaded, and which copy of compat/tss/ in the release jar belongs to it. Asks
 * Fabric Loader, so it already works while mixin configs are read (see {@link TssMixins}).
 */
public final class TssVersion {
    /** The Shooting Star's mod id (shooting_star_mod_id in gradle.properties). */
    public static final String MOD_ID = "shooting_star_demo";

    private TssVersion() {
    }

    /** The loaded The Shooting Star's version, such as "1.4.1-demo+mc26.3", or null if it isn't loaded. */
    public static String running() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse(null);
    }

    /**
     * The package name the release jar gives the copy of compat/tss/ for a version: "1.4.1-demo+mc26.3" is "v1_4_1".
     * Only the numbers count, so a re-upload with the same number (such as 1.4.0A, still "1.4.0-demo") uses the same
     * copy. gradle/tss.gradle names the copies the same way.
     */
    public static String tag(String version) {
        String numbers = version.split("[-+ ]", 2)[0];
        return "v" + numbers.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }
}
