package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.shootingstardemo.client.cinematic.CameraPose;
import cyou.rimuru.shootingstardemo.client.cinematic.Cutscene;
import cyou.rimuru.shootingstardemo.client.cinematic.Shot;
import cyou.rimuru.shootingstardemo.client.cinematic.Subject;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.film.HalleyStoryboard;
import dev.ss05.halley.compat.tss.TssLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Turns {@link HalleyStoryboard}'s takes into a cutscene for The Shooting Star's director. */
final class HalleyCutscene {
    private HalleyCutscene() {
    }

    static Cutscene of(HalleyFxAdapter adapter) {
        HalleyFx fx = adapter.halley();
        Subject subject = adapter.subject();
        HalleyStoryboard.Stage stage = new HalleyStoryboard.Stage() {
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
        for (HalleyStoryboard.Take take : HalleyStoryboard.takes(fx.plan, fx.visuals)) {
            HalleyStoryboard.Path path = take.path();
            shots.add(new Shot(take.start(), take.end(), take.blendIn(), (x, partial, k) -> {
                HalleyStoryboard.Pose pose = path.pose(stage, partial, k);
                return new CameraPose(pose.position(), pose.look(), pose.fov(), pose.roll());
            }));
        }
        // full frame, cut straight in, and ahead of the built-in skills' films if two are cast at once (as theirs are)
        return new Cutscene(subject, TssLink.SKILL.tier(), TssLink.SKILL.title(), TssLink.SKILL.color(), 0, shots)
            .hardCut()
            .fullFrame()
            .priority(2)
            // its clock runs at the remote's speed slider, like the spell's (or the shots would drift off the action)
            .speed(adapter.filmSpeed());
    }
}
