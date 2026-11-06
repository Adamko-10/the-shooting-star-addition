package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.Cutscene;
import dev.ss05.halley.client.ConvergenceView;
import dev.ss05.halley.compat.tss.ConvergenceFx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Stellar Convergences on their way, on this game: told by the server ({@link ConvergenceFx}) before its weapons are
 * cast. While the player's own is on its way, the weapons' own films are held back (each would take the screen in turn);
 * they are kept for the convergence's own film.
 */
public final class ConvergenceClient {
    /** How long after the hit the weapons' films are still held back (the last of them start at the hit). */
    private static final int HOLD_AFTER = 20;
    /** How long a convergence is remembered after its hit (for its effect). */
    private static final int KEEP_AFTER = 400;

    /** A convergence and the world it was told in (game times only mean something in that one). */
    private record Active(ConvergenceFx fx, Level level) {
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final List<Cutscene> HELD = new ArrayList<>();

    private ConvergenceClient() {
    }

    static void start(ConvergenceFx fx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ACTIVE.add(new Active(fx, minecraft.level));
        ConvergenceView.start(fx.target(), minecraft.level.getGameTime(), fx.hitAt(), fx.colors(), fx.points(), fx.ring());
        if (mine(fx)) {
            HELD.clear();
            ConvergenceFilm.start(fx);
        }
    }

    /** From the film director: true (and the film is kept) while the player's own convergence is on its way. */
    public static boolean hold(Cutscene film) {
        if (ConvergenceFilm.is(film)) {
            return false;
        }
        for (ConvergenceFx fx : active()) {
            if (mine(fx) && Minecraft.getInstance().level.getGameTime() <= fx.hitAt() + HOLD_AFTER) {
                HELD.add(film);
                return true;
            }
        }
        return false;
    }

    /** The convergences on their way or just done, in this world. */
    static List<ConvergenceFx> active() {
        Minecraft minecraft = Minecraft.getInstance();
        if (ACTIVE.isEmpty()) {
            return List.of();
        }
        if (minecraft.level == null) {
            clear();
            return List.of();
        }
        long now = minecraft.level.getGameTime();
        ACTIVE.removeIf(a -> a.level() != minecraft.level || now > a.fx().hitAt() + KEEP_AFTER);
        return ACTIVE.stream().map(Active::fx).toList();
    }

    /**
     * From the film director's tick: the held films run on unseen (their clocks, unless they follow a clock of their
     * own), so the convergence's film can borrow each one's camera when it cuts to it.
     */
    public static void tickHeld() {
        for (Cutscene film : HELD) {
            if (film.clock == null && !film.finished()) {
                film.advance();
            }
        }
    }

    /** The weapons' films held back for the player's latest convergence, in the order they came. */
    static List<Cutscene> held() {
        return HELD;
    }

    static boolean mine(ConvergenceFx fx) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && fx.casterId() == minecraft.player.getId();
    }

    public static void clear() {
        ACTIVE.clear();
        HELD.clear();
        ConvergenceFilm.end();
    }
}
