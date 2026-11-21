package dev.ss05.halley;

import net.minecraftforge.fml.ModList;

/** Whether another mod is loaded in this game (each loader asks its own mod list; a wrong-loader jar isn't loaded). */
public final class OtherAddons {
    private OtherAddons() {
    }

    public static boolean loaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }
}
