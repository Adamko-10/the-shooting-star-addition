package dev.ss05.halley.compat;

import cyou.rimuru.shootingstardemo.magic.Casting;
import cyou.rimuru.shootingstardemo.magic.Skill;
import cyou.rimuru.shootingstardemo.magic.SkillSet;
import cyou.rimuru.shootingstardemo.registry.ModSkills;
import cyou.rimuru.shootingstardemo.spell.GreaterTeleportation;
import cyou.rimuru.shootingstardemo.spell.SpellEngine;
import cyou.rimuru.shootingstardemo.spell.SpellUtil;
import cyou.rimuru.shootingstardemo.star.Carving;
import cyou.rimuru.shootingstardemo.star.Erasure;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.world.Excavation;
import dev.ss05.halley.world.HalleyCasting;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one place the addon reaches into The Shooting Star (server and common side; the client side is
 * {@link dev.ss05.halley.compat.client.StarClientBridge}).
 *
 * <h2>When The Shooting Star updates</h2>
 * Rebuild against the new jar. If it no longer compiles, or the game logs "SS-05 couldn't attach", the fix is in
 * this package:
 * <ul>
 *   <li>Renamed packages or classes - fix the imports at the top of the files in {@code compat/}.</li>
 *   <li>The remote's skill list ({@code SkillSet}) changed shape - adjust {@link #install()}; the field names it needs
 *       are the constants just below.</li>
 *   <li>A helper moved (spells, carving, erasure, the evac lift) - fix the one-line wrapper for it at the bottom.</li>
 * </ul>
 * Nothing outside {@code compat/} imports The Shooting Star, so nothing else should need to change.
 */
public final class StarBridge {
    // Private members of SkillSet that SS-05 has to write to, because the Stellar Remote's skill list is built
    // once, from an enum, and has no public "add a skill" hook.
    private static final String SET_SKILLS = "skills";
    private static final String SET_CASTER = "caster";
    private static final String TABLE_ALL = "ALL";
    private static final String TABLE_OWNER = "OWNER";
    private static final String TABLE_INDEX = "INDEX";

    /** SS-05 as The Shooting Star sees it. */
    public static final HalleySkill SKILL = new HalleySkill();

    private static volatile boolean installed;
    private static volatile String problem = "not installed yet";

    private StarBridge() {
    }

    public static boolean installed() {
        return installed;
    }

    public static String problem() {
        return problem;
    }

    /**
     * Adds SS-05 to the Stellar Remote: after the three built-in skills, with its own index, key binding, menu row,
     * tooltip line and cooldown, exactly as if it had been one of them. Safe to call more than once.
     */
    @SuppressWarnings("unchecked")
    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        try {
            SkillSet remote = ModSkills.STELLAR_REMOTE;
            if (SkillSet.of(SKILL) != null) {
                installed = true;
                return true;
            }

            // Look everything up first, so that a renamed field fails before anything has been changed.
            List<Skill> all = (List<Skill>) staticField(TABLE_ALL).get(null);
            Map<Skill, SkillSet> owner = (Map<Skill, SkillSet>) staticField(TABLE_OWNER).get(null);
            Map<Skill, Integer> index = (Map<Skill, Integer>) staticField(TABLE_INDEX).get(null);
            Field skillsField = instanceField(SET_SKILLS);
            Field casterField = instanceField(SET_CASTER);
            SkillSet.Caster<Skill> builtIn = (SkillSet.Caster<Skill>) casterField.get(remote);
            List<Skill> before = remote.skills;
            if (builtIn == null || before == null || before.isEmpty()) {
                throw new IllegalStateException("the Stellar Remote has no skills to join");
            }

            List<Skill> after = new ArrayList<>(before);
            after.add(SKILL);
            // The Shooting Star 1.3.2+ hands every cast a Tuning (the remote's power/speed gauge). SS-05 has no power()
            // to tune, so it casts the same way whatever the gauge says; the built-in skills get theirs unchanged.
            SkillSet.Caster<Skill> caster = (player, skill, tuning) -> skill == SKILL ? HalleyCasting.cast(player) : builtIn.cast(player, skill, tuning);

            index.put(SKILL, all.size());
            all.add(SKILL);
            owner.put(SKILL, remote);
            try {
                skillsField.set(remote, List.copyOf(after));
                casterField.set(remote, caster);
            } catch (ReflectiveOperationException | RuntimeException failed) {
                all.remove(SKILL);
                owner.remove(SKILL);
                index.remove(SKILL);
                skillsField.set(remote, before);
                casterField.set(remote, builtIn);
                throw failed;
            }

            installed = true;
            problem = "";
            HalleyAddon.LOG.info("SS-05 Halley joined the Stellar Remote as skill #{} (key slot {}).", after.size(), SkillSet.indexOf(SKILL));
            return true;
        } catch (Throwable failed) {
            problem = failed.getClass().getSimpleName() + ": " + failed.getMessage();
            HalleyAddon.LOG.error("SS-05 Halley couldn't attach to The Shooting Star, so the skill is disabled. "
                + "This usually means The Shooting Star was updated - see compat/StarBridge.java.", failed);
            return false;
        }
    }

    private static Field staticField(String name) throws NoSuchFieldException {
        Field field = SkillSet.class.getDeclaredField(name);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException("SkillSet." + name + " is no longer static");
        }
        field.setAccessible(true);
        return field;
    }

    private static Field instanceField(String name) throws NoSuchFieldException {
        Field field = SkillSet.class.getDeclaredField(name);
        if (Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException("SkillSet." + name + " is now static");
        }
        field.setAccessible(true);
        return field;
    }

    /** The index The Shooting Star uses for SS-05 in its packets, or -1 when it isn't attached. */
    public static int skillIndex() {
        return installed ? SkillSet.indexOf(SKILL) : -1;
    }

    // ---- Thin wrappers over The Shooting Star's helpers. One line each, so an update only touches these. ----------

    /** Starts a spell and broadcasts its effect packet to nearby players, like the built-in skills. */
    public static void start(HalleySpell spell) {
        SpellEngine.start(spell);
    }

    public static boolean alreadyFlying(ServerPlayer player) {
        return SpellEngine.running(SKILL, player.getUUID());
    }

    /** The point on the ground under the crosshair (or under a creature in it), the way the built-in skills aim. */
    public static Vec3 aimGround(ServerPlayer player, double reach, double fallback) {
        return SpellEngine.aimGround(player, reach, fallback);
    }

    /** The purple action-bar message the remote uses when it refuses. */
    public static void deny(ServerPlayer player, String message) {
        Casting.deny(player, message);
    }

    /** Resistance and fire resistance, refreshed every half second while the caster's own spell runs. */
    public static void ward(ServerPlayer caster) {
        SpellUtil.ward(caster);
    }

    /** Whether a spell from {@code caster} may hit this entity (not the caster, their pets, armour stands...). */
    public static boolean affects(Entity entity, ServerPlayer caster) {
        return SpellUtil.affects(entity, caster);
    }

    /** Kills outright, the way the other skills' strike zones do (bosses included). */
    public static void erase(ServerLevel level, LivingEntity victim, ServerPlayer attacker) {
        Erasure.strike(level, victim, attacker);
    }

    /** Teleports a caster standing within {@code zone} of the target out to {@code refuge}, held in the air. */
    public static boolean liftClear(ServerPlayer caster, Vec3 target, double zone, double refuge, int ticks) {
        return GreaterTeleportation.lift(caster, target, zone, refuge, ticks) != null;
    }

    /** Places blocks fast (straight into the chunk sections), relights and resends the chunks. */
    public static void paint(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        Carving.paint(level, blocks);
    }

    /**
     * Cuts everything above a floor away, a chunk at a time, nearest {@code centre} first: the same fast carver the
     * built-in skills use. {@code floor} returns {@link dev.ss05.halley.HalleyPlan#UNTOUCHED} outside the shape.
     */
    public static Excavation excavate(ServerLevel level, Vec3 centre, double radius, Excavation.Floor floor,
                                      Excavation.Surface surface, BlockPos spared, String name, int chunksPerTick) {
        Carving carving = Carving.flatten(level, centre, radius, floor::floor, surface::at, spared)
            .perTick(chunksPerTick)
            .lazy()
            .named(name);
        return new Excavation() {
            @Override
            public void tick(double front) {
                carving.tick(front);
            }

            @Override
            public boolean done() {
                return carving.done();
            }

            @Override
            public void finish() {
                carving.finish();
            }
        };
    }
}
