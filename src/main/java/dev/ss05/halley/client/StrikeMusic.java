package dev.ss05.halley.client;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.luna.MoonFx;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
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
 *
 * <p>Players can swap the music for their own (1.9.0; a CurseForge request of 2026-10-13): an Ogg Vorbis file in
 * {@code config/shooting_star_addition/music/} named as in {@link #OWN} plays in place of ours, from the same moment and
 * kept in step the same way; the folder's README says where each track's drop has to be. A file that won't open is
 * logged and ours plays.
 */
public final class StrikeMusic extends AbstractTickableSoundInstance implements FabricSoundInstance {
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
    /** The player's own track for it, or null for ours. */
    private final Path own;

    /**
     * Each of our tracks and the file name a player's own takes in the music folder, with the second its drop lands on
     * (from the track's start: Luna's alarm, Proxima's gate opening; at the remote's speed 1).
     */
    private static final Map<String, String> OWN = Map.of("luna_music", "luna.ogg", "luna_music_epic", "luna_epic.ogg",
        "proxima_music", "proxima.ogg");
    private static final Set<String> TOLD = new HashSet<>();

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
        this.own = music ? own(name) : null;
    }

    /** The music folder (made with its README the first time a strike's music plays). */
    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("shooting_star_addition").resolve("music");
    }

    /** Makes the music folder and its README if they aren't there. */
    public static void ensureFolder() {
        Path folder = folder();
        try {
            if (!Files.isDirectory(folder)) {
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("README.txt"), README, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException failed) {
            HalleyAddon.LOG.debug("Couldn't make the music folder {}: {}", folder, failed.toString());
        }
    }

    /** The player's own file for our track {@code name}, or null. */
    private static Path own(String name) {
        String file = OWN.get(name);
        if (file == null) {
            return null;
        }
        Path path = folder().resolve(file);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        if (TOLD.add(file)) {
            HalleyAddon.LOG.info("Playing your own music for {}: {}", name, path);
        }
        return path;
    }

    /** Ours from the mod's sounds, or the player's own file (ours if that won't open). */
    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary buffers, ResourceLocation sound, boolean looping) {
        if (this.own == null) {
            return buffers.getStream(sound, looping);
        }
        Path path = this.own;
        CompletableFuture<AudioStream> own = CompletableFuture.supplyAsync(() -> {
            try {
                InputStream in = new BufferedInputStream(Files.newInputStream(path));
                try {
                    return new OggAudioStream(in);
                } catch (IOException | RuntimeException bad) {
                    in.close();
                    throw bad;
                }
            } catch (IOException failed) {
                throw new CompletionException(failed);
            }
        }, Util.backgroundExecutor());
        return own.exceptionallyCompose(failed -> {
            HalleyAddon.LOG.warn("Couldn't play your music file {} (it must be Ogg Vorbis, .ogg), so the addon's own plays: {}", path,
                (failed instanceof CompletionException && failed.getCause() != null ? failed.getCause() : failed).toString());
            return buffers.getStream(sound, looping);
        });
    }

    private static final String README = """
        Your own music for The Shooting Star Addition: SA Series
        ========================================================

        Put an Ogg Vorbis file (.ogg) here with one of these names and it plays in place of the addon's own music
        (no restart needed: it is looked for each time a strike's music starts). Delete it to get ours back.

          luna.ogg        SA-02 Luna. Starts at the alarm; the moon hits 16.0 seconds into it.
          luna_epic.ogg   SA-02 Luna in Epic mode (G on Luna's card). Starts at the alarm; the hit is at 20.0 seconds.
          proxima.ogg     SA-04 Proxima. Starts as the gate opens; the flare hits 22.5 seconds into it.

        So put your track's drop at that second (trim the start, or add silence before it). The addon keeps it in step with
        the strike as it plays, and the remote's Speed slider speeds it up or slows it down with everything else.
        It plays on Minecraft's Music volume, while Music (M) is on in the Stellar Remote's menu (H).

        To turn any audio file into .ogg you can use Audacity (File > Export > Ogg Vorbis) or ffmpeg:
          ffmpeg -i "my song.mp3" -c:a libvorbis -q:a 5 luna.ogg
        Only use music you have the right to use.
        """;

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
