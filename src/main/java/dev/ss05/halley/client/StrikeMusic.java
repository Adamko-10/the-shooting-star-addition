package dev.ss05.halley.client;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.luna.MoonFx;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A skill's music (Luna's), cut so that its drop lands on the impact, and kept in step with the strike: the
 * game's clock and the sound card's drift apart (a slow frame, a stutter while land loads), so every tick it compares how
 * much of the track has played with the strike's clock and nudges its speed by up to 5% until they agree again. It plays
 * on the game's Music volume slider, pitched with the remote's speed slider, while the music switch (M in the remote's
 * menu) is on; the game's own background music makes way for it. A film's own soundtrack ({@link #film}: Proxima's) is
 * kept in step the same way, on the Players volume slider, whatever the music switch says. Unless the player lets music
 * stack (the = key in the remote's menu), a new strike's music takes over from any still playing.
 */
public final class StrikeMusic extends AbstractTickableSoundInstance {
    private final MoonFx.Host host;
    /** The strike's tick at which the track's time 0 plays. */
    private final int from;
    /** The strike's clock, in ticks. */
    private final DoubleSupplier clock;
    /** The strikes' music now playing (not the films' soundtracks), oldest first. */
    private static final List<StrikeMusic> PLAYING = new ArrayList<>();
    private final BooleanSupplier over;
    /** Whether it is music (the music switch stops it), not a film's soundtrack. */
    private final boolean music;
    /** The track's own pitch: the remote's speed slider, as far as the game lets a sound go. */
    private final float base;
    private long last;
    /** Seconds of the track played so far. */
    private double played;
    private boolean started;

    private StrikeMusic(String name, MoonFx.Host host, int from, DoubleSupplier clock, BooleanSupplier over, boolean music) {
        super(SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)), music ? SoundSource.MUSIC : SoundSource.PLAYERS,
            SoundInstance.createUnseededRandom());
        this.music = music;
        this.host = host;
        this.from = from;
        this.clock = clock;
        this.over = over;
        this.base = Mth.clamp(host.speed(), 0.5F, 2.0F);
        this.volume = music ? 0.85F : 1.0F;
        this.pitch = this.base;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
    }

    /** Starts the sound {@code name}, whose time 0 is tick {@code from} of the strike; it stops once {@code over} is true. */
    public static StrikeMusic play(String name, MoonFx.Host host, int from, DoubleSupplier clock, BooleanSupplier over) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getMusicManager().stopPlaying();
        PLAYING.removeIf(playing -> !minecraft.getSoundManager().isActive(playing));
        if (!HalleyClientConfig.stackMusic()) {
            for (StrikeMusic playing : PLAYING) {
                minecraft.getSoundManager().stop(playing);
            }
            PLAYING.clear();
        }
        StrikeMusic music = new StrikeMusic(name, host, from, clock, over, true);
        PLAYING.add(music);
        minecraft.getSoundManager().play(music);
        return music;
    }

    /** Starts a film's soundtrack {@code name}, whose time 0 is film tick {@code from}; it stops once {@code over} is true. */
    public static StrikeMusic film(String name, MoonFx.Host host, int from, DoubleSupplier clock, BooleanSupplier over) {
        StrikeMusic track = new StrikeMusic(name, host, from, clock, over, false);
        Minecraft.getInstance().getSoundManager().play(track);
        return track;
    }

    @Override
    public void tick() {
        if (this.over.getAsBoolean() || this.music && !HalleyClientConfig.lunaMusic()) {
            this.stop();
            return;
        }
        long now = System.nanoTime();
        if (!this.started) {
            // the track starts from its beginning once its stream is open (a tick or so after it is asked for): the
            // time that took is caught up like any other drift
            if (Minecraft.getInstance().getSoundManager().isActive(this)) {
                this.started = true;
                this.last = now;
                this.played = 0.0;
            }
            return;
        }
        double dt = (now - this.last) / 1.0E9;
        this.last = now;
        // a long gap: the game was paused, and the music with it
        this.played += (dt > 1.0 ? 0.05 : dt) * this.pitch;
        double want = (this.clock.getAsDouble() - this.from) / 20.0;
        // at the speed slider's ends the game can't play it fast or slow enough anyway: leave it be
        if (Math.abs(this.base - this.host.speed()) < 0.01F) {
            double drift = this.played - want;
            this.pitch = this.base * (float) (1.0 - Mth.clamp(drift * 0.6, -0.05, 0.05));
        }
    }
}
