package dev.ss05.halley.clienttest;

import cyou.rimuru.shootingstardemo.mc1211.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.mc1211.magic.Casting;
import cyou.rimuru.shootingstardemo.mc1211.magic.Skill;
import cyou.rimuru.shootingstardemo.mc1211.magic.Tuning;
import cyou.rimuru.shootingstardemo.mc1211.registry.ModItems;
import dev.ss05.halley.compat.StarBridge;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;

/**
 * The actual harness: creates a flat creative world, places the player, casts a skill for real through The
 * Shooting Star, and screenshots it at the requested ticks. Entirely driven by {@link HarnessParams}; see that
 * class for what each property does.
 *
 * <p>Two client-side event hooks drive it ({@link Ss05ClientTestMod}):
 * <ul>
 *   <li>{@link #onClientTick} - the slow state machine (create world, wait for it, place the player, settle,
 *       cast). Ticks are coarse; nothing here needs frame precision.</li>
 *   <li>{@link #onRenderFrame} - watches the level's game time and takes the screenshots, because a shot has to
 *       happen at the end of an actually-rendered frame, not merely "when a tick happens".</li>
 * </ul>
 */
final class Harness {
    private static final String LEVEL_ID = "ss05test";
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Z = 0.5;

    private static boolean worldCreateRequested;
    private static boolean placed;
    private static long settleUntilTick = -1;
    private static volatile boolean castRequested;
    private static volatile long castGameTime = -1;
    private static int nextShotIndex;
    private static volatile boolean done;
    private static volatile double groundY;

    private Harness() {
    }

    // ---- The slow state machine: world creation, placement, settling, casting. --------------------------------

    private static long nextStatusLogMillis = 0;

    static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        // No human here to dismiss the "Move with W, A, S and D" tutorial toast or any other.
        mc.options.tutorialStep = TutorialSteps.NONE;
        // nobody is at the keyboard: losing focus (another window) must not pause the game mid-strike
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        mc.getToasts().clear();

        if (System.currentTimeMillis() >= nextStatusLogMillis) {
            nextStatusLogMillis = System.currentTimeMillis() + 2000;
            log("status: screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getName())
                + " level=" + (mc.level != null) + " player=" + (mc.player != null)
                + " singleplayerServer=" + (mc.getSingleplayerServer() != null)
                + " worldCreateRequested=" + worldCreateRequested + " placed=" + placed
                + " castRequested=" + castRequested);
        }

        if (mc.level == null) {
            if (!worldCreateRequested) {
                // Nothing here to click through a first-run screen: Minecraft's own accessibility onboarding,
                // The Shooting Star's GPU warning (GpuIncompatibleScreen, which only offers "Exit game"), or
                // anything else that isn't the title screen. Bulldoze straight to the title screen. Once world
                // creation has actually been requested, leave the screen alone - it is legitimately a loading
                // screen (LevelLoadingScreen/ReceivingLevelScreen) that clears itself once the level arrives.
                if (!(mc.screen instanceof TitleScreen)) {
                    if (mc.screen != null) {
                        log("skipping screen " + mc.screen.getClass().getName() + " (no human here to click through it)");
                    }
                    mc.setScreen(new TitleScreen());
                    return;
                }
                worldCreateRequested = true;
                createWorld(mc);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }

        if (!placed) {
            placed = true;
            placePlayerAndSetUpWorld(mc);
            // "wait ~3s for the world to settle" = 60 ticks.
            settleUntilTick = mc.level.getGameTime() + 60;
            return;
        }

        if (mc.level.getGameTime() < settleUntilTick) {
            return;
        }

        if (!castRequested) {
            castRequested = true;
            if ("third".equals(HarnessParams.VIEW)) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            castGameTime = mc.level.getGameTime();
            castAndMaybeRepositionCamera(mc);
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            Path savesDir = mc.gameDirectory.toPath().resolve("saves").resolve(LEVEL_ID);
            deleteRecursive(savesDir);
        } catch (IOException e) {
            log("couldn't delete the old test save (continuing anyway): " + e);
        }

        LevelSettings levelSettings = new LevelSettings(LEVEL_ID, GameType.CREATIVE, false, Difficulty.PEACEFUL,
            true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions worldOptions = WorldOptions.defaultWithRandomSeed();
        Function<RegistryAccess, WorldDimensions> dimensions = access ->
            access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions();
        Screen parent = mc.screen != null ? mc.screen : new TitleScreen();

        log("creating world '" + LEVEL_ID + "'");
        mc.createWorldOpenFlows().createFreshLevel(LEVEL_ID, levelSettings, worldOptions, dimensions, parent);
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log("couldn't delete " + p + ": " + e);
                }
            });
        }
    }

    private static void placePlayerAndSetUpWorld(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        log("world ready, placing the player");
        server.execute(() -> {
            ServerLevel level = server.overworld();
            MinecraftServer mcServer = level.getServer();
            ServerPlayer player = mcServer.getPlayerList().getPlayers().get(0);

            level.setDayTime(HarnessParams.TIME);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mcServer);
            level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);
            level.resetWeatherCycle();

            // Force the spawn column loaded so getHeight below is accurate, not a fallback guess.
            level.getChunk(0, 0);
            groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);

            double eyeHeight = Player.DEFAULT_EYE_HEIGHT;
            float pitch = (float) Math.toDegrees(Math.atan2(eyeHeight, HarnessParams.DISTANCE));
            player.teleportTo(level, SPAWN_X, groundY, SPAWN_Z, -90f, pitch);

            player.setGameMode(GameType.CREATIVE);
            player.getInventory().setItem(player.getInventory().selected, new ItemStack(ModItems.STELLAR_REMOTE));

            CutsceneDirector.setEnabled(HarnessParams.FILM);
        });
    }

    private static void castAndMaybeRepositionCamera(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        log("casting " + HarnessParams.SKILL);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);

            Skill skill = HarnessParams.MOON ? StarBridge.MOON : StarBridge.SKILL;
            int index = HarnessParams.MOON ? StarBridge.moonIndex() : StarBridge.skillIndex();
            if (index < 0) {
                log("SS-05/SS-06 never attached to the Stellar Remote (StarBridge.problem(): " + StarBridge.problem() + ")");
                return;
            }
            if (Boolean.getBoolean("ss05.clienttest.eat")) {
                eatCheck(level, player);
            }
            Tuning tuning = Tuning.initial(skill);
            Casting.request(player, index, tuning.power(), tuning.damage(), tuning.speed());

            // So the very first shot (offset 0) already has the right camera too.
            maintainCamera(level, player);
        });
    }

    /** -Dss05.clienttest.eat=true: eats one of each moon cheese through the real item path and logs what it did. */
    private static void eatCheck(ServerLevel level, ServerPlayer player) {
        player.getFoodData().setFoodLevel(5);
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOON_CHEESE_ITEM.get()).finishUsingItem(level, player);
        log("eat moon_cheese: food 5 -> " + player.getFoodData().getFoodLevel() + ", saturation " + player.getFoodData().getSaturationLevel());
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM.get()).finishUsingItem(level, player);
        net.minecraft.world.effect.MobEffectInstance strength = player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
        net.minecraft.world.effect.MobEffectInstance might = player.getEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT.get()));
        log("eat molten_moon_cheese: maxHealth " + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            + " health " + player.getHealth()
            + " strength " + (strength == null ? "none" : "amplifier " + strength.getAmplifier() + " for " + strength.getDuration() + " ticks")
            + " molten_might " + (might == null ? "none" : might.getDuration() + " ticks"));
        // a relog, in miniature: save the player and load the data into a fresh player. (NeoForge's FakePlayerFactory
        // has no Forge equivalent, so this uses the plain vanilla ServerPlayer constructor instead - loader-agnostic.)
        net.minecraft.nbt.CompoundTag saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        ServerPlayer reloaded = new ServerPlayer(level.getServer(), level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ss05reload"),
            net.minecraft.server.level.ClientInformation.createDefault());
        reloaded.load(saved);
        log("after a save/load: maxHealth " + reloaded.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            + " health " + reloaded.getHealth()
            + " strength " + (reloaded.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST) == null ? "none"
                : "amplifier " + reloaded.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST).getAmplifier()));
        // and again after the effects are gone, as if they ran out
        player.removeEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT.get()));
        log("after molten_might removed: maxHealth " + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM.get()).finishUsingItem(level, player);
    }

    /**
     * Re-applied every server tick from {@link #onServerTick} while a cast is in flight, because the spell (its
     * evacuation lift, or the caster's own cutscene) can otherwise pull the player back to the cast spot mid-shot.
     * Does nothing for view=first/third, which just ride along with wherever the caster ends up.
     */
    private static void maintainCamera(ServerLevel level, ServerPlayer player) {
        double markX = SPAWN_X + HarnessParams.DISTANCE;
        double markZ = SPAWN_Z;
        double markY = groundY;

        if ("wide".equals(HarnessParams.VIEW)) {
            double camX = markX;
            double camY = markY + 40;
            double camZ = markZ + 180;

            Abilities abilities = player.getAbilities();
            if (!abilities.flying) {
                abilities.flying = true;
                player.onUpdateAbilities();
            }

            // Aim a bit above the mark, not at it, so the sky it falls out of is in frame too.
            float[] yawPitch = lookAt(camX, camY, camZ, markX, markY + 60, markZ);
            teleportAndFace(level, player, camX, camY, camZ, yawPitch[0], yawPitch[1]);
        } else if ("up".equals(HarnessParams.VIEW)) {
            // Stay at the cast spot; just look back up along the line the moon (or comet) comes in on - toward
            // the mark horizontally, tipped up into the sky - so the fall is centred instead of looking at the
            // ground the whole time.
            float[] yawPitch = lookAt(player.getX(), player.getY(), player.getZ(), markX, markY, markZ);
            teleportAndFace(level, player, player.getX(), player.getY(), player.getZ(), yawPitch[0], -40f);
        }
    }

    /** Yaw/pitch (Minecraft convention: yaw = atan2(-dx, dz), pitch = -atan2(dy, horizontal)), {@code d = to - from}. */
    private static float[] lookAt(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[] {yaw, pitch};
    }

    /** teleportTo only sets yRot/xRot; the head (and body, for third person) has to be pointed explicitly too. */
    private static void teleportAndFace(ServerLevel level, ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRotO = yaw;
        player.yBodyRotO = yaw;
    }

    /** Keeps view=wide/up honest for the rest of the strike (see {@link #maintainCamera}). */
    static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (!castRequested || done) {
            return;
        }
        if (!"wide".equals(HarnessParams.VIEW) && !"up".equals(HarnessParams.VIEW)) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!(server instanceof IntegratedServer integrated) || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        maintainCamera(integrated.overworld(), server.getPlayerList().getPlayers().get(0));
    }

    // ---- Screenshots, frame-precise. -----------------------------------------------------------------------------

    static void onRenderFrame(TickEvent.RenderTickEvent.Post event) {
        if (done || castGameTime < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long gameTime = mc.level.getGameTime();
        long[] shots = HarnessParams.SHOTS;
        while (nextShotIndex < shots.length && gameTime >= castGameTime + shots[nextShotIndex]) {
            takeScreenshot(mc, shots[nextShotIndex]);
            nextShotIndex++;
        }
        if (nextShotIndex >= shots.length) {
            done = true;
            log("done");
            mc.stop();
        }
    }

    private static void takeScreenshot(Minecraft mc, long tick) {
        String fileName = HarnessParams.SKILL + "_" + String.format("%05d", tick) + ".png";
        try {
            Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                component -> log("shot " + new File(new File(mc.gameDirectory, "screenshots"), fileName)));
        } catch (Throwable t) {
            log("exception taking screenshot for tick " + tick + ": " + t);
        }
    }

    static void log(String message) {
        System.out.println("[SS05CLIENTTEST] " + message);
    }
}
