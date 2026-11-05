package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.mc1211.client.cinematic.CameraPose;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Cutscene;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Shot;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Subject;
import dev.ss05.halley.client.film.FilmLand;
import dev.ss05.halley.client.film.MoonStoryboard;
import dev.ss05.halley.client.film.ProximaStoryboard;
import dev.ss05.halley.client.proxima.ProximaFx;
import dev.ss05.halley.compat.tss.TssLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Turns {@link ProximaStoryboard}'s takes into a cutscene for RimuLib's director (as {@code MoonCutscene} does Luna's). */
final class ProximaCutscene {
    private ProximaCutscene() {
    }

    static Cutscene of(ProximaFxAdapter adapter) {
        ProximaFx fx = adapter.proxima();
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

            @Override
            public Vec3 keep(Vec3 pos, Vec3 home) {
                return FilmLand.keep(pos, home);
            }
        };
        List<Shot> shots = new ArrayList<>();
        for (MoonStoryboard.Take take : ProximaStoryboard.takes(fx.plan)) {
            MoonStoryboard.Path path = take.path();
            shots.add(new Shot(take.start(), take.end(), take.blendIn(), (x, partial, k) -> {
                MoonStoryboard.Pose pose = path.pose(stage, partial, k);
                return new CameraPose(pose.position(), pose.look(), pose.fov(), pose.roll());
            }));
        }
        return new Cutscene(subject, TssLink.PROXIMA.tier(), TssLink.PROXIMA.title(), TssLink.PROXIMA.color(), 0, shots)
            .hardCut()
            .fullFrame()
            .priority(2)
            .speed(adapter.filmSpeed());
    }
}
