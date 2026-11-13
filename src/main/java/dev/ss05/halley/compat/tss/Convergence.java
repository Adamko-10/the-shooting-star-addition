package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.rimulib.mc1211.skill.Tuning;
import cyou.rimuru.rimulib.mc1211.terrain.Carving;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.EpicMode;
import dev.ss05.halley.StellarQuartet;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * SA-00 · Stellar Convergence, the server half: fires every weapon the player ticked on the addon's plate (H), each
 * with its own sliders (their game sends them, {@link Picks}), timed so that they all hit on the same tick (the user's
 * pick, 2026-11-04): the slowest is cast first, the quicker ones as much later as they are quicker. They land on the
 * crosshair, or spread in a ring round it (the plate's switch). Inspired by The Shooting Star - Stellar Quartet by
 * ikun520 (its own thing: no code or assets of it).
 *
 * <p>Each weapon is cast the way {@code /ssa strike ... at} casts it ({@link StrikeAim}, no "already on its way"),
 * held to the server's slider limits as a cast from the remote is. Weapons whose time to impact isn't known (other
 * addons') are cast at once, with the slowest.
 */
public final class Convergence {
    /** The ring's smallest radius, in blocks. */
    static final double RING = 64.0;
    /** Room between two weapons' impacts round the ring, in blocks. */
    private static final double RING_GAP = 16.0;
    /** How far outside every weapon's reach the ring keeps its caster, in blocks. */
    private static final double SAFE = 24.0;
    /** How far The Shooting Star 1.5.1's weapons reach from where they land at power 1 (the measuring run's carvings). */
    private static final Map<String, Double> TSS_REACH = Map.of(
        "the_shooting_star", 200.0,
        "gungnir", 412.0,
        "seven_stars", 160.0,
        "tesseract", 96.0);
    /** The slowest known weapon's ticks to impact at speed 1 (the skill's duration is counted from it). */
    static final int LONGEST = 353;
    /**
     * Gravitia sends the whole world into its space stage for good, which stopped the other weapons' impacts in a test
     * run: it fires last, once the others are done (the user, 2026-11-05: a fixed 30 s felt too long): no carving still
     * cutting in that world and none of the addon's strikes still working ({@link #othersDone}), but not before this long
     * after the hit (Seven Stars starts its constellation lines ~2.5 s after it)...
     */
    private static final int FINALE_MIN = 100;
    /** ...and at the latest this long after it (a carving waiting for land that never loads). Its own film plays then. */
    private static final int FINALE_MAX = 1200;

    /**
     * Ticks from the cast to the impact at speed 1, power 1, for The Shooting Star 1.5.1's weapons: measured in a game
     * run (2026-11-04, the server tick its carving started on, `test builds\2026-11-04 auto test 11 convergence`).
     * Seven Stars' stars land one every 16 ticks from 291 on; 339 is the middle one, on the mark. Tesseract carves no
     * land and Gravitia turns the world into its space stage, so neither has a moment to time: they fire at once, with
     * the slowest. Halley's and Luna's come from their plans.
     */
    private static final Map<String, Integer> TSS_IMPACT = Map.of(
        "the_shooting_star", 353,
        "gungnir", 323,
        "seven_stars", 339);

    /**
     * The same for other addons' skills on the remote (by skill id), read from their spells' timelines (2026-11-11):
     * SS-21 Quasar 1.8.0's jet touches down at 758 (its burst at 998), SX-01 Solar Flare 1.1.1's whip touches the
     * ground at 365. Without an entry a weapon fires at once, and a long one (Quasar) then runs its whole build-up
     * through the convergence's film, into every panel. Check them when those addons update (docs/ADDONS.md).
     */
    private static final Map<String, Integer> ADDON_IMPACT = Map.of(
        "quasar", 758,
        "solar_flare", 365);

    private static final Map<UUID, Picks> PICKS = new ConcurrentHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private Convergence() {
    }

    /** One ticked weapon and its sliders, as the player's game had them. */
    public record Pick(String id, float power, float damage, float speed, float width) {
    }

    /** The player's picks: the ring switch and the ticked weapons. Sent by their game (client to server). */
    public record Picks(boolean ring, List<Pick> picks) {
        public static final RimuPacket<Picks> PACKET = NetApi.packet(HalleyAddon.id("convergence_picks"), Picks.class,
            Picks::write, Picks::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(this.ring);
            buf.writeVarInt(this.picks.size());
            for (Pick pick : this.picks) {
                buf.writeUtf(pick.id(), 64);
                buf.writeFloat(pick.power());
                buf.writeFloat(pick.damage());
                buf.writeFloat(pick.speed());
                buf.writeFloat(pick.width());
            }
        }

        private static Picks read(FriendlyByteBuf buf) {
            boolean ring = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), 32);
            List<Pick> picks = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                picks.add(new Pick(buf.readUtf(64), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
            }
            return new Picks(ring, List.copyOf(picks));
        }
    }

    /** One weapon waiting for its tick, aimed at a point in the caster's world at the cast ({@code level}). */
    private record Pending(UUID caster, ServerLevel level, Skill skill, Tuning tuning, Vec3 at, long tick, long latest) {
    }

    static void received(ServerPlayer player, Picks picks) {
        PICKS.put(player.getUUID(), picks);
    }

    static void forget(UUID player) {
        PICKS.remove(player);
    }

    /** The remote fired SA-00 (or an operator's /ssa strike stellar_convergence). */
    static boolean cast(ServerPlayer player, Tuning master) {
        // the remote fires it only for players with Stellar Quartet installed (operators' /ssa strike always can)
        if (!StellarQuartet.unlocked(player) && !NoCooldown.commandCast()) {
            StarBridge.deny(player, "Stellar Convergence unlocks with The Shooting Star - Stellar Quartet (by ikun520) installed");
            return false;
        }
        Picks picks = PICKS.get(player.getUUID());
        List<Weapon> weapons = new ArrayList<>();
        List<Weapon> finale = new ArrayList<>();
        if (picks != null) {
            // the picks come from the player's game: each weapon fires once, however often a (modified) game lists it
            Set<Skill> seen = new HashSet<>();
            for (Pick pick : picks.picks()) {
                Skill skill = find(pick.id());
                if (skill == null || skill == TssLink.CONVERGENCE || !seen.add(skill)) {
                    continue;
                }
                Tuning tuning = Limits.hold(skill, scaled(pick, master).clampFor(skill));
                (finale(skill) ? finale : weapons).add(new Weapon(skill, tuning, impact(skill, tuning, player), reach(skill, tuning)));
            }
        }
        if (weapons.isEmpty() && finale.isEmpty()) {
            StarBridge.deny(player, "Tick the weapons to fire on the Stellar Convergence plate (H)");
            return false;
        }
        if (weapons.isEmpty()) {
            // Gravitia alone: nothing to wait for
            weapons.addAll(finale);
            finale.clear();
        }
        Vec3 target = StarBridge.aimGround(player, HalleyConfig.tuning().reach(), 260.0);
        int last = weapons.stream().mapToInt(Weapon::impact).max().orElse(0);
        ServerLevel level = player.serverLevel();
        // the first weapon goes out on the next tick (the server's tick runs the waiting casts), the rest counted from it
        long now = level.getGameTime() + 1;
        Vec3[] spots = null;
        if (picks.ring() && weapons.size() > 1) {
            Ring ring = ring(player, target, weapons);
            target = ring.centre();
            spots = ring.spots();
        }
        int[] colors = new int[weapons.size()];
        Vec3[] points = new Vec3[weapons.size()];
        for (int i = 0; i < weapons.size(); i++) {
            Weapon weapon = weapons.get(i);
            Vec3 at = spots != null ? ground(level, spots[i], target.y) : target;
            // unknown (another addon's): with the slowest, at once
            long delay = weapon.impact() < 0 ? 0 : last - weapon.impact();
            PENDING.add(new Pending(player.getUUID(), level, weapon.skill(), weapon.tuning(), at, now + delay, -1L));
            colors[i] = weapon.skill().color();
            points[i] = at;
        }
        for (Weapon weapon : finale) {
            PENDING.add(new Pending(player.getUUID(), level, weapon.skill(), weapon.tuning(), target, now + last + FINALE_MIN,
                now + last + FINALE_MAX));
        }
        HalleyAddon.LOG.info("{} cast Stellar Convergence: {} at {} (all hit in {} ticks{})", player.getGameProfile().getName(),
            weapons.stream().map(w -> w.skill().id() + "+" + (w.impact() < 0 ? 0 : last - w.impact())).toList(), target, last,
            finale.isEmpty() ? "" : ", then " + finale.stream().map(w -> w.skill().id()).toList() + " once the rest are done");
        ConvergenceFx.send(player, new ConvergenceFx(player.getId(), target, now + last, picks.ring(), colors, points));
        return true;
    }

    /** Casts whatever is due; from the server tick. */
    static void tick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.caster());
            if (player == null || player.serverLevel() != pending.level()) {
                // gone, or in another world now: its point means nothing there
                it.remove();
            } else if (player.serverLevel().getGameTime() >= pending.tick() && (pending.latest() < 0
                    || player.serverLevel().getGameTime() >= pending.latest() || othersDone(player.serverLevel(), pending.caster()))) {
                it.remove();
                due.add(pending);
            }
        }
        for (Pending pending : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.caster());
            SkillSet set = SkillSet.of(pending.skill());
            if (player == null || set == null) {
                continue;
            }
            if (pending.latest() >= 0) {
                HalleyAddon.LOG.info("Stellar Convergence fires its finale, {}, {} ticks after the hit", pending.skill().id(),
                    player.serverLevel().getGameTime() - (pending.latest() - FINALE_MAX));
            }
            StrikeAim.set(pending.at());
            NoCooldown.forced(true);
            try {
                set.cast(player, pending.skill(), pending.tuning());
            } catch (RuntimeException | LinkageError failed) {
                HalleyAddon.LOG.warn("Stellar Convergence couldn't fire {}", pending.skill().id(), failed);
            } finally {
                NoCooldown.forced(false);
                StrikeAim.set(null);
            }
        }
    }

    static void clear() {
        PENDING.clear();
        PICKS.clear();
    }

    private record Weapon(Skill skill, Tuning tuning, int impact, double reach) {
    }

    /**
     * A weapon's own sliders with SA-00's applied (the master sliders, the user, 2026-11-05): its power and speed times
     * SA-00's, its damage no higher than SA-00's (∞ by default: no cap). Each weapon then stays within its own slider
     * range and the server's limits.
     */
    public static Tuning scaled(Pick pick, Tuning master) {
        if (master == null) {
            return new Tuning(pick.power(), pick.damage(), pick.speed(), pick.width());
        }
        return new Tuning(pick.power() * master.power(), Math.min(pick.damage(), master.damage()), pick.speed() * master.speed(), pick.width());
    }

    private record Ring(Vec3 centre, Vec3[] spots) {
    }

    /**
     * Where each weapon lands when they are spread out (the user, 2026-11-04: "keep in mind the distance", or the small
     * ones vanish under the giants): each gets a stretch of the circle as wide as its own impact, so none lands in
     * another's; the smallest face the caster (where they can be seen), the biggest go round the far side; and the whole
     * ring is moved out along the aim until the caster stands outside every weapon's reach. Puts {@code weapons} in ring
     * order; the spots are in the same order.
     */
    private static Ring ring(ServerPlayer player, Vec3 target, List<Weapon> weapons) {
        weapons.sort(Comparator.comparingDouble(Weapon::reach));
        // the smallest in the middle of the run, the others to either side in turn: the biggest meet round the back
        java.util.ArrayDeque<Weapon> run = new java.util.ArrayDeque<>();
        for (int i = 0; i < weapons.size(); i++) {
            if (i % 2 == 0) {
                run.addLast(weapons.get(i));
            } else {
                run.addFirst(weapons.get(i));
            }
        }
        Weapon smallest = weapons.get(0);
        weapons.clear();
        weapons.addAll(run);
        int n = weapons.size();
        double total = 0.0;
        for (Weapon weapon : weapons) {
            total += 2.0 * weapon.reach() + RING_GAP;
        }
        double radius = Math.max(RING, total / (Math.PI * 2.0));
        double[] middle = new double[n];
        double at = 0.0;
        for (int i = 0; i < n; i++) {
            double width = Math.PI * 2.0 * (2.0 * weapons.get(i).reach() + RING_GAP) / total;
            middle[i] = at + width * 0.5;
            at += width;
        }
        Vec3 flat = new Vec3(player.getX() - target.x, 0.0, player.getZ() - target.z);
        Vec3 toCaster = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : flat.normalize();
        double turn = Math.atan2(toCaster.z, toCaster.x) - middle[weapons.indexOf(smallest)];
        Vec3 centre = target;
        Vec3[] spots = new Vec3[n];
        for (int tries = 0; tries < 16; tries++) {
            double worst = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double a = middle[i] + turn;
                spots[i] = centre.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
                worst = Math.min(worst, Math.hypot(player.getX() - spots[i].x, player.getZ() - spots[i].z) - weapons.get(i).reach());
            }
            if (worst >= SAFE) {
                break;
            }
            centre = centre.add(toCaster.scale(-(SAFE - worst + 8.0)));
        }
        return new Ring(centre, spots);
    }

    /**
     * How far a weapon's impact reaches from where it lands, in blocks, at its power (to space them in a ring). The
     * remote's own: measured at power 1 (Railgun's column, Gungnir's crater, a Seven Stars star's crater; its stars fall
     * in a pattern much wider than that), times its power over its default (some sliders count blocks, not times).
     */
    private static double reach(Skill skill, Tuning tuning) {
        if (skill == TssLink.SKILL) {
            return HalleyParams.of(HalleyConfig.tuning(), 64, tuning.power()).craterRadius();
        }
        if (skill == TssLink.MOON) {
            return MoonParams.of(MoonConfig.tuning(), 64, Vec3.ZERO, tuning.power()).craterRadius();
        }
        if (skill == TssLink.PROXIMA) {
            // its flash burns out to a quarter past the scorch
            return ProximaParams.of(tuning.power()).scorchRadius() * 1.25;
        }
        double base = TSS_REACH.getOrDefault(skill.id(), 48.0);
        cyou.rimuru.rimulib.mc1211.skill.Power power = skill.power();
        double times = power != null && power.initial() > 0.0F ? tuning.power() / power.initial() : 1.0;
        return base * Math.max(0.1, times);
    }

    /** Ticks from its cast to its impact at its speed slider, or -1 when that isn't known. */
    private static int impact(Skill skill, Tuning tuning, ServerPlayer caster) {
        int base;
        if (skill == TssLink.SKILL) {
            HalleyParams params = HalleyParams.of(HalleyConfig.tuning(), 64, tuning.power());
            base = HalleyPlan.TOUCHDOWN + HalleyPlan.ploughTicks(params.trenchLength(), params.scale());
        } else if (skill == TssLink.MOON) {
            // the caster's Epic mode slows Luna's fall
            base = MoonPlan.contactClock(EpicMode.on(caster));
        } else if (skill == TssLink.PROXIMA) {
            base = ProximaPlan.IMPACT;
        } else if (SkillSet.of(skill) == SkillSet.of(TssLink.SKILL) && TSS_IMPACT.containsKey(skill.id())) {
            base = TSS_IMPACT.get(skill.id());
        } else if (ADDON_IMPACT.containsKey(skill.id())) {
            base = ADDON_IMPACT.get(skill.id());
        } else {
            return -1;
        }
        return Mth.ceil(base / Math.max(0.05F, tuning.speed()));
    }

    /** Whether this weapon fires last, once the others are done ({@link #FINALE_MIN}): The Shooting Star's Gravitia. */
    public static boolean finale(Skill skill) {
        return skill.id().equals("gravitia") && SkillSet.of(skill) == SkillSet.of(TssLink.SKILL);
    }

    /**
     * Whether the weapons before the finale are done: none of the addon's strikes of this caster still working on the land
     * (their effects may run on: Luna's aftermath lasts 17 s after the moon is down), and no
     * carving in this world still cutting (RimuLib's, which every remote skill cuts through; the ones lingering for land
     * that isn't loaded don't count). Read from RimuLib's internals; if they move, the finale waits {@link #FINALE_MIN}.
     */
    private static boolean othersDone(ServerLevel level, UUID caster) {
        if (Spell.busy(TssLink.SKILL, caster) || Spell.busy(TssLink.MOON, caster) || Spell.busy(TssLink.PROXIMA, caster)) {
            return false;
        }
        try {
            if (live == null) {
                live = Carving.class.getDeclaredField("LIVE");
                waiting = Carving.class.getDeclaredField("LINGERING");
                carvingLevel = Carving.class.getDeclaredField("level");
                live.setAccessible(true);
                waiting.setAccessible(true);
                carvingLevel.setAccessible(true);
            }
            List<?> lingering = new ArrayList<>((java.util.Collection<?>) waiting.get(null));
            for (Object each : new ArrayList<>((java.util.Collection<?>) live.get(null))) {
                if (each instanceof Carving carving && carvingLevel.get(carving) == level && !carving.done() && !lingering.contains(carving)) {
                    return false;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException moved) {
            if (!warned) {
                warned = true;
                HalleyAddon.LOG.warn("Stellar Convergence can't see RimuLib's carvings; its finale fires {} ticks after the hit", FINALE_MIN, moved);
            }
        }
        return true;
    }

    private static java.lang.reflect.Field live;
    private static java.lang.reflect.Field waiting;
    private static java.lang.reflect.Field carvingLevel;
    private static boolean warned;

    /** A non-hidden remote skill by its id (any mod's). */
    static Skill find(String id) {
        // only the remote's own list (TSS's skills, ours and other addons' that joined it): never a skill from another
        // mod's own RimuLib skill set, which the player may not even hold the item for (Fable's review, A5)
        SkillSet set = remote();
        if (set != null) {
            for (Skill skill : set.skills) {
                if (skill.id().equals(id)) {
                    return skill;
                }
            }
        }
        return null;
    }

    /** The Stellar Remote's skill set (the one we joined), or null before we have. */
    static SkillSet remote() {
        return SkillSet.of(TssLink.SKILL);
    }

    /** The ground at x, z if that land is loaded (never loads it), else {@code fallback}. */
    private static Vec3 ground(ServerLevel level, Vec3 at, double fallback) {
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return new Vec3(at.x, fallback, at.z);
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(at.x, y <= level.getMinBuildHeight() + 1 ? fallback : y, at.z);
    }
}
