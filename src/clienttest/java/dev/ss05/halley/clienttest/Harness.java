package dev.ss05.halley.clienttest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import cyou.rimuru.rimulib.mc1201.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.mc1201.client.magic.WhiteSpace;
import cyou.rimuru.shootingstardemo.mc1201.magic.Casting;
import cyou.rimuru.rimulib.mc1201.skill.Skill;
import cyou.rimuru.rimulib.mc1201.skill.Tuning;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
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
import net.minecraftforge.event.TickEvent;

/**
 * The actual harness (Forge 1.20.1): creates a flat creative world, places the player, casts a skill for real through
 * The Shooting Star, and screenshots it at the requested ticks of the strike. Entirely driven by
 * {@link HarnessParams}; see that class for what each property does. The same harness as the other branches', trimmed
 * to what 1.20.1 needs: the views that film from a fixed spot use a camera of this client's own.
 */
final class Harness {
    private static final String LEVEL_ID = "ss05test";
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Z = 0.5;

    private static boolean worldCreateRequested;
    private static boolean placed;
    private static long settleUntilTick = -1;
    private static volatile boolean castRequested;
    private static int nextShotIndex;
    private static volatile boolean done;
    private static volatile double groundY;
    private static long nextStatusLogMillis;
    private static long landWaitSince = -1;
    private static Marker camera;

    private Harness() {
    }

    // ---- The slow state machine: world creation, placement, settling, casting. --------------------------------

    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
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
                // Nothing here to click through a first-run screen (The Shooting Star's GPU warning comes back every
                // time the title screen opens, as a software renderer always fails its check), so the world is created
                // straight from whatever screen is up, once the game has finished loading.
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
            settleUntilTick = mc.level.getGameTime() + 60;
            return;
        }
        if (mc.level.getGameTime() < settleUntilTick) {
            return;
        }
        // and until the land round the mark has arrived and been built (a slow client is sent its chunks slowly), for
        // four minutes at the most
        if (landWaitSince < 0) {
            landWaitSince = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - landWaitSince < 240_000L && (!landArrived(mc) || !mc.levelRenderer.hasRenderedAllChunks())) {
            return;
        }

        if (!castRequested) {
            castRequested = true;
            if ("third".equals(HarnessParams.VIEW)) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            cast(mc);
        }
    }

    /** Whether this client has every chunk within {@link HarnessParams#LAND} blocks of the mark. */
    private static boolean landArrived(Minecraft mc) {
        int r = (int) Math.ceil(HarnessParams.LAND / 16.0);
        int cx = (int) Math.floor(SPAWN_X + HarnessParams.DISTANCE) >> 4;
        int cz = (int) Math.floor(SPAWN_Z) >> 4;
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
            deleteRecursive(mc.gameDirectory.toPath().resolve("saves").resolve(LEVEL_ID));
        } catch (IOException e) {
            log("couldn't delete the old test save (continuing anyway): " + e);
        }
        LevelSettings levelSettings = new LevelSettings(LEVEL_ID, GameType.CREATIVE, false, Difficulty.PEACEFUL,
            true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions worldOptions = WorldOptions.defaultWithRandomSeed();
        Function<RegistryAccess, WorldDimensions> dimensions = access ->
            world(access, access.registryOrThrow(Registries.WORLD_PRESET)
                .getOrThrow("normal".equals(HarnessParams.WORLD) ? WorldPresets.NORMAL : WorldPresets.FLAT).createWorldDimensions());
        log("creating world '" + LEVEL_ID + "'");
        mc.createWorldOpenFlows().createFreshLevel(LEVEL_ID, levelSettings, worldOptions, dimensions);
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
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);

            level.setDayTime(HarnessParams.TIME);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);

            // Force the spawn column loaded so getHeight below is accurate, not a fallback guess.
            level.getChunk(0, 0);
            groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);

            float pitch = (float) Math.toDegrees(Math.atan2(1.62, HarnessParams.DISTANCE));
            player.teleportTo(level, SPAWN_X, groundY, SPAWN_Z, -90f, pitch);
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().setItem(player.getInventory().selected, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("shooting_star_demo", "stellar_remote"))));

            CutsceneDirector.setEnabled(HarnessParams.FILM);
        });
    }

    /** -Dss05.clienttest.<name>=<value> ("inf" for no limit), else {@code fallback}. */
    private static float slider(String name, float fallback) {
        String value = System.getProperty("ss05.clienttest." + name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().equalsIgnoreCase("inf") ? Float.POSITIVE_INFINITY : Float.parseFloat(value.trim());
    }

    private static void cast(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        log("casting " + HarnessParams.SKILL);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Skill skill = (Skill) StarBridge.skill(HarnessParams.MOON);
            int index = HarnessParams.MOON ? StarBridge.moonIndex() : StarBridge.skillIndex();
            if (index < 0) {
                log("Halley/Luna never attached to the Stellar Remote (StarBridge.problem(): " + StarBridge.problem() + ")");
                return;
            }
            // the remote's sliders for this cast: their defaults, or -Dss05.clienttest.power / .damage / .speed
            Tuning initial = Tuning.initial(skill);
            Tuning tuning = new Tuning(slider("power", initial.power()), slider("damage", initial.damage()),
                slider("speed", initial.speed())).clampFor(skill);
            log("tuning: power " + tuning.power() + ", damage " + tuning.damage() + ", speed " + tuning.speed());
            // The Shooting Star 1.5.0's cast request (obfuscated, unmapped): player, index, power, damage, speed, width, for everyone
            Casting.liiillli1l(player, index, tuning.power(), tuning.damage(), tuning.speed(), tuning.width(), false);
            // Filmed from a camera of its own: the caster is lifted high and clear, flying, so the ground Luna heaves up
            // never closes over their head (the overlay for a view blocked by a block is drawn from the player's eyes,
            // not the camera's)
            if (HarnessParams.MOON && HarnessParams.camera() != null) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.getX(), player.getY() + 160.0, player.getZ());
            }
        });
    }

    /** Not used on 1.20.1 (the camera is this client's own, see {@link #onRenderTick}); kept for the mod's wiring. */
    static void onServerTick(TickEvent.ServerTickEvent event) {
    }

    // ---- The camera and the screenshots, frame by frame. ---------------------------------------------------------

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
                lastMark = fx.plan.target;
            }
        } else {
            for (HalleyFx fx : HalleyFx.active()) {
                age = fx.age();
                lastMark = fx.plan.target;
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

    private static long lastAge = -1;
    private static long lastAgeAt;
    private static net.minecraft.world.phys.Vec3 lastMark;
    private static int finalView = -1;
    private static long finalSince;
    private static int finalFrames;

    static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || !placed || mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            // (in place as soon as the player is, so the land the camera looks at is what gets built first)
            placeCamera(mc);
            return;
        }
        if (!castRequested) {
            return;
        }
        long age = strikeAge();
        if (age < 0) {
            return;
        }
        long[] shots = HarnessParams.SHOTS;
        while (nextShotIndex < shots.length && age >= shots[nextShotIndex]) {
            takeScreenshot(mc, String.format("%05d", shots[nextShotIndex]));
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
            String view = HarnessParams.FINAL_VIEWS[finalView].trim();
            if (view.equals("menu") || view.equals("skills")) {
                // the remote's menu (H), through the caster's own eyes: opened once, filmed once it has opened fully;
                // "skills" scrolls its list to the end first, where Halley and Luna are (with their sliders)
                if (finalFrames == 1) {
                    mc.setCameraEntity(mc.player);
                    if (!WhiteSpace.capturesInput()) {
                        WhiteSpace.open(mc);
                    }
                }
                if (view.equals("skills") && finalFrames > 10 && finalFrames % 4 == 0 && WhiteSpace.listScroll() < WhiteSpace.maxScroll()) {
                    WhiteSpace.scroll(-1.0);
                }
                if (finalFrames > 30 && System.currentTimeMillis() - finalSince > (view.equals("skills") ? 7000L : 4000L)) {
                    takeScreenshot(mc, "final_" + view);
                    finalView++;
                    finalSince = System.currentTimeMillis();
                    finalFrames = 0;
                }
                return;
            }
            if (finalFrames > 30 && mc.levelRenderer.hasRenderedAllChunks() || System.currentTimeMillis() - finalSince > 300_000L) {
                takeScreenshot(mc, "final_" + HarnessParams.FINAL_VIEWS[finalView].trim());
                finalView++;
                finalSince = System.currentTimeMillis();
                finalFrames = 0;
            }
            return;
        }
        done = true;
        log("done");
        mc.stop();
    }

    /** The views that film from a fixed spot use a camera of this client's own (see HarnessParams.camera()). */
    private static void placeCamera(Minecraft mc) {
        double[] cam = finalView >= 0 && finalView < HarnessParams.FINAL_VIEWS.length
            ? HarnessParams.camera(HarnessParams.FINAL_VIEWS[finalView]) : HarnessParams.camera();
        if (cam == null) {
            return;
        }
        // round the strike's real mark once it has one (the land can have put it somewhere else than aimed at)
        double markX = lastMark != null ? lastMark.x : SPAWN_X + HarnessParams.DISTANCE;
        double markY = lastMark != null ? lastMark.y : groundY;
        double markZ = lastMark != null ? lastMark.z : SPAWN_Z;
        double x = markX + cam[0];
        double y = markY + cam[1];
        double z = markZ + cam[2];
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
