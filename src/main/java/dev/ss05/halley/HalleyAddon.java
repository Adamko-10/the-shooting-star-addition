package dev.ss05.halley;

import com.mojang.logging.LogUtils;
import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * The Shooting Star Addition — an unofficial addon for The Shooting Star. Adds SS-05 · Halley to the Stellar Remote.
 *
 * <p>Where things live:
 * <ul>
 *   <li>{@code compat/}  — the ONLY code that touches The Shooting Star. When that mod updates, start there.</li>
 *   <li>{@link HalleyConfig} — every number you might want to tune (also written to config/shooting_star_addition-common.toml).</li>
 *   <li>{@link HalleyClientConfig} — how it looks on each player's screen (config/shooting_star_addition-client.toml).</li>
 *   <li>{@link HalleyPlan} — the timeline and the shape of the strike, shared by the server and the client film.</li>
 *   <li>{@code world/} — what the strike does to the world (server side).</li>
 *   <li>{@code client/} — what it looks and sounds like (client side).</li>
 * </ul>
 */
@Mod(HalleyAddon.MOD_ID)
public final class HalleyAddon {
    public static final String MOD_ID = "shooting_star_addition";
    public static final Logger LOG = LogUtils.getLogger();

    public HalleyAddon(IEventBus modBus, ModContainer container, Dist dist) {
        container.registerConfig(ModConfig.Type.COMMON, HalleyConfig.SPEC);
        HalleyContent.register(modBus);

        // Mods are constructed in parallel. Hooking into The Shooting Star waits until every mod has been
        // constructed (the deferred queue runs on the main thread), but still runs before key bindings are
        // registered, so SS-05 gets its own key like the other three skills.
        modBus.addListener(FMLConstructModEvent.class, event -> event.enqueueWork(() -> {
            StarBridge.install();
            if (dist.isClient()) {
                HalleyClient.afterConstruction();
            }
        }));

        if (dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, HalleyClientConfig.SPEC);
            HalleyClient.init(modBus);
        }

        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, HalleyAddon::warnIfDetached);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** If The Shooting Star changed under us, say so in chat instead of failing silently. */
    private static void warnIfDetached(PlayerEvent.PlayerLoggedInEvent event) {
        if (!StarBridge.installed() && event.getEntity() instanceof ServerPlayer player && player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("[The Shooting Star Addition] SS-05 Halley couldn't attach to The Shooting Star ("
                    + StarBridge.problem() + "). The skill is disabled until the addon is updated for this version - see the log.")
                .withStyle(ChatFormatting.RED));
        }
    }
}
