package dev.ss05.halley.compat.client;

import cyou.rimuru.shootingstardemo.client.cinematic.CameraPose;
import cyou.rimuru.shootingstardemo.client.cinematic.Cutscene;
import cyou.rimuru.shootingstardemo.client.cinematic.Shot;
import cyou.rimuru.shootingstardemo.client.cinematic.Subject;
import dev.ss05.halley.client.film.MoonStoryboard;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Turns {@link MoonStoryboard}'s takes into a cutscene for The Shooting Star's director (see {@code HalleyCutscene}). */
final class MoonCutscene {
    private MoonCutscene() {
    }

    static Cutscene of(MoonFxAdapter adapter) {
        MoonFx fx = adapter.moon();
        Subject subject = adapter.subject();
        MoonStoryboard.Stage stage = new MoonStoryboard.Stage() {
            @Override
            public Vec3 feet(float partial) {
                return subject.feet(partial);
            }

            @Override
            public Vec3 forward() {
                return subject.forward();
            }

            @Override
            public float time(float partial) {
                return fx.time(partial);
            }
        };

        List<Shot> shots = new ArrayList<>();
        for (MoonStoryboard.Take take : MoonStoryboard.takes(fx.plan)) {
            MoonStoryboard.Path path = take.path();
            shots.add(new Shot(take.start(), take.end(), take.blendIn(), (x, partial, k) -> {
                MoonStoryboard.Pose pose = path.pose(stage, partial, k);
                return new CameraPose(pose.position(), pose.look(), pose.fov(), pose.roll());
            }));
        }
        return new Cutscene(subject, StarBridge.MOON.tier(), StarBridge.MOON.title(), StarBridge.MOON.color(), 0, shots)
            .hardCut()
            .fullFrame()
            .priority(2);
    }
}
