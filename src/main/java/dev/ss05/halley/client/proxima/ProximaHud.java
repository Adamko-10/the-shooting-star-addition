package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.film.ProximaStoryboard;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * SA-04 Proxima's overlay on the caster's film (as Luna's MoonHud.film): letterbox, corner brackets, and a caption for
 * each phase with its readout (the flare's class, the front's distance, the shock wave's reach). While the coronal
 * front is overhead its radio burst garbles the caption and drops it out now and then: the "radio blackout", on the
 * skill's own text only (never chat).
 */
public final class ProximaHud {
    private static final int CRIMSON = 0xFF5A78;
    private static final int PALE = 0xFFD8E0;
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
        ProximaPlan p = fx.plan;
        Font font = Minecraft.getInstance().font;
        int a = (int) (fade * 255.0F);
        int ink = a << 24 | CRIMSON;
        int dim = (int) (a * 0.6F) << 24 | PALE;

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

        String caption;
        String detail;
        if (ct < ProximaPlan.ASCENT) {
            caption = "UPLINK";
            detail = "STELLAR REMOTE · SA-04";
        } else if (ct < ProximaPlan.SPACE) {
            caption = "ASCENDING";
            if (ct < ProximaPlan.ORBIT) {
                detail = String.format(Locale.ROOT, "ALTITUDE  %d m", Math.round(ProximaStoryboard.riseHeight(ct)));
            } else {
                double q = Mth.clamp(((ct / 20.0 - 1.6) / 3.8 - 0.36) / 0.64, 0.0, 1.0);
                detail = String.format(Locale.ROOT, "ALTITUDE  %,d km", Math.round(ProximaCosmos.altitude(q) * 6371.0));
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
            caption = "CORONAL MASS EJECTION INBOUND";
            detail = String.format(Locale.ROOT, "FRONT  %.2f km", p.frontDistance(ct) / 1000.0);
        } else if (ct < ProximaPlan.IMPACT) {
            caption = "· · ·";
            detail = "";
        } else if (ct < ProximaPlan.IMPACT + 32) {
            caption = "IMPACT";
            detail = "GROUND ZERO";
        } else {
            caption = "SHOCK FRONT";
            detail = String.format(Locale.ROOT, "%d m", (int) p.waveRadius(ct));
        }
        // the radio blackout: the front's radio burst garbles the readouts, and now and then drops them out
        float garble = ct >= ProximaPlan.FRONT && ct < ProximaPlan.IMPACT + 30 ? 0.3F + 0.3F * Mth.sin(ct * 0.7F) : 0.0F;
        int frame = (int) (ct * 3.0F);
        if (garble > 0.0F && hash(frame, 7) < garble * 0.35F) {
            return;
        }
        caption = garble(caption, garble, frame);
        detail = garble(detail, garble, frame + 101);
        int y = h - m - 26;
        g.drawString(font, caption, (w - font.width(caption)) / 2, y, ink, false);
        g.drawString(font, detail, (w - font.width(detail)) / 2, y + 12, dim, false);
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
