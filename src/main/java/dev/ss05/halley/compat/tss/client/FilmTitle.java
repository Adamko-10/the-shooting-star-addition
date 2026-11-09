package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.text.TessType;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.proxima.ProximaFx;
import dev.ss05.halley.client.proxima.ProximaHud;
import dev.ss05.halley.world.Codenames;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

/**
 * The skill's name across its film at the moment it is revealed, the way The Shooting Star 1.5's films title their
 * own skills (asked for on CurseForge): HALLEY as the comet first hangs over the horizon (shot 4), LUNA as the moon
 * cracks open (shot 2). Letter by letter in RimuLib's display type, the remote's own lettering; none of The Shooting
 * Star's assets. Off in RimuLib's legacy film style, as The Shooting Star's titles are. Overlay units (710 high).
 */
final class FilmTitle {
    /** The display face's cap height at scale 1 (RimuLib: its size 16 x 0.7). */
    private static final float CAP = 11.2F;

    /**
     * Proxima's caption and readout over its lower letterbox bar (client/proxima/ProximaHud builds them): the caption
     * tracked wide, the readout in the fixed-width face, so changing numbers don't make it shuffle.
     */
    static void caption(GuiGraphics g, ProximaHud.@Nullable Caption c, int w, int h) {
        if (c == null || c.alpha() <= 0.01F) {
            return;
        }
        float scale = h * 0.0145F / CAP;
        float y = h * 0.895F;
        TessType.draw(g, TessType.Face.LABEL, c.caption(), w * 0.5F, y, 0.5F, scale, 2.5F,
            TessType.Paint.flat(0xFF000000 | ProximaHud.CRIMSON), c.alpha(), 1.0F);
        TessType.draw(g, TessType.Face.MONO, c.detail(), w * 0.5F, y + h * 0.026F, 0.5F, scale * 0.8F, 1.0F,
            TessType.Paint.flat(0xFF000000 | ProximaHud.PALE), c.alpha() * 0.8F, 1.0F);
    }

    // Halley: shot 4 (ticks 64-122), the telephoto of the comet over the horizon.
    static final float HALLEY_IN = 74.0F;
    static final float HALLEY_OUT = 104.0F;
    static final float HALLEY_END = 114.0F;
    // Luna: shot 2, from the moon cracking open to it tearing loose (the shot cuts at FALL + 12).
    static final float LUNA_IN = MoonPlan.BREAK + 4.0F;
    static final float LUNA_OUT = MoonPlan.FALL + 2.0F;
    static final float LUNA_END = MoonPlan.FALL + 12.0F;

    private static final int ICE = 0x6FE8FF;
    private static final int PALE = 0xE2FBFF;
    private static final int HOT_TOP = 0xFFF1C4;
    private static final int HOT_FOOT = 0xFF6A1E;
    private static final int COOL_TOP = 0xF4F0EA;
    private static final int COOL_FOOT = 0xB4ACA0;
    private static final int GOLD = 0xFFB347;

    private FilmTitle() {
    }

    /** HALLEY: the letters streak in like small comets from high on the right, lock with a flash, then sublimate away. */
    static void halley(GuiGraphics g, HalleyFx fx, float partial, int w, int h, HalleyHud.Projector project) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        if (Float.isNaN(ct) || ct < HALLEY_IN || ct > HALLEY_END || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.1F;
        float scale = cap / CAP;
        float[] comet = project.project(fx.plan.comet(ct));
        float cx = comet != null ? Mth.clamp(comet[0], w * 0.32F, w * 0.68F) : w * 0.5F;
        // under the comet (its tails rise above it), clear of its range readout beside it
        float base = Mth.clamp((comet != null ? comet[1] : h * 0.45F) + h * 0.12F + cap, h * 0.3F + cap, h * 0.82F);
        String word = "HALLEY";
        float tracking = 1.5F;
        float[] xs = new float[word.length()];
        float width = layout(g, word, scale, tracking, xs);
        Line line = new Line(g, Codenames.HALLEY, scale, cap, width, cx);

        float shown = fade * clamp((ct - HALLEY_IN) / 4.0F) * (1.0F - clamp((ct - HALLEY_OUT) / 10.0F));
        TessType.veil(g, cx, base - cap * 0.5F, line.total * 0.8F, cap * 1.4F, 0.4F * shown);
        TessType.draw(g, TessType.Face.LABEL, "1P/HALLEY  ·  PERIODIC COMET", cx, base - cap - h * 0.035F, 0.5F, scale * 0.3F, 2.5F,
            TessType.Paint.flat(0xFF000000 | PALE), 0.85F * shown, 1.0F);
        // the code name first, flickering on like a readout, then sublimating with the word
        float codeLeave = clamp((ct - HALLEY_OUT) / 6.0F);
        float codeIn = clamp((ct - HALLEY_IN) / 3.0F) * (ct < HALLEY_IN + 4.0F && (int) (ct * 3.0F) % 3 == 0 ? 0.35F : 1.0F);
        TessType.draw(g, TessType.Face.DISPLAY, Codenames.HALLEY, line.codeLeft, base - codeLeave * codeLeave * cap * 0.5F, 0.0F,
            line.codeScale, Line.CODE_TRACKING, TessType.Paint.flat(0xFF000000 | ICE), fade * codeIn * (1.0F - codeLeave), 1.2F);
        cx = line.wordCentre;
        float grow = clamp((ct - HALLEY_IN - 4.0F) / 10.0F);
        float half = line.total * 0.56F * grow * (2.0F - grow);
        TessType.rule(g, line.centre - half, line.centre + half, base + h * 0.018F, Math.max(1.0F, h * 0.003F),
            (u, v) -> 0xFF000000 | mix(ICE, PALE, 1.0F - Math.abs(u - 0.5F) * 2.0F), shown);

        // the way the letters come in from (screen y runs down), and how far they start out
        float dx = 0.8F;
        float dy = -0.6F;
        float reach = h * 0.55F;
        for (int i = 0; i < word.length(); i++) {
            float start = HALLEY_IN + 1.3F * i;
            if (ct < start) {
                continue;
            }
            float fly = clamp((ct - start) / 6.0F);
            float eased = 1.0F - cube(1.0F - fly);
            float away = (1.0F - eased) * reach;
            float locked = ct - (start + 6.0F);
            float flash = locked >= 0.0F ? (float) Math.exp(-locked / 1.6F) : 0.75F;
            float leave = clamp((ct - (HALLEY_OUT + 0.8F * i)) / 6.0F);
            float x = cx + xs[i] + dx * away;
            float y = base + dy * away - leave * leave * cap * 0.9F;
            float alpha = fade * Math.min(1.0F, fly * 3.0F) * (1.0F - leave);
            TessType.Paint ice = ice(flash);
            String letter = word.substring(i, i + 1);
            // the streak: fading copies behind it while it flies, a trail of vapour below it as it sublimates
            for (int k = 4; k >= 1; k--) {
                float back = (float) Math.sqrt(1.0F - eased) * h * 0.05F * k;
                float vapour = leave * cap * 0.22F * k;
                float a = alpha * 0.45F * (1.0F - k / 5.0F) * Math.min(1.0F, Math.max((1.0F - eased) * 3.0F, leave * 2.0F));
                letter(g, letter, x + dx * back, y + dy * back + vapour, scale, 0.0F, ice, a, 0.0F);
            }
            letter(g, letter, x, y, scale, 0.0F, ice, alpha, 1.5F);
        }
    }

    /**
     * LUNA: the letters crack in from the middle outward, glowing molten and shuddering as the moon does, cool to the
     * moon's grey, then drop away and burn up as it tears loose and falls.
     */
    static void luna(GuiGraphics g, MoonFx fx, float partial, int w, int h, HalleyHud.@Nullable Projector project) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        if (Float.isNaN(ct) || ct < LUNA_IN || ct > LUNA_END || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.11F;
        float scale = cap / CAP;
        Vec3 moon = fx.plan.centre(ct);
        float[] at = project != null ? project.project(moon) : null;
        float above = h * 0.5F;
        float cx = w * 0.5F;
        if (at != null) {
            cx = Mth.clamp(at[0], w * 0.32F, w * 0.68F);
            float[] edge = project.project(moon.add(screenUp(moon).scale(fx.plan.params.moonRadius())));
            float r = edge != null ? (float) Math.hypot(edge[0] - at[0], edge[1] - at[1]) : 0.0F;
            above = at[1] - r - h * 0.04F;
        }
        // over the moon, below the letterbox and the top corners' lines
        float base = Mth.clamp(above, h * 0.15F + cap, h * 0.5F);
        String word = "LUNA";
        float tracking = 2.0F;
        float[] xs = new float[word.length()];
        float width = layout(g, word, scale, tracking, xs);
        Line line = new Line(g, Codenames.LUNA, scale, cap, width, cx);

        float shown = fade * clamp((ct - LUNA_IN) / 4.0F) * (1.0F - clamp((ct - LUNA_OUT) / 8.0F));
        TessType.veil(g, cx, base - cap * 0.5F, line.total * 0.85F, cap * 1.4F, 0.4F * shown);
        TessType.draw(g, TessType.Face.LABEL, "THE MOONFALL", cx, base - cap - h * 0.035F, 0.5F, scale * 0.3F, 2.5F,
            TessType.Paint.flat(0xFF000000 | 0xFFE2C8), 0.85F * shown, 1.0F);
        float grow = clamp((ct - LUNA_IN - 3.0F) / 12.0F);
        float half = line.total * 0.6F * grow * (2.0F - grow);
        TessType.rule(g, line.centre - half, line.centre + half, base + h * 0.018F, Math.max(1.0F, h * 0.003F),
            (u, v) -> 0xFF000000 | mix(HOT_FOOT, GOLD, 1.0F - Math.abs(u - 0.5F) * 2.0F), shown);
        // the code name glows in with the first letters, cools with them, and is the first thing to break away
        float codeSince = ct - LUNA_IN;
        float codeFall = clamp((ct - LUNA_OUT) / 7.0F);
        float codeDrop = codeFall * codeFall;
        float codeHeat = Math.max(1.0F - clamp((codeSince - 4.0F) / 14.0F), codeFall * 0.8F);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(line.codeLeft, base + codeDrop * cap * 1.8F);
        pose.rotate(-codeDrop * 18.0F * Mth.DEG_TO_RAD);
        TessType.draw(g, TessType.Face.DISPLAY, Codenames.LUNA, -codeDrop * cap * 0.3F, 0.0F, 0.0F, line.codeScale, Line.CODE_TRACKING,
            molten(codeHeat), fade * clamp(codeSince / 3.0F) * (1.0F - codeFall), 1.2F);
        pose.popMatrix();
        cx = line.wordCentre;

        float mid = (word.length() - 1) * 0.5F;
        for (int i = 0; i < word.length(); i++) {
            float rank = Math.abs(i - mid);
            float start = LUNA_IN + 1.6F * rank;
            if (ct < start) {
                continue;
            }
            float since = ct - start;
            float rise = clamp(since / 5.0F);
            float eased = 1.0F - cube(1.0F - rise);
            float shudder = (float) Math.exp(-since / 6.0F) * cap * 0.05F * Mth.sin(ct * 2.7F + i * 1.9F);
            // the outer letters go first, as the moon's edge breaks away
            float fall = clamp((ct - (LUNA_OUT + 1.2F * (1.5F - rank))) / 7.0F);
            float drop = fall * fall;
            float heat = Math.max(1.0F - clamp((since - 4.0F) / 14.0F), fall * 0.8F);
            float x = cx + xs[i] + shudder + (i < mid ? -1.0F : 1.0F) * drop * cap * 0.3F;
            float y = base + (1.0F - eased) * cap * 0.35F + drop * cap * 1.8F;
            float tilt = (i < mid ? -1.0F : 1.0F) * drop * 24.0F;
            float alpha = fade * Math.min(1.0F, rise * 2.5F) * (1.0F - fall);
            String letter = word.substring(i, i + 1);
            TessType.Paint paint = molten(heat);
            // while hot, a soft larger copy round it for a glow
            if (heat > 0.05F) {
                TessType.Paint glow = TessType.Paint.flat(0xFF000000 | HOT_FOOT);
                letter(g, letter, x, y + cap * 0.03F, scale * 1.07F, tilt, glow, alpha * 0.35F * heat, 0.0F);
            }
            letter(g, letter, x, y, scale, tilt, paint, alpha, 1.5F);
        }
    }

    /** Where Proxima's four cards stand: low on the frame, over the captions (the previz's). */
    private static final float PROXIMA_BASE = 0.72F;

    /**
     * PROXIMA CENTAURI as the film first sees the star in its shell: wide-tracked, each letter glowing in out of a red
     * blur, left to right, cooling to a warm white; a rule drawn out under it.
     */
    static void centauri(GuiGraphics g, ProximaFx fx, float partial, int w, int h) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        float in = ProximaPlan.SPACE + 6.0F;
        if (Float.isNaN(ct) || ct < in || ct > in + 41.0F || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.07F;
        float scale = cap / CAP;
        float cx = w * 0.5F;
        float base = h * PROXIMA_BASE;
        String word = "PROXIMA CENTAURI";
        float[] xs = new float[word.length()];
        float width = layout(g, word, scale, 3.0F, xs);
        float shown = fade * window(ct, in, in + 8.0F, in + 32.0F, in + 41.0F);
        TessType.veil(g, cx, base - cap * 0.5F, width * 0.9F, cap * 1.5F, 0.35F * shown);
        TessType.draw(g, TessType.Face.LABEL, "RED DWARF \u00B7 M5.5 Ve \u00B7 4.24 LIGHT YEARS", cx, base - cap - h * 0.03F, 0.5F,
            scale * 0.32F, 3.0F, TessType.Paint.flat(0xFF000000 | 0xE9E1EE), 0.85F * shown, 1.0F);
        float half = w * 0.21F * smooth((ct - in - 6.0F) / 16.0F);
        TessType.rule(g, cx - half, cx + half, base + h * 0.02F, Math.max(1.0F, h * 0.003F),
            (u, v) -> 0xFF000000 | mix(0xFFECE2, 0xFF4A3C, Math.abs(u - 0.5F) * 2.0F), shown);
        for (int i = 0; i < word.length(); i++) {
            float q = smooth((ct - in - 0.9F * i) / 9.0F);
            if (q <= 0.0F || word.charAt(i) == ' ') {
                continue;
            }
            TessType.Paint warm = TessType.Paint.flat(0xFF000000 | mix(0xFF786E, 0xFFECE2, q));
            // the blur it glows in out of: a soft, larger red copy, fading as the letter sharpens
            letter(g, word.substring(i, i + 1), cx + xs[i], base + cap * 0.04F, scale * (1.0F + 0.25F * (1.0F - q)), 0.0F,
                TessType.Paint.flat(0xFF000000 | 0xFF463C), shown * 0.6F * (1.0F - q * 0.7F), 0.0F);
            letter(g, word.substring(i, i + 1), cx + xs[i], base, scale, 0.0F, warm, shown * q, 1.0F + 2.0F * (1.0F - q));
        }
    }

    /** DYSON SPHERE as the iris opens: steel letters sliding in from either side like panels. */
    static void dyson(GuiGraphics g, ProximaFx fx, float partial, int w, int h) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        float in = ProximaPlan.IRIS + 7.0F;
        if (Float.isNaN(ct) || ct < in || ct > in + 36.0F || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.08F;
        float scale = cap / CAP;
        float cx = w * 0.5F;
        float base = h * PROXIMA_BASE;
        String word = "DYSON SPHERE";
        float[] xs = new float[word.length()];
        float width = layout(g, word, scale, 2.6F, xs);
        float shown = fade * window(ct, in, in + 5.0F, in + 28.0F, in + 36.0F);
        TessType.veil(g, cx, base - cap * 0.5F, width * 0.9F, cap * 1.5F, 0.35F * shown);
        TessType.draw(g, TessType.Face.LABEL, "STELLAR ENGINE \u00B7 980 PANELS", cx, base - cap - h * 0.03F, 0.5F, scale * 0.3F, 3.0F,
            TessType.Paint.flat(0xFF000000 | 0xE9E1EE), 0.85F * shown, 1.0F);
        float half = w * 0.15F * smooth((ct - in - 5.0F) / 12.0F);
        TessType.rule(g, cx - half, cx + half, base + h * 0.02F, Math.max(1.0F, h * 0.003F),
            (u, v) -> 0xFF000000 | mix(0xE6EAF2, 0xFF8C5A, Math.abs(u - 0.5F) * 2.0F), shown);
        TessType.Paint steel = (u, v) -> 0xFF000000 | mix(0xE6EAF2, 0x6A7080, v);
        for (int i = 0; i < word.length(); i++) {
            float q = 1.0F - cube(1.0F - clamp((ct - in - 0.7F * i) / 8.0F));
            if (q <= 0.0F) {
                continue;
            }
            float side = i % 2 == 0 ? -1.0F : 1.0F;
            letter(g, word.substring(i, i + 1), cx + xs[i] + side * (1.0F - q) * cap * 2.5F, base, scale, 0.0F, steel, shown * q, 1.0F);
        }
    }

    /** CORONAL MASS EJECTION as the lens charges: half-formed flickering plasma, torn apart as the beam fires. */
    static void cme(GuiGraphics g, ProximaFx fx, float partial, int w, int h) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        float in = ProximaPlan.BEAM - 18.0F;
        float tearAt = ProximaPlan.BEAM;
        if (Float.isNaN(ct) || ct < in || ct > in + 28.0F || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.07F;
        float scale = cap / CAP;
        float cx = w * 0.5F;
        float base = h * PROXIMA_BASE;
        String word = "CORONAL MASS EJECTION";
        float[] xs = new float[word.length()];
        layout(g, word, scale, 1.6F, xs);
        float tear = smooth((ct - tearAt) / 9.0F);
        float shown = fade * window(ct, in, in + 5.0F, in + 21.0F, in + 28.0F);
        float mid = (word.length() - 1) * 0.5F;
        for (int i = 0; i < word.length(); i++) {
            float since = ct - in - 0.4F * i;
            if (since < 0.0F || word.charAt(i) == ' ') {
                continue;
            }
            // half-formed: each letter flickers in and out of its filaments
            float flicker = 0.55F + 0.45F * Mth.sin(ct * 1.1F + i * 1.7F);
            float side = i < mid ? -1.0F : 1.0F;
            float x = cx + xs[i] + (i - mid) * tear * tear * cap * 0.9F;
            float y = base + Mth.sin(i * 2.1F) * tear * cap * 1.2F;
            float alpha = shown * clamp(since / 3.0F) * flicker * (1.0F - tear);
            TessType.Paint plasma = (u, v) -> 0xFF000000 | mix(0xFFF0F5, 0xFF5A8C, v * 0.8F + 0.2F * tear);
            letter(g, word.substring(i, i + 1), x, y, scale, side * tear * 22.0F, plasma, alpha, 1.6F);
        }
    }

    /**
     * SA-04 PROXIMA at the flash, bigger: the code name burns in first, then each letter flares in white-hot and a
     * little large, left to right, settling and burning down to crimson; a rule drawn out under it.
     */
    static void proxima(GuiGraphics g, ProximaFx fx, float partial, int w, int h) {
        float ct = fx.filmTime(partial);
        float fade = fade(fx.filmWeight());
        float in = ProximaPlan.FLASH + 5;
        if (Float.isNaN(ct) || ct < in || ct > ProximaPlan.FRONT || fade <= 0.01F || CutsceneDirector.legacy()) {
            return;
        }
        float cap = h * 0.12F;
        float scale = cap / CAP;
        float cx = w * 0.5F;
        float base = h * PROXIMA_BASE;
        String word = "PROXIMA";
        float tracking = 2.4F;
        float[] xs = new float[word.length()];
        float width = layout(g, word, scale, tracking, xs);
        Line line = new Line(g, Codenames.PROXIMA, scale, cap, width, cx);

        float shown = fade * window(ct, in, in + 4.0F, ProximaPlan.FLASH + 29, ProximaPlan.FRONT);
        TessType.veil(g, cx, base - cap * 0.5F, line.total * 0.85F, cap * 1.4F, 0.4F * shown);
        TessType.draw(g, TessType.Face.LABEL, "SOLAR FLARE \u00B7 CLASS " + dev.ss05.halley.ProximaParams.flareClass(fx.plan.params.power()),
            cx, base - cap - h * 0.035F, 0.5F, scale * 0.28F, 3.0F, TessType.Paint.flat(0xFF000000 | 0xFFC8D2), 0.85F * shown, 1.0F);
        float half = w * 0.18F * smooth((ct - in - 5.0F) / 12.0F);
        TessType.rule(g, line.centre - half, line.centre + half, base + h * 0.02F, Math.max(1.0F, h * 0.003F),
            (u, v) -> 0xFF000000 | mix(0xFFFFFF, FLARE_FOOT, Math.abs(u - 0.5F) * 2.0F), shown);
        float codeIn = smooth((ct - in) / 6.0F);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(line.codeLeft, base);
        TessType.draw(g, TessType.Face.DISPLAY, Codenames.PROXIMA, 0.0F, 0.0F, 0.0F, line.codeScale, Line.CODE_TRACKING,
            TessType.Paint.flat(0xFF000000 | 0xFFB3C0), shown * codeIn, 1.2F);
        pose.popMatrix();
        cx = line.wordCentre;
        for (int i = 0; i < word.length(); i++) {
            float q = smooth((ct - in - 1.0F - i) / 7.0F);
            if (q <= 0.0F) {
                continue;
            }
            float heat = 1.0F - smooth((ct - in - 6.0F - i) / 12.0F);
            float alpha = shown * q;
            String letter = word.substring(i, i + 1);
            float big = scale * (1.25F - 0.25F * q);
            if (heat > 0.05F) {
                letter(g, letter, cx + xs[i], base + cap * 0.03F, big * 1.08F, 0.0F, TessType.Paint.flat(0xFF000000 | FLARE_TOP),
                    alpha * 0.45F * heat, 0.0F);
            }
            letter(g, letter, cx + xs[i], base, big, 0.0F, flare(heat), alpha, 1.0F + heat);
        }
    }

    /** Up from {@code a} to {@code b}, held, down from {@code c} to {@code d}, eased. */
    private static float window(float t, float a, float b, float c, float d) {
        return smooth((t - a) / (b - a)) * (1.0F - smooth((t - c) / (d - c)));
    }

    private static float smooth(float x) {
        float k = clamp(x);
        return k * k * (3.0F - 2.0F * k);
    }

    // ---- Helpers. ---------------------------------------------------------------------------------------------------

    /** The title's line: the code name (smaller, on the same baseline), a gap, then the word, centred together. */
    private static final class Line {
        static final float CODE_TRACKING = 1.0F;
        final float codeScale;
        final float total;
        final float centre;
        final float codeLeft;
        final float wordCentre;

        Line(GuiGraphics g, String code, float scale, float cap, float wordWidth, float centre) {
            this.codeScale = scale * 0.42F;
            float codeWidth = TessType.width(g, TessType.Face.DISPLAY, code, this.codeScale, CODE_TRACKING);
            float gap = cap * 0.4F;
            this.total = codeWidth + gap + wordWidth;
            this.centre = centre;
            this.codeLeft = centre - this.total * 0.5F;
            this.wordCentre = this.codeLeft + codeWidth + gap + wordWidth * 0.5F;
        }
    }

    /** Each letter's centre, from the word's centre, into {@code xs}; returns the word's width. */
    private static float layout(GuiGraphics g, String word, float scale, float tracking, float[] xs) {
        float x = 0.0F;
        for (int i = 0; i < word.length(); i++) {
            float lw = TessType.width(g, TessType.Face.DISPLAY, word.substring(i, i + 1), scale, 0.0F);
            xs[i] = x + lw * 0.5F;
            x += lw + (i < word.length() - 1 ? tracking * scale : 0.0F);
        }
        for (int i = 0; i < xs.length; i++) {
            xs[i] -= x * 0.5F;
        }
        return x;
    }

    /** One letter centred on {@code x}, its baseline on {@code y}, turned {@code degrees} about its middle. */
    private static void letter(GuiGraphics g, String letter, float x, float y, float scale, float degrees, TessType.Paint paint,
                               float alpha, float halo) {
        if (alpha <= 0.004F) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float middle = CAP * scale * 0.5F;
        pose.translate(x, y - middle);
        if (degrees != 0.0F) {
            pose.rotate(degrees * Mth.DEG_TO_RAD);
        }
        TessType.draw(g, TessType.Face.DISPLAY, letter, 0.0F, middle, 0.5F, scale, 0.0F, paint, alpha, halo);
        pose.popMatrix();
    }

    /** Proxima's crimson, top and foot. */
    private static final int FLARE_TOP = 0xFF4A6A;
    private static final int FLARE_FOOT = 0x8E0F2A;

    /** White-hot while {@code heat} is 1, burning down to crimson at 0. */
    private static TessType.Paint flare(float heat) {
        return (u, v) -> 0xFF000000 | mix(mix(FLARE_TOP, FLARE_FOOT, v), mix(0xFFFFFF, 0xFFD0DA, v), heat);
    }

    /** Pale at the top to ice blue at the foot, lifted toward white by {@code flash}. */
    private static TessType.Paint ice(float flash) {
        return (u, v) -> 0xFF000000 | mix(mix(0xFFFFFF, ICE, v * 0.85F), 0xFFFFFF, flash);
    }

    /** White-hot to orange while {@code heat} is 1, the moon's pale grey once it is 0. */
    private static TessType.Paint molten(float heat) {
        return (u, v) -> 0xFF000000 | mix(mix(COOL_TOP, COOL_FOOT, v), mix(HOT_TOP, HOT_FOOT, v), heat);
    }

    /** A direction across the screen's up, at {@code at}, for its size on screen. */
    private static Vec3 screenUp(Vec3 at) {
        Vec3 view = at.subtract(MoonFx.camera()).normalize();
        Vec3 right = view.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        return right.normalize().cross(view).normalize();
    }

    /** The film overlay's own fade (as HalleyHud.film and MoonHud.film). */
    private static float fade(float weight) {
        return Mth.clamp(weight * 1.6F - 0.3F, 0.0F, 1.0F);
    }

    private static int mix(int a, int b, float k) {
        k = clamp(k);
        int r = (int) Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int gr = (int) Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return r << 16 | gr << 8 | bl;
    }

    private static float cube(float k) {
        return k * k * k;
    }

    private static float clamp(float k) {
        return Mth.clamp(k, 0.0F, 1.0F);
    }
}
