package dev.ss05.halley.compat.tss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import cyou.rimuru.rimulib.skill.Power;
import cyou.rimuru.rimulib.skill.Skill;
import cyou.rimuru.rimulib.skill.SkillSet;
import cyou.rimuru.rimulib.skill.Tuning;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.ProximaConfig;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.world.ProximaStrike;
import dev.ss05.halley.world.SolarFlares;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /ssa strike <skill> [at <x y z>] [power] [damage|inf] [speed]}, for operators: casts any of the remote's skills
 * (The Shooting Star's own, Halley and Luna, other addons') as the player running it, without the remote, aimed where
 * they look or at {@code x y z}. No cooldown and none of the server's slider limits (operators set those; the user,
 * 2026-11-03), so a warning is said whenever a value is past the remote's own range.
 */
final class StrikeCommand {

    private static final String WARNING = "No limits: values past the remote's own range can freeze or crash the server.";
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(Component.literal("The skill didn't cast (see the message above)."));

    private StrikeCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ssa")
            .requires(source -> source.hasPermission(2))
            .executes(StrikeCommand::help)
            .then(Commands.literal("strike")
                .executes(StrikeCommand::help)
                .then(Commands.argument("skill", StringArgumentType.word())
                    .suggests((c, builder) -> SharedSuggestionProvider.suggest(SkillSet.all().stream().filter(set -> !set.hidden)
                        .flatMap(set -> set.skills.stream()).map(Skill::id), builder))
                    .executes(c -> strike(c, false, 0))
                    .then(tuned(false))
                    .then(Commands.literal("at")
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(c -> strike(c, true, 0))
                            .then(tuned(true))))))
            // a random solar flare now, near the player running it (announced first, like the random ones)
            .then(Commands.literal("flare")
                .executes(StrikeCommand::help)
                .then(Commands.argument("tier", StringArgumentType.word())
                    .suggests((c, builder) -> SharedSuggestionProvider.suggest(new String[] {"minor", "major", "cataclysm"}, builder))
                    .executes(c -> flare(c, ProximaConfig.tuning().warningSeconds()))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(c -> flare(c, IntegerArgumentType.getInteger(c, "seconds"))))))
            // Proxima's radiation zones in this world: list them, or clear them all (a permanent one has no other way out)
            .then(Commands.literal("zones")
                .executes(StrikeCommand::listZones)
                .then(Commands.literal("list").executes(StrikeCommand::listZones))
                .then(Commands.literal("clear").executes(c -> {
                    int cleared = ProximaStrike.clearZones(c.getSource().getLevel());
                    c.getSource().sendSuccess(() -> Component.literal("Cleared " + cleared + " radiation zone(s) in this world."), true);
                    return cleared;
                }))));
    }

    private static int listZones(CommandContext<CommandSourceStack> c) {
        java.util.List<String> lines = ProximaStrike.zoneLines(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "No radiation zones in this world."
            : lines.size() + " radiation zone(s) in this world:"), false);
        for (String line : lines) {
            c.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        }
        return lines.size();
    }

    private static int flare(CommandContext<CommandSourceStack> c, int seconds) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        int tier = switch (StringArgumentType.getString(c, "tier").toLowerCase(Locale.ROOT)) {
            case "minor" -> ProximaParams.MINOR;
            case "major" -> ProximaParams.MAJOR;
            case "cataclysm" -> ProximaParams.CATACLYSM;
            default -> throw new SimpleCommandExceptionType(Component.literal("A flare is minor, major or cataclysm")).create();
        };
        SolarFlares.schedule(player, tier, seconds);
        return 1;
    }

    /** [power [damage [speed]]] */
    private static ArgumentBuilder<CommandSourceStack, ?> tuned(boolean at) {
        return Commands.argument("power", FloatArgumentType.floatArg(0.01F))
            .executes(c -> strike(c, at, 1))
            .then(Commands.argument("damage", StringArgumentType.word())
                .executes(c -> strike(c, at, 2))
                .then(Commands.argument("speed", FloatArgumentType.floatArg(0.01F))
                    .executes(c -> strike(c, at, 3))));
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("/ssa strike <skill> [at <x y z>] [power] [damage|inf] [speed]: casts a remote "
            + "skill as you, aimed where you look (or at x y z), with no cooldown. Unset values are the skill's defaults."), false);
        c.getSource().sendSuccess(() -> Component.literal("/ssa flare <minor|major|cataclysm> [seconds]: a solar flare near you, "
            + "announced to your world first (seconds: the warning, else the config's warning_seconds)."), false);
        c.getSource().sendSuccess(() -> Component.literal("/ssa zones [list|clear]: Proxima's radiation zones in your world: "
            + "where they are and how long they have left, or clear them all."), false);
        c.getSource().sendSuccess(() -> Component.literal(WARNING).withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int strike(CommandContext<CommandSourceStack> c, boolean at, int given) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "skill");
        Skill skill = SkillSet.all().stream().filter(set -> !set.hidden).flatMap(set -> set.skills.stream())
            .filter(s -> s.id().equalsIgnoreCase(id)).findFirst()
            .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No remote skill called " + id)).create());
        Tuning base = Tuning.initial(skill);
        float power = given >= 1 ? FloatArgumentType.getFloat(c, "power") : base.power();
        float damage = given >= 2 ? damage(StringArgumentType.getString(c, "damage")) : base.damage();
        float speed = given >= 3 ? FloatArgumentType.getFloat(c, "speed") : base.speed();
        Tuning tuning = new Tuning(power, damage, speed, base.width());

        Power powers = Tuning.power(skill);
        Power speeds = Tuning.speed(skill);
        if (powers != null && power > powers.max() || speeds != null && (speed > speeds.max() || speed < speeds.min())) {
            c.getSource().sendSuccess(() -> Component.literal(WARNING).withStyle(ChatFormatting.RED), true);
        }

        boolean cast;
        StrikeAim.set(at ? Vec3Argument.getVec3(c, "pos") : null);
        NoCooldown.forced(true);
        try {
            cast = SkillSet.of(skill).cast(player, skill, tuning);
        } finally {
            NoCooldown.forced(false);
            StrikeAim.set(null);
        }
        if (!cast) {
            throw FAILED.create();
        }
        String where = at ? String.format(Locale.ROOT, " at %.1f %.1f %.1f", Vec3Argument.getVec3(c, "pos").x,
            Vec3Argument.getVec3(c, "pos").y, Vec3Argument.getVec3(c, "pos").z) : "";
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s%s (power %s, damage %s, speed %s)", skill.title(),
            where, power, Float.isInfinite(damage) ? "inf" : damage, speed)), true);
        return 1;
    }

    private static float damage(String text) throws CommandSyntaxException {
        if (text.equalsIgnoreCase("inf") || text.equals("∞")) {
            return Tuning.INFINITE;
        }
        try {
            float value = Float.parseFloat(text);
            if (value > 0.0F && !Float.isNaN(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // the message below
        }
        throw new SimpleCommandExceptionType(Component.literal("Damage is a number above 0, or inf")).create();
    }
}
