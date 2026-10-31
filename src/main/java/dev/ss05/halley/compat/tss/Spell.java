package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.net.RimuNet;
import cyou.rimuru.rimulib.skill.Skill;
import cyou.rimuru.rimulib.skill.SkillSet;
import cyou.rimuru.shootingstardemo.magic.Casting;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A strike running on the server. The Shooting Star 1.5.0's spell engine only runs its own skills, so Halley and Luna
 * run here, the way its engine ran them: a clock at the remote's speed slider, the caster looked up every tick, the
 * effect packet sent at the start to everyone near the target.
 */
abstract class Spell {
    private static final List<Spell> ACTIVE = new ArrayList<>();

    final Skill skill;
    final ServerLevel level;
    final UUID casterId;
    final Vec3 origin;
    final Vec3 target;
    final long seed;
    private final CastTuning tuning;
    private final float yaw;
    protected ServerPlayer caster;
    protected int age;
    private double clock;

    Spell(Skill skill, ServerPlayer caster, Vec3 target, long seed, CastTuning tuning) {
        this.skill = skill;
        this.level = caster.serverLevel();
        this.casterId = caster.getUUID();
        this.caster = caster;
        this.origin = caster.position();
        this.target = target;
        this.seed = seed;
        this.tuning = tuning;
        this.yaw = caster.getYRot();
    }

    protected abstract void onTick(int t);

    protected abstract boolean finished();

    /** Rides along in the effect packet (the strike's sizes), so clients film the same strike. */
    protected abstract int[] fxTargets();

    protected abstract void onEnd();

    private boolean tick() {
        this.caster = this.level.getServer().getPlayerList().getPlayer(this.casterId);
        if (this.caster != null && this.caster.level() != this.level) {
            this.caster = null;
        }
        int due = (int) Math.floor(this.clock);
        while (this.age <= due && !this.finished()) {
            this.onTick(this.age);
            this.age++;
        }
        this.clock += this.tuning.speed();
        return this.finished();
    }

    static void start(Spell spell) {
        ACTIVE.add(spell);
        StrikeFx fx = new StrikeFx(SkillSet.indexOf(spell.skill), spell.caster.getId(), spell.origin, spell.target, spell.yaw,
            spell.seed, spell.fxTargets(), everyone(spell.casterId) ? StrikeFx.FLAG_FOR_EVERYONE : 0,
            spell.tuning.power(), spell.tuning.speed());
        // as far as the built-in skills' effects reach
        double range = 512.0;
        for (ServerPlayer player : spell.level.players()) {
            if (player.distanceToSqr(spell.target) <= range * range && RimuNet.canSend(player, StrikeFx.PACKET)) {
                RimuNet.send(player, fx);
            }
        }
    }

    /** Whether the caster plays their films for everyone near (the remote's menu, "Play for everyone"). */
    private static boolean everyone(UUID caster) {
        try {
            return Casting.a(caster);
        } catch (LinkageError | RuntimeException unknown) {
            return false;
        }
    }

    static boolean running(Skill skill, UUID caster) {
        for (Spell spell : ACTIVE) {
            if (spell.skill == skill && spell.casterId.equals(caster)) {
                return true;
            }
        }
        return false;
    }

    static void tickAll() {
        for (Spell spell : List.copyOf(ACTIVE)) {
            boolean done;
            try {
                done = spell.tick();
            } catch (RuntimeException failed) {
                HalleyAddon.LOG.error("{} failed and was stopped", spell.skill.id(), failed);
                done = true;
            }
            if (done) {
                ACTIVE.remove(spell);
                try {
                    spell.onEnd();
                } catch (RuntimeException failed) {
                    HalleyAddon.LOG.error("{} failed while ending", spell.skill.id(), failed);
                }
            }
        }
    }

    static void clear() {
        ACTIVE.clear();
    }
}
