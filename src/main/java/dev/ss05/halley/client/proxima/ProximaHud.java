package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.film.ProximaStoryboard;
import dev.ss05.halley.client.HalleyClientConfig;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * SA-04 Proxima's overlay on the caster's film (as Luna's MoonHud.film): letterbox, corner brackets, and a caption for
 * each phase with its readout (the flare's class, the front's distance, the shock wave's reach), in kilometres or miles
 * (the client's units switch). While the front is overhead its radio burst garbles the caption and drops it out now and
 * then: the "radio blackout", on the skill's own text only (never chat). The caption is drawn by compat's FilmTitle in
 * the title cards' smooth type (the game's pixel font at the overlay's scale broke up).
 */
public final class ProximaHud {
    public static final int CRIMSON = 0xFF5A78;
    public static final int PALE = 0xFFD8E0;
    private static final double MILE = 1609.344;
    private static final double FOOT = 0.3048;
    private static final String NOISE = "#%/0123456789@&*+=";

    private ProximaHud() {
    }

    public static void film(GuiGraphics g, ProximaFx fx, float partial, int w, int h) {
        float ct = fx.filmTime(partial);
        if (Float.isNaN(ct)) {
            return;
        }
        float fade = Mth.clamp(fx.filmWeight() * 1.6F - 0.3F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        int a = (int) (fade * 255.0F);
        int ink = a << 24 | CRIMSON;

        // the white punch through the cloud layer that hides the cut to orbit
        float punch = smooth((ct - (ProximaPlan.ORBIT - 6)) / 5.0F) * (1.0F - smooth((ct - (ProximaPlan.ORBIT + 1)) / 8.0F));
        if (punch > 0.01F) {
            g.fill(0, 0, w, h, (int) (punch * fade * 255.0F) << 24 | 0xFFFFFF);
        }
        int bar = (int) (h * 0.055F * Mth.clamp(ct / 12.0F, 0.0F, 1.0F) * fade);
        if (bar > 0) {
            g.fill(0, 0, w, bar, (int) (a * 0.92F) << 24);
            g.fill(0, h - bar, w, h, (int) (a * 0.92F) << 24);
        }
        int m = 12 + bar;
        corner(g, 12, m, 22, 1, 1, ink);
        corner(g, w - 12, m, 22, -1, 1, ink);
        corner(g, 12, h - m, 22, 1, -1, ink);
        corner(g, w - 12, h - m, 22, -1, -1, ink);
    }

    /** The film's caption and readout this frame, with how strongly they show; null when there is none. */
    @Nullable
    public static Caption caption(ProximaFx fx, float partial) {
        float ct = fx.filmTime(partial);
        if (Float.isNaN(ct)) {
            return null;
        }
        float fade = Mth.clamp(fx.filmWeight() * 1.6F - 0.3F, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return null;
        }
        ProximaPlan p = fx.plan;
        boolean miles = HalleyClientConfig.imperial();
        String caption;
        String detail;
        if (ct < ProximaPlan.ASCENT) {
            caption = "UPLINK";
            detail = "STELLAR REMOTE · SA-04";
        } else if (ct < ProximaPlan.SPACE) {
            caption = "ASCENDING";
            if (ct < ProximaPlan.ORBIT) {
                detail = "ALTITUDE  " + small(ProximaStoryboard.riseHeight(ct), miles);
            } else {
                double q = Mth.clamp(((ct / 20.0 - 1.6) / 3.8 - 0.36) / 0.64, 0.0, 1.0);
                detail = "ALTITUDE  " + large(ProximaCosmos.altitude(q) * 6371000.0, miles, 0);
            }
        } else if (ct < ProximaPlan.IRIS) {
            caption = "PROXIMA CENTAURI";
            detail = "4.24 LIGHT YEARS";
        } else if (ct < ProximaPlan.LOOPS) {
            caption = "DYSON SPHERE";
            detail = "APERTURE OPENING";
        } else if (ct < ProximaPlan.LENS) {
            caption = "MAGNETIC RECONNECTION";
            detail = "CLASS " + ProximaParams.flareClass(p.params.power());
        } else if (ct < ProximaPlan.EARTH) {
            caption = "LENS ARRAY";
            detail = ct < ProximaPlan.BEAM ? "CHARGING" : "BEAM FOCUSED";
        } else if (ct < ProximaPlan.FLASH) {
            caption = "EARTH";
            detail = ct < ProximaPlan.GATE + 8 ? "BEAM INBOUND" : "GATE OPEN";
        } else if (ct < ProximaPlan.FRONT) {
            caption = "FLARE DETECTED";
            detail = "CLASS " + ProximaParams.flareClass(p.params.power());
        } else if (ct < ProximaPlan.HUSH) {
            caption = "SOLAR FLARE INBOUND";
            detail = "FRONT  " + large(p.frontDistance(ct), miles, 2);
        } else if (ct < ProximaPlan.IMPACT) {
            caption = "· · ·";
            detail = "";
        } else if (ct < ProximaPlan.IMPACT + 32) {
            caption = "IMPACT";
            detail = "GROUND ZERO";
        } else {
            caption = "SHOCK FRONT";
            detail = small(p.waveRadius(ct), miles);
        }
        // the radio blackout: the front's radio burst garbles the readouts, and now and then drops them out
        float garble = ct >= ProximaPlan.FRONT && ct < ProximaPlan.IMPACT + 30 ? 0.3F + 0.3F * Mth.sin(ct * 0.7F) : 0.0F;
        int frame = (int) (ct * 3.0F);
        if (garble > 0.0F && hash(frame, 7) < garble * 0.35F) {
            return null;
        }
        return new Caption(garble(caption, garble, frame), garble(detail, garble, frame + 101), fade);
    }

    /** A caption and its readout, as they show this frame, and how strongly (0-1). */
    public record Caption(String caption, String detail, float alpha) {
    }

    /** A short distance: metres, or feet. */
    private static String small(double metres, boolean miles) {
        return miles ? String.format(Locale.ROOT, "%,d ft", Math.round(metres / FOOT)) : String.format(Locale.ROOT, "%,d m", Math.round(metres));
    }

    /** A long distance: kilometres, or miles, to {@code places} decimals. */
    private static String large(double metres, boolean miles, int places) {
        return String.format(Locale.ROOT, "%,." + places + "f " + (miles ? "mi" : "km"), metres / (miles ? MILE : 1000.0));
    }

    /** {@code text} with about {@code amount} of its letters swapped for noise, the same way for a whole frame. */
    private static String garble(String text, float amount, int frame) {
        if (amount <= 0.0F) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c != ' ' && hash(frame, i) < amount ? NOISE.charAt((int) (hash(frame, i + 31) * NOISE.length())) : c);
        }
        return out.toString();
    }

    private static float smooth(float x) {
        float k = Mth.clamp(x, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    private static float hash(int a, int b) {
        long h = (long) a * 0x9E3779B97F4A7C15L ^ (long) b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int argb) {
        g.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm), y + dy, argb);
        g.fill(x, Math.min(y, y + dy * arm), x + dx, Math.max(y, y + dy * arm), argb);
    }
}
