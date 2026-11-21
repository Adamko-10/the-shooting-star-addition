package dev.ss05.halley.client.render;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Where this frame's camera will be, for what is painted at a frame's start from the camera (Proxima's sky gate with a
 * shader pack, ProximaCosmos.paintSkyCard): normally the last frame's camera is close enough, but Stellar Convergence's
 * split screen moves the camera to another weapon every frame, so while it plays it says where the camera goes next.
 */
public final class FilmEye {
    /** Set while a film moves the camera far between frames; null otherwise. */
    @Nullable
    public static volatile Supplier<Vec3> next;

    private FilmEye() {
    }

    /** This frame's camera position, or {@code fallback} (the last frame's). */
    public static Vec3 or(Vec3 fallback) {
        Supplier<Vec3> hint = next;
        if (hint == null) {
            return fallback;
        }
        try {
            Vec3 eye = hint.get();
            return eye != null ? eye : fallback;
        } catch (RuntimeException failed) {
            return fallback;
        }
    }
}
