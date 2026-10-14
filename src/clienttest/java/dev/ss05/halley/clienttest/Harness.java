package dev.ss05.halley.clienttest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import cyou.rimuru.shootingstardemo.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.magic.Casting;
import cyou.rimuru.shootingstardemo.magic.Skill;
import cyou.rimuru.shootingstardemo.magic.Tuning;
import cyou.rimuru.shootingstardemo.registry.ModItems;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.luna.MoonFx;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

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
public final class Harness {
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
    private static long sectionsWaitSince = -1;

    private Harness() {
    }

    // ---- The slow state machine: world creation, placement, settling, casting. --------------------------------

    private static long nextStatusLogMillis = 0;

    public static void onClientTick() {
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
                // anything else that isn't the title screen. The Shooting Star puts its GPU warning back every time
                // the title screen opens (a software renderer such as llvmpipe always fails its check), so the world
                // is created straight from whatever screen is up, once the game has finished loading. After that,
                // leave the screen alone - it is legitimately a loading screen (LevelLoadingScreen /
                // ReceivingLevelScreen) that clears itself once the level arrives.
                if (mc.getOverlay() != null || mc.screen == null) {
                    return;
                }
                if (!(mc.screen instanceof TitleScreen)) {
                    log("skipping screen " + mc.screen.getClass().getName() + " (no human here to click through it)");
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
            return;
        }
        // Still "Loading terrain..." (slow with a software renderer): the shots would only show that screen.
        if (mc.screen != null) {
            settleUntilTick = -1;
            return;
        }
        if (settleUntilTick < 0) {
            // "wait ~3s for the world to settle" = 60 ticks, counted from when the terrain screen went away.
            settleUntilTick = mc.level.getGameTime() + 60;
            return;
        }
        if (mc.level.getGameTime() < settleUntilTick) {
            return;
        }
        // and until the land round the mark has arrived and been built: a slow client (a software renderer on a small
        // machine) is sent its chunks slowly, and a shot of a strike over land that isn't there yet shows nothing.
        // Four minutes at the most.
        if (sectionsWaitSince < 0) {
            sectionsWaitSince = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - sectionsWaitSince < 240_000L
                && (!landArrived(mc) || !mc.levelRenderer.hasRenderedAllSections())) {
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

    /** Whether this client has every chunk within {@link HarnessParams#LAND} blocks of the mark. */
    private static boolean landArrived(Minecraft mc) {
        double markX = SPAWN_X + HarnessParams.DISTANCE;
        double markZ = SPAWN_Z;
        int r = (int) Math.ceil(HarnessParams.LAND / 16.0);
        int cx = (int) Math.floor(markX) >> 4;
        int cz = (int) Math.floor(markZ) >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!mc.level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
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
            world(access, access.registryOrThrow(Registries.WORLD_PRESET)
                .getOrThrow("normal".equals(HarnessParams.WORLD) ? WorldPresets.NORMAL : WorldPresets.FLAT).createWorldDimensions());
        Screen parent = new TitleScreen();

        log("creating world '" + LEVEL_ID + "'");
        mc.createWorldOpenFlows().createFreshLevel(LEVEL_ID, levelSettings, worldOptions, dimensions, parent);
    }

    /** The deep flat world: bedrock, a hundred blocks of stone, dirt and grass, plains (no trees, lakes or villages). */
    private static final String DEEP_FLAT = "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
        + "{\"block\":\"minecraft:stone\",\"height\":100},{\"block\":\"minecraft:dirt\",\"height\":3},"
        + "{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}";

    /**
     * The test world's land (see {@link HarnessParams#WORLD}): the preset's own, or for {@code deep} the flat preset's
     * with deep ground under it (the classic flat world is only four blocks deep: no room for a crater, or for most of a
     * buried moon).
     */
    private static WorldDimensions world(RegistryAccess access, WorldDimensions preset) {
        if (!"deep".equals(HarnessParams.WORLD)) {
            return preset;
        }
        FlatLevelGeneratorSettings deep = FlatLevelGeneratorSettings.CODEC
            .parse(RegistryOps.create(JsonOps.INSTANCE, access), JsonParser.parseString(DEEP_FLAT))
            .result().orElseThrow(() -> new IllegalStateException("the deep flat world's settings don't parse"));
        return preset.replaceOverworldGenerator(access, new FlatLevelSource(deep));
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

            Skill skill = (Skill) StarBridge.skill(HarnessParams.MOON);
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
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOON_CHEESE_ITEM).finishUsingItem(level, player);
        log("eat moon_cheese: food 5 -> " + player.getFoodData().getFoodLevel() + ", saturation " + player.getFoodData().getSaturationLevel());
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM).finishUsingItem(level, player);
        net.minecraft.world.effect.MobEffectInstance strength = player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
        net.minecraft.world.effect.MobEffectInstance might = player.getEffect(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT);
        log("eat molten_moon_cheese: maxHealth " + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            + " health " + player.getHealth()
            + " strength " + (strength == null ? "none" : "amplifier " + strength.getAmplifier() + " for " + strength.getDuration() + " ticks")
            + " molten_might " + (might == null ? "none" : might.getDuration() + " ticks"));
        // a relog, in miniature: save the player and load the data into a fresh player
        net.minecraft.nbt.CompoundTag saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        ServerPlayer reloaded = net.fabricmc.fabric.api.entity.FakePlayer.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ss05reload"));
        reloaded.load(saved);
        log("after a save/load: maxHealth " + reloaded.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            + " health " + reloaded.getHealth()
            + " strength " + (reloaded.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST) == null ? "none"
                : "amplifier " + reloaded.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST).getAmplifier()));
        // and again after the effects are gone, as if they ran out
        player.removeEffect(dev.ss05.halley.content.HalleyContent.MOLTEN_MIGHT);
        log("after molten_might removed: maxHealth " + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
        new net.minecraft.world.item.ItemStack(dev.ss05.halley.content.HalleyContent.MOLTEN_MOON_CHEESE_ITEM).finishUsingItem(level, player);
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

        if ("up".equals(HarnessParams.VIEW)) {
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
    public static void onServerTick(MinecraftServer server) {
        if (!castRequested || done) {
            return;
        }
        if (!"up".equals(HarnessParams.VIEW)) {
            return;
        }
        if (!(server instanceof IntegratedServer integrated) || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        maintainCamera(integrated.overworld(), server.getPlayerList().getPlayers().get(0));
    }

    // ---- Screenshots, frame-precise. -----------------------------------------------------------------------------

    /**
     * How far the strike has got, in ticks: its own clock on this client (the newest running HalleyFx/MoonFx), or -1
     * before it has started. Not the level's game time: a slow client (a software renderer) falls behind the server
     * and its game time then jumps ahead to catch up, while the strike's effects run tick by tick.
     */
    private static long strikeAge() {
        int age = -1;
        if (HarnessParams.MOON) {
            for (MoonFx fx : MoonFx.active()) {
                age = fx.age();
            }
        } else {
            for (HalleyFx fx : HalleyFx.active()) {
                age = fx.age();
            }
        }
        long now = Minecraft.getInstance().level.getGameTime();
        if (age >= 0) {
            lastAge = age;
            lastAgeAt = now;
            return age;
        }
        // once the strike is over its effects are gone: carry on counting from where they left off
        return lastAge < 0 ? -1 : lastAge + (now - lastAgeAt);
    }

    @javax.annotation.Nullable
    private static net.minecraft.world.phys.Vec3 lastMark;

    /** The running strike's mark, or the last one's, or null before there was one. */
    @javax.annotation.Nullable
    private static net.minecraft.world.phys.Vec3 strikeMark() {
        if (HarnessParams.MOON) {
            for (MoonFx fx : MoonFx.active()) {
                lastMark = fx.plan.target;
            }
        } else {
            for (HalleyFx fx : HalleyFx.active()) {
                lastMark = fx.plan.target;
            }
        }
        return lastMark;
    }

    private static long lastAge = -1;
    private static long lastAgeAt;

    /** What the strike left in the world, as the server has it: the land's height along two lines out from the mark. */
    private static void probeLand(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            int mx = (int) Math.floor(SPAWN_X + HarnessParams.DISTANCE);
            int mz = (int) Math.floor(SPAWN_Z);
            StringBuilder east = new StringBuilder();
            StringBuilder south = new StringBuilder();
            for (int d = 0; d <= 140; d += 4) {
                east.append(d).append('=').append(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx + d, mz) - (int) groundY).append(' ');
                south.append(d).append('=').append(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx, mz + d) - (int) groundY).append(' ');
            }
            log("land east of the mark (height over the old ground): " + east);
            log("land south of the mark (height over the old ground): " + south);
            log("block at the mark's core: " + level.getBlockState(new net.minecraft.core.BlockPos(mx, (int) groundY - 5, mz)));
        });
    }

    @javax.annotation.Nullable
    private static Marker camera;

    /**
     * The views that film from a fixed spot (not through the caster's eyes) use a camera of their own on this client,
     * so nothing the strike does to the caster (lifting them clear, holding them while the remote fires) moves it.
     * {@code -Dss05.clienttest.cam=dx,dy,dz,lx,ly,lz} places it at the mark plus (dx, dy, dz), looking at the mark plus
     * (lx, ly, lz); the named views are presets of that.
     */
    public static void onRenderFramePre() {
        Minecraft mc = Minecraft.getInstance();
        // (in place as soon as the player is, so the land the camera looks at is what gets built first)
        if (done || !placed || mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }
        double[] cam = finalView >= 0 ? HarnessParams.camera(HarnessParams.FINAL_VIEWS[finalView]) : HarnessParams.camera();
        if (cam == null) {
            return;
        }
        // round the strike's real mark once it has one (the land can have put it somewhere else than aimed at)
        net.minecraft.world.phys.Vec3 mark = strikeMark();
        double markX = mark != null ? mark.x : SPAWN_X + HarnessParams.DISTANCE;
        double markY = mark != null ? mark.y : groundY;
        double markZ = mark != null ? mark.z : SPAWN_Z;
        double x = markX + cam[0], y = markY + cam[1], z = markZ + cam[2];
        float[] yawPitch = lookAt(x, y, z, markX + cam[3], markY + cam[4], markZ + cam[5]);
        if (camera == null || camera.level() != mc.level) {
            camera = new Marker(EntityType.MARKER, mc.level);
        }
        camera.setPos(x, y, z);
        camera.xo = x;
        camera.yo = y;
        camera.zo = z;
        camera.xOld = x;
        camera.yOld = y;
        camera.zOld = z;
        camera.setYRot(yawPitch[0]);
        camera.setXRot(yawPitch[1]);
        camera.yRotO = yawPitch[0];
        camera.xRotO = yawPitch[1];
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
        mc.options.hideGui = !HarnessParams.HUD;
    }

    public static void onRenderFrame() {
        if (done || castGameTime < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long gameTime = strikeAge();
        if (gameTime < 0) {
            return;
        }
        long[] shots = HarnessParams.SHOTS;
        while (nextShotIndex < shots.length && gameTime >= shots[nextShotIndex]) {
            takeScreenshot(mc, shots[nextShotIndex]);
            nextShotIndex++;
        }
        if (nextShotIndex < shots.length) {
            return;
        }
        // the final views: once the strike is over, each from its own spot once everything in view is drawn again
        if (finalView < HarnessParams.FINAL_VIEWS.length) {
            if (finalView < 0) {
                if (MoonFx.active().isEmpty() && HalleyFx.active().isEmpty()) {
                    finalView = 0;
                    finalSince = System.currentTimeMillis();
                    finalFrames = 0;
                }
                return;
            }
            finalFrames++;
            boolean drawn = finalFrames > 30 && mc.levelRenderer.hasRenderedAllSections();
            if (drawn || System.currentTimeMillis() - finalSince > 300_000L) {
                takeScreenshot(mc, "final_" + HarnessParams.FINAL_VIEWS[finalView].trim());
                finalView++;
                finalSince = System.currentTimeMillis();
                finalFrames = 0;
            }
            return;
        }
        done = true;
        probeLand(mc);
        log("done");
        mc.stop();
    }

    private static int finalView = -1;
    private static long finalSince;
    private static int finalFrames;

    private static void takeScreenshot(Minecraft mc, long tick) {
        takeScreenshot(mc, String.format("%05d", tick));
    }

    private static void takeScreenshot(Minecraft mc, String name) {
        String fileName = HarnessParams.SKILL + "_" + name + ".png";
        try {
            Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                component -> log("shot " + new File(new File(mc.gameDirectory, "screenshots"), fileName)));
        } catch (Throwable t) {
            log("exception taking screenshot " + name + ": " + t);
        }
    }

    static void log(String message) {
        System.out.println("[SS05CLIENTTEST] " + message);
    }
}
