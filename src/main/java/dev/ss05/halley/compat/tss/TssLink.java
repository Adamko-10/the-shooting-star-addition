package dev.ss05.halley.compat.tss;

import cyou.rimuru.shootingstardemo.mc1211.magic.Casting;
import cyou.rimuru.shootingstardemo.mc1211.magic.Skill;
import cyou.rimuru.shootingstardemo.mc1211.magic.SkillSet;
import cyou.rimuru.shootingstardemo.mc1211.registry.ModSkills;
import cyou.rimuru.shootingstardemo.mc1211.spell.GreaterTeleportation;
import cyou.rimuru.shootingstardemo.mc1211.spell.SpellEngine;
import cyou.rimuru.shootingstardemo.mc1211.spell.SpellUtil;
import cyou.rimuru.shootingstardemo.mc1211.star.Carving;
import cyou.rimuru.shootingstardemo.mc1211.star.Erasure;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.compat.StarLink;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import dev.ss05.halley.world.Excavation;
import dev.ss05.halley.world.HalleyCasting;
import dev.ss05.halley.world.HalleyInfo;
import dev.ss05.halley.world.MoonCasting;
import dev.ss05.halley.world.MoonInfo;
import dev.ss05.halley.world.SkillNumbers;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one place the addon reaches into The Shooting Star (server and common side; the client side is
 * {@link StarClientBridge}). The rest of the addon calls it through {@link dev.ss05.halley.compat.StarBridge}.
 *
 * <p>Everything in {@code compat/tss/} is built once per supported version of The Shooting Star: the release jar
 * holds one copy of this package for each (as {@code compat/v1_4_1/} and so on, mapped to that version's names), and
 * StarBridge picks the copy for the version that is running. See gradle/tss.gradle.
 *
 * <h2>When The Shooting Star updates</h2>
 * Put the new jar in libs/ next to the old ones and rebuild. If it no longer compiles, or the game logs "couldn't
 * attach", the fix is in this package:
 * <ul>
 *   <li>Renamed packages or classes - fix the imports at the top of the files in {@code compat/tss/}.</li>
 *   <li>The remote's skill list ({@code SkillSet}) changed shape - adjust {@link #install()}; it finds the fields it
 *       needs by their types.</li>
 *   <li>A helper moved (spells, carving, erasure, the evac lift) - fix the one-line wrapper for it at the bottom.</li>
 * </ul>
 * Nothing outside {@code compat/tss/} imports The Shooting Star, so nothing else should need to change.
 */
public final class TssLink implements StarLink {
    /** Halley as The Shooting Star sees it. */
    public static final HalleySkill SKILL = new HalleySkill();
    /** Luna as The Shooting Star sees it. */
    public static final MoonSkill MOON = new MoonSkill();

    /**
     * Adds Halley and Luna to the Stellar Remote: after the built-in skills, each with its own index, key binding,
     * menu row, tooltip line and cooldown, exactly as if they had been among them. Safe to call more than once.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void install() throws ReflectiveOperationException {
        SkillSet remote = ModSkills.STELLAR_REMOTE;
        if (SkillSet.of(SKILL) != null) {
            return;
        }

        // Look everything up first, so that a renamed field fails before anything has been changed.
        // SkillSet's private tables and the remote's own list and caster: Halley has to write to them, because the
        // Stellar Remote's skill list is built once, from an enum, with no public "add a skill" hook. The Shooting
        // Star 1.4.0+ is obfuscated, so they are found by their types (all distinct), not their names.
        List<Skill> all = (List<Skill>) field(true, List.class, Skill.class).get(null);
        Map<Skill, SkillSet> owner = (Map<Skill, SkillSet>) field(true, Map.class, Skill.class, SkillSet.class).get(null);
        Map<Skill, Integer> index = (Map<Skill, Integer>) field(true, Map.class, Skill.class, Integer.class).get(null);
        Field skillsField = field(false, List.class, Skill.class);
        Field casterField = field(false, SkillSet.Caster.class, Skill.class);
        SkillSet.Caster<Skill> builtIn = (SkillSet.Caster<Skill>) casterField.get(remote);
        List<Skill> before = remote.skills;
        if (builtIn == null || before == null || before.isEmpty()) {
            throw new IllegalStateException("the Stellar Remote has no skills to join");
        }

        // The Shooting Star's own skills keep their SS numbers; Halley and Luna take the next two after the highest.
        int highest = 0;
        for (Skill skill : before) {
            Matcher number = Pattern.compile("SS-(\\d+)").matcher(String.valueOf(skill.title()));
            if (number.find()) {
                highest = Math.max(highest, Integer.parseInt(number.group(1)));
            }
        }
        SkillNumbers.after(highest);

        List<Skill> after = new ArrayList<>(before);
        after.add(SKILL);
        after.add(MOON);
        // The Shooting Star 1.3.2+ hands every cast a Tuning (the remote's power/speed gauge). Halley has no power()
        // to tune, so it casts the same way whatever the gauge says; the built-in skills get theirs unchanged.
        SkillSet.Caster<Skill> caster = (player, skill, tuning) -> skill == SKILL ? HalleyCasting.cast(player)
            : skill == MOON ? MoonCasting.cast(player) : builtIn.cast(player, skill, tuning);

        for (Skill added : List.<Skill>of(SKILL, MOON)) {
            index.put(added, all.size());
            all.add(added);
            owner.put(added, remote);
        }
        try {
            skillsField.set(remote, List.copyOf(after));
            casterField.set(remote, caster);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            for (Skill added : List.<Skill>of(SKILL, MOON)) {
                all.remove(added);
                owner.remove(added);
                index.remove(added);
            }
            skillsField.set(remote, before);
            casterField.set(remote, builtIn);
            throw failed;
        }

        HalleyAddon.LOG.info("{} (Halley) joined the Stellar Remote as skill #{} (key slot {}).", HalleyInfo.title(), after.indexOf(SKILL) + 1, SkillSet.indexOf(SKILL));
        HalleyAddon.LOG.info("{} (Luna) joined the Stellar Remote as skill #{} (key slot {}).", MoonInfo.title(), after.indexOf(MOON) + 1, SkillSet.indexOf(MOON));
    }

    /** SkillSet's one field (static or not) of type {@code raw<args>}. */
    private static Field field(boolean isStatic, Class<?> raw, Class<?>... args) throws NoSuchFieldException {
        Field found = null;
        for (Field field : SkillSet.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) != isStatic || field.getType() != raw
                || !(field.getGenericType() instanceof ParameterizedType type) || !Arrays.equals(type.getActualTypeArguments(), args)) {
                continue;
            }
            if (found != null) {
                throw new NoSuchFieldException("SkillSet has more than one " + describe(isStatic, raw, args));
            }
            found = field;
        }
        if (found == null) {
            throw new NoSuchFieldException("SkillSet has no " + describe(isStatic, raw, args));
        }
        found.setAccessible(true);
        return found;
    }

    private static String describe(boolean isStatic, Class<?> raw, Class<?>... args) {
        StringBuilder b = new StringBuilder(isStatic ? "static " : "").append(raw.getSimpleName()).append('<');
        for (int i = 0; i < args.length; i++) {
            b.append(i == 0 ? "" : ", ").append(args[i].getSimpleName());
        }
        return b.append("> field").toString();
    }

    @Override
    public void installClient() {
        StarClientBridge.install();
    }

    @Override
    public int skillIndex() {
        return SkillSet.indexOf(SKILL);
    }

    @Override
    public int moonIndex() {
        return SkillSet.indexOf(MOON);
    }

    @Override
    public Object skill(boolean moon) {
        return moon ? MOON : SKILL;
    }

    // ---- Thin wrappers over The Shooting Star's helpers. One line each, so an update only touches these. ----------

    @Override
    public void startHalley(ServerPlayer caster, Vec3 target, long seed, HalleyParams params) {
        SpellEngine.start(new HalleySpell(caster, target, seed, params));
    }

    @Override
    public void startMoon(ServerPlayer caster, Vec3 target, long seed, MoonParams params) {
        SpellEngine.start(new MoonSpell(caster, target, seed, params));
    }

    @Override
    public boolean alreadyFlying(boolean moon, ServerPlayer player) {
        return SpellEngine.running(moon ? MOON : SKILL, player.getUUID());
    }

    @Override
    public Vec3 aimGround(ServerPlayer player, double reach, double fallback) {
        return SpellEngine.aimGround(player, reach, fallback);
    }

    @Override
    public void deny(ServerPlayer player, String message) {
        Casting.deny(player, message);
    }

    @Override
    public void ward(ServerPlayer caster) {
        SpellUtil.ward(caster);
    }

    @Override
    public boolean affects(Entity entity, ServerPlayer caster) {
        return SpellUtil.affects(entity, caster);
    }

    @Override
    public void erase(ServerLevel level, LivingEntity victim, ServerPlayer attacker) {
        Erasure.strike(level, victim, attacker);
    }

    @Override
    public boolean liftClear(ServerPlayer caster, Vec3 target, double zone, double refuge, int ticks) {
        return GreaterTeleportation.lift(caster, target, zone, refuge, ticks) != null;
    }

    @Override
    public void paint(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        Carving.paint(level, blocks);
    }

    @Override
    public Excavation excavate(ServerLevel level, Vec3 centre, double radius, Excavation.Floor floor,
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
