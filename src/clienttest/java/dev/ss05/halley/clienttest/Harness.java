package dev.ss05.halley.clienttest;

import com.mojang.authlib.GameProfile;
import cyou.rimuru.shootingstardemo.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.magic.Casting;
import cyou.rimuru.shootingstardemo.magic.Skill;
import cyou.rimuru.shootingstardemo.magic.Tuning;
import cyou.rimuru.shootingstardemo.registry.ModItems;
import dev.ss05.halley.compat.StarBridge;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * The actual harness: creates a flat creative world, places the player, casts a skill for real through The
 * Shooting Star, and screenshots it at the requested ticks. Entirely driven by {@link HarnessParams}; see that
 * class for what each property does.
 *
 * <p>One client-side event hook drives it ({@link Ss05ClientTestMod}): {@link #onClientTick}, both the slow state
 * machine (create world, wait for it, place the player, settle, cast) and the screenshots. Fabric 26.3 has no
 * per-rendered-frame client event; a screenshot requested when {@code gameTime} first reaches a shot's tick is
 * actually grabbed one tick later, by when Minecraft's main loop has rendered at least one frame of that tick's
 * state (close enough for eyeballing an effect - this harness was never meant for pixel-exact regression tests).
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
    private static boolean shotPending;
    private static long pendingShotTick;
    private static volatile boolean done;
    private static volatile double groundY;

    private Harness() {
    }

    // ---- The slow state machine: world creation, placement, settling, casting, screenshots. -------------------

    private static long nextStatusLogMillis = 0;

    static void onClientTick(Minecraft mc) {
        if (done) {
            return;
        }

        // No human here to dismiss the "Move with W, A, S and D" tutorial toast or any other.
        mc.options.tutorialStep = TutorialSteps.NONE;
        // nobody is at the keyboard: losing focus (another window) must not pause the game mid-strike
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        mc.getToastManager().clear();

        if (System.currentTimeMillis() >= nextStatusLogMillis) {
            nextStatusLogMillis = System.currentTimeMillis() + 2000;
            log("status: screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getName())
                + " level=" + (mc.level != null) + " player=" + (mc.player != null)
                + " singleplayerServer=" + (mc.getSingleplayerServer() != null)
                + " worldCreateRequested=" + worldCreateRequested + " placed=" + placed
                + " castRequested=" + castRequested);
        }

        if (shotPending) {
            shotPending = false;
            takeScreenshot(mc, pendingShotTick);
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
            return;
        }

        checkForShot(mc);
    }

    private static void checkForShot(Minecraft mc) {
        if (done || castGameTime < 0 || mc.level == null) {
            return;
        }
        long gameTime = mc.level.getGameTime();
        long[] shots = HarnessParams.SHOTS;
        if (nextShotIndex < shots.length && gameTime >= castGameTime + shots[nextShotIndex]) {
            shotPending = true;
            pendingShotTick = shots[nextShotIndex];
            nextShotIndex++;
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            Path savesDir = mc.gameDirectory.toPath().resolve("saves").resolve(LEVEL_ID);
            deleteRecursive(savesDir);
        } catch (IOException e) {
            log("couldn't delete the old test save (continuing anyway): " + e);
        }

        LevelSettings levelSettings = new LevelSettings(LEVEL_ID, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        WorldOptions worldOptions = WorldOptions.defaultWithRandomSeed();
        Function<HolderLookup.Provider, WorldDimensions> dimensions = access ->
            access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
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
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, mcServer);
            level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, mcServer);
            level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);

            // Force the spawn column loaded so getHeight below is accurate, not a fallback guess.
            level.getChunk(0, 0);
            groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);

            double eyeHeight = Avatar.DEFAULT_EYE_HEIGHT;
            float pitch = (float) Math.toDegrees(Math.atan2(eyeHeight, HarnessParams.DISTANCE));
            player.teleportTo(level, SPAWN_X, groundY, SPAWN_Z, Set.of(), -90f, pitch, true);

            player.setGameMode(GameType.CREATIVE);
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(ModItems.STELLAR_REMOTE));

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
        new ItemStack(dev.ss05.halley.content.HalleyContent.MOON_CHEESE_ITEM).finishUsingItem(level, player);
        log("eat moon_cheese: food 5 -> " + player.getFoodData().getFoodLevel() + ", saturation " + player.getFoodData().getSaturationLevel());
        new ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM).finishUsingItem(level, player);
        MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
        MobEffectInstance might = player.getEffect(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT);
        log("eat molten_moon_cheese: maxHealth " + player.getAttributeValue(Attributes.MAX_HEALTH)
            + " health " + player.getHealth()
            + " strength " + (strength == null ? "none" : "amplifier " + strength.getAmplifier() + " for " + strength.getDuration() + " ticks")
            + " molten_might " + (might == null ? "none" : might.getDuration() + " ticks"));
        // a relog, in miniature: save the player and load the data into a fresh player
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        player.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        ServerPlayer reloaded = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ss05reload"),
            ClientInformation.createDefault());
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved);
        reloaded.load(input);
        log("after a save/load: maxHealth " + reloaded.getAttributeValue(Attributes.MAX_HEALTH)
            + " health " + reloaded.getHealth()
            + " strength " + (reloaded.getEffect(MobEffects.STRENGTH) == null ? "none"
                : "amplifier " + reloaded.getEffect(MobEffects.STRENGTH).getAmplifier()));
        // and again after the effects are gone, as if they ran out
        player.removeEffect(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT);
        log("after molten_might removed: maxHealth " + player.getAttributeValue(Attributes.MAX_HEALTH));
        new ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM).finishUsingItem(level, player);
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
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRotO = yaw;
        player.yBodyRotO = yaw;
    }

    /** Keeps view=wide/up honest for the rest of the strike (see {@link #maintainCamera}). */
    static void onServerTick(MinecraftServer server) {
        if (!castRequested || done) {
            return;
        }
        if (!"wide".equals(HarnessParams.VIEW) && !"up".equals(HarnessParams.VIEW)) {
            return;
        }
        if (!(server instanceof IntegratedServer integrated) || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        maintainCamera(integrated.overworld(), server.getPlayerList().getPlayers().get(0));
    }

    // ---- Screenshots. ----------------------------------------------------------------------------------------

    private static void takeScreenshot(Minecraft mc, long tick) {
        String fileName = HarnessParams.SKILL + "_" + String.format("%05d", tick) + ".png";
        log("camera at tick " + tick + ": " + mc.gameRenderer.getMainCamera().position() + " xRot " + mc.gameRenderer.getMainCamera().xRot() + " yRot " + mc.gameRenderer.getMainCamera().yRot() + " player " + (mc.player == null ? null : mc.player.position()) + " window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
        try {
            Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(), 1,
                component -> log("shot " + new File(new File(mc.gameDirectory, "screenshots"), fileName)));
        } catch (Throwable t) {
            log("exception taking screenshot for tick " + tick + ": " + t);
        }
        if (nextShotIndex >= HarnessParams.SHOTS.length) {
            done = true;
            log("done");
            mc.stop();
        }
    }

    static void log(String message) {
        System.out.println("[SS05CLIENTTEST] " + message);
    }
}
